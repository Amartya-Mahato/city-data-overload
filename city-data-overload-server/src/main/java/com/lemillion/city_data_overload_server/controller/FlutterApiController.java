package com.lemillion.city_data_overload_server.controller;

import com.lemillion.city_data_overload_server.agent.AgentRequest;
import com.lemillion.city_data_overload_server.agent.AgentResponse;
import com.lemillion.city_data_overload_server.agent.impl.CoordinatorAgent;
import com.lemillion.city_data_overload_server.service.VertexAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Flutter API Controller - Single entry point for Flutter application
 * 
 * All Flutter requests go through the CoordinatorAgent for intelligent routing
 * and unified chat interface.
 */
@RestController
@RequestMapping("/api/flutter")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class FlutterApiController {

    private final CoordinatorAgent coordinatorAgent;
    private final VertexAiService vertexAiService;

    /**
     * Main chat endpoint - handles all text-based interactions
     * Used for: Home page chat, Events page queries, Alerts page questions, general chat
     * 
     * @param request Chat request with query and context
     * @return Unified response with data + chat message
     */
    @PostMapping("/chat")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> chat(
            @RequestBody ChatRequest request) {
        
        log.info("Flutter chat request: '{}' for page: {}", request.getQuery(), request.getPage());
        
        AgentRequest agentRequest = AgentRequest.builder()
            .requestId("flutter_" + System.currentTimeMillis())
            .requestType(determineRequestType(request.getPage()))
            .userId(request.getUserId())
            .timestamp(LocalDateTime.now())
            .query(request.getQuery())
            .latitude(request.getLatitude())
            .longitude(request.getLongitude())
            .area(request.getArea())
            .radiusKm(request.getRadiusKm())
            .category(request.getCategory())
            .severity(request.getSeverity())
            .maxResults(request.getMaxResults())
            .parameters(Map.of(
                "page", request.getPage() != null ? request.getPage() : "home",
                "session_id", request.getSessionId() != null ? request.getSessionId() : "default"
            ))
            .build();

        return coordinatorAgent.processRequest(agentRequest)
            .thenApply(this::formatFlutterResponse)
            .exceptionally(throwable -> {
                log.error("Flutter chat request failed", throwable);
                return ResponseEntity.internalServerError()
                    .body(createErrorResponse("Chat request failed", throwable));
            });
    }

    /**
     * Events page endpoint - specifically for events listing
     * Flutter static prompt: "Show me events in my area"
     * 
     * @param request Events request with filters
     * @return Events list with chat commentary
     */
    @PostMapping("/events")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> getEvents(
            @RequestBody EventsRequest request) {
        
        log.info("Flutter events request for area: {}", request.getArea());
        
        AgentRequest agentRequest = AgentRequest.builder()
            .requestId("flutter_events_" + System.currentTimeMillis())
            .requestType("FLUTTER_EVENTS")
            .userId(request.getUserId())
            .timestamp(LocalDateTime.now())
            .query(request.getQuery() != null ? request.getQuery() : "Show me events in my area")
            .latitude(request.getLatitude())
            .longitude(request.getLongitude())
            .area(request.getArea())
            .radiusKm(request.getRadiusKm() != null ? request.getRadiusKm() : 5.0)
            .category(request.getCategory())
            .severity(request.getSeverity())
            .startTime(request.getStartTime())
            .endTime(request.getEndTime())
            .maxResults(request.getMaxResults() != null ? request.getMaxResults() : 20)
            .parameters(Map.of("page", "events"))
            .build();

        return coordinatorAgent.processRequest(agentRequest)
            .thenApply(this::formatFlutterResponse)
            .exceptionally(throwable -> {
                log.error("Flutter events request failed", throwable);
                return ResponseEntity.internalServerError()
                    .body(createErrorResponse("Events request failed", throwable));
            });
    }

    /**
     * Alerts page endpoint - for predictions and warnings
     * Uses PredictiveAgent through Coordinator
     * 
     * @param request Alerts request with location context
     * @return Alerts list with safety guidance
     */
    @PostMapping("/alerts")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> getAlerts(
            @RequestBody AlertsRequest request) {
        
        log.info("Flutter alerts request for area: {}", request.getArea());
        
        AgentRequest agentRequest = AgentRequest.builder()
            .requestId("flutter_alerts_" + System.currentTimeMillis())
            .requestType("FLUTTER_ALERTS")
            .userId(request.getUserId())
            .timestamp(LocalDateTime.now())
            .query(request.getQuery() != null ? request.getQuery() : 
                   "What alerts should I be aware of in my area?")
            .latitude(request.getLatitude())
            .longitude(request.getLongitude())
            .area(request.getArea())
            .radiusKm(request.getRadiusKm() != null ? request.getRadiusKm() : 5.0)
            .maxResults(request.getMaxResults() != null ? request.getMaxResults() : 10)
            .parameters(Map.of("page", "alerts"))
            .build();

        return coordinatorAgent.processRequest(agentRequest)
            .thenApply(this::formatFlutterResponse)
            .exceptionally(throwable -> {
                log.error("Flutter alerts request failed", throwable);
                return ResponseEntity.internalServerError()
                    .body(createErrorResponse("Alerts request failed", throwable));
            });
    }

    /**
     * Reporting endpoint - handles user submissions with media
     * Uses AnalyzerAgent through Coordinator for processing
     * 
     * @param description Text description
     * @param latitude Location latitude
     * @param longitude Location longitude
     * @param area Area name
     * @param image Optional image file
     * @param video Optional video file
     * @param userId User identifier
     * @return Submission confirmation with analysis
     */
    @PostMapping("/report")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> submitReport(
            @RequestParam(required = false) String description,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) MultipartFile image,
            @RequestParam(required = false) MultipartFile video,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String category,
            HttpServletRequest request) {
        
        log.info("Flutter report submission from user: {} in area: {}", userId, area);
        
        // Handle both form data and JSON body
        String imageUrl = null;
        String videoUrl = null;
        String finalDescription = description;
        
        // List to track analysis futures for image/video processing
        List<CompletableFuture<Map<String, Object>>> analysisFutures = new ArrayList<>();
        
        // Manually parse JSON body to handle malformed JSON gracefully
        Map<String, Object> reportData = parseJsonBodySafely(request);
        if (reportData != null && !reportData.isEmpty()) {
            if (reportData.containsKey("description")) {
                finalDescription = (String) reportData.get("description");
            }
            if (reportData.containsKey("image")) {
                imageUrl = (String) reportData.get("image");
                String currentDescription = finalDescription; // Use current value for analysis
                analysisFutures.add(processImageContent(imageUrl, currentDescription));
                log.info("Image URL from JSON: {}", imageUrl);
            }
            if (reportData.containsKey("video")) {
                videoUrl = (String) reportData.get("video");
                String currentDescription = finalDescription; // Use current value for analysis
                analysisFutures.add(processVideoContent(videoUrl, currentDescription));
            }

        }
        
        // Create final copies for lambda usage
        final String finalImageUrl = imageUrl;
        final String finalVideoUrl = videoUrl;
        final String finalDescriptionForLambda = finalDescription;
        
        // Build parameters map including JSON body data
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("page", "reporting");
        parameters.put("has_content", imageUrl != null || videoUrl != null || (finalDescription != null && !finalDescription.trim().isEmpty()));
        if (reportData != null) {
            parameters.putAll(reportData);
        }
        
        // Wait for all analysis futures to complete if any exist
        if (!analysisFutures.isEmpty()) {
            return CompletableFuture.allOf(analysisFutures.toArray(new CompletableFuture[0]))
                .thenCompose(v -> {
                    // Collect analysis results
                    List<Map<String, Object>> analysisResults = analysisFutures.stream()
                        .map(CompletableFuture::join)
                        .collect(java.util.stream.Collectors.toList());
                    
                    // Add analysis results to parameters
                    parameters.put("analysis_results", analysisResults);
                    
                    // Extract enhanced description from analysis if available
                    String enhancedDescription = finalDescriptionForLambda;
                    for (Map<String, Object> result : analysisResults) {
                        if (result.containsKey("description")) {
                            String analysisDesc = (String) result.get("description");
                            if (analysisDesc != null && !analysisDesc.isEmpty()) {
                                enhancedDescription = (enhancedDescription != null ? enhancedDescription + ". " : "") + analysisDesc;
                            }
                        }
                    }
                    
                    AgentRequest agentRequest = AgentRequest.builder()
                        .requestId("user_report_" + System.currentTimeMillis())
                        .requestType("FLUTTER_REPORTING")
                        .userId(userId)
                        .timestamp(LocalDateTime.now())
                        .textContent(enhancedDescription)
                        .latitude(latitude)
                        .longitude(longitude)
                        .area(area)
                        .imageUrl(finalImageUrl)
                        .videoUrl(finalVideoUrl)
                        .category(category)
                        .parameters(parameters)
                        .build();

                    return coordinatorAgent.processRequest(agentRequest);
                })
                .thenApply(this::formatFlutterResponse)
                .exceptionally(throwable -> {
                    log.error("Flutter report submission failed", throwable);
                    return ResponseEntity.internalServerError()
                        .body(createErrorResponse("Report submission failed", throwable));
                });
        } else {
            // No media to process, proceed directly
            AgentRequest agentRequest = AgentRequest.builder()
                .requestId("user_report_" + System.currentTimeMillis())
                .requestType("FLUTTER_REPORTING")
                .userId(userId)
                .timestamp(LocalDateTime.now())
                .textContent(finalDescriptionForLambda)
                .latitude(latitude)
                .longitude(longitude)
                .area(area)
                .imageUrl(finalImageUrl)
                .videoUrl(finalVideoUrl)
                .category(category)
                .parameters(parameters)
                .build();

            return coordinatorAgent.processRequest(agentRequest)
                .thenApply(this::formatFlutterResponse)
                .exceptionally(throwable -> {
                    log.error("Flutter report submission failed", throwable);
                    return ResponseEntity.internalServerError()
                        .body(createErrorResponse("Report submission failed", throwable));
                });
        }
    }

    /**
     * Home/Dashboard endpoint - gets summary data for main page
     * 
     * @param request Dashboard request with location
     * @return Dashboard data with welcome message
     */
    @PostMapping("/dashboard")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> getDashboard(
            @RequestBody DashboardRequest request) {
        
        log.info("Flutter dashboard request for user: {} in area: {}", request.getUserId(), request.getArea());
        
        AgentRequest agentRequest = AgentRequest.builder()
            .requestId("flutter_dashboard_" + System.currentTimeMillis())
            .requestType("FLUTTER_HOME")
            .userId(request.getUserId())
            .timestamp(LocalDateTime.now())
            .query(request.getQuery() != null ? request.getQuery() : 
                   "Show me what's happening in my area")
            .latitude(request.getLatitude())
            .longitude(request.getLongitude())
            .area(request.getArea())
            .radiusKm(request.getRadiusKm() != null ? request.getRadiusKm() : 5.0)
            .parameters(Map.of("page", "home"))
            .build();

        return coordinatorAgent.processRequest(agentRequest)
            .thenApply(this::formatFlutterResponse)
            .exceptionally(throwable -> {
                log.error("Flutter dashboard request failed", throwable);
                return ResponseEntity.internalServerError()
                    .body(createErrorResponse("Dashboard request failed", throwable));
            });
    }

    // Helper Methods

    /**
     * Determine request type based on page
     */
    private String determineRequestType(String page) {
        if (page == null) return "FLUTTER_HOME";
        
        return switch (page.toLowerCase()) {
            case "events" -> "FLUTTER_EVENTS";
            case "alerts" -> "FLUTTER_ALERTS";
            case "reporting" -> "FLUTTER_REPORTING";
            case "chat" -> "FLUTTER_CHAT";
            default -> "FLUTTER_HOME";
        };
    }

    /**
     * Format agent response for Flutter consumption
     */
    private ResponseEntity<Map<String, Object>> formatFlutterResponse(AgentResponse agentResponse) {
        Map<String, Object> response = new HashMap<>();
        
        response.put("success", agentResponse.isSuccess());
        response.put("message", agentResponse.getMessage());
        response.put("timestamp", agentResponse.getTimestamp());
        response.put("request_id", agentResponse.getRequestId());
        
        // Include all data from metadata
        if (agentResponse.getMetadata() != null) {
            response.putAll(agentResponse.getMetadata());
        }
        
        // Add specific data fields if available
        if (agentResponse.getEvents() != null) {
            response.put("events", agentResponse.getEvents());
        }
        
        if (agentResponse.getAlerts() != null) {
            response.put("alerts", agentResponse.getAlerts());
        }
        
        if (agentResponse.getAnalysisResults() != null) {
            response.put("analysis", agentResponse.getAnalysisResults());
        }
        
        return ResponseEntity.ok(response);
    }

    /**
     * Create error response
     */
    private Map<String, Object> createErrorResponse(String message, Throwable throwable) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        response.put("error_details", throwable.getMessage());
        response.put("timestamp", LocalDateTime.now());
        response.put("chat_response", "I'm sorry, I encountered an issue. Please try again or contact support.");
        return response;
    }

    // Request DTOs

    /**
     * Chat request DTO
     */
    public static class ChatRequest {
        private String query;
        private String page;
        private String userId;
        private String sessionId;
        private Double latitude;
        private Double longitude;
        private String area;
        private Double radiusKm;
        private String category;
        private String severity;
        private Integer maxResults;

        // Getters and setters
        public String getQuery() { return query; }
        public void setQuery(String query) { this.query = query; }
        
        public String getPage() { return page; }
        public void setPage(String page) { this.page = page; }
        
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        
        public Double getLatitude() { return latitude; }
        public void setLatitude(Double latitude) { this.latitude = latitude; }
        
        public Double getLongitude() { return longitude; }
        public void setLongitude(Double longitude) { this.longitude = longitude; }
        
        public String getArea() { return area; }
        public void setArea(String area) { this.area = area; }
        
        public Double getRadiusKm() { return radiusKm; }
        public void setRadiusKm(Double radiusKm) { this.radiusKm = radiusKm; }
        
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        
        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }
        
        public Integer getMaxResults() { return maxResults; }
        public void setMaxResults(Integer maxResults) { this.maxResults = maxResults; }
    }

    /**
     * Events request DTO
     */
    public static class EventsRequest {
        private String userId;
        private String query;
        private Double latitude;
        private Double longitude;
        private String area;
        private Double radiusKm;
        private String category;
        private String severity;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private Integer maxResults;

        // Getters and setters
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        
        public String getQuery() { return query; }
        public void setQuery(String query) { this.query = query; }
        
        public Double getLatitude() { return latitude; }
        public void setLatitude(Double latitude) { this.latitude = latitude; }
        
        public Double getLongitude() { return longitude; }
        public void setLongitude(Double longitude) { this.longitude = longitude; }
        
        public String getArea() { return area; }
        public void setArea(String area) { this.area = area; }
        
        public Double getRadiusKm() { return radiusKm; }
        public void setRadiusKm(Double radiusKm) { this.radiusKm = radiusKm; }
        
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        
        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }
        
        public LocalDateTime getStartTime() { return startTime; }
        public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
        
        public LocalDateTime getEndTime() { return endTime; }
        public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
        
        public Integer getMaxResults() { return maxResults; }
        public void setMaxResults(Integer maxResults) { this.maxResults = maxResults; }
    }

    /**
     * Alerts request DTO
     */
    public static class AlertsRequest {
        private String userId;
        private String query;
        private Double latitude;
        private Double longitude;
        private String area;
        private Double radiusKm;
        private Integer maxResults;

        // Getters and setters
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        
        public String getQuery() { return query; }
        public void setQuery(String query) { this.query = query; }
        
        public Double getLatitude() { return latitude; }
        public void setLatitude(Double latitude) { this.latitude = latitude; }
        
        public Double getLongitude() { return longitude; }
        public void setLongitude(Double longitude) { this.longitude = longitude; }
        
        public String getArea() { return area; }
        public void setArea(String area) { this.area = area; }
        
        public Double getRadiusKm() { return radiusKm; }
        public void setRadiusKm(Double radiusKm) { this.radiusKm = radiusKm; }
        
        public Integer getMaxResults() { return maxResults; }
        public void setMaxResults(Integer maxResults) { this.maxResults = maxResults; }
    }

    /**
     * Dashboard request DTO
     */
    public static class DashboardRequest {
        private String userId;
        private String query;
        private Double latitude;
        private Double longitude;
        private String area;
        private Double radiusKm;

        // Getters and setters
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        
        public String getQuery() { return query; }
        public void setQuery(String query) { this.query = query; }
        
        public Double getLatitude() { return latitude; }
        public void setLatitude(Double latitude) { this.latitude = latitude; }
        
        public Double getLongitude() { return longitude; }
        public void setLongitude(Double longitude) { this.longitude = longitude; }
        
        public String getArea() { return area; }
        public void setArea(String area) { this.area = area; }
        
        public Double getRadiusKm() { return radiusKm; }
        public void setRadiusKm(Double radiusKm) { this.radiusKm = radiusKm; }
    }
    
    /**
     * Safely parse JSON body, handling malformed JSON gracefully
     */
    private Map<String, Object> parseJsonBodySafely(HttpServletRequest request) {
        try {
            // Read the request body
            StringBuilder sb = new StringBuilder();
            try (java.io.BufferedReader reader = request.getReader()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
            
            String jsonBody = sb.toString().trim();
            if (jsonBody.isEmpty()) {
                return null;
            }
            
            log.debug("Raw JSON body: {}", jsonBody);
            
            // Fix common JSON issues
            jsonBody = fixCommonJsonIssues(jsonBody);
            
            // Parse the corrected JSON
            ObjectMapper mapper = new ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> result = mapper.readValue(jsonBody, Map.class);
            
            log.debug("Parsed JSON successfully: {}", result);
            return result;
            
        } catch (Exception e) {
            log.warn("Failed to parse JSON body, ignoring: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Fix common JSON formatting issues
     */
    private String fixCommonJsonIssues(String json) {
        // Remove trailing commas before closing braces/brackets
        json = json.replaceAll(",\\s*}", "}");
        json = json.replaceAll(",\\s*]", "]");
        
        return json;
    }
    
    /**
     * Process image content using AI vision
     */
    private CompletableFuture<Map<String, Object>> processImageContent(String imageUrl, String additionalContext) {
        log.debug("Processing image content from URL: {}", imageUrl);
        
        return vertexAiService.analyzeImage(imageUrl, additionalContext)
            .thenApply(analysis -> {
                Map<String, Object> result = new HashMap<>(analysis);
                result.put("content_type", "image");
                result.put("media_url", imageUrl);
                return result;
            })
            .exceptionally(throwable -> {
                log.warn("Image analysis failed, using defaults", throwable);
                return Map.of(
                    "content_type", "image",
                    "media_url", imageUrl,
                    "description", "Image content could not be analyzed",
                    "category", "COMMUNITY",
                    "confidence", 0.2
                );
            });
    }

    /**
     * Process video content (placeholder implementation)
     */
    private CompletableFuture<Map<String, Object>> processVideoContent(String videoUrl, String additionalContext) {
        log.debug("Processing video content from URL: {}", videoUrl);
        
        // Placeholder - in production, this would use video analysis services
        return CompletableFuture.completedFuture(Map.of(
            "content_type", "video",
            "media_url", videoUrl,
            "description", "Video content analysis not yet implemented",
            "category", "COMMUNITY",
            "confidence", 0.1
        ));
    }
} 