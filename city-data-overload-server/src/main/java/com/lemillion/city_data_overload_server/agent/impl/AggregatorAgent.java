package com.lemillion.city_data_overload_server.agent.impl;

import com.lemillion.city_data_overload_server.agent.Agent;
import com.lemillion.city_data_overload_server.agent.AgentRequest;
import com.lemillion.city_data_overload_server.agent.AgentResponse;
import com.lemillion.city_data_overload_server.model.CityEvent;
import com.lemillion.city_data_overload_server.service.VertexAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Aggregator Agent - Uses Vertex AI to deduplicate and aggregate similar events
 * from multiple sources into unique event clusters.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AggregatorAgent implements Agent {

    private final VertexAiService vertexAiService;
    
    private static final double SIMILARITY_THRESHOLD = 0.75;
    private static final int MAX_EVENTS_PER_CLUSTER = 10;
    private static final int MAX_PROCESSING_BATCH_SIZE = 50;

    @Override
    public String getAgentId() {
        return "aggregator-agent";
    }

    @Override
    public AgentType getAgentType() {
        return AgentType.SYNTHESIZER;
    }

    @Override
    public String getDescription() {
        return "AI-powered event aggregation agent that deduplicates and aggregates similar events from multiple sources into unique event clusters using Vertex AI";
    }

    @Override
    public boolean canHandle(String requestType) {
        return "AGGREGATE_EVENTS".equals(requestType) || 
               "DEDUPLICATE_EVENTS".equals(requestType) ||
               "SYNTHESIZE_SIMILAR_EVENTS".equals(requestType);
    }

    @Override
    public HealthStatus getHealthStatus() {
        try {
            // Simple health check - could be enhanced with dependency checks
            return HealthStatus.HEALTHY;
        } catch (Exception e) {
            log.error("Health check failed", e);
            return HealthStatus.DEGRADED;
        }
    }

    @Override
    public CompletableFuture<AgentResponse> processRequest(AgentRequest request) {
        log.info("AggregatorAgent processing request: {} with {} events", 
                request.getRequestId(), getEventCount(request));

        try {
            List<CityEvent> events = extractEventsFromRequest(request);
            
            if (events.isEmpty()) {
                return CompletableFuture.completedFuture(createEmptyResponse(request));
            }

            return aggregateEvents(events)
                .thenApply(aggregatedEvents -> createSuccessResponse(request, aggregatedEvents))
                .exceptionally(throwable -> {
                    log.error("Error in AggregatorAgent processing", throwable);
                    return createErrorResponse(request, throwable);
                });

        } catch (Exception e) {
            log.error("Error processing AggregatorAgent request", e);
            return CompletableFuture.completedFuture(createErrorResponse(request, e));
        }
    }

    /**
     * Main aggregation logic - groups similar events and creates unified representations
     */
    private CompletableFuture<List<CityEvent>> aggregateEvents(List<CityEvent> events) {
        log.info("Starting event aggregation for {} events", events.size());

        // Step 1: Pre-filter and group by basic similarity
        Map<String, List<CityEvent>> basicGroups = groupEventsByBasicSimilarity(events);
        log.debug("Created {} basic groups from {} events", basicGroups.size(), events.size());

        // Step 2: AI-powered detailed aggregation for each group
        List<CompletableFuture<List<CityEvent>>> aggregationFutures = basicGroups.values().stream()
            .filter(group -> !group.isEmpty())
            .map(this::aggregateEventGroup)
            .collect(Collectors.toList());

        return CompletableFuture.allOf(aggregationFutures.toArray(new CompletableFuture[0]))
            .thenApply(ignored -> {
                List<CityEvent> aggregatedEvents = aggregationFutures.stream()
                    .map(CompletableFuture::join)
                    .flatMap(List::stream)
                    .collect(Collectors.toList());

                log.info("Aggregation completed: {} events reduced to {} unique events", 
                        events.size(), aggregatedEvents.size());

                return aggregatedEvents;
            });
    }

    /**
     * Group events by basic similarity (category, location, time proximity)
     */
    private Map<String, List<CityEvent>> groupEventsByBasicSimilarity(List<CityEvent> events) {
        Map<String, List<CityEvent>> groups = new HashMap<>();

        for (CityEvent event : events) {
            String groupKey = generateBasicGroupKey(event);
            groups.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(event);
        }

        return groups;
    }

    /**
     * Generate a basic grouping key for initial clustering
     */
    private String generateBasicGroupKey(CityEvent event) {
        StringBuilder key = new StringBuilder();
        
        // Category
        key.append(event.getCategory() != null ? event.getCategory().name() : "UNKNOWN");
        key.append("_");
        
        // Area (approximate location)
        if (event.getLocation() != null && event.getLocation().getArea() != null) {
            key.append(event.getLocation().getArea().toLowerCase().replaceAll("\\s+", "_"));
        } else {
            key.append("unknown_area");
        }
        key.append("_");
        
        // Time window (group events within 2-hour windows)
        if (event.getTimestamp() != null) {
            int hourWindow = event.getTimestamp().getHour() / 2; // 2-hour windows
            key.append(event.getTimestamp().toLocalDate()).append("_h").append(hourWindow);
        } else {
            key.append("unknown_time");
        }

        return key.toString();
    }

    /**
     * Use AI to aggregate a group of potentially similar events
     */
    private CompletableFuture<List<CityEvent>> aggregateEventGroup(List<CityEvent> eventGroup) {
        if (eventGroup.size() == 1) {
            return CompletableFuture.completedFuture(eventGroup);
        }

        log.debug("AI aggregating group of {} events", eventGroup.size());

        return findSimilarEventClusters(eventGroup)
            .thenCompose(this::createAggregatedEventsFromClusters);
    }

    /**
     * Use Vertex AI to identify clusters of similar events within a group
     */
    private CompletableFuture<List<List<CityEvent>>> findSimilarEventClusters(List<CityEvent> events) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Create similarity matrix using AI-powered content analysis
                Map<String, List<CityEvent>> clusters = new HashMap<>();
                List<CityEvent> processed = new ArrayList<>();

                for (CityEvent currentEvent : events) {
                    boolean addedToCluster = false;

                    // Check against existing clusters using AI-powered similarity
                    for (Map.Entry<String, List<CityEvent>> clusterEntry : clusters.entrySet()) {
                        List<CityEvent> cluster = clusterEntry.getValue();
                        
                        // Use representative event from cluster for comparison
                        CityEvent representative = cluster.get(0);
                        
                        // Use AI-powered similarity detection
                        if (areEventsSimilarWithAI(currentEvent, representative).join()) {
                            cluster.add(currentEvent);
                            addedToCluster = true;
                            break;
                        }
                    }

                    // Create new cluster if no similar cluster found
                    if (!addedToCluster) {
                        String clusterId = "cluster_" + clusters.size();
                        clusters.put(clusterId, new ArrayList<>(List.of(currentEvent)));
                    }
                }

                log.debug("AI-powered clustering: Created {} clusters from {} events", clusters.size(), events.size());
                return new ArrayList<>(clusters.values());

            } catch (Exception e) {
                log.error("Error in AI-powered event clustering", e);
                // Fallback: each event is its own cluster
                return events.stream()
                    .map(List::of)
                    .collect(Collectors.toList());
            }
        });
    }

    /**
     * Use Vertex AI to check if two events are similar
     */
    private CompletableFuture<Boolean> areEventsSimilarWithAI(CityEvent event1, CityEvent event2) {
        // Fast pre-checks before AI analysis
        if (!event1.getCategory().equals(event2.getCategory())) {
            return CompletableFuture.completedFuture(false);
        }

        if (!areLocationsProximate(event1.getLocation(), event2.getLocation())) {
            return CompletableFuture.completedFuture(false);
        }

        if (!areTimestampsProximate(event1.getTimestamp(), event2.getTimestamp())) {
            return CompletableFuture.completedFuture(false);
        }

        // Use Vertex AI for content similarity analysis
        String combinedText1 = buildEventContext(event1);
        String combinedText2 = buildEventContext(event2);
        
        String context = String.format(
            "Determine if these two city events are similar enough to be aggregated:\n\nEvent 1: %s\n\nEvent 2: %s",
            combinedText1, combinedText2
        );

        return vertexAiService.categorizeEvent(context, "SIMILARITY_CHECK")
            .thenApply(result -> {
                // Extract similarity score from AI response
                double confidence = (Double) result.getOrDefault("confidence", 0.0);
                return confidence >= SIMILARITY_THRESHOLD;
            })
            .exceptionally(throwable -> {
                log.warn("AI similarity check failed, using fallback", throwable);
                return areContentsimilar(event1, event2); // Fallback to basic similarity
            });
    }

    /**
     * Build context string for an event
     */
    private String buildEventContext(CityEvent event) {
        StringBuilder context = new StringBuilder();
        if (event.getTitle() != null) context.append(event.getTitle()).append(" ");
        if (event.getDescription() != null) context.append(event.getDescription()).append(" ");
        if (event.getLocation() != null && event.getLocation().getArea() != null) {
            context.append("in ").append(event.getLocation().getArea()).append(" ");
        }
        return context.toString().trim();
    }

    /**
     * Check if two events are similar using AI-based analysis
     */
    private boolean areEventsSimilar(CityEvent event1, CityEvent event2) {
        try {
            // Fast pre-checks
            if (!event1.getCategory().equals(event2.getCategory())) {
                return false;
            }

            // Location proximity check
            if (!areLocationsProximate(event1.getLocation(), event2.getLocation())) {
                return false;
            }

            // Time proximity check (within 4 hours)
            if (!areTimestampsProximate(event1.getTimestamp(), event2.getTimestamp())) {
                return false;
            }

            // Content similarity using simple text matching (could be enhanced with AI)
            return areContentsimilar(event1, event2);

        } catch (Exception e) {
            log.warn("Error comparing events similarity", e);
            return false;
        }
    }

    /**
     * Check if locations are proximate (within same area or nearby coordinates)
     */
    private boolean areLocationsProximate(CityEvent.LocationData loc1, CityEvent.LocationData loc2) {
        if (loc1 == null || loc2 == null) {
            return true; // Consider similar if location unknown
        }

        // Same area
        if (loc1.getArea() != null && loc2.getArea() != null) {
            return loc1.getArea().equalsIgnoreCase(loc2.getArea());
        }

        // Geographic proximity (within 2 km)
        if (loc1.getLatitude() != null && loc1.getLongitude() != null &&
            loc2.getLatitude() != null && loc2.getLongitude() != null) {
            
            double distance = calculateDistance(
                loc1.getLatitude(), loc1.getLongitude(),
                loc2.getLatitude(), loc2.getLongitude()
            );
            return distance <= 2.0; // 2 km radius
        }

        return true; // Default to similar if can't determine
    }

    /**
     * Check if timestamps are proximate (within 4 hours)
     */
    private boolean areTimestampsProximate(LocalDateTime time1, LocalDateTime time2) {
        if (time1 == null || time2 == null) {
            return true; // Consider similar if time unknown
        }

        long hoursDiff = Math.abs(java.time.Duration.between(time1, time2).toHours());
        return hoursDiff <= 4;
    }

    /**
     * Check content similarity using keyword and title matching
     */
    private boolean areContentsimilar(CityEvent event1, CityEvent event2) {
        // Title similarity
        double titleSimilarity = calculateTextSimilarity(
            event1.getTitle(), event2.getTitle()
        );

        // Description similarity
        double descSimilarity = calculateTextSimilarity(
            event1.getDescription(), event2.getDescription()
        );

        // Keyword overlap
        double keywordSimilarity = calculateKeywordSimilarity(
            event1.getKeywords(), event2.getKeywords()
        );

        // Combined similarity score
        double overallSimilarity = (titleSimilarity * 0.4) + 
                                 (descSimilarity * 0.4) + 
                                 (keywordSimilarity * 0.2);

        return overallSimilarity >= SIMILARITY_THRESHOLD;
    }

    /**
     * Create aggregated events from clusters using AI synthesis
     */
    private CompletableFuture<List<CityEvent>> createAggregatedEventsFromClusters(
            List<List<CityEvent>> clusters) {
        
        List<CompletableFuture<CityEvent>> aggregationFutures = clusters.stream()
            .map(this::synthesizeEventsInCluster)
            .collect(Collectors.toList());

        return CompletableFuture.allOf(aggregationFutures.toArray(new CompletableFuture[0]))
            .thenApply(ignored -> aggregationFutures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList()));
    }

    /**
     * Synthesize multiple events in a cluster into a single representative event using Vertex AI
     */
    private CompletableFuture<CityEvent> synthesizeEventsInCluster(List<CityEvent> cluster) {
        if (cluster.size() == 1) {
            return CompletableFuture.completedFuture(cluster.get(0));
        }

        log.debug("AI synthesizing {} events into one representative event", cluster.size());

        String context = String.format(
            "Aggregating %d similar events from %s area about %s category", 
            cluster.size(),
            getClusterArea(cluster),
            cluster.get(0).getCategory()
        );

        return vertexAiService.synthesizeEvents(cluster, context)
            .thenApply(synthesizedContent -> createAggregatedEventWithAI(cluster, synthesizedContent))
            .exceptionally(throwable -> {
                log.warn("AI synthesis failed, using manual aggregation", throwable);
                return createManualAggregatedEvent(cluster);
            });
    }

    /**
     * Create aggregated event with AI-generated content
     */
    private CityEvent createAggregatedEventWithAI(List<CityEvent> cluster, String synthesizedContent) {
        CityEvent primaryEvent = cluster.get(0);
        
        // Use AI to extract title from synthesized content
        String aiTitle = extractTitleFromSynthesis(synthesizedContent, primaryEvent.getTitle());
        
        return CityEvent.builder()
            .id("ai_agg_" + System.currentTimeMillis() + "_" + cluster.size())
            .title(aiTitle)
            .description(synthesizedContent)
            .content(synthesizedContent)
            .location(getBestLocation(cluster))
            .timestamp(getLatestTimestamp(cluster))
            .category(primaryEvent.getCategory())
            .severity(getHighestSeverity(cluster))
            .source(CityEvent.EventSource.SYSTEM_GENERATED)
            .keywords(combineKeywords(cluster))
            .aiSummary("AI-aggregated from " + cluster.size() + " similar events using Vertex AI")
            .confidenceScore(calculateAggregateConfidence(cluster))
            .metadata(createAIAggregationMetadata(cluster, synthesizedContent))
            .createdAt(LocalDateTime.now())
            .build();
    }

    /**
     * Fallback manual aggregation when AI fails
     */
    private CityEvent createManualAggregatedEvent(List<CityEvent> cluster) {
        CityEvent primaryEvent = cluster.get(0);
        
        return CityEvent.builder()
            .id("man_agg_" + System.currentTimeMillis())
            .title("Multiple " + primaryEvent.getCategory() + " reports")
            .description(createManualSummary(cluster))
            .location(getBestLocation(cluster))
            .timestamp(getLatestTimestamp(cluster))
            .category(primaryEvent.getCategory())
            .severity(getHighestSeverity(cluster))
            .source(CityEvent.EventSource.SYSTEM_GENERATED)
            .keywords(combineKeywords(cluster))
            .aiSummary("Manual aggregation of " + cluster.size() + " events")
            .confidenceScore(calculateAggregateConfidence(cluster))
            .metadata(createAggregationMetadata(cluster))
            .createdAt(LocalDateTime.now())
            .build();
    }

    // Helper methods

    @SuppressWarnings("unchecked")
    private List<CityEvent> extractEventsFromRequest(AgentRequest request) {
        // Events can be passed through parameters
        if (request.getParameters() != null && request.getParameters().containsKey("events")) {
            Object eventsObj = request.getParameters().get("events");
            if (eventsObj instanceof List) {
                try {
                    return (List<CityEvent>) eventsObj;
                } catch (ClassCastException e) {
                    log.warn("Events parameter is not a List<CityEvent>", e);
                }
            }
        }
        return new ArrayList<>();
    }

    private int getEventCount(AgentRequest request) {
        return extractEventsFromRequest(request).size();
    }

    private String getClusterArea(List<CityEvent> cluster) {
        return cluster.stream()
            .filter(e -> e.getLocation() != null && e.getLocation().getArea() != null)
            .map(e -> e.getLocation().getArea())
            .findFirst()
            .orElse("Unknown Area");
    }

    private CityEvent.LocationData getBestLocation(List<CityEvent> cluster) {
        return cluster.stream()
            .map(CityEvent::getLocation)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    }

    private LocalDateTime getLatestTimestamp(List<CityEvent> cluster) {
        return cluster.stream()
            .map(CityEvent::getTimestamp)
            .filter(Objects::nonNull)
            .max(LocalDateTime::compareTo)
            .orElse(LocalDateTime.now());
    }

    private CityEvent.EventSeverity getHighestSeverity(List<CityEvent> cluster) {
        return cluster.stream()
            .map(CityEvent::getSeverity)
            .filter(Objects::nonNull)
            .max(Enum::compareTo)
            .orElse(CityEvent.EventSeverity.LOW);
    }

    private List<String> combineKeywords(List<CityEvent> cluster) {
        return cluster.stream()
            .map(CityEvent::getKeywords)
            .filter(Objects::nonNull)
            .flatMap(List::stream)
            .distinct()
            .limit(20)
            .collect(Collectors.toList());
    }

    private Double calculateAggregateConfidence(List<CityEvent> cluster) {
        return cluster.stream()
            .mapToDouble(e -> e.getConfidenceScore() != null ? e.getConfidenceScore() : 0.5)
            .average()
            .orElse(0.5);
    }

    private Map<String, Object> createAggregationMetadata(List<CityEvent> cluster) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("aggregated_event_count", cluster.size());
        metadata.put("source_event_ids", cluster.stream()
            .map(CityEvent::getId)
            .collect(Collectors.toList()));
        metadata.put("aggregation_timestamp", LocalDateTime.now());
        metadata.put("aggregation_method", "ai_synthesis");
        return metadata;
    }

    private Map<String, Object> createAIAggregationMetadata(List<CityEvent> cluster, String aiSynthesis) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("aggregated_event_count", cluster.size());
        metadata.put("source_event_ids", cluster.stream()
            .map(CityEvent::getId)
            .collect(Collectors.toList()));
        metadata.put("aggregation_timestamp", LocalDateTime.now());
        metadata.put("aggregation_method", "vertex_ai_synthesis");
        metadata.put("ai_synthesis_length", aiSynthesis.length());
        metadata.put("similarity_threshold", SIMILARITY_THRESHOLD);
        metadata.put("processing_agent", "AggregatorAgent");
        return metadata;
    }

    private String combineEventContents(List<CityEvent> cluster) {
        return cluster.stream()
            .map(CityEvent::getContent)
            .filter(Objects::nonNull)
            .collect(Collectors.joining("; "));
    }

    private String extractTitleFromSynthesis(String synthesis, String fallbackTitle) {
        // Simple extraction - in production, could use more sophisticated parsing
        String[] lines = synthesis.split("\n");
        for (String line : lines) {
            if (line.length() > 10 && line.length() < 100) {
                return line.trim();
            }
        }
        return fallbackTitle;
    }

    private String createManualSummary(List<CityEvent> cluster) {
        return String.format("Multiple reports of %s in %s area. %d similar events aggregated.",
            cluster.get(0).getCategory(),
            getClusterArea(cluster),
            cluster.size());
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Radius of the earth in km
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private double calculateTextSimilarity(String text1, String text2) {
        if (text1 == null || text2 == null) return 0.0;
        
        Set<String> words1 = Set.of(text1.toLowerCase().split("\\W+"));
        Set<String> words2 = Set.of(text2.toLowerCase().split("\\W+"));
        
        Set<String> intersection = new HashSet<>(words1);
        intersection.retainAll(words2);
        
        Set<String> union = new HashSet<>(words1);
        union.addAll(words2);
        
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    private double calculateKeywordSimilarity(List<String> keywords1, List<String> keywords2) {
        if (keywords1 == null || keywords2 == null) return 0.0;
        
        Set<String> set1 = new HashSet<>(keywords1);
        Set<String> set2 = new HashSet<>(keywords2);
        
        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);
        
        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);
        
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    private AgentResponse createEmptyResponse(AgentRequest request) {
        return AgentResponse.builder()
            .requestId(request.getRequestId())
            .agentId(getAgentId())
            .success(true)
            .events(List.of())
            .message("No events to aggregate")
            .timestamp(LocalDateTime.now())
            .processingTimeMs(0L)
            .build();
    }

    private AgentResponse createSuccessResponse(AgentRequest request, List<CityEvent> aggregatedEvents) {
        return AgentResponse.builder()
            .requestId(request.getRequestId())
            .agentId(getAgentId())
            .success(true)
            .events(aggregatedEvents)
            .metadata(Map.of(
                "original_count", getEventCount(request),
                "aggregated_count", aggregatedEvents.size(),
                "deduplication_ratio", calculateDeduplicationRatio(getEventCount(request), aggregatedEvents.size())
            ))
            .message(String.format("Successfully aggregated %d events into %d unique events", 
                    getEventCount(request), aggregatedEvents.size()))
            .timestamp(LocalDateTime.now())
            .processingTimeMs(System.currentTimeMillis())
            .build();
    }

    private AgentResponse createErrorResponse(AgentRequest request, Throwable throwable) {
        return AgentResponse.builder()
            .requestId(request.getRequestId())
            .agentId(getAgentId())
            .success(false)
            .message("Error during event aggregation: " + throwable.getMessage())
            .timestamp(LocalDateTime.now())
            .processingTimeMs(System.currentTimeMillis())
            .build();
    }

    private double calculateDeduplicationRatio(int original, int aggregated) {
        return original == 0 ? 0.0 : (double) (original - aggregated) / original;
    }
} 