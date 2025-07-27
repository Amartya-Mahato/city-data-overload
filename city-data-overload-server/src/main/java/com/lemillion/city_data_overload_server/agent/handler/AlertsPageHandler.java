package com.lemillion.city_data_overload_server.agent.handler;

import com.lemillion.city_data_overload_server.agent.*;
import com.lemillion.city_data_overload_server.agent.impl.PredictiveAgent;
import com.lemillion.city_data_overload_server.agent.impl.AlertAgent;
import com.lemillion.city_data_overload_server.service.VertexAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Handler for Flutter ALERTS page requests
 * Provides predictive alerts and warnings
 */
@FlutterPageType(value = "ALERTS", priority = 10, description = "Predictive alerts and safety warnings page")
@RequiredArgsConstructor
@Slf4j
public class AlertsPageHandler implements FlutterPageHandler {

    private final PredictiveAgent predictiveAgent;
    private final AlertAgent alertAgent;
    private final VertexAiService vertexAiService;

    @Override
    public String getPageType() {
        return "ALERTS";
    }

    @Override
    public int getPriority() {
        return 10;
    }

    @Override
    public boolean canHandle(AgentRequest request) {
        String requestType = request.getRequestType();
        Map<String, Object> params = request.getParameters();
        
        return (requestType != null && requestType.contains("ALERTS")) ||
               (params != null && "alerts".equals(params.get("page")));
    }

    @Override
    public CompletableFuture<AgentResponse> handle(AgentRequest request) {
        log.info("Processing ALERTS page request with query: {}", request.getQuery());
        
        // Get real alerts first
        AgentRequest alertsRequest = createAlertsAgentRequest(request);
        
        return alertAgent.processRequest(alertsRequest)
            .thenCompose(realAlertsResponse -> {
                
                // Debug logging
                log.debug("Real alerts response: success={}, alerts={}", 
                    realAlertsResponse.isSuccess(), 
                    realAlertsResponse.getAlerts() != null ? realAlertsResponse.getAlerts().size() : 0);
                
                // Get real alerts
                List<Map<String, Object>> realAlerts = realAlertsResponse.getAlerts() != null ? 
                    realAlertsResponse.getAlerts() : List.<Map<String, Object>>of();
                
                // Only generate predictive alerts if there are real alerts to base them on
                if (!realAlerts.isEmpty()) {
                    AgentRequest predictiveRequest = createPredictiveAgentRequest(request, realAlerts);
                    return predictiveAgent.processRequest(predictiveRequest)
                        .thenApply(predictiveAlertsResponse -> {
                            log.debug("Predictive alerts response: success={}, predictions={}", 
                                predictiveAlertsResponse.isSuccess(), 
                                predictiveAlertsResponse.getPredictions() != null ? predictiveAlertsResponse.getPredictions().size() : 0);
                            
                            List<Map<String, Object>> predictiveAlerts = extractAlertsFromResponse(predictiveAlertsResponse);
                            log.debug("Extracted {} real alerts and {} predictive alerts", 
                                realAlerts.size(), predictiveAlerts.size());
                            
                            return combineAlerts(realAlerts, predictiveAlerts);
                        });
                } else {
                    log.debug("No real alerts found, skipping predictive alerts generation");
                    return CompletableFuture.completedFuture(realAlerts);
                }
            })
            .thenCompose(allAlerts -> {
                
                // Generate chat response based on combined alerts
                return generateChatResponse(
                    request.getQuery() != null ? request.getQuery() : "What alerts should I be aware of in my area?", 
                    request,
                    allAlerts
                ).thenApply(chatResponse -> {
                    
                    long realCount = allAlerts.stream().filter(alert -> "REAL".equals(alert.get("alert_type"))).count();
                    long predictiveCount = allAlerts.stream().filter(alert -> "PREDICTIVE".equals(alert.get("alert_type"))).count();
                    
                    Map<String, Object> alertsData = Map.of(
                        "page", "alerts",
                        "alerts", allAlerts,
                        "real_alerts_count", (int) realCount,
                        "predictive_alerts_count", (int) predictiveCount,
                        "severity_summary", categorizeBySeverity(allAlerts),
                        "location_summary", categorizeByLocation(allAlerts, request),
                        "safety_tips", generateSafetyTips(allAlerts)
                    );
                

                
                return createSuccessResponse(request, alertsData, chatResponse);
                });
            });
    }

