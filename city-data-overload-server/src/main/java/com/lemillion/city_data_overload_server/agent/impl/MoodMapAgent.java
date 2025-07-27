package com.lemillion.city_data_overload_server.agent.impl;

import com.lemillion.city_data_overload_server.agent.Agent;
import com.lemillion.city_data_overload_server.agent.AgentRequest;
import com.lemillion.city_data_overload_server.agent.AgentResponse;
import com.lemillion.city_data_overload_server.model.CityEvent;
import com.lemillion.city_data_overload_server.service.BigQueryService;
import com.lemillion.city_data_overload_server.service.FirestoreService;
import com.lemillion.city_data_overload_server.service.VertexAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Mood Map Agent - Analyzes sentiment data to create mood maps of different areas.
 * Provides insights into the emotional state of different parts of the city.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MoodMapAgent implements Agent {

    private final FirestoreService firestoreService;
    private final BigQueryService bigQueryService;
    private final VertexAiService vertexAiService;

    // Bengaluru areas for mood analysis
    private static final List<String> BENGALURU_AREAS = Arrays.asList(
        "Koramangala", "HSR Layout", "Indiranagar", "Whitefield", 
        "Electronic City", "Marathahalli", "Bellandur", "Sarjapur",
        "Jayanagar", "Malleshwaram", "Rajinagar", "Yelahanka"
    );

    @Override
    public String getAgentId() {
        return "mood-map-agent";
    }

    @Override
    public AgentType getAgentType() {
        return AgentType.ANALYZER;
    }

    @Override
    public String getDescription() {
        return "Analyzes sentiment data to create mood maps of different areas in Bengaluru";
    }

    @Override
    public CompletableFuture<AgentResponse> processRequest(AgentRequest request) {
        long startTime = System.currentTimeMillis();
        
        log.info("Mood map agent processing request: {} of type: {}", 
                request.getRequestId(), request.getRequestType());
        
        try {
            return generateMoodMap(request)
                .thenApply(moodData -> {
                    AgentResponse response = AgentResponse.success(
                        request.getRequestId(),
                        getAgentId(),
                        "Mood map analysis completed"
                    );
                    
                    response.setSentimentData(moodData);
                    response.setProcessingTimeMs(System.currentTimeMillis() - startTime);
                    
                    log.info("Mood map agent completed request: {} in {}ms", 
                            request.getRequestId(), response.getProcessingTimeMs());
                    
                    return response;
                })
                .exceptionally(throwable -> {
                    log.error("Mood map agent failed to process request: {}", 
                            request.getRequestId(), throwable);
                    return AgentResponse.error(
                        request.getRequestId(),
                        getAgentId(),
                        "Failed to generate mood map: " + throwable.getMessage(),
                        "MOOD_MAP_ERROR"
                    );
                });
                
        } catch (Exception e) {
            log.error("Error in mood map agent request processing", e);
            return CompletableFuture.completedFuture(
                AgentResponse.error(request.getRequestId(), getAgentId(), 
                    "Mood map agent error: " + e.getMessage(), "MOOD_MAP_AGENT_ERROR")
            );
        }
    }

    @Override
    public boolean canHandle(String requestType) {
        return "GET_MOOD_MAP".equals(requestType) || 
               "ANALYZE_SENTIMENT".equals(requestType) ||
               "GET_AREA_MOOD".equals(requestType) ||
               "SENTIMENT_TRENDS".equals(requestType);
    }

    @Override
    public HealthStatus getHealthStatus() {
        try {
            // Test with a simple mood query for one area
            generateAreaMood("Koramangala").get(
                java.util.concurrent.TimeUnit.MILLISECONDS.toMillis(3000), 
                java.util.concurrent.TimeUnit.MILLISECONDS
            );
            return HealthStatus.HEALTHY;
        } catch (Exception e) {
            log.warn("Mood map agent health check failed", e);
            return HealthStatus.DEGRADED;
        }
    }

    /**
     * Generate comprehensive mood map for the city or specific area
     */
    private CompletableFuture<Map<String, Object>> generateMoodMap(AgentRequest request) {
        if (request.getArea() != null) {
            // Single area mood analysis
            return generateAreaMood(request.getArea())
                .thenApply(areaMood -> Map.of(
                    "type", "single_area",
                    "area", request.getArea(),
                    "mood_data", areaMood,
                    "timestamp", LocalDateTime.now()
                ));
        } else {
            // City-wide mood map
            return generateCityWideMoodMap();
        }
    }

    /**
     * Generate mood data for a specific area
     */
    private CompletableFuture<Map<String, Object>> generateAreaMood(String area) {
        log.info("Generating mood analysis for area: {}", area);
        
        // Get recent events with sentiment data from Firestore first
        return firestoreService.getRecentEventsByArea(area, 100)
            .thenCompose(events -> {
                if (events.isEmpty() || events.size() < 10) {
                    // Fallback to BigQuery for more historical data
                    log.info("Limited Firestore data for {}, querying BigQuery", area);
                    return getAreaEventsFromBigQuery(area);
                } else {
                    return CompletableFuture.completedFuture(events);
                }
            })
            .thenCompose(events -> {
                List<CityEvent.SentimentData> sentiments = events.stream()
                    .map(CityEvent::getSentiment)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
                
                if (sentiments.isEmpty()) {
                    // No sentiment data available, analyze event content
                    return analyzeSentimentFromEvents(events, area);
                } else {
                    // Use existing sentiment data
                    return CompletableFuture.completedFuture(
                        analyzeSentimentData(sentiments, area, events.size())
                    );
                }
            });
    }

    /**
     * Generate city-wide mood map
     */
    private CompletableFuture<Map<String, Object>> generateCityWideMoodMap() {
        log.info("Generating city-wide mood map");
        
        List<CompletableFuture<Map<String, Object>>> areaAnalyses = BENGALURU_AREAS.stream()
            .map(this::generateAreaMood)
            .collect(Collectors.toList());
        
        return CompletableFuture.allOf(areaAnalyses.toArray(new CompletableFuture[0]))
            .thenCompose(ignored -> {
                Map<String, List<CityEvent.SentimentData>> areaSentiments = new HashMap<>();
                Map<String, Object> areaResults = new HashMap<>();
                
                // Collect results from all areas
                for (int i = 0; i < BENGALURU_AREAS.size(); i++) {
                    String area = BENGALURU_AREAS.get(i);
                    try {
                        Map<String, Object> areaResult = areaAnalyses.get(i).join();
                        areaResults.put(area, areaResult);
                        
                        // Extract sentiment data for overall analysis
                        if (areaResult.containsKey("sentiments")) {
                            @SuppressWarnings("unchecked")
                            List<CityEvent.SentimentData> sentiments = 
                                (List<CityEvent.SentimentData>) areaResult.get("sentiments");
                            areaSentiments.put(area, sentiments);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to get mood data for area: {}", area, e);
                        areaResults.put(area, createDefaultMoodData(area));
                    }
                }
                
                // Generate overall city analysis using AI
                return vertexAiService.generateMoodMapAnalysis(areaSentiments)
                    .thenApply(aiAnalysis -> {
                        Map<String, Object> cityMoodMap = new HashMap<>();
                        cityMoodMap.put("type", "city_wide");
                        cityMoodMap.put("areas", areaResults);
                        cityMoodMap.put("ai_analysis", aiAnalysis);
                        cityMoodMap.put("overall_summary", generateOverallSummary(areaResults));
                        cityMoodMap.put("timestamp", LocalDateTime.now());
                        cityMoodMap.put("total_areas_analyzed", areaResults.size());
                        
                        return cityMoodMap;
                    });
            });
    }

    /**
     * Get area events from BigQuery for mood analysis
     */
    private CompletableFuture<List<CityEvent>> getAreaEventsFromBigQuery(String area) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Query for recent events in the area
                List<CityEvent> allEvents = new ArrayList<>();
                
                // Get events from different categories
                CityEvent.EventCategory[] categories = {
                    CityEvent.EventCategory.CIVIC_ISSUE,
                    CityEvent.EventCategory.CULTURAL_EVENT,
                    CityEvent.EventCategory.TRAFFIC,
                    CityEvent.EventCategory.COMMUNITY
                };
                
                for (CityEvent.EventCategory category : categories) {
                    try {
                        List<CityEvent> categoryEvents = bigQueryService.queryEventsByCategoryAndSeverity(
                            category, 
                            CityEvent.EventSeverity.MODERATE,
                            LocalDateTime.now().minusDays(7),
                            25
                        );
                        
                        // Filter events for the specific area
                        List<CityEvent> areaEvents = categoryEvents.stream()
                            .filter(event -> event.getLocation() != null && 
                                           area.equalsIgnoreCase(event.getLocation().getArea()))
                            .collect(Collectors.toList());
                        
                        allEvents.addAll(areaEvents);
                    } catch (Exception e) {
                        log.warn("Failed to query category {} for area {}", category, area, e);
                    }
                }
                
                return allEvents;
                
            } catch (Exception e) {
                log.error("Error querying BigQuery for area mood: {}", area, e);
                return new ArrayList<>();
            }
        });
    }

    /**
     * Analyze sentiment from event content using AI
     */
    private CompletableFuture<Map<String, Object>> analyzeSentimentFromEvents(
            List<CityEvent> events, String area) {
        
        if (events.isEmpty()) {
            return CompletableFuture.completedFuture(createDefaultMoodData(area));
        }
        
        // Analyze sentiment for events without sentiment data
        List<CompletableFuture<CityEvent.SentimentData>> sentimentFutures = events.stream()
            .filter(event -> event.getSentiment() == null)
            .limit(20) // Limit AI calls for performance
            .map(event -> {
                String content = buildEventContent(event);
                return vertexAiService.analyzeSentiment(content);
            })
            .collect(Collectors.toList());
        
        if (sentimentFutures.isEmpty()) {
            return CompletableFuture.completedFuture(createDefaultMoodData(area));
        }
        
        return CompletableFuture.allOf(sentimentFutures.toArray(new CompletableFuture[0]))
            .thenApply(ignored -> {
                List<CityEvent.SentimentData> sentiments = sentimentFutures.stream()
                    .map(future -> {
                        try {
                            return future.join();
                        } catch (Exception e) {
                            log.warn("Failed to get sentiment analysis result", e);
                            return CityEvent.SentimentData.builder()
                                .type(CityEvent.SentimentType.NEUTRAL)
                                .score(0.0)
                                .confidence(0.5)
                                .build();
                        }
                    })
                    .collect(Collectors.toList());
                
                return analyzeSentimentData(sentiments, area, events.size());
            });
    }

    /**
     * Analyze collected sentiment data
     */
    private Map<String, Object> analyzeSentimentData(
            List<CityEvent.SentimentData> sentiments, String area, int totalEvents) {
        
        Map<String, Object> analysis = new HashMap<>();
        analysis.put("area", area);
        analysis.put("total_events", totalEvents);
        analysis.put("analyzed_events", sentiments.size());
        
        if (sentiments.isEmpty()) {
            return createDefaultMoodData(area);
        }
        
        // Calculate sentiment statistics
        double avgScore = sentiments.stream()
            .mapToDouble(s -> s.getScore() != null ? s.getScore() : 0.0)
            .average()
            .orElse(0.0);
        
        double avgConfidence = sentiments.stream()
            .mapToDouble(s -> s.getConfidence() != null ? s.getConfidence() : 0.5)
            .average()
            .orElse(0.5);
        
        // Count sentiment types
        Map<CityEvent.SentimentType, Long> sentimentCounts = sentiments.stream()
            .collect(Collectors.groupingBy(
                CityEvent.SentimentData::getType,
                Collectors.counting()
            ));
        
        // Determine overall mood
        CityEvent.SentimentType overallMood = determineOverallMood(avgScore, sentimentCounts);
        
        analysis.put("overall_mood", overallMood.name());
        analysis.put("mood_score", avgScore);
        analysis.put("confidence", avgConfidence);
        analysis.put("sentiment_distribution", sentimentCounts);
        analysis.put("sentiments", sentiments);
        
        // Generate mood description
        analysis.put("mood_description", generateMoodDescription(overallMood, avgScore, area));
        
        // Add recommendations
        analysis.put("recommendations", generateMoodRecommendations(overallMood, avgScore));
        
        return analysis;
    }

    /**
     * Generate overall summary for city-wide mood map
     */
    private Map<String, Object> generateOverallSummary(Map<String, Object> areaResults) {
        Map<String, Object> summary = new HashMap<>();
        
        List<String> positiveAreas = new ArrayList<>();
        List<String> negativeAreas = new ArrayList<>();
        List<String> neutralAreas = new ArrayList<>();
        
        for (Map.Entry<String, Object> entry : areaResults.entrySet()) {
            String area = entry.getKey();
            @SuppressWarnings("unchecked")
            Map<String, Object> areaData = (Map<String, Object>) entry.getValue();
            
            String mood = (String) areaData.get("overall_mood");
            if (mood != null) {
                switch (mood) {
                    case "POSITIVE" -> positiveAreas.add(area);
                    case "NEGATIVE" -> negativeAreas.add(area);
                    default -> neutralAreas.add(area);
                }
            }
        }
        
        summary.put("positive_areas", positiveAreas);
        summary.put("negative_areas", negativeAreas);
        summary.put("neutral_areas", neutralAreas);
        summary.put("city_mood_trend", determineCityMoodTrend(positiveAreas, negativeAreas, neutralAreas));
        
        return summary;
    }

    // Helper methods

    private String buildEventContent(CityEvent event) {
        StringBuilder content = new StringBuilder();
        if (event.getTitle() != null) content.append(event.getTitle()).append(" ");
        if (event.getDescription() != null) content.append(event.getDescription()).append(" ");
        if (event.getContent() != null) content.append(event.getContent());
        return content.toString().trim();
    }

    private CityEvent.SentimentType determineOverallMood(
            double avgScore, Map<CityEvent.SentimentType, Long> counts) {
        
        if (avgScore > 0.3) return CityEvent.SentimentType.POSITIVE;
        if (avgScore < -0.3) return CityEvent.SentimentType.NEGATIVE;
        
        // Check distribution if score is neutral
        long positive = counts.getOrDefault(CityEvent.SentimentType.POSITIVE, 0L);
        long negative = counts.getOrDefault(CityEvent.SentimentType.NEGATIVE, 0L);
        
        if (positive > negative * 1.5) return CityEvent.SentimentType.POSITIVE;
        if (negative > positive * 1.5) return CityEvent.SentimentType.NEGATIVE;
        
        return CityEvent.SentimentType.NEUTRAL;
    }

    private String generateMoodDescription(CityEvent.SentimentType mood, double score, String area) {
        return switch (mood) {
            case POSITIVE -> String.format("Residents of %s are generally upbeat with positive sentiment (%.2f)", area, score);
            case NEGATIVE -> String.format("Some concerns and negative sentiment in %s (%.2f)", area, score);
            case NEUTRAL -> String.format("%s shows balanced sentiment with mixed reactions (%.2f)", area, score);
            case MIXED -> String.format("Mixed emotions and varied sentiment in %s (%.2f)", area, score);
        };
    }

    private List<String> generateMoodRecommendations(CityEvent.SentimentType mood, double score) {
        return switch (mood) {
            case POSITIVE -> Arrays.asList(
                "Continue promoting positive community initiatives",
                "Share success stories to maintain momentum",
                "Engage residents in more community activities"
            );
            case NEGATIVE -> Arrays.asList(
                "Address community concerns promptly",
                "Increase communication with residents",
                "Focus on resolving reported issues",
                "Organize community meetings for feedback"
            );
            case NEUTRAL, MIXED -> Arrays.asList(
                "Monitor sentiment trends closely",
                "Gather more specific feedback from residents",
                "Balance different community needs",
                "Promote positive community events"
            );
        };
    }

    private String determineCityMoodTrend(List<String> positive, List<String> negative, List<String> neutral) {
        if (positive.size() > negative.size() + neutral.size()) {
            return "PREDOMINANTLY_POSITIVE";
        } else if (negative.size() > positive.size() + neutral.size()) {
            return "PREDOMINANTLY_NEGATIVE";
        } else {
            return "BALANCED";
        }
    }

    private Map<String, Object> createDefaultMoodData(String area) {
        Map<String, Object> defaultData = new HashMap<>();
        defaultData.put("area", area);
        defaultData.put("overall_mood", "NEUTRAL");
        defaultData.put("mood_score", 0.0);
        defaultData.put("confidence", 0.3);
        defaultData.put("mood_description", "Insufficient data for mood analysis in " + area);
        defaultData.put("recommendations", Arrays.asList("Collect more community feedback", "Monitor area activities"));
        defaultData.put("total_events", 0);
        defaultData.put("analyzed_events", 0);
        return defaultData;
    }
} 