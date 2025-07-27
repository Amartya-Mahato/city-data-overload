package com.lemillion.city_data_overload_server.agent.handler;

import com.lemillion.city_data_overload_server.agent.*;
import com.lemillion.city_data_overload_server.agent.impl.EventsAgent;
import com.lemillion.city_data_overload_server.model.CityEvent;
import com.lemillion.city_data_overload_server.service.VertexAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Handler for Flutter EVENTS page requests
 * Provides event listing with filtering and chat
 */
@FlutterPageType(value = "EVENTS", priority = 10, description = "Events listing page with filtering and search")
@RequiredArgsConstructor
@Slf4j
public class EventsPageHandler implements FlutterPageHandler {

    private final EventsAgent eventsAgent;
    private final VertexAiService vertexAiService;

    @Override
    public String getPageType() {
        return "EVENTS";
    }

    @Override
    public int getPriority() {
        return 10;
    }

    @Override
    public boolean canHandle(AgentRequest request) {
        String requestType = request.getRequestType();
        Map<String, Object> params = request.getParameters();
        
        return (requestType != null && requestType.contains("EVENTS")) ||
               (params != null && "events".equals(params.get("page")));
    }

    @Override
    public CompletableFuture<AgentResponse> handle(AgentRequest request) {
        log.info("Processing EVENTS page request with query: {}", request.getQuery());
        
        // Create optimized request for EventsAgent
        AgentRequest eventsRequest = createEventsAgentRequest(request);
        
        return eventsAgent.processRequest(eventsRequest)
            .thenCompose(eventsResponse -> {
                List<CityEvent> events = eventsResponse.getEvents() != null ? 
                    eventsResponse.getEvents() : List.of();
                
                // Generate chat response based on actual events
                CompletableFuture<String> chatResponseFuture = generateChatResponse(
                    request.getQuery() != null ? request.getQuery() : "Show me recent events in my area", 
                    request,
                    events
                );
                
                return chatResponseFuture.thenApply(chatResponse -> {
                
                Map<String, Object> eventsData = Map.of(
                    "page", "events",
                    "events", events,
                    "total_count", events.size(),
                    "filters_applied", extractFiltersFromRequest(request),
                    "search_suggestions", generateEventSearchSuggestions()
                );
                
                                 return createSuccessResponse(request, eventsData, chatResponse);
                });
            });
    }

    @Override
    public String getDescription() {
        return "Handles events listing page with filtering, search, and event details";
    }

    private AgentRequest createEventsAgentRequest(AgentRequest originalRequest) {
        return AgentRequest.builder()
            .requestId(originalRequest.getRequestId() + "_events")
            .requestType("GET_EVENTS")
            .userId(originalRequest.getUserId())
            .timestamp(LocalDateTime.now())
            .latitude(originalRequest.getLatitude())
            .longitude(originalRequest.getLongitude())
            .area(originalRequest.getArea())
            .radiusKm(originalRequest.getRadiusKm() != null ? originalRequest.getRadiusKm() : 5.0)
            .category(originalRequest.getCategory())
            .severity(originalRequest.getSeverity())
            .startTime(originalRequest.getStartTime())
            .endTime(originalRequest.getEndTime())
            .maxResults(originalRequest.getMaxResults() != null ? originalRequest.getMaxResults() : 20)
            .parameters(originalRequest.getParameters())
            .build();
    }

    private CompletableFuture<String> generateChatResponse(String userQuery, AgentRequest request, List<CityEvent> events) {
        // If no events found, return a simple message without fictional events
        if (events.isEmpty()) {
            return CompletableFuture.completedFuture(
                "No events found in your area at the moment. Please check back later or try adjusting your search filters."
            );
        }
        
        String enhancedPrompt = buildContextualPrompt(userQuery, request);
        
        return vertexAiService.synthesizeEvents(events, enhancedPrompt)
            .exceptionally(throwable -> {
                log.warn("AI chat response failed, using fallback", throwable);
                return "Here are the latest events in your area. What specific information are you looking for?";
            });
    }

    private String buildContextualPrompt(String userQuery, AgentRequest request) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a helpful city assistant for Bengaluru. ");
        prompt.append("User is looking for events in their area. Focus on relevant local happenings. ");
        
        if (request.getArea() != null) {
            prompt.append("Focus on the ").append(request.getArea()).append(" area. ");
        }
        
        prompt.append("User query: \"").append(userQuery).append("\"");
        prompt.append("\n\nProvide a concise, helpful response in conversational tone.");
        
        return prompt.toString();
    }

    private Map<String, Object> extractFiltersFromRequest(AgentRequest request) {
        Map<String, Object> filters = new HashMap<>();
        if (request.getCategory() != null) filters.put("category", request.getCategory());
        if (request.getSeverity() != null) filters.put("severity", request.getSeverity());
        if (request.getArea() != null) filters.put("area", request.getArea());
        if (request.getRadiusKm() != null) filters.put("radius_km", request.getRadiusKm());
        return filters;
    }

    private List<String> generateEventSearchSuggestions() {
        return List.of(
            "Events near me",
            "Traffic updates",
            "Emergency alerts",
            "Community events",
            "Infrastructure issues"
        );
    }

    private AgentResponse createSuccessResponse(AgentRequest request, Map<String, Object> data, String chatResponse) {
        return AgentResponse.builder()
            .requestId(request.getRequestId())
            .agentId("events-page-handler")
            .success(true)
            .message(chatResponse)
            .timestamp(LocalDateTime.now())
            .metadata(data)
            .build();
    }
} 