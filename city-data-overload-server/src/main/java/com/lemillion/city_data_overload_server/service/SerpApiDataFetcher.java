package com.lemillion.city_data_overload_server.service;

import com.lemillion.city_data_overload_server.agent.AgentRequest;
import com.lemillion.city_data_overload_server.agent.impl.AggregatorAgent;
import com.lemillion.city_data_overload_server.agent.impl.AnalyzerAgent;
import com.lemillion.city_data_overload_server.config.SerpApiConfig;
import com.lemillion.city_data_overload_server.model.CityEvent;
import com.lemillion.city_data_overload_server.service.fetcher.BaseSerpApiFetcher;
import com.lemillion.city_data_overload_server.service.fetcher.EmergencyDataFetcher;
import com.lemillion.city_data_overload_server.service.fetcher.TrafficDataFetcher;
import com.lemillion.city_data_overload_server.service.fetcher.WaterIssueDataFetcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Main orchestrator service for SerpApi data fetching operations.
 * Coordinates multiple category-specific fetchers to gather comprehensive city data.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SerpApiDataFetcher {

    private final SerpApiConfig serpApiConfig;
    private final FirestoreService firestoreService;
    private final VertexAiService vertexAiService;
    private final BigQueryService bigQueryService;
    
    // Category-specific fetchers
    private final TrafficDataFetcher trafficDataFetcher;
    private final EmergencyDataFetcher emergencyDataFetcher;
    private final WaterIssueDataFetcher waterIssueDataFetcher;
    
    // AI Agents for proper pipeline
    private final AggregatorAgent aggregatorAgent;
    private final AnalyzerAgent analyzerAgent;

    /**
     * Fetch data from all categories for a specific location
     */
    public CompletableFuture<List<CityEvent>> fetchAllCategoryData(String location) {
        log.info("Starting comprehensive data fetch for location: {}", location);
        
        List<BaseSerpApiFetcher> allFetchers = Arrays.asList(
            trafficDataFetcher,
            emergencyDataFetcher,
            waterIssueDataFetcher
        );

        List<CompletableFuture<List<CityEvent>>> futures = allFetchers.stream()
            .map(fetcher -> {
                log.debug("Starting fetch for category: {}", fetcher.getCategory());
                return fetcher.fetchCategoryData(location)
                    .handle((result, throwable) -> {
                        if (throwable != null) {
                            log.error("Error fetching data for category {}: {}", 
                                    fetcher.getCategory(), throwable.getMessage());
                            return new ArrayList<CityEvent>();
                        }
                        log.debug("Successfully fetched {} events for category: {}", 
                                result.size(), fetcher.getCategory());
                        return result;
                    });
            })
            .collect(Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> {
                List<CityEvent> allEvents = futures.stream()
                    .map(CompletableFuture::join)
                    .flatMap(List::stream)
                    .collect(Collectors.toList());
                
                log.info("Completed data fetch for location: {}. Total events: {}", 
                        location, allEvents.size());
                
                return allEvents;
            });
    }

    /**
     * Fetch data for specific categories only
     */
    public CompletableFuture<List<CityEvent>> fetchSpecificCategories(
            String location, 
            Set<CityEvent.EventCategory> categories) {
        
        log.info("Starting targeted data fetch for location: {} and categories: {}", 
                location, categories);

        List<BaseSerpApiFetcher> targetFetchers = getFilteredFetchers(categories);
        
        List<CompletableFuture<List<CityEvent>>> futures = targetFetchers.stream()
            .map(fetcher -> fetcher.fetchCategoryData(location)
                .handle((result, throwable) -> {
                    if (throwable != null) {
                        log.error("Error fetching data for category {}: {}", 
                                fetcher.getCategory(), throwable.getMessage());
                        return new ArrayList<CityEvent>();
                    }
                    return result;
                }))
            .collect(Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .collect(Collectors.toList()));
    }

    /**
     * NEW: Process events through the complete AI pipeline for scheduler
     */
    public CompletableFuture<List<CityEvent>> processEventsWithAIPipeline(List<CityEvent> events, String location) {
        if (events.isEmpty()) {
            return CompletableFuture.completedFuture(events);
        }

        log.info("Processing {} events through COMPLETE AI pipeline for location: {}", events.size(), location);

        // Step 1: AggregatorAgent - Deduplicate and aggregate similar events
        return callAggregatorAgent(events)
            .thenCompose(aggregatedEvents -> {
                log.info("Aggregation completed: {} → {} events for location: {}", 
                        events.size(), aggregatedEvents.size(), location);
                
                // Step 2: AnalyzerAgent - Analyze, enhance, and store in both systems
                return callAnalyzerAgent(aggregatedEvents);
            })
            .thenApply(analyzedEvents -> {
                log.info("Complete pipeline finished for location: {} - {} events processed", 
                        location, analyzedEvents.size());
                return analyzedEvents;
            })
            .exceptionally(throwable -> {
                log.error("Error in complete AI pipeline for location: {}, falling back to basic processing", 
                        location, throwable);
                return performFallbackProcessing(events);
            });
    }

    /**
     * Call AggregatorAgent properly
     */
    private CompletableFuture<List<CityEvent>> callAggregatorAgent(List<CityEvent> events) {
        AgentRequest aggregatorRequest = AgentRequest.builder()
            .requestId("agg_" + System.currentTimeMillis())
            .requestType("AGGREGATE_EVENTS")
            .timestamp(LocalDateTime.now())
            .parameters(Map.of("events", events))
            .build();

        return aggregatorAgent.processRequest(aggregatorRequest)
            .thenApply(response -> {
                if (response.isSuccess() && response.getEvents() != null) {
                    log.debug("AggregatorAgent processed successfully: {} events", response.getEvents().size());
                    return response.getEvents();
                } else {
                    log.warn("AggregatorAgent failed, using fallback aggregation");
                    return performBasicAggregation(events);
                }
            });
    }

    /**
     * Call AnalyzerAgent properly - includes dual storage
     */
    private CompletableFuture<List<CityEvent>> callAnalyzerAgent(List<CityEvent> events) {
        AgentRequest analyzerRequest = AgentRequest.builder()
            .requestId("ana_" + System.currentTimeMillis())
            .requestType("ANALYZE_EVENTS")
            .timestamp(LocalDateTime.now())
            .parameters(Map.of("events", events))
            .build();

        return analyzerAgent.processRequest(analyzerRequest)
            .thenApply(response -> {
                if (response.isSuccess()) {
                    log.debug("AnalyzerAgent processed successfully with dual storage");
                    // AnalyzerAgent handles dual storage internally
                    return events; // Events are already stored by AnalyzerAgent
                } else {
                    log.warn("AnalyzerAgent failed, using fallback analysis and storage");
                    return performFallbackAnalysisAndStorage(events);
                }
            });
    }

    /**
     * Fallback processing when AI agents fail
     */
    private List<CityEvent> performFallbackProcessing(List<CityEvent> events) {
        try {
            // Basic aggregation
            List<CityEvent> aggregated = performBasicAggregation(events);
            
            // Basic analysis
            List<CityEvent> analyzed = performBasicAnalysis(aggregated);
            
            // Manual dual storage
            analyzed.forEach(this::storeToBothSystems);
            
            return analyzed;
        } catch (Exception e) {
            log.error("Fallback processing failed", e);
            return events;
        }
    }

    /**
     * Fallback analysis with manual dual storage
     */
    private List<CityEvent> performFallbackAnalysisAndStorage(List<CityEvent> events) {
        List<CityEvent> analyzed = performBasicAnalysis(events);
        
        // Manual dual storage
        analyzed.forEach(this::storeToBothSystems);
        
        return analyzed;
    }

    /**
     * Store event to both Firestore and BigQuery
     */
    private void storeToBothSystems(CityEvent event) {
        try {
            // Store to Firestore
            firestoreService.storeCityEvent(event)
                .thenRun(() -> log.debug("Stored event {} to Firestore", event.getId()))
                .exceptionally(throwable -> {
                    log.error("Failed to store event {} to Firestore", event.getId(), throwable);
                    return null;
                });
            
            // Store to BigQuery
            CompletableFuture.runAsync(() -> {
                try {
                    bigQueryService.storeCityEvent(event);
                    log.debug("Stored event {} to BigQuery", event.getId());
                } catch (Exception e) {
                    log.error("Failed to store event {} to BigQuery", event.getId(), e);
                }
            });
            
        } catch (Exception e) {
            log.error("Error in dual storage for event {}", event.getId(), e);
        }
    }

    /**
     * UPDATED: fetchProcessAndStore now uses the complete pipeline
     */
    public CompletableFuture<Map<String, Object>> fetchProcessAndStore(String location) {
        return fetchAllCategoryData(location)
            .thenCompose(events -> {
                log.info("Starting complete AI pipeline for {} events at location: {}", events.size(), location);
                
                // Use the complete pipeline
                return processEventsWithAIPipeline(events, location)
                    .thenApply(processedEvents -> {
                                Map<String, Object> result = new HashMap<>();
                                result.put("location", location);
                                result.put("totalEventsFetched", events.size());
                                result.put("processedEventsStored", processedEvents.size());
                                result.put("timestamp", LocalDateTime.now());
                                result.put("status", "SUCCESS");
                        result.put("pipeline", "COMPLETE_AI_PIPELINE");
                                
                        log.info("Complete pipeline finished for location: {} - {}/{} events processed", 
                                location, processedEvents.size(), events.size());
                                
                                return result;
                    });
            })
            .handle((result, throwable) -> {
                if (throwable != null) {
                    log.error("Error in complete pipeline for location: {}", location, throwable);
                    Map<String, Object> errorResult = new HashMap<>();
                    errorResult.put("location", location);
                    errorResult.put("status", "ERROR");
                    errorResult.put("error", throwable.getMessage());
                    errorResult.put("timestamp", LocalDateTime.now());
                    return errorResult;
                }
                return result;
            });
    }

    /**
     * Get statistics about available data fetchers
     */
    public Map<String, Object> getFetcherStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        List<BaseSerpApiFetcher> allFetchers = Arrays.asList(
            trafficDataFetcher,
            emergencyDataFetcher,
            waterIssueDataFetcher
        );

        List<Map<String, Object>> fetcherInfo = allFetchers.stream()
            .map(fetcher -> {
                Map<String, Object> info = new HashMap<>();
                info.put("category", fetcher.getCategory().name());
                info.put("defaultQueries", fetcher.getDefaultQueries());
                info.put("queryCount", fetcher.getDefaultQueries().size());
                return info;
            })
            .collect(Collectors.toList());

        stats.put("totalFetchers", allFetchers.size());
        stats.put("fetchers", fetcherInfo);
        stats.put("supportedCategories", allFetchers.stream()
            .map(fetcher -> fetcher.getCategory().name())
            .collect(Collectors.toList()));
        
        return stats;
    }

    /**
     * Test connection to SerpApi service
     */
    public CompletableFuture<Map<String, Object>> testConnection() {
        return trafficDataFetcher.fetchRawData("test query", serpApiConfig.getDefaultLocation())
            .thenApply(response -> {
                Map<String, Object> result = new HashMap<>();
                if (response != null && !response.hasError()) {
                    result.put("status", "SUCCESS");
                    result.put("message", "SerpApi connection successful");
                    result.put("searchMetadata", response.getSearchMetadata());
                } else {
                    result.put("status", "ERROR");
                    result.put("message", "SerpApi connection failed");
                    if (response != null && response.hasError()) {
                        result.put("error", response.getErrorMessage());
                    }
                }
                result.put("timestamp", LocalDateTime.now());
                return result;
            })
            .handle((result, throwable) -> {
                if (throwable != null) {
                    Map<String, Object> errorResult = new HashMap<>();
                    errorResult.put("status", "ERROR");
                    errorResult.put("message", "Connection test failed");
                    errorResult.put("error", throwable.getMessage());
                    errorResult.put("timestamp", LocalDateTime.now());
                    return errorResult;
                }
                return result;
            });
    }

    private List<BaseSerpApiFetcher> getFilteredFetchers(Set<CityEvent.EventCategory> categories) {
        List<BaseSerpApiFetcher> allFetchers = Arrays.asList(
            trafficDataFetcher,
            emergencyDataFetcher,
            waterIssueDataFetcher
        );

        return allFetchers.stream()
            .filter(fetcher -> categories.contains(fetcher.getCategory()))
            .collect(Collectors.toList());
    }

    private CompletableFuture<List<CityEvent>> processEventsWithAI(List<CityEvent> events) {
        if (events.isEmpty()) {
            return CompletableFuture.completedFuture(events);
        }

        log.info("Processing {} events through AI pipeline (Aggregator -> Analyzer)", events.size());

        // Step 1: Use AggregatorAgent to deduplicate and aggregate similar events
        return aggregateEventsWithAI(events)
            .thenCompose(aggregatedEvents -> {
                log.info("Aggregation completed: {} -> {} events", events.size(), aggregatedEvents.size());
                
                // Step 2: Use AnalyzerAgent to analyze and enhance the aggregated events
                return analyzeEventsWithAI(aggregatedEvents);
            })
            .exceptionally(throwable -> {
                log.error("Error in AI processing pipeline, falling back to original events", throwable);
                return events; // Fallback to original events
            });
    }

    /**
     * Use AggregatorAgent to deduplicate and aggregate similar events
     */
    private CompletableFuture<List<CityEvent>> aggregateEventsWithAI(List<CityEvent> events) {
        // Create request for AggregatorAgent
        AgentRequest aggregatorRequest = AgentRequest.builder()
            .requestId("agg_" + System.currentTimeMillis())
            .requestType("AGGREGATE_EVENTS")
            .timestamp(LocalDateTime.now())
            .parameters(Map.of("events", events))
            .build();

        // Use AggregatorAgent (would need dependency injection)
        return CompletableFuture.supplyAsync(() -> {
            try {
                // For now, we'll implement basic aggregation logic directly
                // In production, this would use the AggregatorAgent
                return performBasicAggregation(events);
            } catch (Exception e) {
                log.error("Error in event aggregation", e);
                return events; // Fallback to original events
            }
        });
    }

    /**
     * Use AnalyzerAgent to analyze and enhance events
     */
    private CompletableFuture<List<CityEvent>> analyzeEventsWithAI(List<CityEvent> events) {
        // Create request for AnalyzerAgent
        AgentRequest analyzerRequest = AgentRequest.builder()
            .requestId("ana_" + System.currentTimeMillis())
            .requestType("ANALYZE_EVENTS")
            .timestamp(LocalDateTime.now())
            .parameters(Map.of("events", events))
            .build();

        // Use AnalyzerAgent (would need dependency injection)
        return CompletableFuture.supplyAsync(() -> {
            try {
                // For now, we'll implement basic analysis logic directly
                // In production, this would use the AnalyzerAgent
                return performBasicAnalysis(events);
            } catch (Exception e) {
                log.error("Error in event analysis", e);
                return events; // Fallback to original events
            }
        });
    }

    /**
     * Basic aggregation logic (placeholder for AggregatorAgent)
     */
    private List<CityEvent> performBasicAggregation(List<CityEvent> events) {
        // Group events by category and area
        Map<String, List<CityEvent>> groups = events.stream()
            .collect(Collectors.groupingBy(event -> {
                String category = event.getCategory() != null ? event.getCategory().name() : "UNKNOWN";
                String area = (event.getLocation() != null && event.getLocation().getArea() != null) 
                    ? event.getLocation().getArea() : "UNKNOWN";
                return category + "_" + area;
            }));

        List<CityEvent> aggregatedEvents = new ArrayList<>();
        
        for (List<CityEvent> group : groups.values()) {
            if (group.size() == 1) {
                aggregatedEvents.add(group.get(0));
            } else {
                // Create aggregated event for group
                CityEvent aggregated = createAggregatedEvent(group);
                aggregatedEvents.add(aggregated);
            }
        }

        log.debug("Basic aggregation: {} -> {} events", events.size(), aggregatedEvents.size());
        return aggregatedEvents;
    }

    /**
     * Basic analysis logic (placeholder for AnalyzerAgent)
     */
    private List<CityEvent> performBasicAnalysis(List<CityEvent> events) {
        return events.stream()
            .map(this::enhanceEventBasic)
            .collect(Collectors.toList());
    }

    /**
     * Create aggregated event from a group of similar events
     */
    private CityEvent createAggregatedEvent(List<CityEvent> group) {
        CityEvent primary = group.get(0);
        
        return CityEvent.builder()
            .id("agg_" + System.currentTimeMillis() + "_" + group.size())
            .title("Multiple " + primary.getCategory() + " reports")
            .description(createAggregatedDescription(group))
            .category(primary.getCategory())
            .location(primary.getLocation())
            .timestamp(getLatestTimestamp(group))
            .severity(getHighestSeverity(group))
            .source(CityEvent.EventSource.SYSTEM_GENERATED)
            .keywords(combineKeywords(group))
            .aiSummary("Aggregated from " + group.size() + " similar events")
            .confidenceScore(calculateAverageConfidence(group))
            .metadata(Map.of(
                "aggregated_from", group.stream().map(CityEvent::getId).collect(Collectors.toList()),
                "event_count", group.size()
            ))
            .createdAt(LocalDateTime.now())
            .build();
    }

    /**
     * Basic event enhancement
     */
    private CityEvent enhanceEventBasic(CityEvent event) {
        CityEvent.CityEventBuilder builder = event.toBuilder();
        
        // Fill missing fields with defaults
        if (event.getTimestamp() == null) {
            builder.timestamp(LocalDateTime.now());
        }
        
        if (event.getConfidenceScore() == null) {
            builder.confidenceScore(0.7); // Default confidence
        }
        
        if (event.getCreatedAt() == null) {
            builder.createdAt(LocalDateTime.now());
        }
        
        builder.updatedAt(LocalDateTime.now());
        
        return builder.build();
    }

    // Helper methods for aggregation

    private String createAggregatedDescription(List<CityEvent> group) {
        return String.format("%d similar events reported in the area", group.size());
    }

    private LocalDateTime getLatestTimestamp(List<CityEvent> group) {
        return group.stream()
            .map(CityEvent::getTimestamp)
            .filter(Objects::nonNull)
            .max(LocalDateTime::compareTo)
            .orElse(LocalDateTime.now());
    }

    private CityEvent.EventSeverity getHighestSeverity(List<CityEvent> group) {
        return group.stream()
            .map(CityEvent::getSeverity)
            .filter(Objects::nonNull)
            .max(Enum::compareTo)
            .orElse(CityEvent.EventSeverity.LOW);
    }

    private List<String> combineKeywords(List<CityEvent> group) {
        return group.stream()
            .map(CityEvent::getKeywords)
            .filter(Objects::nonNull)
                .flatMap(List::stream)
            .distinct()
            .limit(10)
            .collect(Collectors.toList());
    }

    private Double calculateAverageConfidence(List<CityEvent> group) {
        return group.stream()
            .mapToDouble(e -> e.getConfidenceScore() != null ? e.getConfidenceScore() : 0.5)
            .average()
            .orElse(0.5);
    }
} 