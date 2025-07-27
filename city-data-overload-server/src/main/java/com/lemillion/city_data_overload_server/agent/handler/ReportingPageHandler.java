package com.lemillion.city_data_overload_server.agent.handler;

import com.lemillion.city_data_overload_server.agent.*;
import com.lemillion.city_data_overload_server.agent.impl.AnalyzerAgent;
import com.lemillion.city_data_overload_server.agent.impl.CoordinatorAgent;
import com.lemillion.city_data_overload_server.model.CityEvent;
import com.lemillion.city_data_overload_server.service.VertexAiService;
import com.lemillion.city_data_overload_server.service.IntelligentSeverityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Handler for Flutter REPORTING page requests
 * Provides user submission interface and processing
 */
@Component
@FlutterPageType(value = "REPORTING", priority = 10, description = "User reporting interface with AI analysis")
@RequiredArgsConstructor
@Slf4j
public class ReportingPageHandler implements FlutterPageHandler {

    private final AnalyzerAgent analyzerAgent;
    private final CoordinatorAgent coordinatorAgent;
    private final VertexAiService vertexAiService;
    private final IntelligentSeverityService intelligentSeverityService;

    @Override
    public String getPageType() {
        return "REPORTING";
    }

    @Override
    public int getPriority() {
        return 10;
    }

    @Override
    public boolean canHandle(AgentRequest request) {
        String requestType = request.getRequestType();
        Map<String, Object> params = request.getParameters();
        
        return (requestType != null && requestType.contains("REPORT")) ||
               (params != null && "reporting".equals(params.get("page")));
    }

    @Override
    public CompletableFuture<AgentResponse> handle(AgentRequest request) {
        log.info("Processing REPORTING page request with content: {}", 
                request.getTextContent() != null ? "text+media" : "query");
        
        if (hasReportingContent(request)) {
            // Process actual user report submission
            return processUserReport(request);
        } else {
            // Show reporting interface with chat guidance
            return showReportingInterface(request);
        }
    }

    @Override
    public String getDescription() {
        return "Handles user reporting interface and processes submissions with AI analysis";
    }

    private boolean hasReportingContent(AgentRequest request) {
        // Check request fields
        boolean hasRequestContent = (request.getTextContent() != null && !request.getTextContent().trim().isEmpty()) ||
               request.getImageUrl() != null || request.getVideoUrl() != null;
        
        // Check parameters for JSON body content
        boolean hasParameterContent = false;
        if (request.getParameters() != null) {
            Map<String, Object> params = request.getParameters();
            hasParameterContent = params.containsKey("image") || params.containsKey("video") || 
                                params.containsKey("description") || Boolean.TRUE.equals(params.get("has_content"));
        }
        
        log.debug("Content detection: request={}, parameters={}", hasRequestContent, hasParameterContent);
        return hasRequestContent || hasParameterContent;
    }

    private CompletableFuture<AgentResponse> processUserReport(AgentRequest request) {
        log.info("Processing user report submission for user: {}", request.getUserId());
        
        // Create a basic event structure from user report
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
                    String chatResponse = generateReportConfirmation(request, analyzerResponse);
                    
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

    private CompletableFuture<AgentResponse> showReportingInterface(AgentRequest request) {
        return generateChatResponse(
            request.getQuery() != null ? request.getQuery() : "I want to report something in my area", 
            request
        ).thenApply(chatResponse -> {
            Map<String, Object> interfaceData = Map.of(
                "page", "reporting",
                "mode", "interface",
                "reporting_categories", getReportingCategories(),
                "location_info", buildLocationInfo(request),
                "submission_tips", getSubmissionTips()
            );
            
            return createSuccessResponse(request, interfaceData, chatResponse);
        });
    }

    private CompletableFuture<String> generateChatResponse(String userQuery, AgentRequest request) {
        String enhancedPrompt = buildContextualPrompt(userQuery, request);
        
        return vertexAiService.synthesizeEvents(List.of(), enhancedPrompt)
            .exceptionally(throwable -> {
                log.warn("AI chat response failed, using fallback", throwable);
                return "I'm here to help you report issues in your community. What would you like to report?";
            });
    }

    private String buildContextualPrompt(String userQuery, AgentRequest request) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a helpful city assistant for Bengaluru. ");
        prompt.append("User wants to report something. Guide them through the reporting process. ");
        
        if (request.getArea() != null) {
            prompt.append("Focus on the ").append(request.getArea()).append(" area. ");
        }
        
        prompt.append("User query: \"").append(userQuery).append("\"");
        prompt.append("\n\nProvide a concise, helpful response in conversational tone.");
        
        return prompt.toString();
    }

    private CityEvent createEventFromUserReport(AgentRequest request) {
        // Extract image/video URLs from parameters if available
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
        
        // Build media attachments if available
        List<CityEvent.MediaAttachment> mediaAttachments = new ArrayList<>();
        if (imageUrl != null) {
            mediaAttachments.add(CityEvent.MediaAttachment.builder()
                .url(imageUrl)
                .description("User uploaded image")
                .build());
        }
        if (videoUrl != null) {
            mediaAttachments.add(CityEvent.MediaAttachment.builder()
                .url(videoUrl)
                .description("User uploaded video")
                .build());
        }
        
        // Create metadata for user submission
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("user_submission", true);
        metadata.put("has_media", !mediaAttachments.isEmpty());
        metadata.put("submission_timestamp", LocalDateTime.now());
        if (request.getCategory() != null) {
            metadata.put("user_category", request.getCategory());
        }
        
        return CityEvent.builder()
            .id("user_report_" + System.currentTimeMillis())
            .title(generateTitleFromContent(description, request.getCategory()))
            .description(description)
            .content(description)
            .category(categorizeUserReport(request.getCategory(), description))
            .severity(intelligentSeverityService.predictSeveritySync(description, request.getCategory(), request.getArea())) // AI-powered severity prediction
            .location(CityEvent.LocationData.builder()
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .area(request.getArea())
                .build())
            .timestamp(LocalDateTime.now())
            .source(CityEvent.EventSource.USER_REPORT)
            .mediaAttachments(mediaAttachments.isEmpty() ? null : mediaAttachments)
            .metadata(metadata)
            .confidenceScore(0.5) // Default confidence for user reports
            .build();
    }
    
