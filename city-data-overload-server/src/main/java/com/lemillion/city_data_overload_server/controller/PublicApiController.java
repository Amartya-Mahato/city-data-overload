package com.lemillion.city_data_overload_server.controller;

import com.lemillion.city_data_overload_server.agent.AgentRequest;
import com.lemillion.city_data_overload_server.agent.AgentResponse;
import com.lemillion.city_data_overload_server.agent.impl.*;
import com.lemillion.city_data_overload_server.model.CityEvent;
import com.lemillion.city_data_overload_server.service.EventStreamService;
import com.lemillion.city_data_overload_server.service.BigQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Flutter-focused API controller for City Data Overload challenge.
 * Implements all endpoints required by the Flutter mobile application.
 */
@RestController
@RequestMapping("/api/v1/flutter")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Flutter API", description = "Mobile app endpoints for City Data Overload challenge")
@Validated
@CrossOrigin(origins = "*") // Configure properly for production
public class PublicApiController {

    private final CoordinatorAgent coordinatorAgent;
    private final FusionAgent fusionAgent;
    private final PredictiveAgent predictiveAgent;
    private final UserReportingAgent userReportingAgent;
    private final MoodMapAgent moodMapAgent;
    private final EventStreamService eventStreamService;
    private final com.lemillion.city_data_overload_server.service.UserReportService userReportService;
    private final BigQueryService bigQueryService;

    // ============ 1. MAP & CHAT PAGE ENDPOINTS ============