    @Override
    public String getDescription() {
        return "Handles alerts and predictions page with safety warnings and contextual advice";
    }

    private AgentRequest createAlertsAgentRequest(AgentRequest originalRequest) {
        return AgentRequest.builder()
            .requestId(originalRequest.getRequestId() + "_alerts")
            .requestType("CHECK_ALERTS")
            .userId(originalRequest.getUserId())
            .timestamp(LocalDateTime.now())
            .latitude(originalRequest.getLatitude())
            .longitude(originalRequest.getLongitude())
            .area(originalRequest.getArea())
            .radiusKm(originalRequest.getRadiusKm() != null ? originalRequest.getRadiusKm() : 5.0)
            .maxResults(originalRequest.getMaxResults() != null ? originalRequest.getMaxResults() : 10)
            .parameters(originalRequest.getParameters())
            .build();
    }
    
    private AgentRequest createPredictiveAgentRequest(AgentRequest originalRequest, List<Map<String, Object>> realAlerts) {
        Map<String, Object> parameters = new HashMap<>(originalRequest.getParameters() != null ? originalRequest.getParameters() : Map.of());
        parameters.put("real_alerts", realAlerts);
        parameters.put("context_based", true);
        
        return AgentRequest.builder()
            .requestId(originalRequest.getRequestId() + "_predictive")
            .requestType("GET_CONTEXTUAL_PREDICTIONS")
            .userId(originalRequest.getUserId())
            .timestamp(LocalDateTime.now())
            .latitude(originalRequest.getLatitude())
            .longitude(originalRequest.getLongitude())
            .area(originalRequest.getArea())
            .radiusKm(originalRequest.getRadiusKm() != null ? originalRequest.getRadiusKm() : 5.0)
            .maxResults(originalRequest.getMaxResults() != null ? originalRequest.getMaxResults() : 3)
            .parameters(parameters)
            .build();
    }

    private CompletableFuture<String> generateChatResponse(String userQuery, AgentRequest request, List<Map<String, Object>> alerts) {
        // If no alerts at all, provide factual response
        if (alerts.isEmpty()) {
            return CompletableFuture.completedFuture(
                "I checked for current alerts and predictions in your area, but there are no warnings to report. Everything seems normal right now!"
            );
        }
        
        String enhancedPrompt = buildContextualPrompt(userQuery, request, alerts);
        
        // Pass empty events list but provide alerts context in prompt
        return vertexAiService.synthesizeEvents(List.of(), enhancedPrompt)
            .exceptionally(throwable -> {
                log.warn("AI chat response failed, using fallback", throwable);
                return "I'm monitoring conditions in your area. Everything looks normal right now.";
            });
    }

    private String buildContextualPrompt(String userQuery, AgentRequest request, List<Map<String, Object>> alerts) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a helpful city assistant for Bengaluru. ");
        prompt.append("Provide factual information based on current alerts in the database. ");
        prompt.append("DO NOT make up fictional alerts, weather conditions, or traffic situations. ");
        
        if (request.getArea() != null) {
            prompt.append("Focus on the ").append(request.getArea()).append(" area. ");
        }
        
        if (!alerts.isEmpty()) {
            // Separate real and predictive alerts
            List<Map<String, Object>> realAlerts = alerts.stream()
                .filter(alert -> "REAL".equals(alert.get("alert_type")))
                .toList();
            List<Map<String, Object>> predictiveAlerts = alerts.stream()
                .filter(alert -> "PREDICTIVE".equals(alert.get("alert_type")))
                .toList();
            
            if (!realAlerts.isEmpty()) {
                prompt.append("\nCurrent real alerts from database: ");
                realAlerts.stream().limit(3).forEach(alert -> {
                    prompt.append("\n- ").append(alert.get("title"))
                         .append(" (").append(alert.get("severity")).append(" severity)")
                         .append(" in ").append(alert.get("area"));
                });
            }
            
            if (!predictiveAlerts.isEmpty()) {
                prompt.append("\nPredictive alerts and warnings: ");
                predictiveAlerts.stream().limit(3).forEach(alert -> {
                    prompt.append("\n- ").append(alert.get("title"))
                         .append(" (").append(alert.get("severity")).append(" severity)")
                         .append(" in ").append(alert.get("area"));
                });
            }
        }
        
