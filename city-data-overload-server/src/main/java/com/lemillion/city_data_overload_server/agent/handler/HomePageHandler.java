package com.lemillion.city_data_overload_server.agent.handler;

import com.lemillion.city_data_overload_server.agent.*;
import com.lemillion.city_data_overload_server.agent.impl.*;
import com.lemillion.city_data_overload_server.model.CityEvent;
import com.lemillion.city_data_overload_server.service.VertexAiService;
import com.lemillion.city_data_overload_server.service.IntelligentSeverityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Handler for Flutter HOME page requests
 * Provides dashboard data with contextual chat
 */
@FlutterPageType(value = "HOME", priority = 10, description = "Main dashboard with chat and summary data")
@RequiredArgsConstructor
@Slf4j
public class HomePageHandler implements FlutterPageHandler {

    private final EventsAgent eventsAgent;
    private final AlertAgent alertAgent;
    private final AnalyzerAgent analyzerAgent;
    private final VertexAiService vertexAiService;
    private final IntelligentSeverityService intelligentSeverityService;

    @Override
    public String getPageType() {
        return "HOME";
    }

    @Override
    public int getPriority() {
        return 10; // High priority
    }

    @Override
    public boolean canHandle(AgentRequest request) {
        // Handle HOME page requests and default requests
        return true; // HOME is the fallback handler
    }

    @Override
    public CompletableFuture<AgentResponse> handle(AgentRequest request) {
        log.info("Processing HOME page request with query: {}", request.getQuery());
        
        // Check if this is a reporting request that fell back to HOME
        if (isReportingRequest(request)) {
            log.info("Detected reporting request, processing as report submission");
            return handleReportSubmission(request);
        }
        
        // Get summary data from multiple sources
        CompletableFuture<List<CityEvent>> recentEventsFuture = getRecentEvents(request, 5);
        CompletableFuture<List<Map<String, Object>>> alertsFuture = getRecentAlerts(request, 3);
        
        return CompletableFuture.allOf(recentEventsFuture, alertsFuture)
            .thenCompose(ignored -> {
                List<CityEvent> events = recentEventsFuture.join();
                List<Map<String, Object>> alerts = alertsFuture.join();
                
                // Generate chat response based on actual events and alerts
                return generateChatResponse(request.getQuery(), request, events, alerts)
                    .thenApply(chatResponse -> {
                
                Map<String, Object> homeData = Map.of(
                    "page", "home",
                    "summary", Map.of(
                        "total_events", events.size(),
                        "active_alerts", alerts.size(),
                        "last_updated", LocalDateTime.now()
                    ),
                    "recent_events", events.stream().limit(3).collect(Collectors.toList()),
                    "recent_alerts", alerts,
                    "quick_actions", getQuickActions()
                );
                
                return createSuccessResponse(request, homeData, chatResponse);
                    });
            });
    }

    @Override
    public String getDescription() {
        return "Handles main dashboard page with recent events, alerts, and contextual chat";
    }

    private CompletableFuture<List<CityEvent>> getRecentEvents(AgentRequest request, int limit) {
        AgentRequest eventsRequest = AgentRequest.builder()
            .requestId(request.getRequestId() + "_recent_events")
            .requestType("GET_EVENTS")
            .latitude(request.getLatitude())
            .longitude(request.getLongitude())
            .area(request.getArea())
            .maxResults(limit)
            .startTime(LocalDateTime.now().minusDays(7))
            .build();
        
        return eventsAgent.processRequest(eventsRequest)
            .thenApply(response -> response.getEvents() != null ? 
                (List<CityEvent>) response.getEvents() : List.<CityEvent>of())
            .exceptionally(throwable -> {
                log.warn("Failed to get recent events", throwable);
                return List.<CityEvent>of();
            });
    }

    private CompletableFuture<List<Map<String, Object>>> getRecentAlerts(AgentRequest request, int limit) {
        AgentRequest alertsRequest = AgentRequest.builder()
            .requestId(request.getRequestId() + "_recent_alerts")
            .requestType("CHECK_ALERTS")
            .latitude(request.getLatitude())
            .longitude(request.getLongitude())
            .area(request.getArea())
            .maxResults(limit)
            .build();
        
        return alertAgent.processRequest(alertsRequest)
            .thenApply(response -> response.getAlerts() != null ? 
                response.getAlerts() : List.<Map<String, Object>>of())
            .exceptionally(throwable -> {
                log.warn("Failed to get recent alerts", throwable);
                return List.<Map<String, Object>>of();
            });
    }

