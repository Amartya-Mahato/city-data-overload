package com.lemillion.city_data_overload_server.service;

import com.lemillion.city_data_overload_server.config.SerpApiConfig;
import com.lemillion.city_data_overload_server.model.CityEvent;
import com.lemillion.city_data_overload_server.model.SourceLocation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Scheduled service for automatic data fetching from SerpApi.
 * Runs periodic data collection tasks and stores results in Firestore.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "serpapi.scheduler.enabled", havingValue = "true", matchIfMissing = false)
public class SerpApiSchedulerService {

    private final SerpApiDataFetcher serpApiDataFetcher;
    private final SerpApiConfig serpApiConfig;
    private final EventStreamService eventStreamService;
    private final LocationService locationService;

    /**
     * High-priority data fetch - every 15 minutes
     * Fetches critical data like emergencies and traffic for high-priority locations
     */
    @Scheduled(fixedRate = 900000) // 15 minutes
    public void fetchHighPriorityData() {
        log.info("Starting scheduled high-priority data fetch for all high-priority locations");
        
        try {
            // Focus on high-impact categories
            var priorityCategories = new HashSet<>(Arrays.asList(
                CityEvent.EventCategory.EMERGENCY,
                CityEvent.EventCategory.TRAFFIC
            ));

            // Get high-priority locations
            locationService.getLocationsByPriority(SourceLocation.LocationPriority.HIGH)
                .thenCompose(locations -> {
                    if (locations.isEmpty()) {
                        log.debug("No high-priority locations found, skipping high-priority fetch");
                        return CompletableFuture.completedFuture(new ArrayList<SourceLocation>());
                    }
                    
                    // Filter locations by time window to prevent duplicate fetches
                    int timeWindow = serpApiConfig.getScheduler().getTimeWindows().getHighPriority();
                    return locationService.filterLocationsByTimeWindow(locations, timeWindow);
                })
                .thenAccept(eligibleLocations -> {
                    if (eligibleLocations.isEmpty()) {
                        log.info("No high-priority locations eligible for fetch (within time window)");
                        return;
                    }
                    
                    log.info("Fetching high-priority data for {}/{} eligible locations", 
                            eligibleLocations.size(), eligibleLocations.size());
                    
                    // Fetch data for each eligible location
                    List<CompletableFuture<Void>> locationFutures = eligibleLocations.stream()
                        .<CompletableFuture<Void>>map(location -> fetchDataForLocation(location, priorityCategories, true))
                        .collect(Collectors.toList());
                    
                    CompletableFuture.allOf(locationFutures.toArray(new CompletableFuture[0]))
                        .thenRun(() -> log.info("Completed high-priority data fetch for all eligible locations"));
                })
                .exceptionally(throwable -> {
                    log.error("Error in high-priority data fetch", throwable);
                    return null;
                });
            
        } catch (Exception e) {
            log.error("Failed to start high-priority data fetch", e);
        }
    }

    /**
     * Standard data fetch - every hour
     * Fetches all categories of data for medium priority locations
     */
    @Scheduled(fixedRate = 3600000) // 1 hour
    public void fetchStandardData() {
        log.info("Starting scheduled standard data fetch for all medium-priority locations");
        
        try {
            locationService.getLocationsByPriority(SourceLocation.LocationPriority.MEDIUM)
                .thenCompose(locations -> {
                    if (locations.isEmpty()) {
                        log.debug("No medium-priority locations found, using default location");
                        // Fallback to default location if no locations are registered
                        CompletableFuture<Map<String, Object>> fetchFuture = serpApiDataFetcher
                            .fetchProcessAndStore(serpApiConfig.getDefaultLocation());
                        handleStandardFetchResult(fetchFuture);
                        return CompletableFuture.completedFuture(new ArrayList<SourceLocation>());
                    }
                    
                    // Filter locations by time window
                    int timeWindow = serpApiConfig.getScheduler().getTimeWindows().getStandard();
                    return locationService.filterLocationsByTimeWindow(locations, timeWindow);
                })
                .thenAccept(eligibleLocations -> {
                    if (eligibleLocations.isEmpty()) {
                        log.info("No medium-priority locations eligible for fetch (within time window)");
                        return;
                    }
                    
                    log.info("Fetching standard data for {}/{} eligible locations", 
                            eligibleLocations.size(), eligibleLocations.size());
                    
                    // Fetch data for each eligible location
                    List<CompletableFuture<Map<String, Object>>> locationFutures = eligibleLocations.stream()
                        .<CompletableFuture<Map<String, Object>>>map(location -> {
                            return serpApiDataFetcher.fetchProcessAndStore(location.getFormattedLocation())
                                .thenApply(result -> {
                                    // Update location stats after successful fetch
                                    if ("SUCCESS".equals(result.get("status"))) {
                                        int eventCount = (Integer) result.getOrDefault("processedEventsStored", 0);
                                        locationService.updateLocationStats(location.getId(), eventCount);
                                    }
                                    return result;
                                });
                        })
                        .collect(Collectors.toList());
                    
                    CompletableFuture.allOf(locationFutures.toArray(new CompletableFuture[0]))
                        .thenRun(() -> {
                            // Aggregate results
                            List<Map<String, Object>> results = locationFutures.stream()
                                .map(CompletableFuture::join)
                                .collect(Collectors.toList());
                            
                            int totalSuccess = (int) results.stream()
                                .filter(result -> "SUCCESS".equals(result.get("status")))
                                .count();
                            
                            log.info("Completed standard data fetch: {}/{} locations successful", 
                                   totalSuccess, eligibleLocations.size());
                            
                            // Publish aggregate metrics
                            Map<String, Object> aggregateResult = Map.of(
                                "status", "SUCCESS",
                                "locationsProcessed", eligibleLocations.size(),
                                "successfulLocations", totalSuccess,
                                "timestamp", LocalDateTime.now()
                            );
                            publishDataFetchMetrics(aggregateResult);
                        });
                })
                .exceptionally(throwable -> {
                    log.error("Error in standard data fetch", throwable);
                    return null;
                });
            
        } catch (Exception e) {
            log.error("Failed to start standard data fetch", e);
        }
    }