        prompt.append("\n\nUser query: \"").append(userQuery).append("\"");
        prompt.append("\n\nProvide a helpful, factual response based only on the real alerts above. If no alerts, confirm everything is normal.");
        
        return prompt.toString();
    }
    
    private List<Map<String, Object>> combineAlerts(List<Map<String, Object>> realAlerts, 
                                                   List<Map<String, Object>> predictiveAlerts) {
        List<Map<String, Object>> combined = new ArrayList<>();
        
        // Add real alerts first (higher priority)
        for (Map<String, Object> alert : realAlerts) {
            Map<String, Object> enhancedAlert = new HashMap<>(alert);
            enhancedAlert.put("alert_type", "REAL");
            enhancedAlert.put("data_source", "DATABASE");
            combined.add(enhancedAlert);
        }
        
        // Add predictive alerts
        for (Map<String, Object> alert : predictiveAlerts) {
            Map<String, Object> enhancedAlert = new HashMap<>(alert);
            enhancedAlert.put("alert_type", "PREDICTIVE");
            enhancedAlert.put("data_source", "AI_PREDICTION");
            combined.add(enhancedAlert);
        }
        
        // Sort by severity: CRITICAL -> HIGH -> MEDIUM -> LOW
        combined.sort((a, b) -> {
            String severityA = (String) a.getOrDefault("severity", "LOW");
            String severityB = (String) b.getOrDefault("severity", "LOW");
            return compareSeverity(severityA, severityB);
        });
        
        return combined;
    }
    
    private int compareSeverity(String severityA, String severityB) {
        Map<String, Integer> severityOrder = Map.of(
            "CRITICAL", 0,
            "HIGH", 1,
            "MEDIUM", 2,
            "MODERATE", 2,
            "LOW", 3
        );
        
        int orderA = severityOrder.getOrDefault(severityA, 3);
        int orderB = severityOrder.getOrDefault(severityB, 3);
        
        return Integer.compare(orderA, orderB);
    }

    private List<Map<String, Object>> extractAlertsFromResponse(AgentResponse response) {
        if (response.getAlerts() != null) {
            return response.getAlerts();
        }
        
        // PredictiveAgent returns predictions, not alerts - convert them to alert format
        if (response.getPredictions() != null) {
            return response.getPredictions().stream()
                .map(this::convertPredictionToAlert)
                .toList();
        }
        
        return List.of();
    }
    
    private Map<String, Object> convertPredictionToAlert(Map<String, Object> prediction) {
        Map<String, Object> alert = new HashMap<>(prediction);
        
        // Ensure required alert fields exist
        if (!alert.containsKey("severity")) {
            alert.put("severity", "MEDIUM");
        }
        if (!alert.containsKey("area")) {
            alert.put("area", "Bengaluru");
        }
        if (!alert.containsKey("type")) {
            alert.put("type", "PREDICTION");
        }
        
        // Add alert-specific metadata
        alert.put("alert_type", "PREDICTIVE");
        alert.put("data_source", "AI_PREDICTION");
        
        return alert;
    }

    private Map<String, Integer> categorizeBySeverity(List<Map<String, Object>> alerts) {
        Map<String, Integer> severityCount = new HashMap<>();
        for (Map<String, Object> alert : alerts) {
            String severity = (String) alert.getOrDefault("severity", "UNKNOWN");
            severityCount.merge(severity, 1, Integer::sum);
        }
        return severityCount;
    }

    private Map<String, Integer> categorizeByLocation(List<Map<String, Object>> alerts, AgentRequest request) {
        Map<String, Integer> locationCount = new HashMap<>();
        for (Map<String, Object> alert : alerts) {
            String location = (String) alert.getOrDefault("area", "UNKNOWN");
            locationCount.merge(location, 1, Integer::sum);
        }
        return locationCount;
    }

    private List<String> generateSafetyTips(List<Map<String, Object>> alerts) {
        return List.of(
            "Stay updated on local conditions",
            "Plan alternate routes",
            "Keep emergency contacts handy",
            "Follow official announcements"
        );
    }

    private AgentResponse createSuccessResponse(AgentRequest request, Map<String, Object> data, String chatResponse) {
        return AgentResponse.builder()
            .requestId(request.getRequestId())
            .agentId("alerts-page-handler")
            .success(true)
            .message(chatResponse)
            .timestamp(LocalDateTime.now())
            .metadata(data)
            .build();
    }
} 