    private CompletableFuture<String> generateChatResponse(String userQuery, AgentRequest request, 
                                                          List<CityEvent> events, List<Map<String, Object>> alerts) {
        if (userQuery == null || userQuery.trim().isEmpty()) {
            // If no events or alerts, return simple welcome message
            if (events.isEmpty() && alerts.isEmpty()) {
                return CompletableFuture.completedFuture("Welcome to your city dashboard! No current events or alerts in your area.");
            }
            return CompletableFuture.completedFuture("Welcome to your city dashboard! How can I help you today?");
        }
        
        // If no real data, don't generate fictional content
        if (events.isEmpty() && alerts.isEmpty()) {
            return CompletableFuture.completedFuture(
                "No current events or alerts found in your area. Everything seems quiet for now!"
            );
        }
        
        String enhancedPrompt = buildContextualPrompt(userQuery, request);
        
        return vertexAiService.synthesizeEvents(events, enhancedPrompt)
            .exceptionally(throwable -> {
                log.warn("AI chat response failed, using fallback", throwable);
                return "I understand you're asking about: \"" + userQuery + "\". Let me help you with that based on the available information in your area.";
            });
    }

    private String buildContextualPrompt(String userQuery, AgentRequest request) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a helpful city assistant for Bengaluru. ");
        prompt.append("User is on the main dashboard. Provide a welcoming summary of city conditions. ");
        
        if (request.getArea() != null) {
            prompt.append("Focus on the ").append(request.getArea()).append(" area. ");
        }
        
        prompt.append("User query: \"").append(userQuery).append("\"");
        prompt.append("\n\nProvide a concise, helpful response in conversational tone.");
        