    /**
     * Flutter Map/Chat Page: Get comprehensive city data (events, alerts, mood) for map display
     * Uses CoordinatorAgent to orchestrate multiple agents and provide complete city pulse
     */
    @PostMapping("/map/comprehensive")
    @Operation(summary = "Get comprehensive city data for map", 
               description = "Returns events, alerts, and mood data for map visualization")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> getComprehensiveCityData(
            @RequestBody Map<String, Object> request) {
        
        double lat = ((Number) request.get("latitude")).doubleValue();
        double lon = ((Number) request.get("longitude")).doubleValue();
        String area = (String) request.getOrDefault("area", "Bengaluru");
        double radius = ((Number) request.getOrDefault("radius", 10.0)).doubleValue();
        
        log.info("Flutter Map API: Comprehensive data for lat={}, lon={}, area={}", lat, lon, area);
        
        AgentRequest agentRequest = AgentRequest.builder()
            .requestId(UUID.randomUUID().toString())
            .requestType("COMPREHENSIVE_ANALYSIS")
            .latitude(lat)
            .longitude(lon)
            .area(area)
            .radiusKm(radius)
            .timestamp(LocalDateTime.now())
            .build();
        
        return coordinatorAgent.processRequest(agentRequest)
            .thenApply(agentResponse -> {
                Map<String, Object> response = new HashMap<>();
                response.put("success", agentResponse.isSuccess());
                
                if (agentResponse.isSuccess()) {
                    response.put("events", agentResponse.getEvents());
                    response.put("alerts", agentResponse.getAlerts());
                    response.put("moodData", agentResponse.getSentimentData());
                    response.put("metadata", agentResponse.getMetadata());
                } else {
                    response.put("error", agentResponse.getMessage());
                }
                
                response.put("location", Map.of("latitude", lat, "longitude", lon, "area", area));
                response.put("timestamp", LocalDateTime.now());
                
                return ResponseEntity.ok(response);
            })
            .exceptionally(throwable -> {
                log.error("Error getting comprehensive city data", throwable);
                return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "error", throwable.getMessage()));
            });
    }

    /**
     * Flutter Chat: AI-powered query endpoint for conversational city data access
     */
    @PostMapping("/chat/query")
    @Operation(summary = "AI chat query", description = "Process natural language queries about city data")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> processChatQuery(
            @RequestBody Map<String, Object> request) {
        
        String query = (String) request.get("query");
        String userId = (String) request.getOrDefault("userId", "anonymous");
        Double lat = request.containsKey("latitude") ? ((Number) request.get("latitude")).doubleValue() : null;
        Double lon = request.containsKey("longitude") ? ((Number) request.get("longitude")).doubleValue() : null;
        
        log.info("Flutter Chat API: Processing query='{}' for user={}", query, userId);
        
        AgentRequest agentRequest = AgentRequest.builder()
            .requestId(UUID.randomUUID().toString())
            .requestType("COMPREHENSIVE_ANALYSIS")
            .query(query)
            .userId(userId)
            .latitude(lat)
            .longitude(lon)
            .timestamp(LocalDateTime.now())
            .build();
        
        return coordinatorAgent.processRequest(agentRequest)
            .thenApply(agentResponse -> {
                Map<String, Object> response = Map.of(
                    "success", agentResponse.isSuccess(),
                    "answer", agentResponse.getMessage(),
                    "data", agentResponse.getMetadata(),
                    "query", query,
                    "timestamp", LocalDateTime.now()
                );
                return ResponseEntity.ok(response);
            })
            .exceptionally(throwable -> {
                log.error("Error processing chat query", throwable);
                return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "error", throwable.getMessage()));
            });
    }

    /**
     * Flutter Map + Chat: Intelligent chat that returns BOTH conversational responses AND structured map data
     * Perfect for map page - provides chat text AND events/alerts with lat/lng for map markers
     */
    @PostMapping("/chat/intelligent")
    @Operation(summary = "Intelligent chat with map data", 
               description = "Returns both conversational AI responses and structured data for map visualization")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> processIntelligentChat(
            @RequestBody Map<String, Object> request) {
        
        String query = (String) request.get("query");
        String userId = (String) request.getOrDefault("userId", "anonymous");
        Double lat = request.containsKey("latitude") ? ((Number) request.get("latitude")).doubleValue() : null;
        Double lon = request.containsKey("longitude") ? ((Number) request.get("longitude")).doubleValue() : null;
        String area = (String) request.getOrDefault("area", null);
        
        log.info("Flutter Intelligent Chat API: Processing query='{}' for user={} at lat={}, lon={}", 
                query, userId, lat, lon);
        
        // Create intelligent chat request
        AgentRequest agentRequest = AgentRequest.builder()
            .requestId(UUID.randomUUID().toString())
            .requestType("INTELLIGENT_CHAT")  // Use the new intelligent chat type
            .query(query)
            .userId(userId)
            .latitude(lat)
            .longitude(lon)
            .area(area)
            .context("Generate both conversational response and structured map data")
            .timestamp(LocalDateTime.now())
            .build();
        
        return coordinatorAgent.processRequest(agentRequest)
            .thenApply(agentResponse -> {
                Map<String, Object> response = new HashMap<>();
                response.put("success", agentResponse.isSuccess());
                response.put("query", query);
                response.put("timestamp", LocalDateTime.now());
                
                if (agentResponse.isSuccess()) {
                    // Conversational AI response for chat display
                    response.put("chatResponse", agentResponse.getMessage());
                    
                    // Structured data for map visualization
                    Map<String, Object> mapData = new HashMap<>();
                    
                    // Events with lat/lng for map markers
                    if (agentResponse.getEvents() != null && !agentResponse.getEvents().isEmpty()) {
                        mapData.put("events", agentResponse.getEvents().stream()
                            .map(event -> Map.of(
                                "id", event.getId(),
                                "title", event.getTitle(),
                                "description", event.getDescription() != null ? event.getDescription() : "",
                                "category", event.getCategory().name(),
                                "severity", event.getSeverity().name(),
                                "latitude", event.getLocation().getLatitude(),
                                "longitude", event.getLocation().getLongitude(),
                                "area", event.getLocation().getArea() != null ? event.getLocation().getArea() : "",
                                "timestamp", event.getTimestamp(),
                                "aiSummary", event.getAiSummary() != null ? event.getAiSummary() : ""
                            ))
                            .toList());
                    }
                    
                    // Alerts with lat/lng for map markers
                    if (agentResponse.getAlerts() != null && !agentResponse.getAlerts().isEmpty()) {
                        mapData.put("alerts", agentResponse.getAlerts().stream()
                            .map(alert -> {
                                Map<String, Object> alertMap = new HashMap<>(alert);
                                // Ensure lat/lng are present for map display
                                if (!alertMap.containsKey("latitude") && lat != null) {
                                    alertMap.put("latitude", lat);
                                }
                                if (!alertMap.containsKey("longitude") && lon != null) {
                                    alertMap.put("longitude", lon);
                                }
                                return alertMap;
                            })
                            .toList());
                    }
                    
                    // Mood data with coordinates (from metadata)
                    Map<String, Object> metadata = agentResponse.getMetadata();
                    if (metadata != null && metadata.containsKey("mood_data")) {
                        Map<String, Object> moodData = new HashMap<>();
                        Object moodObj = metadata.get("mood_data");
                        if (moodObj instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> sentimentMap = (Map<String, Object>) moodObj;
                            moodData.putAll(sentimentMap);
                        }
                        // Add coordinates for mood visualization
                        if (lat != null && lon != null) {
                            moodData.put("latitude", lat);
                            moodData.put("longitude", lon);
                            moodData.put("area", area);
                        }
                        mapData.put("mood", moodData);
                    }
                    
                    // Location context
                    if (lat != null && lon != null) {
                        mapData.put("location", Map.of(
                            "latitude", lat,
                            "longitude", lon,
                            "area", area != null ? area : "Current Location"
                        ));
                    }
                    
                    response.put("mapData", mapData);
                    response.put("hasMapData", !mapData.isEmpty());
                    
                    // Additional metadata
                    response.put("metadata", agentResponse.getMetadata());
                } else {
                    response.put("chatResponse", "I'm sorry, I couldn't process your request at the moment. Please try again.");
                    response.put("error", agentResponse.getMessage());
                    response.put("hasMapData", false);
                }
                
                return ResponseEntity.ok(response);
            })
            .exceptionally(throwable -> {
                log.error("Error processing intelligent chat query", throwable);
                return ResponseEntity.internalServerError()
                    .body(Map.of(
                        "success", false, 
                        "chatResponse", "I'm experiencing technical difficulties. Please try again later.",
                        "error", throwable.getMessage(),
                        "hasMapData", false,
                        "query", query,
                        "timestamp", LocalDateTime.now()
                    ));
            });
    }

    // ============ 2. EVENTS PAGE ENDPOINTS ============

    /**
     * Flutter Events Page: Get synthesized events for user's current location
     * Uses FusionAgent to provide clean, AI-summarized events without duplicates
     */
    @PostMapping("/events/synthesized")
    @Operation(summary = "Get synthesized events for location", 
               description = "Returns AI-fused and summarized events for user's area")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> getSynthesizedEvents(
            @RequestBody Map<String, Object> request) {
        
        Double lat = request.containsKey("latitude") ? ((Number) request.get("latitude")).doubleValue() : null;
        Double lon = request.containsKey("longitude") ? ((Number) request.get("longitude")).doubleValue() : null;
        String area = (String) request.get("area");
        String city = (String) request.getOrDefault("city", "Bengaluru");
        int maxResults = ((Number) request.getOrDefault("maxResults", 20)).intValue();
        
        log.info("Flutter Events API: Synthesized events for area={}, city={}", area, city);
        
        AgentRequest agentRequest = AgentRequest.builder()
            .requestId(UUID.randomUUID().toString())
            .requestType("FUSE_DATA")
            .latitude(lat)
            .longitude(lon)
            .area(area)
            .maxResults(maxResults)
            .context("Get latest events and news for " + (area != null ? area : city))
            .timestamp(LocalDateTime.now())
            .build();
        
        return fusionAgent.processRequest(agentRequest)
            .thenApply(agentResponse -> {
                Map<String, Object> response = Map.of(
                    "success", agentResponse.isSuccess(),
                    "synthesizedEvents", agentResponse.getEvents() != null ? agentResponse.getEvents() : List.of(),
                    "summary", agentResponse.getMessage(),
                    "area", area != null ? area : city,
                    "timestamp", LocalDateTime.now()
                );
                return ResponseEntity.ok(response);
            })
            .exceptionally(throwable -> {
                log.error("Error getting synthesized events", throwable);
                return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "error", throwable.getMessage()));
            });
    }

    // ============ 3. ALERT PAGE ENDPOINTS ============

    /**
     * Flutter Alert Page: Get predictive alerts for user's area
     * Uses PredictiveAgent to analyze patterns and forecast potential issues
     */
    @PostMapping("/alerts/predictive")
    @Operation(summary = "Get predictive alerts", 
               description = "Analyze current events to predict potential issues in user's area")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> getPredictiveAlerts(
            @RequestBody Map<String, Object> request) {
        
        String area = (String) request.get("area");
        String category = (String) request.get("category");
        String city = (String) request.getOrDefault("city", "Bengaluru");
        Double lat = request.containsKey("latitude") ? ((Number) request.get("latitude")).doubleValue() : null;
        Double lon = request.containsKey("longitude") ? ((Number) request.get("longitude")).doubleValue() : null;
        
        log.info("Flutter Alert API: Predictive alerts for area={}, city={}", area, city);
        
        AgentRequest agentRequest = AgentRequest.builder()
            .requestId(UUID.randomUUID().toString())
            .requestType("GET_PREDICTIONS")
            .area(area)
            .latitude(lat)
            .longitude(lon)
            .category(category)
            .context("Generate predictive alerts and insights for " + (area != null ? area : city))
            .timestamp(LocalDateTime.now())
            .build();
                
        return predictiveAgent.processRequest(agentRequest)
            .thenApply(agentResponse -> {
                Map<String, Object> response = Map.of(
                    "success", agentResponse.isSuccess(),
                    "predictions", agentResponse.getPredictions() != null ? agentResponse.getPredictions() : List.of(),
                    "alerts", agentResponse.getAlerts() != null ? agentResponse.getAlerts() : List.of(),
                    "insights", agentResponse.getMessage(),
                    "area", area != null ? area : city,
                    "timestamp", LocalDateTime.now()
                );
                return ResponseEntity.ok(response);
            })
            .exceptionally(throwable -> {
                log.error("Error getting predictive alerts", throwable);
                return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "error", throwable.getMessage()));
            });
    }

    /**
     * Flutter Alert Page: Get mood map data with lat/lng coordinates
     * Uses MoodMapAgent to provide sentiment analysis for map visualization
     */
    @PostMapping("/map/mood")
    @Operation(summary = "Get mood map with coordinates", 
               description = "Get sentiment analysis data with lat/lng for map display")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> getMoodMapData(
            @RequestBody Map<String, Object> request) {
        
        String area = (String) request.get("area");
        String city = (String) request.getOrDefault("city", "Bengaluru");
        Double lat = request.containsKey("latitude") ? ((Number) request.get("latitude")).doubleValue() : null;
        Double lon = request.containsKey("longitude") ? ((Number) request.get("longitude")).doubleValue() : null;
        
        log.info("Flutter Mood API: Mood map for area={}, city={}", area, city);
        
        AgentRequest agentRequest = AgentRequest.builder()
            .requestId(UUID.randomUUID().toString())
            .requestType("GET_MOOD_MAP")
            .area(area)
            .latitude(lat)
            .longitude(lon)
            .context("Generate mood map analysis for " + (area != null ? area : city))
            .timestamp(LocalDateTime.now())
            .build();
        
        return moodMapAgent.processRequest(agentRequest)
            .thenApply(agentResponse -> {
                Map<String, Object> response = Map.of(
                    "success", agentResponse.isSuccess(),
                    "moodData", agentResponse.getSentimentData() != null ? agentResponse.getSentimentData() : Map.of(),
                    "coordinates", Map.of("latitude", lat, "longitude", lon),
                    "area", area != null ? area : city,
                    "summary", agentResponse.getMessage(),
                    "timestamp", LocalDateTime.now()
                );
                return ResponseEntity.ok(response);
            })
            .exceptionally(throwable -> {
                log.error("Error getting mood map data", throwable);
                return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "error", throwable.getMessage()));
            });
    }

    // ============ 4. REPORTING PAGE ENDPOINTS ============

    /**
     * Flutter Reporting Page: Submit citizen report with image/video and content
     * Uses UserReportingAgent to process multimodal content with Gemini AI analysis
     * Includes TTL management and user tracking for report deletion capabilities
     */
    @PostMapping(value = "/reports/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Submit multimodal citizen report", 
               description = "Process and store geo-tagged reports with AI analysis, cloud storage, and user tracking")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> submitCitizenReport(
            @RequestParam @Parameter(description = "Report text content") String content,
            @RequestParam @Parameter(description = "Latitude coordinate") double latitude,
            @RequestParam @Parameter(description = "Longitude coordinate") double longitude,
            @RequestParam @Parameter(description = "User ID for tracking") String userId,
            @RequestParam(required = false) @Parameter(description = "Area/locality name") String area,
            @RequestParam(required = false, defaultValue = "24") @Parameter(description = "TTL in hours for Firestore") int ttlHours,
            @RequestParam(required = false) @Parameter(description = "Image file") MultipartFile image,
            @RequestParam(required = false) @Parameter(description = "Video file") MultipartFile video) {
        
        log.info("Flutter Report API: Processing report for user={} at lat={}, lon={}, TTL={}h", 
                userId, latitude, longitude, ttlHours);
        
        // Validate required parameters
        if (userId == null || userId.trim().isEmpty()) {
            return CompletableFuture.completedFuture(
                ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", "User ID is required"))
            );
        }
        
        if (content == null || content.trim().isEmpty()) {
            return CompletableFuture.completedFuture(
                ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", "Report content is required"))
            );
        }
        
        // Validate TTL (between 1 hour and 30 days)
        final int finalTtlHours = (ttlHours < 1 || ttlHours > 720) ? 24 : ttlHours;
        
        // Process complete report with media upload, AI analysis, and user tracking
        return userReportingAgent.processCompleteReport(
                userId, content, latitude, longitude, area, image, video, finalTtlHours)
            .thenApply(processedEvent -> {
                Map<String, Object> response = Map.of(
                    "success", true,
                    "reportId", processedEvent.getId(),
                    "eventId", processedEvent.getId(),
                    "processedEvent", Map.of(
                        "id", processedEvent.getId(),
                        "title", processedEvent.getTitle(),
                        "description", processedEvent.getDescription(),
                        "category", processedEvent.getCategory().name(),
                        "severity", processedEvent.getSeverity().name(),
                        "aiSummary", processedEvent.getAiSummary(),
                        "confidenceScore", processedEvent.getConfidenceScore(),
                        "mediaAttachments", processedEvent.getMediaAttachments() != null 
                            ? processedEvent.getMediaAttachments().size() : 0
                    ),
                    "location", Map.of(
                        "latitude", latitude, 
                        "longitude", longitude, 
                        "area", area != null ? area : ""
                    ),
                    "userTracking", Map.of(
                        "userId", userId,
                        "ttlHours", finalTtlHours,
                        "canDelete", true
                    ),
                    "timestamp", LocalDateTime.now()
                );
                
                log.info("Report processed successfully: {} for user: {}", processedEvent.getId(), userId);
                return ResponseEntity.ok(response);
            })
            .exceptionally(throwable -> {
                log.error("Error processing citizen report for user: {}", userId, throwable);
                return ResponseEntity.internalServerError()
                    .body(Map.of(
                        "success", false, 
                        "error", throwable.getMessage(),
                        "userId", userId,
                        "timestamp", LocalDateTime.now()
                    ));
            });
    }

    /**
     * Get user's submitted reports history with detailed information
     */
    @GetMapping("/reports/history/{userId}")
    @Operation(summary = "Get user report history", description = "Retrieve user's previously submitted reports with AI analysis")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> getUserReportHistory(
            @PathVariable @Parameter(description = "User ID") String userId,
            @RequestParam(defaultValue = "20") @Parameter(description = "Maximum number of reports") int limit) {
        
        log.info("Flutter Report API: Getting report history for user={} (limit={})", userId, limit);
        
        return userReportService.getUserReportHistory(userId, limit)
            .thenCompose(reports -> {
                // Also get user statistics
                return userReportService.getUserStatistics(userId)
                    .thenApply(stats -> {
                        Map<String, Object> response = Map.of(
                            "success", true,
                            "userId", userId,
                            "reports", reports,
                            "total", reports.size(),
                            "statistics", stats,
                            "timestamp", LocalDateTime.now()
                        );
                        return ResponseEntity.ok(response);
                    });
            })
            .exceptionally(throwable -> {
                log.error("Error getting user report history for user: {}", userId, throwable);
                return ResponseEntity.internalServerError()
                    .body(Map.of(
                        "success", false, 
                        "error", throwable.getMessage(),
                        "userId", userId,
                        "timestamp", LocalDateTime.now()
                    ));
            });
    }

    /**
     * Delete user's report (removes from storage and user tracking)
     */
    @DeleteMapping("/reports/{reportId}")
    @Operation(summary = "Delete user report", description = "Delete user's report including media files")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> deleteUserReport(
            @PathVariable @Parameter(description = "Report ID") String reportId,
            @RequestParam @Parameter(description = "User ID for ownership verification") String userId) {
        
        log.info("Flutter Report API: Deleting report={} for user={}", reportId, userId);
        
        return userReportService.deleteUserReport(userId, reportId)
            .thenApply(deleted -> {
                if (deleted) {
                    Map<String, Object> response = Map.of(
                        "success", true,
                        "message", "Report deleted successfully",
                        "reportId", reportId,
                        "userId", userId,
                        "timestamp", LocalDateTime.now()
                    );
                    return ResponseEntity.ok(response);
                } else {
                    Map<String, Object> response = Map.of(
                        "success", false,
                        "error", "Report not found or not owned by user",
                        "reportId", reportId,
                        "userId", userId,
                        "timestamp", LocalDateTime.now()
                    );
                    return ResponseEntity.badRequest().body(response);
                }
            })
            .exceptionally(throwable -> {
                log.error("Error deleting report={} for user={}", reportId, userId, throwable);
                return ResponseEntity.internalServerError()
                    .body(Map.of(
                        "success", false,
                        "error", throwable.getMessage(),
                        "reportId", reportId,
                        "userId", userId,
                        "timestamp", LocalDateTime.now()
                    ));
            });
    }

    // ============ 5. REAL-TIME STREAMS ============

    /**
     * SSE endpoint for real-time city events (all Flutter pages can use this)
     */
    @GetMapping(value = "/stream/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Real-time events stream", description = "Server-sent events for live city updates")
    public SseEmitter streamEvents() {
        log.info("Flutter Stream API: New client connected to events stream");
        return eventStreamService.createEventStream("events");
    }

    /**
     * SSE endpoint for real-time alerts (Alert page specific)
     */
    @GetMapping(value = "/stream/alerts", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Real-time alerts stream", description = "Server-sent events for live alert updates")
    public SseEmitter streamAlerts() {
        log.info("Flutter Stream API: New client connected to alerts stream");
        return eventStreamService.createEventStream("alerts");
    }

    /**
     * SSE endpoint for mood map updates (Map page specific)
     */
    @GetMapping(value = "/stream/mood", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Real-time mood stream", description = "Server-sent events for live mood map updates")
    public SseEmitter streamMoodUpdates() {
        log.info("Flutter Stream API: New client connected to mood stream");
        return eventStreamService.createEventStream("mood");
    }

    // ============ 6. UTILITY ENDPOINTS ============

    /**
     * Get system health status for Flutter app monitoring
     */
    @GetMapping("/health")
    @Operation(summary = "System health check", description = "Health status for Flutter app")
    public ResponseEntity<Map<String, Object>> getSystemHealth() {
        Map<String, Object> connectionStats = eventStreamService.getConnectionStats();
        
        Map<String, Object> health = Map.of(
            "status", "healthy",
            "service", "City Data Overload Server",
            "version", "1.0.0",
            "timestamp", LocalDateTime.now(),
            "agents", Map.of(
                "coordinator", coordinatorAgent.getHealthStatus().name(),
                "fusion", fusionAgent.getHealthStatus().name(),
                "predictive", predictiveAgent.getHealthStatus().name(),
                "userReporting", userReportingAgent.getHealthStatus().name(),
                "moodMap", moodMapAgent.getHealthStatus().name()
            ),
            "connections", connectionStats
        );
        
        return ResponseEntity.ok(health);
    }

    /**
     * Get available Bengaluru areas for Flutter dropdowns
     */
    @GetMapping("/bengaluru/areas")
    @Operation(summary = "Get Bengaluru areas", description = "List of available areas in Bengaluru")
    public ResponseEntity<Map<String, Object>> getBengaluruAreas() {
        List<Map<String, Object>> areas = List.of(
            Map.of("name", "Koramangala", "lat", 12.9279, "lng", 77.6271),
            Map.of("name", "HSR Layout", "lat", 12.9082, "lng", 77.6476),
            Map.of("name", "Indiranagar", "lat", 12.9784, "lng", 77.6408),
            Map.of("name", "Whitefield", "lat", 12.9698, "lng", 77.7500),
            Map.of("name", "Electronic City", "lat", 12.8456, "lng", 77.6603),
            Map.of("name", "Marathahalli", "lat", 12.9591, "lng", 77.6974),
            Map.of("name", "Bellandur", "lat", 12.9165, "lng", 77.6761),
            Map.of("name", "Sarjapur", "lat", 12.8797, "lng", 77.6762)
        );
        
        Map<String, Object> response = Map.of(
            "success", true,
            "areas", areas,
            "city", "Bengaluru",
            "total", areas.size(),
            "timestamp", LocalDateTime.now()
        );
        
        return ResponseEntity.ok(response);
    }

    /**
     * Test BigQuery data fetching directly (for debugging)
     */
    @GetMapping("/test/bigquery")
    @Operation(summary = "Test BigQuery", description = "Direct test of BigQuery data fetching")
    public ResponseEntity<Map<String, Object>> testBigQuery(
            @RequestParam(defaultValue = "10") int limit) {
        
        log.info("Testing BigQuery with limit: {}", limit);
        
        try {
            List<CityEvent> events = bigQueryService.queryAllRecentEvents(limit);
            
            Map<String, Object> response = Map.of(
                "success", true,
                "events_found", events.size(),
                "events", events.stream().limit(3).map(event -> Map.of(
                    "id", event.getId(),
                    "title", event.getTitle(),
                    "area", event.getLocation() != null ? event.getLocation().getArea() : "null",
                    "timestamp", event.getTimestamp() != null ? event.getTimestamp().toString() : "null"
                )).toList(),
                "timestamp", LocalDateTime.now()
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("BigQuery test failed", e);
            return ResponseEntity.internalServerError()
                .body(Map.of(
                    "success", false,
                    "error", e.getMessage(),
                    "timestamp", LocalDateTime.now()
                ));
        }
    }

    @PostMapping("/test/patterns")
    @Operation(summary = "Test pattern detection", 
               description = "Debug endpoint to test BigQuery pattern detection without AI")
    public ResponseEntity<Map<String, Object>> testPatterns(
            @RequestBody Map<String, Object> request) {
        
        String category = (String) request.get("category");
        Integer days = (Integer) request.getOrDefault("days", 30);
        
        log.info("Testing pattern detection for category={}, days={}", category, days);
        
        try {
            if (category != null) {
                List<Map<String, Object>> patterns = bigQueryService.queryPredictivePatterns(
                    CityEvent.EventCategory.valueOf(category), days);
                
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "category", category,
                    "days", days,
                    "patterns", patterns,
                    "count", patterns.size()
                ));
            } else {
                Map<String, Object> allPatterns = new HashMap<>();
                CityEvent.EventCategory[] categories = {
                    CityEvent.EventCategory.TRAFFIC,
                    CityEvent.EventCategory.UTILITY,
                    CityEvent.EventCategory.HEALTH
                };
                
                for (CityEvent.EventCategory cat : categories) {
                    List<Map<String, Object>> patterns = bigQueryService.queryPredictivePatterns(cat, days);
                    allPatterns.put(cat.name(), patterns);
                }
                
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "days", days,
                    "patterns", allPatterns
                ));
            }
        } catch (Exception e) {
            log.error("Error testing patterns", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("success", false, "error", e.getMessage()));
        }
    }
} 