    private String generateTitleFromContent(String content, String category) {
        if (content != null && content.length() > 10) {
            // Take first 50 characters as title
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
            if (lowerContent.contains("traffic") || lowerContent.contains("road") || lowerContent.contains("accident")) {
                return CityEvent.EventCategory.TRAFFIC;
            }
            if (lowerContent.contains("water") || lowerContent.contains("power") || lowerContent.contains("electricity")) {
                return CityEvent.EventCategory.UTILITY;
            }
            if (lowerContent.contains("garbage") || lowerContent.contains("pothole") || lowerContent.contains("street")) {
                return CityEvent.EventCategory.CIVIC_ISSUE;
            }
            if (lowerContent.contains("emergency") || lowerContent.contains("fire") || lowerContent.contains("accident")) {
                return CityEvent.EventCategory.EMERGENCY;
            }
        }
        
        return CityEvent.EventCategory.COMMUNITY; // Default
    }

    private CityEvent.EventSeverity determineSeverityFromContent(String description, String category) {
        if (description == null) {
            return CityEvent.EventSeverity.LOW;
        }
        
        String content = description.toLowerCase();
        
        // Critical severity keywords
        if (content.contains("crash") || content.contains("accident") || 
            content.contains("emergency") || content.contains("urgent") || 
            content.contains("critical") || content.contains("fire") ||
            content.contains("bomb") || content.contains("explosion") ||
            content.contains("major") || content.contains("severe") ||
            content.contains("life threatening") || content.contains("danger")) {
            return CityEvent.EventSeverity.CRITICAL;
        }
        
        // High severity keywords  
        if (content.contains("blocked") || content.contains("breakdown") ||
            content.contains("outage") || content.contains("disruption") ||
            content.contains("warning") || content.contains("alert") ||
            content.contains("flooding") || content.contains("leak") ||
            content.contains("fallen tree") || content.contains("stuck")) {
            return CityEvent.EventSeverity.HIGH;
        }
        
        // Moderate severity keywords
        if (content.contains("slow") || content.contains("delayed") ||
            content.contains("issue") || content.contains("problem") ||
            content.contains("pothole") || content.contains("garbage")) {
            return CityEvent.EventSeverity.MODERATE;
        }
        
        // Category-based severity
        if ("EMERGENCY".equals(category) || "TRAFFIC".equals(category)) {
            return CityEvent.EventSeverity.HIGH;
        }
        
        return CityEvent.EventSeverity.LOW; // Default
    }

    private String generateReportConfirmation(AgentRequest request, AgentResponse analyzerResponse) {
        if (analyzerResponse.isSuccess()) {
            return "Thank you for your report! I've analyzed your submission and it has been processed successfully. Your contribution helps keep our community informed.";
        } else {
            return "I received your report but encountered some issues during processing. Don't worry - I've saved your submission and our team will review it manually.";
        }
    }

    private String extractCategoryFromAnalysis(AgentResponse analyzerResponse) {
        // Extract category from analyzer response metadata
        if (analyzerResponse.getAnalysisResults() != null) {
            return (String) analyzerResponse.getAnalysisResults().getOrDefault("category", "COMMUNITY");
        }
        return "COMMUNITY";
    }

    private List<String> generateNextSteps(AgentResponse analyzerResponse) {
        if (analyzerResponse.isSuccess()) {
            return List.of(
                "Your report has been saved to our database",
                "It will be visible to other users in your area",
                "You'll receive updates if there are similar reports",
                "Thank you for helping your community!"
            );
        } else {
            return List.of(
                "Your report is being reviewed by our team",
                "You may be contacted for additional information",
                "Check back later for updates"
            );
        }
    }

    private List<String> getReportingCategories() {
        return List.of(
            "Traffic Issue",
            "Civic Problem", 
            "Emergency",
            "Infrastructure",
            "Community Event",
            "Other"
        );
    }

    private Map<String, Object> buildLocationInfo(AgentRequest request) {
        Map<String, Object> locationInfo = new HashMap<>();
        if (request.getLatitude() != null) locationInfo.put("latitude", request.getLatitude());
        if (request.getLongitude() != null) locationInfo.put("longitude", request.getLongitude());
        if (request.getArea() != null) locationInfo.put("area", request.getArea());
        return locationInfo;
    }

    private List<String> getSubmissionTips() {
        return List.of(
            "Include clear photos or videos if possible",
            "Provide specific location details",
            "Describe the issue clearly",
            "Mention any safety concerns",
            "Include relevant timing information"
        );
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
            .agentId("reporting-page-handler")
            .success(true)
            .message(chatResponse)
            .timestamp(LocalDateTime.now())
            .metadata(data)
            .build();
    }
} 