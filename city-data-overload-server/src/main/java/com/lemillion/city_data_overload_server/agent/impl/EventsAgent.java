package com.lemillion.city_data_overload_server.agent.impl;

import com.lemillion.city_data_overload_server.agent.Agent;
import com.lemillion.city_data_overload_server.agent.AgentRequest;
import com.lemillion.city_data_overload_server.agent.AgentResponse;
import com.lemillion.city_data_overload_server.model.CityEvent;
import com.lemillion.city_data_overload_server.service.BigQueryService;
import com.lemillion.city_data_overload_server.service.FirestoreService;
import com.lemillion.city_data_overload_server.service.VertexAiService; // Add VertexAI integration
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.HashMap;

/**
 * Events Agent - Responsible for fetching current events from data sources.
 * Primary strategy: Query Firestore first (fast, real-time)
 * Fallback strategy: Query BigQuery (comprehensive, historical)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventsAgent implements Agent {

    /**
     * Enhanced Events Agent - now with Vertex AI-powered insights
     */
    private final BigQueryService bigQueryService;
    private final FirestoreService firestoreService;
    private final VertexAiService vertexAiService; // Add VertexAI integration

    @Override
    public String getAgentId() {
        return "events-agent";
    }

    @Override
    public AgentType getAgentType() {
        return AgentType.DATA_FETCHER;
    }

    @Override
    public String getDescription() {
        return "Fetches current city events from Firestore and BigQuery data sources";
    }

    @Override
    public CompletableFuture<AgentResponse> processRequest(AgentRequest request) {
        long startTime = System.currentTimeMillis();
        
        log.info("Events agent processing request: {} of type: {} with params: lat={}, lng={}, area={}", 
                request.getRequestId(), request.getRequestType(), 
                request.getLatitude(), request.getLongitude(), request.getArea());
        
        try {
            return fetchEventsWithFallback(request)
                .thenApply(events -> {
                    // Check if we should include all severities (for chat context)
                    boolean includeAllSeverities = request.getParameters() != null && 
                        Boolean.TRUE.equals(request.getParameters().get("includeAllSeverities"));
                    
                    List<CityEvent> finalEvents;
                    if (includeAllSeverities) {
                        // For chat: include all events including HIGH/CRITICAL
                        finalEvents = events;
                    } else {
                        // For regular endpoints: filter out HIGH/CRITICAL severity events
                        finalEvents = filterEventsForDisplay(events);
                    }
                    
                    AgentResponse response = AgentResponse.success(
                        request.getRequestId(),
                        getAgentId(),
                        includeAllSeverities ? 
                            String.format("Retrieved %d events (all severities)", finalEvents.size()) :
                            String.format("Retrieved %d events (%d after filtering)", events.size(), finalEvents.size())
                    );
                    
                    response.setEvents(finalEvents);
                    response.setTotalResults(finalEvents.size());
                    response.setProcessingTimeMs(System.currentTimeMillis() - startTime);
                    
                    log.info("Events agent completed request: {} with {} events in {}ms", 
                            request.getRequestId(), finalEvents.size(), response.getProcessingTimeMs());
                    
                    // Log first few events for debugging
                    if (!finalEvents.isEmpty()) {
                        log.debug("First event details: id={}, title={}, area={}, severity={}", 
                                finalEvents.get(0).getId(), finalEvents.get(0).getTitle(), 
                                finalEvents.get(0).getLocation() != null ? finalEvents.get(0).getLocation().getArea() : "null",
                                finalEvents.get(0).getSeverity());
                    }
                    
                    return response;
                })
                .exceptionally(throwable -> {
                    log.error("Events agent failed to process request: {}", 
                            request.getRequestId(), throwable);
                    return AgentResponse.error(
                        request.getRequestId(),
                        getAgentId(),
                        "Failed to fetch events: " + throwable.getMessage(),
                        "EVENTS_FETCH_ERROR"
                    );
                });
                
        } catch (Exception e) {
            log.error("Error in events agent request processing", e);
            return CompletableFuture.completedFuture(
                AgentResponse.error(request.getRequestId(), getAgentId(), 
                    "Events agent error: " + e.getMessage(), "EVENTS_AGENT_ERROR")
            );
        }
    }

    @Override
    public boolean canHandle(String requestType) {
        return "GET_EVENTS".equals(requestType) || 
               "GET_EVENTS_BY_LOCATION".equals(requestType) ||
               "GET_EVENTS_BY_CATEGORY".equals(requestType) ||
               "GET_RECENT_EVENTS".equals(requestType);
    }

    @Override
    public HealthStatus getHealthStatus() {
        try {
            // Test both services with a simple query
            CompletableFuture<List<CityEvent>> firestoreTest = firestoreService.getRecentEventsByArea("Koramangala", 1);
            
            // Wait briefly for the test
            firestoreTest.get(java.util.concurrent.TimeUnit.SECONDS.toMillis(2), 
                            java.util.concurrent.TimeUnit.MILLISECONDS);
            
            return HealthStatus.HEALTHY;
        } catch (Exception e) {
            log.warn("Events agent health check failed", e);
            return HealthStatus.DEGRADED;
        }
    }

    /**
     * Fetch events with AI-powered enhancements and recommendations
     */
    private CompletableFuture<List<CityEvent>> fetchEventsWithFallback(AgentRequest request) {
        // Try Firestore first (faster, real-time data)
        return fetchFromFirestore(request)
            .thenCompose(events -> {
                if (events.isEmpty() || shouldFallbackToBigQuery(request, events)) {
                    log.info("Falling back to BigQuery for request: {}", request.getRequestId());
                    return fetchFromBigQuery(request);
                } else {
                    log.debug("Using Firestore results for request: {}", request.getRequestId());
                    return CompletableFuture.completedFuture(events);
                }
            })
            .thenCompose(events -> {
                // Enhanced: Apply AI processing to improve event relevance and insights
                return enhanceEventsWithAI(events, request);
            })
            .exceptionally(throwable -> {
                log.warn("Event fetching failed for request: {}, returning empty list", 
                        request.getRequestId(), throwable);
                return List.of();
            });
    }

    /**
     * Enhance events with AI-powered insights and recommendations
     */
    private CompletableFuture<List<CityEvent>> enhanceEventsWithAI(List<CityEvent> events, AgentRequest request) {
        if (events.isEmpty()) {
            return CompletableFuture.completedFuture(events);
        }
        
        log.debug("Enhancing {} events with AI insights", events.size());
        
        // Process events in parallel with AI enhancements
        List<CompletableFuture<CityEvent>> enhancementFutures = events.stream()
            .map(event -> enhanceSingleEventWithAI(event, request))
            .collect(Collectors.toList());
        
        return CompletableFuture.allOf(enhancementFutures.toArray(new CompletableFuture[0]))
            .thenApply(ignored -> {
                List<CityEvent> enhancedEvents = enhancementFutures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());
                
                // Sort by AI-enhanced relevance score
                return sortEventsByAIRelevance(enhancedEvents, request);
            });
    }

    /**
     * Enhance a single event with AI analysis
     */
    private CompletableFuture<CityEvent> enhanceSingleEventWithAI(CityEvent event, AgentRequest request) {
        // Build context for AI analysis
        String enhancementContext = buildEventEnhancementContext(event, request);
        
        return vertexAiService.categorizeEvent(enhancementContext, "EVENT_ENHANCEMENT")
            .thenApply(aiResult -> {
                return applyAIEnhancementsToEvent(event, aiResult, request);
            })
            .exceptionally(throwable -> {
                log.debug("AI enhancement failed for event {}, using original", event.getId());
                return event;
            });
    }

    /**
     * Build context for AI event enhancement
     */
    private String buildEventEnhancementContext(CityEvent event, AgentRequest request) {
        StringBuilder context = new StringBuilder();
        context.append("Enhance this city event with additional insights and user relevance: ");
        
        if (event.getTitle() != null) {
            context.append("Title: ").append(event.getTitle()).append(". ");
        }
        if (event.getDescription() != null) {
            context.append("Description: ").append(event.getDescription()).append(". ");
        }
        if (event.getLocation() != null && event.getLocation().getArea() != null) {
            context.append("Location: ").append(event.getLocation().getArea()).append(". ");
        }
        
        // Add user context
        if (request.getArea() != null) {
            context.append("User is interested in ").append(request.getArea()).append(" area. ");
        }
        if (request.getCategory() != null) {
            context.append("User is looking for ").append(request.getCategory()).append(" events. ");
        }
        
        return context.toString();
    }

    /**
     * Apply AI enhancements to event
     */
    private CityEvent applyAIEnhancementsToEvent(CityEvent originalEvent, Map<String, Object> aiResult, AgentRequest request) {
        CityEvent.CityEventBuilder builder = originalEvent.toBuilder();
        Map<String, Object> metadata = originalEvent.getMetadata() != null ? 
            new HashMap<>(originalEvent.getMetadata()) : new HashMap<>();
        
        // Enhance title if it's generic
        if (aiResult.containsKey("title") && isGenericTitle(originalEvent.getTitle())) {
            String aiTitle = (String) aiResult.get("title");
            if (aiTitle != null && !aiTitle.equals(originalEvent.getTitle())) {
                builder.title(aiTitle);
                metadata.put("ai_enhanced_title", true);
            }
        }
        
        // Add AI-generated summary if missing or improve existing
        if (aiResult.containsKey("summary")) {
            String aiSummary = (String) aiResult.get("summary");
            if (aiSummary != null) {
                builder.aiSummary(aiSummary);
                metadata.put("ai_enhanced_summary", true);
            }
        }
        
        // Enhance keywords
        if (aiResult.containsKey("keywords")) {
            @SuppressWarnings("unchecked")
            List<String> aiKeywords = (List<String>) aiResult.get("keywords");
            if (aiKeywords != null) {
                List<String> combinedKeywords = combineKeywords(originalEvent.getKeywords(), aiKeywords);
                builder.keywords(combinedKeywords);
                metadata.put("ai_enhanced_keywords", true);
            }
        }
        
        // Calculate AI relevance score based on user context
        double relevanceScore = calculateAIRelevanceScore(originalEvent, aiResult, request);
        metadata.put("ai_relevance_score", relevanceScore);
        metadata.put("ai_enhancement_timestamp", LocalDateTime.now());
        metadata.put("events_agent_ai_version", "1.0");
        
        return builder.metadata(metadata).build();
    }

    /**
     * Calculate AI-based relevance score for user context
     */
    private double calculateAIRelevanceScore(CityEvent event, Map<String, Object> aiResult, AgentRequest request) {
        double baseScore = 0.5;
        double confidence = (Double) aiResult.getOrDefault("confidence", 0.5);
        
        // Location relevance
        if (request.getArea() != null && event.getLocation() != null) {
            if (request.getArea().equalsIgnoreCase(event.getLocation().getArea())) {
                baseScore += 0.3;
            }
        }
        
        // Category relevance
        if (request.getCategory() != null) {
            if (request.getCategory().equalsIgnoreCase(event.getCategory().name())) {
                baseScore += 0.2;
            }
        }
        
        // Severity relevance (higher severity = higher relevance)
        if (event.getSeverity() != null) {
            switch (event.getSeverity()) {
                case CRITICAL -> baseScore += 0.3;
                case HIGH -> baseScore += 0.2;
                case MODERATE -> baseScore += 0.1;
                case LOW -> baseScore += 0.0;
            }
        }
        
        // Time relevance (more recent = higher relevance)
        if (event.getTimestamp() != null) {
            long hoursAgo = java.time.Duration.between(event.getTimestamp(), LocalDateTime.now()).toHours();
            if (hoursAgo <= 2) baseScore += 0.2;
            else if (hoursAgo <= 6) baseScore += 0.15;
            else if (hoursAgo <= 24) baseScore += 0.1;
        }
        
        // Combine with AI confidence
        return Math.min(1.0, (baseScore * 0.7) + (confidence * 0.3));
    }

    /**
     * Sort events by AI-calculated relevance score
     */
    private List<CityEvent> sortEventsByAIRelevance(List<CityEvent> events, AgentRequest request) {
        return events.stream()
            .sorted((e1, e2) -> {
                double score1 = getRelevanceScore(e1);
                double score2 = getRelevanceScore(e2);
                return Double.compare(score2, score1); // Descending order
            })
            .collect(Collectors.toList());
    }

    /**
     * Get AI relevance score from event metadata
     */
    private double getRelevanceScore(CityEvent event) {
        if (event.getMetadata() != null && event.getMetadata().containsKey("ai_relevance_score")) {
            return (Double) event.getMetadata().get("ai_relevance_score");
        }
        return 0.5; // Default score
    }

    /**
     * Generate AI-powered fallback events when fetching fails
     */
    private List<CityEvent> generateAIFallbackEvents(AgentRequest request) {
        log.info("Generating AI fallback events for request: {}", request.getRequestId());
        
        try {
            // Create context for AI to generate relevant event suggestions
            String fallbackContext = buildFallbackContext(request);
            
            // Use AI to generate event suggestions
            CompletableFuture<Map<String, Object>> aiFallback = vertexAiService.categorizeEvent(
                fallbackContext, "EVENT_SUGGESTIONS");
            
            Map<String, Object> aiResult = aiFallback.join();
            
            return createFallbackEventsFromAI(aiResult, request);
            
        } catch (Exception e) {
            log.error("AI fallback event generation failed", e);
            return createBasicFallbackEvents(request);
        }
    }

    /**
     * Build context for AI fallback event generation
     */
    private String buildFallbackContext(AgentRequest request) {
        StringBuilder context = new StringBuilder();
        context.append("Generate relevant city event suggestions for Bengaluru based on: ");
        
        if (request.getArea() != null) {
            context.append("Area: ").append(request.getArea()).append(". ");
        }
        if (request.getCategory() != null) {
            context.append("Category: ").append(request.getCategory()).append(". ");
        }
        if (request.getQuery() != null) {
            context.append("User query: ").append(request.getQuery()).append(". ");
        }
        
        context.append("Provide helpful information about typical events or activities in this context.");
        
        return context.toString();
    }

    /**
     * Create fallback events from AI suggestions
     */
    private List<CityEvent> createFallbackEventsFromAI(Map<String, Object> aiResult, AgentRequest request) {
        List<CityEvent> fallbackEvents = new ArrayList<>();
        
        // Extract suggestions from AI result
        String title = (String) aiResult.getOrDefault("title", "No current events found");
        String summary = (String) aiResult.getOrDefault("summary", "Check back later for updates");
        
        CityEvent fallbackEvent = CityEvent.builder()
            .id("ai_fallback_" + System.currentTimeMillis())
            .title(title)
            .description(summary)
            .content("This is an AI-generated suggestion based on your query.")
            .category(request.getCategory() != null ? 
                parseCategory(request.getCategory()) : CityEvent.EventCategory.COMMUNITY)
            .severity(CityEvent.EventSeverity.LOW)
            .source(CityEvent.EventSource.SYSTEM_GENERATED)
            .timestamp(LocalDateTime.now())
            .location(createLocationFromRequest(request))
            .aiSummary("AI-generated event suggestion")
            .confidenceScore(0.6)
            .metadata(Map.of(
                "ai_generated", true,
                "fallback_event", true,
                "events_agent_ai_version", "1.0"
            ))
            .build();
        
        fallbackEvents.add(fallbackEvent);
        return fallbackEvents;
    }

    // Helper methods for AI enhancements

    private boolean isGenericTitle(String title) {
        if (title == null) return true;
        String lowerTitle = title.toLowerCase();
        return lowerTitle.contains("event") || lowerTitle.contains("incident") || 
               lowerTitle.contains("issue") || title.length() < 10;
    }

    private List<String> combineKeywords(List<String> original, List<String> aiKeywords) {
        Set<String> combined = new HashSet<>();
        if (original != null) combined.addAll(original);
        if (aiKeywords != null) combined.addAll(aiKeywords);
        return new ArrayList<>(combined);
    }

    private CityEvent.EventCategory parseCategory(String categoryString) {
        try {
            return CityEvent.EventCategory.valueOf(categoryString.toUpperCase());
        } catch (IllegalArgumentException e) {
            return CityEvent.EventCategory.COMMUNITY;
        }
    }

    private CityEvent.LocationData createLocationFromRequest(AgentRequest request) {
        if (request.getLatitude() != null && request.getLongitude() != null) {
            return CityEvent.LocationData.builder()
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .area(request.getArea() != null ? request.getArea() : "Bengaluru")
                .build();
        }
        
        return CityEvent.LocationData.builder()
            .area(request.getArea() != null ? request.getArea() : "Bengaluru")
            .build();
    }

    private List<CityEvent> createBasicFallbackEvents(AgentRequest request) {
        // Basic fallback without AI
        return List.of(CityEvent.builder()
            .id("basic_fallback_" + System.currentTimeMillis())
            .title("No events found for your criteria")
            .description("Try adjusting your search criteria or check back later")
            .category(CityEvent.EventCategory.COMMUNITY)
            .severity(CityEvent.EventSeverity.LOW)
            .source(CityEvent.EventSource.SYSTEM_GENERATED)
            .timestamp(LocalDateTime.now())
            .build());
    }

    /**
     * Fetch events from Firestore (primary data source)
     */
    private CompletableFuture<List<CityEvent>> fetchFromFirestore(AgentRequest request) {
        try {
            // Determine query type based on request parameters
            if (request.getLatitude() != null && request.getLongitude() != null) {
                // Location-based query
                double radiusKm = request.getRadiusKm() != null ? request.getRadiusKm() : 5.0;
                int maxResults = request.getMaxResults() != null ? request.getMaxResults() : 50;
                
                return firestoreService.getEventsByLocation(
                    request.getLatitude(), 
                    request.getLongitude(), 
                    radiusKm, 
                    maxResults
                );
                
            } else if (request.getCategory() != null && request.getSeverity() != null) {
                // Category and severity query
                CityEvent.EventCategory category = CityEvent.EventCategory.valueOf(request.getCategory());
                CityEvent.EventSeverity severity = CityEvent.EventSeverity.valueOf(request.getSeverity());
                int maxResults = request.getMaxResults() != null ? request.getMaxResults() : 50;
                
                return firestoreService.getEventsByCategoryAndSeverity(category, severity, maxResults);
                
            } else if (request.getArea() != null) {
                // Area-based query
                int maxResults = request.getMaxResults() != null ? request.getMaxResults() : 50;
                
                return firestoreService.getRecentEventsByArea(request.getArea(), maxResults);
                
            } else {
                // General recent events query
                return firestoreService.getRecentEventsByArea("Koramangala", 50)
                    .thenCompose(koramangalaEvents -> {
                        // Get events from multiple areas and combine
                        List<CompletableFuture<List<CityEvent>>> areaQueries = new ArrayList<>();
                        
                        String[] areas = {"HSR Layout", "Indiranagar", "Whitefield", "Electronic City"};
                        for (String area : areas) {
                            areaQueries.add(firestoreService.getRecentEventsByArea(area, 10));
                        }
                        
                        return CompletableFuture.allOf(areaQueries.toArray(new CompletableFuture[0]))
                            .thenApply(ignored -> {
                                List<CityEvent> allEvents = new ArrayList<>(koramangalaEvents);
                                
                                for (CompletableFuture<List<CityEvent>> areaQuery : areaQueries) {
                                    try {
                                        allEvents.addAll(areaQuery.join());
                                    } catch (Exception e) {
                                        log.warn("Failed to get events for an area", e);
                                    }
                                }
                                
                                // Sort by timestamp and limit results
                                return allEvents.stream()
                                    .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                                    .limit(request.getMaxResults() != null ? request.getMaxResults() : 50)
                                    .toList();
                            });
                    });
            }
            
        } catch (Exception e) {
            log.error("Error constructing Firestore query", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Fetch events from BigQuery (fallback data source)
     */
    private CompletableFuture<List<CityEvent>> fetchFromBigQuery(AgentRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Determine query type based on request parameters
                if (request.getLatitude() != null && request.getLongitude() != null) {
                    // Location-based query
                    double radiusKm = request.getRadiusKm() != null ? request.getRadiusKm() : 5.0;
                    int maxResults = request.getMaxResults() != null ? request.getMaxResults() : 50;
                    LocalDateTime startTime = request.getStartTime() != null ? 
                        request.getStartTime() : LocalDateTime.now().minusDays(7);
                    LocalDateTime endTime = request.getEndTime() != null ? 
                        request.getEndTime() : LocalDateTime.now();
                    
                    return bigQueryService.queryEventsByLocationAndTime(
                        request.getLatitude(), 
                        request.getLongitude(), 
                        radiusKm, 
                        startTime, 
                        endTime, 
                        maxResults
                    );
                    
                } else if (request.getCategory() != null && request.getSeverity() != null) {
                    // Category and severity query
                    CityEvent.EventCategory category = CityEvent.EventCategory.valueOf(request.getCategory());
                    CityEvent.EventSeverity severity = CityEvent.EventSeverity.valueOf(request.getSeverity());
                    LocalDateTime since = request.getStartTime() != null ? 
                        request.getStartTime() : LocalDateTime.now().minusDays(7);
                    int maxResults = request.getMaxResults() != null ? request.getMaxResults() : 50;
                    
                    return bigQueryService.queryEventsByCategoryAndSeverity(category, severity, since, maxResults);
                    
                } else {
                    // General query - get all recent events
                    int maxResults = request.getMaxResults() != null ? request.getMaxResults() : 50;
                    log.info("Fetching {} recent events from BigQuery", maxResults);
                    
                    List<CityEvent> allEvents = bigQueryService.queryAllRecentEvents(maxResults);
                    log.info("Retrieved {} events from BigQuery", allEvents.size());
                    
                    return allEvents;
                }
                
            } catch (Exception e) {
                log.error("Error querying BigQuery", e);
                throw new RuntimeException("BigQuery query failed", e);
            }
        });
    }

    /**
     * Determines if we should fallback to BigQuery based on Firestore results
     */
    private boolean shouldFallbackToBigQuery(AgentRequest request, List<CityEvent> firestoreResults) {
        // Fallback conditions:
        // 1. No results from Firestore
        if (firestoreResults.isEmpty()) {
            return true;
        }
        
        // 2. Request specifically asks for historical data (older than 6 hours)
        if (request.getStartTime() != null && 
            request.getStartTime().isBefore(LocalDateTime.now().minusHours(6))) {
            return true;
        }
        
        // 3. Very few results and we expect more (based on area/category popularity)
        if (firestoreResults.size() < 3 && isHighActivityContext(request)) {
            return true;
        }
        
        return false;
    }

    /**
     * Checks if the request context typically has high activity
     */
    private boolean isHighActivityContext(AgentRequest request) {
        // High activity areas
        if (request.getArea() != null) {
            String[] highActivityAreas = {"Koramangala", "HSR Layout", "Indiranagar", 
                                         "Whitefield", "Electronic City", "Marathahalli"};
            for (String area : highActivityAreas) {
                if (area.equalsIgnoreCase(request.getArea())) {
                    return true;
                }
            }
        }
        
        // High activity categories
        if (request.getCategory() != null) {
            String[] highActivityCategories = {"TRAFFIC", "PUBLIC_TRANSPORT", "WEATHER"};
            for (String category : highActivityCategories) {
                if (category.equalsIgnoreCase(request.getCategory())) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Filter out HIGH/CRITICAL severity events from regular event display
     * These events should only appear in alerts, not in regular events list
     */
    private List<CityEvent> filterEventsForDisplay(List<CityEvent> events) {
        return events.stream()
            .filter(event -> {
                CityEvent.EventSeverity severity = event.getSeverity();
                // Only show LOW, MODERATE, and MEDIUM severity events in regular list
                return severity != CityEvent.EventSeverity.HIGH && 
                       severity != CityEvent.EventSeverity.CRITICAL;
            })
            .collect(Collectors.toList());
    }
} 