    /**
     * Health check - every 30 minutes
     * Tests SerpApi connectivity and service health
     */
    @Scheduled(fixedRate = 1800000) // 30 minutes
    public void performHealthCheck() {
        log.debug("Performing SerpApi health check");
        
        try {
            serpApiDataFetcher.testConnection()
                .thenAccept(result -> {
                    if ("SUCCESS".equals(result.get("status"))) {
                        log.debug("SerpApi health check passed");
                    } else {
                        log.warn("SerpApi health check failed: {}", result);
                    }
                })
                .exceptionally(throwable -> {
                    log.error("SerpApi health check error", throwable);
                    return null;
                });
                
        } catch (Exception e) {
            log.error("Failed to perform SerpApi health check", e);
        }
    }

    /**
     * Daily cleanup - runs at 2 AM
     * Cleans up old processed data and optimizes storage
     */
    @Scheduled(cron = "0 0 2 * * *") // Daily at 2 AM
    public void performDailyCleanup() {
        log.info("Starting daily cleanup task");
        
        try {
            // Log daily statistics
            Map<String, Object> stats = serpApiDataFetcher.getFetcherStatistics();
            log.info("Daily SerpApi statistics: {}", stats);
            
            // Cleanup logic could be added here
            // For example: remove old cache entries, compress old data, etc.
            
        } catch (Exception e) {
            log.error("Error during daily cleanup", e);
        }
    }

