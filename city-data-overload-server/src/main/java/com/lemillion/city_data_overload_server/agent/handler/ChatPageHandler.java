package com.lemillion.city_data_overload_server.agent.handler;

import com.lemillion.city_data_overload_server.agent.*;
import com.lemillion.city_data_overload_server.agent.impl.EventsAgent;
import com.lemillion.city_data_overload_server.agent.impl.AlertAgent;
import com.lemillion.city_data_overload_server.model.CityEvent;
import com.lemillion.city_data_overload_server.service.VertexAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Handler for Flutter CHAT page requests
 * Provides pure conversational interface
 */
@FlutterPageType(value = "CHAT", priority = 10, description = "Pure conversational chat interface")
@RequiredArgsConstructor
@Slf4j
public class ChatPageHandler implements FlutterPageHandler {

    private final VertexAiService vertexAiService;
    private final EventsAgent eventsAgent;
    private final AlertAgent alertAgent;

    @Override
    public String getPageType() {
        return "CHAT";
    }

    @Override
    public int getPriority() {
        return 10;
    }

    @Override
    public boolean canHandle(AgentRequest request) {
        String requestType = request.getRequestType();
        Map<String, Object> params = request.getParameters();
        
        return (requestType != null && requestType.contains("CHAT")) ||
               (params != null && "chat".equals(params.get("page")));
    }

    @Override
    public CompletableFuture<AgentResponse> handle(AgentRequest request) {
        log.info("Processing CHAT page request: {}", request.getQuery());
        
        // Get both events and alerts for complete context
        CompletableFuture<List<CityEvent>> eventsFuture = getAllEventsForChat(request);
        CompletableFuture<List<Map<String, Object>>> alertsFuture = getRecentAlerts(request);
        
        return CompletableFuture.allOf(eventsFuture, alertsFuture)
            .thenCompose(ignored -> {
                List<CityEvent> events = eventsFuture.join();
                List<Map<String, Object>> alerts = alertsFuture.join();
                
                return generateChatResponse(request.getQuery(), request, events, alerts)
                    .thenApply(chatResponse -> {
                        Map<String, Object> chatData = Map.of(
                            "page", "chat",
                            "conversation_context", buildConversationContext(request),
                            "suggested_questions", generateSuggestedQuestions(request)
                        );
                        
                        return createSuccessResponse(request, chatData, chatResponse);
                    });
            });
    }

    @Override
    public String getDescription() {
        return "Handles pure conversational chat interface with contextual suggestions";
    }

    private CompletableFuture<String> generateChatResponse(String userQuery, AgentRequest request, 
                                                          List<CityEvent> events, List<Map<String, Object>> alerts) {
        if (userQuery == null || userQuery.trim().isEmpty()) {
            if (events.isEmpty() && alerts.isEmpty()) {
                return CompletableFuture.completedFuture("Hello! I'm your city assistant. No current events or alerts to report in your area. How can I help you today?");
            }
            return CompletableFuture.completedFuture("Hello! I'm your city assistant. Ask me anything about current events, alerts, or reporting issues in your area.");
        }
        
        // If no real data, provide factual response instead of fictional content
        if (events.isEmpty() && alerts.isEmpty()) {
            return CompletableFuture.completedFuture(
                "I checked for current events and alerts in your area, but there are no significant issues to report right now. " +
                "Everything seems quiet! Is there something specific you'd like to know about or report?"
            );
        }
        
        String enhancedPrompt = buildContextualPrompt(userQuery, request, events, alerts);
        
        return vertexAiService.synthesizeEvents(events, enhancedPrompt)
            .exceptionally(throwable -> {
                log.warn("AI chat response failed, using fallback", throwable);
                return "I understand you're asking about: \"" + userQuery + "\". Let me help you with that based on the available information in your area.";
            });
    }

    private String buildContextualPrompt(String userQuery, AgentRequest request, 
                                        List<CityEvent> events, List<Map<String, Object>> alerts) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a helpful city assistant for Bengaluru. ");
        prompt.append("Provide factual information based on current events and alerts in the database. ");
        prompt.append("DO NOT make up fictional events or situations. ");
        