        return prompt.toString();
    }

    private List<String> getQuickActions() {
        return List.of(
            "View recent events",
            "Check alerts in my area", 
            "Report an issue",
            "Get traffic updates",
            "Emergency contacts"
        );
    }

    /**
     * Check if this is a reporting request that fell back to HOME
     */
    private boolean isReportingRequest(AgentRequest request) {
        return "FLUTTER_REPORTING".equals(request.getRequestType()) ||
               (request.getParameters() != null && "reporting".equals(request.getParameters().get("page"))) ||
               hasReportingContent(request);
    }
    
    /**
     * Handle report submission with AnalyzerAgent
     */
    private CompletableFuture<AgentResponse> handleReportSubmission(AgentRequest request) {
        log.info("Processing report submission for user: {}", request.getUserId());
        
        // Create a basic event from user report
        CityEvent userEvent = createEventFromUserReport(request);
        
        // Create request for AnalyzerAgent to process and enhance the event
        AgentRequest analyzerRequest = AgentRequest.builder()
            .requestId(request.getRequestId() + "_analysis")
            .requestType("ANALYZE_USER_SUBMISSION")
            .userId(request.getUserId())
            .timestamp(LocalDateTime.now())
            .latitude(request.getLatitude())
            .longitude(request.getLongitude())
            .area(request.getArea())
            .textContent(request.getTextContent())
            .imageUrl(request.getImageUrl())
            .videoUrl(request.getVideoUrl())
            .parameters(Map.of(
                "events", List.of(userEvent),
                "media_analysis", true,
                "user_submission", true
            ))
            .build();
        
        return analyzerAgent.processRequest(analyzerRequest)
            .thenApply(analyzerResponse -> {
                if (analyzerResponse.isSuccess() && analyzerResponse.getEvents() != null && !analyzerResponse.getEvents().isEmpty()) {
                    // Return the analyzed and enhanced event
                    CityEvent analyzedEvent = analyzerResponse.getEvents().get(0);
                    String chatResponse = "Thank you for your report! I've analyzed your submission and it has been processed successfully.";
                    
                    Map<String, Object> reportData = new HashMap<>();
                    reportData.put("page", "reporting");
                    reportData.put("submission_status", "success");
                    reportData.put("report_id", analyzerRequest.getRequestId());
                    reportData.put("analyzed_event", convertEventToMap(analyzedEvent));
                    reportData.put("processing_summary", extractProcessingSummary(analyzerResponse));
                    
                    return createSuccessResponse(request, reportData, chatResponse);
                } else {
                    // Analysis failed, return basic event
                    String chatResponse = "Thank you for your report. We've received it and will process it shortly.";
                    
                    Map<String, Object> reportData = new HashMap<>();
                    reportData.put("page", "reporting");
                    reportData.put("submission_status", "received");
                    reportData.put("report_id", request.getRequestId());
                    reportData.put("basic_event", convertEventToMap(userEvent));
                    reportData.put("note", "Basic processing completed, detailed analysis pending");
                    
                    return createSuccessResponse(request, reportData, chatResponse);
                }
            });
    }
    
    /**
     * Check if request has reporting content
     */
    private boolean hasReportingContent(AgentRequest request) {
        boolean hasRequestContent = (request.getTextContent() != null && !request.getTextContent().trim().isEmpty()) ||
               request.getImageUrl() != null || request.getVideoUrl() != null;
        
        boolean hasParameterContent = false;
        if (request.getParameters() != null) {
            Map<String, Object> params = request.getParameters();
            hasParameterContent = params.containsKey("image") || params.containsKey("video") || 
                                params.containsKey("description") || Boolean.TRUE.equals(params.get("has_content"));
        }
        
        return hasRequestContent || hasParameterContent;
    }
    
    /**
     * Create basic event from user report
     */
    private CityEvent createEventFromUserReport(AgentRequest request) {
        String imageUrl = request.getImageUrl();
        String videoUrl = request.getVideoUrl();
        String description = request.getTextContent();
        
        if (request.getParameters() != null) {
            Map<String, Object> params = request.getParameters();
            if (params.containsKey("image") && imageUrl == null) {
                imageUrl = (String) params.get("image");
            }
            if (params.containsKey("video") && videoUrl == null) {
                videoUrl = (String) params.get("video");
            }
            if (params.containsKey("description") && description == null) {
                description = (String) params.get("description");
            }
        }
        
        return CityEvent.builder()
            .id("user_report_" + System.currentTimeMillis())
            .title(generateTitleFromContent(description, request.getCategory()))
            .description(description)
            .content(description)
            .category(categorizeUserReport(request.getCategory(), description))
            .severity(intelligentSeverityService.predictSeveritySync(description, request.getCategory(), request.getArea()))
            .location(CityEvent.LocationData.builder()
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .area(request.getArea())
                .build())
            .timestamp(LocalDateTime.now())
            .source(CityEvent.EventSource.USER_REPORT)
            .confidenceScore(0.5)
            .build();
    }
    
    private String generateTitleFromContent(String content, String category) {
        if (content != null && content.length() > 10) {
            String title = content.substring(0, Math.min(content.length(), 50));
            if (content.length() > 50) {
                title += "...";
            }
            return title;
        }
        
        if (category != null) {
            return "User Report: " + category;
        }
        
        return "Community Report";
    }
    
    private CityEvent.EventCategory categorizeUserReport(String userCategory, String content) {
        if (userCategory != null) {
            try {
                return CityEvent.EventCategory.valueOf(userCategory.toUpperCase());
            } catch (IllegalArgumentException e) {
                // Invalid category, will categorize by content
            }
        }
        
        if (content != null) {
            String lowerContent = content.toLowerCase();
            // Emergency situations first (highest priority)
            if (lowerContent.contains("crash") || lowerContent.contains("accident") || 
                lowerContent.contains("emergency") || lowerContent.contains("fire") ||
                lowerContent.contains("urgent") || lowerContent.contains("critical")) {
                return CityEvent.EventCategory.EMERGENCY;
            }
            if (lowerContent.contains("traffic") || lowerContent.contains("road") || lowerContent.contains("vehicle")) {
                return CityEvent.EventCategory.TRAFFIC;
            }
            if (lowerContent.contains("water") || lowerContent.contains("power") || lowerContent.contains("electricity")) {
                return CityEvent.EventCategory.UTILITY;
            }
            if (lowerContent.contains("garbage") || lowerContent.contains("pothole") || lowerContent.contains("street")) {
                return CityEvent.EventCategory.CIVIC_ISSUE;
            }
        }
        
        return CityEvent.EventCategory.COMMUNITY;
    }
    
    private Map<String, Object> convertEventToMap(CityEvent event) {
        Map<String, Object> eventMap = new HashMap<>();
        eventMap.put("id", event.getId());
        eventMap.put("title", event.getTitle());
        eventMap.put("description", event.getDescription());
        eventMap.put("content", event.getContent());
        eventMap.put("category", event.getCategory() != null ? event.getCategory().name() : null);
        eventMap.put("severity", event.getSeverity() != null ? event.getSeverity().name() : null);
        eventMap.put("timestamp", event.getTimestamp());
        eventMap.put("expires_at", event.getExpiresAt());
        
        if (event.getLocation() != null) {
            Map<String, Object> location = new HashMap<>();
            location.put("latitude", event.getLocation().getLatitude());
            location.put("longitude", event.getLocation().getLongitude());
            location.put("address", event.getLocation().getAddress());
            location.put("area", event.getLocation().getArea());
            location.put("pincode", event.getLocation().getPincode());
            location.put("landmark", event.getLocation().getLandmark());
            eventMap.put("location", location);
        }
        
        eventMap.put("keywords", event.getKeywords());
        eventMap.put("confidence_score", event.getConfidenceScore());
        eventMap.put("ai_summary", event.getAiSummary());
        eventMap.put("source", event.getSource() != null ? event.getSource().name() : null);
        eventMap.put("metadata", event.getMetadata());
        
        return eventMap;
    }
    
    private Map<String, Object> extractProcessingSummary(AgentResponse analyzerResponse) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("processing_time_ms", analyzerResponse.getProcessingTimeMs());
        summary.put("ai_enhanced", true);
        summary.put("analysis_agent", analyzerResponse.getAgentId());
        
        if (analyzerResponse.getMetadata() != null) {
            summary.put("analysis_details", analyzerResponse.getMetadata());
        }
        
        return summary;
    }

    private AgentResponse createSuccessResponse(AgentRequest request, Map<String, Object> data, String chatResponse) {
        return AgentResponse.builder()
            .requestId(request.getRequestId())
            .agentId("home-page-handler")
            .success(true)
            .message(chatResponse)
            .timestamp(LocalDateTime.now())
            .metadata(data)
            .build();
    }
} 