    /**
     * Emergency monitoring - every 5 minutes
     * Monitors specifically for emergency situations across all locations
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void monitorEmergencies() {
        log.debug("Running emergency monitoring check for all active locations");
        
        try {
            var emergencyCategories = new HashSet<>(Arrays.asList(
                CityEvent.EventCategory.EMERGENCY
            ));

            locationService.getActiveLocations()
                .thenCompose(locations -> {
                    if (locations.isEmpty()) {
                        log.debug("No active locations found for emergency monitoring");
                        return CompletableFuture.completedFuture(new ArrayList<SourceLocation>());
                    }
                    
                    // Filter locations by time window for emergency monitoring
                    int timeWindow = serpApiConfig.getScheduler().getTimeWindows().getEmergencyMonitoring();
                    return locationService.filterLocationsByTimeWindow(locations, timeWindow);
                })
                .thenAccept(eligibleLocations -> {
                    if (eligibleLocations.isEmpty()) {
                        log.debug("No locations eligible for emergency monitoring (within time window)");
                        return;
                    }
                    
                    log.debug("Emergency monitoring for {}/{} eligible locations", 
                            eligibleLocations.size(), eligibleLocations.size());
                    
                    // Monitor each eligible location for emergencies
                    List<CompletableFuture<List<CityEvent>>> emergencyFutures = eligibleLocations.stream()
                        .<CompletableFuture<List<CityEvent>>>map(location ->
                            serpApiDataFetcher.fetchSpecificCategories(location.getFormattedLocation(), emergencyCategories)
                                .thenApply(events -> {
                                    // Update location stats for emergency monitoring
                                    locationService.updateLocationStats(location.getId(), events.size());
                                    return events;
                                }))
                        .collect(Collectors.toList());
                    
                    CompletableFuture.allOf(emergencyFutures.toArray(new CompletableFuture[0]))
                        .thenRun(() -> {
                            // Collect all critical events from all locations
                            List<CityEvent> allCriticalEvents = emergencyFutures.stream()
                                .map(CompletableFuture::join)
                                .flatMap(List::stream)
                                .filter(event -> event.getSeverity() == CityEvent.EventSeverity.CRITICAL)
                                .collect(Collectors.toList());
                            
                            if (!allCriticalEvents.isEmpty()) {
                                log.warn("CRITICAL EMERGENCY DETECTED: {} critical events found across {} locations", 
                                       allCriticalEvents.size(), eligibleLocations.size());
                                
                                // Immediate processing and alerting for critical emergencies
                                allCriticalEvents.forEach(event -> {
                                    try {
                                        // Publish immediately with high priority
                                        eventStreamService.broadcastCityEvent(event);
                                        log.warn("Published critical emergency: {} - {}", 
                                               event.getId(), event.getTitle());
                                    } catch (Exception e) {
                                        log.error("FAILED to publish critical emergency: {}", 
                                                event.getId(), e);
                                    }
                                });
                            } else {
                                log.debug("Emergency monitoring: no critical events detected across all locations");
                            }
                        });
                })
                .exceptionally(throwable -> {
                    log.error("Error in emergency monitoring", throwable);
                    return null;
                });
                
        } catch (Exception e) {
            log.error("Failed to run emergency monitoring", e);
        }
    }

    /**
     * Handle standard fetch result for fallback scenarios
     */
    private void handleStandardFetchResult(CompletableFuture<Map<String, Object>> fetchFuture) {
        fetchFuture.thenAccept(result -> {
            log.info("Standard fetch completed: {}", result);
            
            // Optionally publish metrics or notifications
            if ("SUCCESS".equals(result.get("status"))) {
                int totalEvents = (Integer) result.getOrDefault("totalEventsFetched", 0);
                int storedEvents = (Integer) result.getOrDefault("processedEventsStored", 0);
                
                log.info("Successfully processed {}/{} events in standard fetch", 
                        storedEvents, totalEvents);
                
                // Publish processing statistics
                publishDataFetchMetrics(result);
            }
        }).exceptionally(throwable -> {
            log.error("Error in fallback standard data fetch", throwable);
            return null;
        });
    }

    /**
     * Fetch data for a specific location and categories - FIXED to follow proper pipeline
     */
    private CompletableFuture<Void> fetchDataForLocation(SourceLocation location, Set<CityEvent.EventCategory> categories, boolean publishCritical) {
        log.debug("Starting pipeline fetch for location: {} with categories: {}", location.getShortName(), categories);
        
                 // Step 1: Fetch raw data from sources
        return serpApiDataFetcher.fetchSpecificCategories(location.getFormattedLocation(), categories)
            .thenCompose(rawEvents -> {
                log.debug("Fetched {} raw events for location: {}", rawEvents.size(), location.getShortName());
                
                if (rawEvents.isEmpty()) {
                    return CompletableFuture.<List<CityEvent>>completedFuture(List.of());
                }
                
                // Step 2-5: Follow the complete pipeline: AggregatorAgent → AnalyzerAgent → Firestore + BigQuery
                return serpApiDataFetcher.processEventsWithAIPipeline(rawEvents, location.getFormattedLocation());
            })
            .thenAccept(processedEvents -> {
                log.debug("Pipeline completed for location: {} - {} events processed", 
                        location.getShortName(), processedEvents.size());
                
                // Update location stats with fetch timestamp
                locationService.updateLocationStats(location.getId(), processedEvents.size());
                
                if (publishCritical) {
                    // Publish critical events immediately
                    processedEvents.stream()
                        .filter(event -> event.getSeverity() == CityEvent.EventSeverity.CRITICAL || 
                                       event.getSeverity() == CityEvent.EventSeverity.HIGH)
                        .forEach(event -> {
                            try {
                                eventStreamService.broadcastCityEvent(event);
                                log.debug("Published critical event: {} for location: {}", event.getId(), location.getShortName());
                            } catch (Exception e) {
                                log.error("Error publishing critical event: {} for location: {}", 
                                        event.getId(), location.getShortName(), e);
                            }
                        });
                }
            })
            .exceptionally(throwable -> {
                log.error("Error in pipeline for location: {}", location.getShortName(), throwable);
                return null;
            });
    }

    private void publishDataFetchMetrics(Map<String, Object> result) {
        try {
            // Create a metrics event
            CityEvent metricsEvent = CityEvent.builder()
                .id("metrics-" + System.currentTimeMillis())
                .title("SerpApi Data Fetch Metrics")
                .description("Automated data collection metrics")
                .category(CityEvent.EventCategory.OTHER)
                .severity(CityEvent.EventSeverity.LOW)
                .source(CityEvent.EventSource.SYSTEM_GENERATED)
                .timestamp(LocalDateTime.now())
                .metadata(result)
                .confidenceScore(1.0)
                .build();
            
            eventStreamService.broadcastCityEvent(metricsEvent);
            
        } catch (Exception e) {
            log.error("Error publishing data fetch metrics", e);
        }
    }

