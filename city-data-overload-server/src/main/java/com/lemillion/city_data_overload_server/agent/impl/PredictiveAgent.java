package com.lemillion.city_data_overload_server.agent.impl;

import com.lemillion.city_data_overload_server.agent.Agent;
import com.lemillion.city_data_overload_server.agent.AgentRequest;
import com.lemillion.city_data_overload_server.agent.AgentResponse;
import com.lemillion.city_data_overload_server.model.CityEvent;
import com.lemillion.city_data_overload_server.service.BigQueryService;
import com.lemillion.city_data_overload_server.service.VertexAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Predictive Agent - Generates predictions and insights based on historical patterns.
 * Uses BigQuery for pattern analysis and Vertex AI for intelligent predictions.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PredictiveAgent implements Agent {

    private final BigQueryService bigQueryService;
    private final VertexAiService vertexAiService;

    @Override
    public String getAgentId() {
        return "predictive-agent";
    }

    @Override
    public AgentType getAgentType() {
        return AgentType.PREDICTOR;
    }

    @Override
    public String getDescription() {
        return "Generates predictive insights and patterns based on historical city data";
    }

    @Override
    public CompletableFuture<AgentResponse> processRequest(AgentRequest request) {
        long startTime = System.currentTimeMillis();
        
        log.info("Predictive agent processing request: {} of type: {}", 
                request.getRequestId(), request.getRequestType());
        
        try {
            return generatePredictions(request)
                .thenApply(predictions -> {
                    log.debug("PredictiveAgent generated {} predictions for request {}", 
                        predictions.size(), request.getRequestId());
                    
                    AgentResponse response = AgentResponse.success(
                        request.getRequestId(),
                        getAgentId(),
                        String.format("Generated %d predictions", predictions.size())
                    );
                    
                    response.setPredictions(predictions);
                    response.setProcessingTimeMs(System.currentTimeMillis() - startTime);
                    
                    log.info("Predictive agent completed request: {} with {} predictions in {}ms", 
                            request.getRequestId(), predictions.size(), response.getProcessingTimeMs());
                    
                    return response;
                })
                .exceptionally(throwable -> {
                    log.error("Predictive agent failed to process request: {}", 
                            request.getRequestId(), throwable);
                    return AgentResponse.error(
                        request.getRequestId(),
                        getAgentId(),
                        "Failed to generate predictions: " + throwable.getMessage(),
                        "PREDICTION_ERROR"
                    );
                });
                
        } catch (Exception e) {
            log.error("Error in predictive agent request processing", e);
            return CompletableFuture.completedFuture(
                AgentResponse.error(request.getRequestId(), getAgentId(), 
                    "Predictive agent error: " + e.getMessage(), "PREDICTIVE_AGENT_ERROR")
            );
        }
    }

    @Override
    public boolean canHandle(String requestType) {
        return "GET_PREDICTIONS".equals(requestType) || 
               "GET_CONTEXTUAL_PREDICTIONS".equals(requestType) ||
               "ANALYZE_PATTERNS".equals(requestType) ||
               "FORECAST_EVENTS".equals(requestType) ||
               "GET_TRENDS".equals(requestType);
    }

    @Override
    public HealthStatus getHealthStatus() {
        try {
            // Test BigQuery connection with a simple statistics query
            bigQueryService.getEventStatistics(java.time.LocalDateTime.now().minusDays(1));
            return HealthStatus.HEALTHY;
        } catch (Exception e) {
            log.warn("Predictive agent health check failed", e);
            return HealthStatus.DEGRADED;
        }
    }

    /**
     * Generate predictions based on historical patterns
     */
    private CompletableFuture<List<Map<String, Object>>> generatePredictions(AgentRequest request) {
        String area = request.getArea() != null ? request.getArea() : "Koramangala";
        String category = request.getCategory();
        
        log.debug("PredictiveAgent generating predictions for area: {} category: {} type: {}", 
            area, category, request.getRequestType());
        
        // For contextual predictions, only generate based on real alerts
        if ("GET_CONTEXTUAL_PREDICTIONS".equals(request.getRequestType())) {
            return generateContextualPredictions(request);
        }
        
        // For all other request types, return empty (no random predictions)
        log.debug("PredictiveAgent only generates contextual predictions based on real alerts");
        return CompletableFuture.completedFuture(List.of());
    }

    /**
     * Generate predictions for a specific category
     */
    private CompletableFuture<List<Map<String, Object>>> generateCategoryPredictions(
            AgentRequest request, String area, CityEvent.EventCategory category) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Generating category predictions for {} in {}", category, area);
                
                // Get historical patterns from BigQuery
                List<Map<String, Object>> patterns = bigQueryService.queryPredictivePatterns(category, 45);
                
                if (patterns.isEmpty()) {
                    log.warn("No patterns found for category {} in area {}", category, area);
                    return createDefaultPredictions(area, category);
                }
                
                // Use Vertex AI to generate intelligent predictions
                return vertexAiService.generatePredictiveInsights(patterns, area).join();
                
            } catch (Exception e) {
                log.error("Error generating category predictions", e);
                return createDefaultPredictions(area, category);
            }
        });
    }

    /**
     * Generate general predictions for an area
     */
    private CompletableFuture<List<Map<String, Object>>> generateAreaPredictions(
            AgentRequest request, String area) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Generating area predictions for {}", area);
                
                List<Map<String, Object>> allPredictions = new ArrayList<>();
                
                // Get patterns for major categories
                CityEvent.EventCategory[] majorCategories = {
                    CityEvent.EventCategory.TRAFFIC,
                    CityEvent.EventCategory.WEATHER,
                    CityEvent.EventCategory.PUBLIC_TRANSPORT,
                    CityEvent.EventCategory.CIVIC_ISSUE,
                    CityEvent.EventCategory.UTILITY,
                    CityEvent.EventCategory.HEALTH,
                    CityEvent.EventCategory.EDUCATION,
                    CityEvent.EventCategory.POLICE,
                    CityEvent.EventCategory.FIRE
                };
                
                for (CityEvent.EventCategory category : majorCategories) {
                    try {
                        List<Map<String, Object>> patterns = bigQueryService.queryPredictivePatterns(category, 30);
                        
                        if (patterns != null && !patterns.isEmpty()) {
                            List<Map<String, Object>> categoryPredictions = 
                                vertexAiService.generatePredictiveInsights(patterns, area).join();
                            allPredictions.addAll(categoryPredictions);
                        }
                        
                    } catch (Exception e) {
                        log.warn("Failed to generate predictions for category {}", category, e);
                        // Add basic prediction for this category
                        allPredictions.add(createBasicPrediction(area, category));
                    }
                }
                
                // Add time-based predictions
                allPredictions.addAll(generateTimeBasedPredictions(area));
                
                return allPredictions;
                
            } catch (Exception e) {
                log.error("Error generating area predictions", e);
                return createDefaultAreaPredictions(area);
            }
        });
    }

    /**
     * Get predictions for a specific area using AI-powered analysis
     */
    private CompletableFuture<List<Map<String, Object>>> getPredictionsForArea(String area) {
        log.debug("Getting AI-powered predictions for area: {}", area);
        
        // Step 1: Get historical patterns from BigQuery
        return getHistoricalPatternsForArea(area)
            .thenCompose(patterns -> {
                if (patterns.isEmpty()) {
                    log.debug("No historical patterns found for area: {}, using general predictions", area);
                    return generateGeneralPredictions(area);
                }
                
                // Step 2: Use Vertex AI to generate intelligent predictions based on patterns
                return vertexAiService.generatePredictiveInsights(patterns, area)
                    .thenApply(aiPredictions -> {
                        log.debug("Generated {} AI predictions for area: {}", aiPredictions.size(), area);
                        
                        // Enhance AI predictions with current context
                        return enhancePredictionsWithContext(aiPredictions, area);
                    });
            })
            .exceptionally(throwable -> {
                log.error("Error getting AI predictions for area: {}, falling back to basic predictions", area, throwable);
                List<Map<String, Object>> fallbackPredictions = generateFallbackPredictions(area);
                log.debug("Generated {} fallback predictions for area: {}", fallbackPredictions.size(), area);
                return fallbackPredictions;
            });
    }

    /**
     * Get historical patterns from BigQuery for AI analysis
     */
    private CompletableFuture<List<Map<String, Object>>> getHistoricalPatternsForArea(String area) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Get patterns for all major categories
                List<Map<String, Object>> allPatterns = new ArrayList<>();
                
                for (CityEvent.EventCategory category : CityEvent.EventCategory.values()) {
                    if (category == CityEvent.EventCategory.OTHER) continue;
                    
                    List<Map<String, Object>> categoryPatterns = bigQueryService
                        .queryPredictivePatterns(category, 30); // Last 30 days
                    
                    // Filter patterns for specific area or nearby areas
                    List<Map<String, Object>> areaPatterns = categoryPatterns.stream()
                        .filter(pattern -> isPatternRelevantToArea(pattern, area))
                        .collect(Collectors.toList());
                    
                    allPatterns.addAll(areaPatterns);
                }
                
                log.debug("Found {} historical patterns for area: {}", allPatterns.size(), area);
                return allPatterns;
                
            } catch (Exception e) {
                log.error("Error querying historical patterns for area: {}", area, e);
                return new ArrayList<>();
            }
        });
    }

    /**
     * Check if a pattern is relevant to the specified area
     */
    private boolean isPatternRelevantToArea(Map<String, Object> pattern, String targetArea) {
        String patternArea = (String) pattern.get("area");
        
        if (patternArea == null) return true; // Citywide patterns are always relevant
        if (patternArea.equalsIgnoreCase(targetArea)) return true;
        if ("citywide".equalsIgnoreCase(patternArea)) return true;
        
        // Check for nearby areas (could be enhanced with location proximity)
        return false;
    }

    /**
     * Enhance AI predictions with current context and real-time information
     */
    private List<Map<String, Object>> enhancePredictionsWithContext(List<Map<String, Object>> aiPredictions, String area) {
        return aiPredictions.stream()
            .map(prediction -> enhanceSinglePrediction(prediction, area))
            .collect(Collectors.toList());
    }

    /**
     * Enhance a single prediction with current context
     */
    private Map<String, Object> enhanceSinglePrediction(Map<String, Object> prediction, String area) {
        Map<String, Object> enhanced = new HashMap<>(prediction);
        
        // Add current time context
        LocalDateTime now = LocalDateTime.now();
        enhanced.put("generated_at", now);
        enhanced.put("target_area", area);
        enhanced.put("ai_powered", true);
        enhanced.put("prediction_type", "pattern_based");
        
        // Add urgency level based on timeframe
        String timeframe = (String) prediction.get("timeframe");
        enhanced.put("urgency", determineUrgency(timeframe));
        
        // Add specific area context
        if (area != null) {
            enhanced.put("localized_advice", generateLocalizedAdvice(prediction, area));
        }
        
        return enhanced;
    }

    /**
     * Determine urgency level from timeframe
     */
    private String determineUrgency(String timeframe) {
        if (timeframe == null) return "LOW";
        
        String lowerTimeframe = timeframe.toLowerCase();
        if (lowerTimeframe.contains("immediate") || lowerTimeframe.contains("now") || 
            lowerTimeframe.contains("next hour")) {
            return "CRITICAL";
        } else if (lowerTimeframe.contains("today") || lowerTimeframe.contains("next 2 hours") ||
                  lowerTimeframe.contains("next 3 hours")) {
            return "HIGH";
        } else if (lowerTimeframe.contains("tomorrow") || lowerTimeframe.contains("this week")) {
            return "MODERATE";
        } else {
            return "LOW";
        }
    }

    /**
     * Generate localized advice for specific area
     */
    private String generateLocalizedAdvice(Map<String, Object> prediction, String area) {
        String category = (String) prediction.get("category");
        String baseAdvice = (String) prediction.get("actionable_advice");
        
        if (category == null || baseAdvice == null) return baseAdvice;
        
        StringBuilder localizedAdvice = new StringBuilder(baseAdvice);
        
        // Add area-specific recommendations
        switch (category.toUpperCase()) {
            case "TRAFFIC":
                localizedAdvice.append(String.format(" For %s area, consider using metro or alternative routes.", area));
                break;
            case "WEATHER":
                localizedAdvice.append(String.format(" Stay updated on conditions specific to %s.", area));
                break;
            case "INFRASTRUCTURE":
                localizedAdvice.append(String.format(" Check %s area maintenance schedules.", area));
                break;
        }
        
        return localizedAdvice.toString();
    }

    /**
     * Generate general predictions when no historical patterns are available
     */
    private CompletableFuture<List<Map<String, Object>>> generateGeneralPredictions(String area) {
        log.debug("Generating general AI predictions for area: {}", area);
        
        // Create a context for general predictions
        List<Map<String, Object>> mockPatterns = createMockPatternsForArea(area);
        
        return vertexAiService.generatePredictiveInsights(mockPatterns, area)
            .thenApply(predictions -> {
                return predictions.stream()
                    .map(prediction -> {
                        Map<String, Object> enhanced = new HashMap<>(prediction);
                        enhanced.put("prediction_type", "general_ai");
                        enhanced.put("ai_powered", true);
                        enhanced.put("confidence", Math.max(0.3, (Double) enhanced.getOrDefault("confidence", 0.5) - 0.2));
                        return enhanced;
                    })
                    .collect(Collectors.toList());
            })
            .exceptionally(throwable -> {
                log.warn("General AI predictions failed for area: {}", area, throwable);
                return generateTimeBasedPredictions(area);
            });
    }

    /**
     * Create mock patterns for areas without historical data
     */
    private List<Map<String, Object>> createMockPatternsForArea(String area) {
        List<Map<String, Object>> mockPatterns = new ArrayList<>();
        
        // General traffic patterns
        mockPatterns.add(Map.of(
            "category", "TRAFFIC",
            "severity", "MODERATE",
            "frequency", 3,
            "timePeriod", "evening_rush",
            "dayType", "weekday",
            "area", area,
            "avgConfidence", 0.7
        ));
        
        // General weather patterns
        mockPatterns.add(Map.of(
            "category", "WEATHER",
            "severity", "LOW",
            "frequency", 2,
            "timePeriod", "afternoon",
            "dayType", "any",
            "area", area,
            "avgConfidence", 0.6
        ));
        
        return mockPatterns;
    }

    /**
     * Fallback predictions when AI fails
     */
    private List<Map<String, Object>> generateFallbackPredictions(String area) {
        log.debug("Generating fallback predictions for area: {}", area);
        
        List<Map<String, Object>> fallbackPredictions = new ArrayList<>();
        
        LocalDateTime now = LocalDateTime.now();
        int currentHour = now.getHour();
        
        // Time-based traffic prediction
        if (currentHour >= 17 && currentHour <= 21) {
            fallbackPredictions.add(Map.of(
                "prediction", "Heavy traffic expected during evening rush hours",
                "timeframe", "Next 2-3 hours",
                "confidence", 0.75,
                "category", "TRAFFIC",
                "actionable_advice", "Consider using public transport or alternative routes",
                "areas_affected", List.of(area),
                "ai_powered", false,
                "prediction_type", "fallback_time_based",
                "urgency", "HIGH"
            ));
        }
        
        // General safety prediction
        fallbackPredictions.add(Map.of(
            "prediction", "Monitor local conditions and stay informed about area updates",
            "timeframe", "Ongoing",
            "confidence", 0.8,
            "category", "SAFETY",
            "actionable_advice", "Stay connected with local news and community updates",
            "areas_affected", List.of(area),
            "ai_powered", false,
            "prediction_type", "fallback_general",
            "urgency", "LOW"
        ));
        
        return fallbackPredictions;
    }

    /**
     * Enhanced time-based predictions (fallback method)
     */
    private List<Map<String, Object>> generateTimeBasedPredictions(String area) {
        List<Map<String, Object>> timePredictions = new ArrayList<>();
        
        LocalDateTime now = LocalDateTime.now();
        int currentHour = now.getHour();
        
        // Morning rush hour predictions
        if (currentHour >= 6 && currentHour <= 10) {
            timePredictions.add(Map.of(
                "prediction", "Heavy traffic expected on major routes during morning rush",
                "timeframe", "Next 2 hours",
                "confidence", 0.85,
                "category", "TRAFFIC",
                "actionable_advice", "Consider metro, buses, or starting travel early/late",
                "areas_affected", List.of(area, "Major arterial roads"),
                "ai_powered", false,
                "prediction_type", "time_based",
                "urgency", "HIGH"
            ));
        }
        
        // Evening rush hour predictions
        if (currentHour >= 17 && currentHour <= 21) {
            timePredictions.add(Map.of(
                "prediction", "Increased traffic congestion during evening rush",
                "timeframe", "Next 3 hours",
                "confidence", 0.80,
                "category", "TRAFFIC",
                "actionable_advice", "Plan for extra travel time or work from home if possible",
                "areas_affected", List.of(area, "IT corridors", "Commercial areas"),
                "ai_powered", false,
                "prediction_type", "time_based",
                "urgency", "HIGH"
            ));
        }
        
        // Weekend predictions
        if (now.getDayOfWeek().getValue() >= 6) {
            timePredictions.add(Map.of(
                "prediction", "Cultural events and weekend activities in commercial areas",
                "timeframe", "Today",
                "confidence", 0.70,
                "category", "CULTURAL_EVENT",
                "actionable_advice", "Check local event listings and plan for parking",
                "areas_affected", List.of("Koramangala", "Indiranagar", "HSR Layout"),
                "ai_powered", false,
                "prediction_type", "time_based",
                "urgency", "LOW"
            ));
        }
        
        return timePredictions;
    }

    /**
     * Generate contextual predictions based on real alerts
     */
    private CompletableFuture<List<Map<String, Object>>> generateContextualPredictions(AgentRequest request) {
        Map<String, Object> parameters = request.getParameters();
        if (parameters == null || !parameters.containsKey("real_alerts")) {
            log.debug("No real alerts provided for contextual predictions");
            return CompletableFuture.completedFuture(List.of());
        }
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> realAlerts = (List<Map<String, Object>>) parameters.get("real_alerts");
        
        log.debug("Generating contextual predictions based on {} real alerts", realAlerts.size());
        
        return CompletableFuture.supplyAsync(() -> {
            List<Map<String, Object>> contextualPredictions = new ArrayList<>();
            
            // Analyze real alerts to generate relevant predictions
            for (Map<String, Object> alert : realAlerts) {
                String category = (String) alert.get("category");
                String area = (String) alert.get("area");
                String severity = (String) alert.get("severity");
                
                if (category != null) {
                    Map<String, Object> prediction = generatePredictionBasedOnAlert(alert, category, area, severity);
                    if (prediction != null) {
                        contextualPredictions.add(prediction);
                    }
                }
            }
            
            log.debug("Generated {} contextual predictions", contextualPredictions.size());
            return contextualPredictions;
        });
    }
    
    /**
     * Generate a specific prediction based on a real alert
     */
    private Map<String, Object> generatePredictionBasedOnAlert(Map<String, Object> alert, String category, String area, String severity) {
        Map<String, Object> prediction = new HashMap<>();
        
        switch (category.toUpperCase()) {
            case "TRAFFIC":
                prediction.put("title", "Extended Traffic Impact");
                prediction.put("message", "Traffic conditions may persist or worsen in nearby areas");
                prediction.put("timeframe", "Next 2-4 hours");
                prediction.put("confidence", 0.75);
                prediction.put("actionable_advice", "Monitor alternative routes and consider delaying non-essential travel");
                break;
                
            case "UTILITY":
                prediction.put("title", "Potential Service Disruption");
                prediction.put("message", "Similar utility issues may affect neighboring areas");
                prediction.put("timeframe", "Next 4-8 hours");
                prediction.put("confidence", 0.60);
                prediction.put("actionable_advice", "Store water and prepare for potential power outages");
                break;
                
            case "HEALTH":
                prediction.put("title", "Health Alert Monitoring");
                prediction.put("message", "Health conditions may spread to adjacent areas");
                prediction.put("timeframe", "Next 1-2 days");
                prediction.put("confidence", 0.65);
                prediction.put("actionable_advice", "Take preventive measures and monitor health advisories");
                break;
                
            case "WEATHER":
                prediction.put("title", "Weather Pattern Extension");
                prediction.put("message", "Weather conditions may affect broader region");
                prediction.put("timeframe", "Next 6-12 hours");
                prediction.put("confidence", 0.80);
                prediction.put("actionable_advice", "Prepare for similar weather conditions in your area");
                break;
                
            default:
                return null; // Skip categories we don't have predictions for
        }
        
        // Add common fields
        prediction.put("severity", severity);
        prediction.put("category", category);
        prediction.put("area", area);
        prediction.put("based_on_alert", alert.get("id"));
        prediction.put("prediction_type", "contextual_alert_based");
        prediction.put("ai_powered", false);
        prediction.put("urgency", "HIGH".equals(severity) || "CRITICAL".equals(severity) ? "HIGH" : "MODERATE");
        
        return prediction;
    }

    /**
     * Create default predictions when no patterns are found
     */
    private List<Map<String, Object>> createDefaultPredictions(String area, CityEvent.EventCategory category) {
        List<Map<String, Object>> defaultPredictions = new ArrayList<>();
        
        Map<String, Object> defaultPrediction = new HashMap<>();
        defaultPrediction.put("prediction", getDefaultPredictionText(category));
        defaultPrediction.put("timeframe", "Next 24 hours");
        defaultPrediction.put("confidence", 0.5);
        defaultPrediction.put("category", category.name());
        defaultPrediction.put("actionable_advice", getDefaultAdvice(category));
        defaultPrediction.put("areas_affected", List.of(area));
        
        defaultPredictions.add(defaultPrediction);
        return defaultPredictions;
    }

    /**
     * Create default predictions for an area
     */
    private List<Map<String, Object>> createDefaultAreaPredictions(String area) {
        List<Map<String, Object>> predictions = new ArrayList<>();
        
        // Traffic prediction
        predictions.add(Map.of(
            "prediction", "Normal traffic patterns expected",
            "timeframe", "Next 24 hours",
            "confidence", 0.6,
            "category", "TRAFFIC",
            "actionable_advice", "Plan for regular commute times",
            "areas_affected", List.of(area)
        ));
        
        // Weather prediction
        predictions.add(Map.of(
            "prediction", "Typical weather conditions for the season",
            "timeframe", "Next 48 hours",
            "confidence", 0.5,
            "category", "WEATHER",
            "actionable_advice", "Check weather updates before outdoor activities",
            "areas_affected", List.of(area)
        ));
        
        return predictions;
    }

    /**
     * Create a basic prediction for a category
     */
    private Map<String, Object> createBasicPrediction(String area, CityEvent.EventCategory category) {
        return Map.of(
            "prediction", getDefaultPredictionText(category),
            "timeframe", "Next 12 hours",
            "confidence", 0.4,
            "category", category.name(),
            "actionable_advice", getDefaultAdvice(category),
            "areas_affected", List.of(area)
        );
    }

    /**
     * Get default prediction text for a category
     */
    private String getDefaultPredictionText(CityEvent.EventCategory category) {
        return switch (category) {
            case TRAFFIC -> "Moderate traffic conditions expected";
            case WEATHER -> "Weather conditions within normal range";
            case PUBLIC_TRANSPORT -> "Regular public transport operations";
            case CIVIC_ISSUE -> "Routine civic maintenance activities";
            case EMERGENCY -> "No emergency situations anticipated";
            case INFRASTRUCTURE -> "Infrastructure operations as usual";
            case CULTURAL_EVENT -> "Potential cultural activities in commercial areas";
            case SAFETY -> "Standard safety protocols in effect";
            case ENVIRONMENT -> "Environmental conditions stable";
            case COMMUNITY -> "Community activities may occur";
            case UTILITY -> "Utility services operating normally";
            case HEALTH -> "Health services operating normally";
            case EDUCATION -> "Education services operating normally";
            case POLICE -> "Police services operating normally";
            case FIRE -> "Fire services operating normally";
            case OTHER -> "Other services operating normally";
        };
    }

    /**
     * Get default advice for a category
     */
    private String getDefaultAdvice(CityEvent.EventCategory category) {
        return switch (category) {
            case TRAFFIC -> "Plan regular commute with buffer time";
            case WEATHER -> "Stay updated with weather forecasts";
            case PUBLIC_TRANSPORT -> "Check for any service updates";
            case CIVIC_ISSUE -> "Report any issues to local authorities";
            case EMERGENCY -> "Keep emergency contacts handy";
            case INFRASTRUCTURE -> "Be aware of any maintenance notices";
            case CULTURAL_EVENT -> "Check local event listings";
            case SAFETY -> "Follow standard safety precautions";
            case ENVIRONMENT -> "Monitor air quality if sensitive";
            case COMMUNITY -> "Engage with local community groups";
            case UTILITY -> "Utility services operating normally";
            case HEALTH -> "Health services operating normally";
            case EDUCATION -> "Education services operating normally";
            case POLICE -> "Police services operating normally";
            case FIRE -> "Fire services operating normally";
            case OTHER -> "Other services operating normally";
        };
    }
} 