        if (request.getArea() != null) {
            prompt.append("Focus on the ").append(request.getArea()).append(" area. ");
        }
        
        if (!events.isEmpty()) {
            prompt.append("\nCurrent events in the area: ");
            events.stream().limit(5).forEach(event -> {
                prompt.append("\n- ").append(event.getTitle())
                     .append(" (").append(event.getCategory()).append(", ")
                     .append(event.getSeverity()).append(")");
            });
        }
        
        if (!alerts.isEmpty()) {
            prompt.append("\nActive alerts: ");
            alerts.stream().limit(3).forEach(alert -> {
                prompt.append("\n- ").append(alert.get("title"))
                     .append(" (").append(alert.get("severity")).append(")");
            });
        }
        
        prompt.append("\n\nUser query: \"").append(userQuery).append("\"");
        prompt.append("\n\nProvide a helpful, factual response based only on the real data above.");
        
        return prompt.toString();
    }

    private Map<String, Object> buildConversationContext(AgentRequest request) {
        return Map.of(
            "user_location", request.getArea() != null ? request.getArea() : "Bengaluru",
            "timestamp", LocalDateTime.now(),
            "session_id", request.getUserId() != null ? request.getUserId() : "anonymous"
        );
    }

    private List<String> generateSuggestedQuestions(AgentRequest request) {
        return List.of(
            "What's happening in my area today?",
            "Are there any traffic issues I should know about?",
            "What events are coming up this weekend?",
            "How do I report a civic issue?",
            "What should I do in case of emergency?"
        );
    }

    private CompletableFuture<List<CityEvent>> getAllEventsForChat(AgentRequest request) {
        AgentRequest eventsRequest = AgentRequest.builder()
            .requestId(request.getRequestId() + "_chat_events")
            .requestType("GET_EVENTS")
            .latitude(request.getLatitude())
            .longitude(request.getLongitude())
            .area(request.getArea())
            .category(request.getCategory())
            // Don't filter by severity - get ALL events for chat context
            .radiusKm(request.getRadiusKm() != null ? request.getRadiusKm() : 15.0)
            .maxResults(request.getMaxResults() != null ? request.getMaxResults() : 15)
            .startTime(LocalDateTime.now().minusDays(7))
            .parameters(Map.of("includeAllSeverities", true)) // Special flag for chat
            .build();
        
        return eventsAgent.processRequest(eventsRequest)
            .thenApply(response -> response.getEvents() != null ? 
                (List<CityEvent>) response.getEvents() : List.<CityEvent>of())
            .exceptionally(throwable -> {
                log.warn("Failed to get events for chat context", throwable);
                return List.<CityEvent>of();
            });
    }
    
    private CompletableFuture<List<Map<String, Object>>> getRecentAlerts(AgentRequest request) {
        AgentRequest alertsRequest = AgentRequest.builder()
            .requestId(request.getRequestId() + "_chat_alerts")
            .requestType("CHECK_ALERTS")
            .latitude(request.getLatitude())
            .longitude(request.getLongitude())
            .area(request.getArea())
            .radiusKm(request.getRadiusKm() != null ? request.getRadiusKm() : 15.0)
            .maxResults(5)
            .build();
        
        return alertAgent.processRequest(alertsRequest)
            .thenApply(response -> response.getAlerts() != null ? 
                response.getAlerts() : List.<Map<String, Object>>of())
            .exceptionally(throwable -> {
                log.warn("Failed to get alerts for chat context", throwable);
                return List.<Map<String, Object>>of();
            });
    }

    private AgentResponse createSuccessResponse(AgentRequest request, Map<String, Object> data, String chatResponse) {
        return AgentResponse.builder()
            .requestId(request.getRequestId())
            .agentId("chat-page-handler")
            .success(true)
            .message(chatResponse)
            .timestamp(LocalDateTime.now())
            .metadata(data)
            .build();
    }
} 