    /**
     * Get time window statistics for all locations
     */
    public CompletableFuture<Map<String, Object>> getTimeWindowStatistics() {
        return locationService.getActiveLocations()
            .thenApply(locations -> {
                LocalDateTime now = LocalDateTime.now();
                Map<String, Object> stats = new HashMap<>();
                
                // Time window configurations
                var timeWindows = serpApiConfig.getScheduler().getTimeWindows();
                stats.put("timeWindows", Map.of(
                    "highPriority", timeWindows.getHighPriority() + " minutes",
                    "standard", timeWindows.getStandard() + " minutes", 
                    "emergencyMonitoring", timeWindows.getEmergencyMonitoring() + " minutes",
                    "healthCheck", timeWindows.getHealthCheck() + " minutes"
                ));
                
                // Location fetch eligibility stats
                long eligibleForHighPriority = locations.stream()
                    .filter(loc -> loc.getPriorityLevel() == SourceLocation.LocationPriority.HIGH)
                    .filter(loc -> isLocationEligibleForFetch(loc, timeWindows.getHighPriority()))
                    .count();
                
                long eligibleForStandard = locations.stream()
                    .filter(loc -> loc.getPriorityLevel() == SourceLocation.LocationPriority.MEDIUM)
                    .filter(loc -> isLocationEligibleForFetch(loc, timeWindows.getStandard()))
                    .count();
                
                long eligibleForEmergency = locations.stream()
                    .filter(SourceLocation::getActive)
                    .filter(loc -> isLocationEligibleForFetch(loc, timeWindows.getEmergencyMonitoring()))
                    .count();
                
                stats.put("eligibilityStats", Map.of(
                    "highPriorityEligible", eligibleForHighPriority,
                    "standardEligible", eligibleForStandard,
                    "emergencyMonitoringEligible", eligibleForEmergency,
                    "totalLocations", locations.size()
                ));
                
                // Recently fetched locations
                List<Map<String, Object>> recentFetches = locations.stream()
                    .filter(loc -> loc.getLastFetchedAt() != null)
                    .filter(loc -> loc.getLastFetchedAt().isAfter(now.minusHours(1)))
                    .<Map<String, Object>>map(loc -> Map.of(
                        "locationId", loc.getId(),
                        "shortName", loc.getShortName(),
                        "lastFetched", loc.getLastFetchedAt(),
                        "minutesAgo", java.time.Duration.between(loc.getLastFetchedAt(), now).toMinutes(),
                        "priority", loc.getPriorityLevel()
                    ))
                    .collect(Collectors.toList());
                
                stats.put("recentFetches", recentFetches);
                stats.put("generatedAt", now);
                
                return stats;
            })
            .exceptionally(throwable -> {
                log.error("Error generating time window statistics", throwable);
                return Map.of("error", throwable.getMessage());
            });
    }

    /**
     * Reset time windows for specific locations (for debugging/testing)
     */
    public CompletableFuture<Map<String, Object>> resetTimeWindowsForLocations(List<String> locationIds) {
        log.info("Resetting time windows for {} locations", locationIds.size());
        
        // Simply reset lastFetchedAt to null for all specified locations
        List<CompletableFuture<Void>> resetFutures = locationIds.stream()
            .<CompletableFuture<Void>>map(locationId -> locationService.resetLocationTimeWindow(locationId))
            .collect(Collectors.toList());
        
        return CompletableFuture.allOf(resetFutures.toArray(new CompletableFuture[0]))
            .thenApply(v -> {
                Map<String, Object> result = Map.of(
                    "status", "SUCCESS",
                    "locationsRequested", locationIds.size(),
                    "resetAt", LocalDateTime.now()
                );
                
                log.info("Reset time windows for {} locations", locationIds.size());
                return result;
            })
            .exceptionally(throwable -> {
                log.error("Error resetting time windows", throwable);
                return Map.of(
                    "status", "ERROR",
                    "error", throwable.getMessage(),
                    "resetAt", LocalDateTime.now()
                );
            });
    }

    /**
     * Check if a location is eligible for fetch based on time window
     */
    private boolean isLocationEligibleForFetch(SourceLocation location, int timeWindowMinutes) {
        LocalDateTime lastFetched = location.getLastFetchedAt();
        if (lastFetched == null) {
            return true;
        }
        
        LocalDateTime windowThreshold = LocalDateTime.now().minusMinutes(timeWindowMinutes);
        return lastFetched.isBefore(windowThreshold);
    }
} 