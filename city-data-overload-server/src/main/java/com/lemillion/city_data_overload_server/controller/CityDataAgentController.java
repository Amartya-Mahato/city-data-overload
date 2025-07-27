package com.lemillion.city_data_overload_server.controller;

import com.lemillion.city_data_overload_server.agent.AgentRequest;
import com.lemillion.city_data_overload_server.agent.AgentResponse;
import com.lemillion.city_data_overload_server.agent.impl.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Main REST controller for City Data Overload agents.
 * Provides endpoints for all agent functionalities including coordination,
 * event fetching, predictions, mood mapping, data fusion, user reporting, and alerts.
 */
@RestController
@RequestMapping("/api/v1/agents")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "City Data Agents", description = "Agentic AI endpoints for Bengaluru city data management")
@Validated
public class CityDataAgentController {

    private final CoordinatorAgent coordinatorAgent;
    private final EventsAgent eventsAgent;
    private final PredictiveAgent predictiveAgent;
    private final MoodMapAgent moodMapAgent;
    private final FusionAgent fusionAgent;
    private final UserReportingAgent userReportingAgent;
    private final AlertAgent alertAgent;

    /**
     * Coordinator endpoint - Main orchestration point for complex queries
     */
    @PostMapping("/coordinate")
    @Operation(summary = "Coordinate multi-agent workflow", 
               description = "Main coordination endpoint that orchestrates multiple agents based on request type")
    public CompletableFuture<ResponseEntity<AgentResponse>> coordinate(
            @RequestBody @Parameter(description = "Coordination request") Map<String, Object> request) {
        
        AgentRequest agentRequest = buildAgentRequest(request, "COMPREHENSIVE_ANALYSIS");
        
        log.info("Coordinator request received: {}", agentRequest.getRequestId());
        
        return coordinatorAgent.processRequest(agentRequest)
            .thenApply(response -> ResponseEntity.ok(response))
            .exceptionally(throwable -> {
                log.error("Coordinator request failed", throwable);
                return ResponseEntity.internalServerError()
                    .body(AgentResponse.error(agentRequest.getRequestId(), 
                          coordinatorAgent.getAgentId(), throwable.getMessage(), "COORDINATION_ERROR"));
            });
    }

    /**
     * Get area overview using coordinator
     */
    @GetMapping("/area/{area}/overview")
    @Operation(summary = "Get comprehensive area overview", 
               description = "Get a complete overview of events, mood, and alerts for a specific area")
    public CompletableFuture<ResponseEntity<AgentResponse>> getAreaOverview(
            @PathVariable @Parameter(description = "Area name") String area,
            @RequestParam(defaultValue = "20") @Parameter(description = "Maximum results") int maxResults) {
        
        Map<String, Object> request = Map.of(
            "area", area,
            "maxResults", maxResults
        );
        
        AgentRequest agentRequest = buildAgentRequest(request, "AREA_OVERVIEW");
        
        return coordinatorAgent.processRequest(agentRequest)
            .thenApply(ResponseEntity::ok)
            .exceptionally(throwable -> ResponseEntity.internalServerError()
                .body(AgentResponse.error(agentRequest.getRequestId(), 
                      coordinatorAgent.getAgentId(), throwable.getMessage(), "AREA_OVERVIEW_ERROR")));
    }

    /**
     * Events agent endpoints
     */
    @GetMapping("/events")
    @Operation(summary = "Get current city events", 
               description = "Fetch current events from Firestore with BigQuery fallback")
    public CompletableFuture<ResponseEntity<AgentResponse>> getEvents(
            @RequestParam(required = false) @Parameter(description = "Area name") String area,
            @RequestParam(required = false) @Parameter(description = "Event category") String category,
            @RequestParam(required = false) @Parameter(description = "Event severity") String severity,
            @RequestParam(defaultValue = "50") @Parameter(description = "Maximum results") int maxResults) {
        
        Map<String, Object> request = new HashMap<>();
        if (area != null) request.put("area", area);
        if (category != null) request.put("category", category);
        if (severity != null) request.put("severity", severity);
        request.put("maxResults", maxResults);
        
        AgentRequest agentRequest = buildAgentRequest(request, "GET_EVENTS");
        
        return eventsAgent.processRequest(agentRequest)
            .thenApply(ResponseEntity::ok)
            .exceptionally(throwable -> ResponseEntity.internalServerError()
                .body(AgentResponse.error(agentRequest.getRequestId(), 
                      eventsAgent.getAgentId(), throwable.getMessage(), "EVENTS_ERROR")));
    }

    @GetMapping("/events/location")
    @Operation(summary = "Get events by location", 
               description = "Fetch events within a specific radius of a location")
    public CompletableFuture<ResponseEntity<AgentResponse>> getEventsByLocation(
            @RequestParam @Parameter(description = "Latitude") double latitude,
            @RequestParam @Parameter(description = "Longitude") double longitude,
            @RequestParam(defaultValue = "5.0") @Parameter(description = "Radius in kilometers") double radiusKm,
            @RequestParam(defaultValue = "50") @Parameter(description = "Maximum results") int maxResults) {
        
        Map<String, Object> request = Map.of(
            "latitude", latitude,
            "longitude", longitude,
            "radiusKm", radiusKm,
            "maxResults", maxResults
        );
        
        AgentRequest agentRequest = buildAgentRequest(request, "GET_EVENTS_BY_LOCATION");
        
        return eventsAgent.processRequest(agentRequest)
            .thenApply(ResponseEntity::ok)
            .exceptionally(throwable -> ResponseEntity.internalServerError()
                .body(AgentResponse.error(agentRequest.getRequestId(), 
                      eventsAgent.getAgentId(), throwable.getMessage(), "LOCATION_EVENTS_ERROR")));
    }

    /**
     * Predictive agent endpoints
     */
    @GetMapping("/predictions")
    @Operation(summary = "Get predictive insights", 
               description = "Generate predictions based on historical patterns")
    public CompletableFuture<ResponseEntity<AgentResponse>> getPredictions(
            @RequestParam(required = false) @Parameter(description = "Area name") String area,
            @RequestParam(required = false) @Parameter(description = "Event category") String category) {
        
        Map<String, Object> request = new HashMap<>();
        if (area != null) request.put("area", area);
        if (category != null) request.put("category", category);
        
        AgentRequest agentRequest = buildAgentRequest(request, "GET_PREDICTIONS");
        
        return predictiveAgent.processRequest(agentRequest)
            .thenApply(ResponseEntity::ok)
            .exceptionally(throwable -> ResponseEntity.internalServerError()
                .body(AgentResponse.error(agentRequest.getRequestId(), 
                      predictiveAgent.getAgentId(), throwable.getMessage(), "PREDICTION_ERROR")));
    }

    @GetMapping("/area/{area}/forecast")
    @Operation(summary = "Get area forecast", 
               description = "Get predictive forecast for a specific area")
    public CompletableFuture<ResponseEntity<AgentResponse>> getAreaForecast(
            @PathVariable @Parameter(description = "Area name") String area) {
        
        Map<String, Object> request = Map.of("area", area);
        AgentRequest agentRequest = buildAgentRequest(request, "FORECAST_EVENTS");
        
        return predictiveAgent.processRequest(agentRequest)
            .thenApply(ResponseEntity::ok)
            .exceptionally(throwable -> ResponseEntity.internalServerError()
                .body(AgentResponse.error(agentRequest.getRequestId(), 
                      predictiveAgent.getAgentId(), throwable.getMessage(), "FORECAST_ERROR")));
    }

    /**
     * Mood map agent endpoints
     */
    @GetMapping("/mood-map")
    @Operation(summary = "Get city-wide mood map", 
               description = "Analyze sentiment across different areas of Bengaluru")
    public CompletableFuture<ResponseEntity<AgentResponse>> getCityMoodMap() {
        
        AgentRequest agentRequest = buildAgentRequest(Map.of(), "GET_MOOD_MAP");
        
        return moodMapAgent.processRequest(agentRequest)
            .thenApply(ResponseEntity::ok)
            .exceptionally(throwable -> ResponseEntity.internalServerError()
                .body(AgentResponse.error(agentRequest.getRequestId(), 
                      moodMapAgent.getAgentId(), throwable.getMessage(), "MOOD_MAP_ERROR")));
    }

    @GetMapping("/area/{area}/mood")
    @Operation(summary = "Get area mood analysis", 
               description = "Get sentiment analysis for a specific area")
    public CompletableFuture<ResponseEntity<AgentResponse>> getAreaMood(
            @PathVariable @Parameter(description = "Area name") String area) {
        
        Map<String, Object> request = Map.of("area", area);
        AgentRequest agentRequest = buildAgentRequest(request, "GET_AREA_MOOD");
        
        return moodMapAgent.processRequest(agentRequest)
            .thenApply(ResponseEntity::ok)
            .exceptionally(throwable -> ResponseEntity.internalServerError()
                .body(AgentResponse.error(agentRequest.getRequestId(), 
                      moodMapAgent.getAgentId(), throwable.getMessage(), "AREA_MOOD_ERROR")));
    }

    /**
     * Fusion agent endpoints
     */
    @PostMapping("/fuse")
    @Operation(summary = "Fuse disparate data sources", 
               description = "Synthesize multiple data sources into coherent summaries")
    public CompletableFuture<ResponseEntity<AgentResponse>> fuseData(
            @RequestBody @Parameter(description = "Fusion request with events or text") Map<String, Object> request) {
        
        AgentRequest agentRequest = buildAgentRequest(request, "FUSE_DATA");
        
        return fusionAgent.processRequest(agentRequest)
            .thenApply(ResponseEntity::ok)
            .exceptionally(throwable -> ResponseEntity.internalServerError()
                .body(AgentResponse.error(agentRequest.getRequestId(), 
                      fusionAgent.getAgentId(), throwable.getMessage(), "FUSION_ERROR")));
    }

    @PostMapping("/synthesize")
    @Operation(summary = "Synthesize events", 
               description = "Create unified summaries from multiple related events")
    public CompletableFuture<ResponseEntity<AgentResponse>> synthesizeEvents(
            @RequestBody @Parameter(description = "Events to synthesize") Map<String, Object> request) {
        
        AgentRequest agentRequest = buildAgentRequest(request, "SYNTHESIZE_EVENTS");
        
        return fusionAgent.processRequest(agentRequest)
            .thenApply(ResponseEntity::ok)
            .exceptionally(throwable -> ResponseEntity.internalServerError()
                .body(AgentResponse.error(agentRequest.getRequestId(), 
                      fusionAgent.getAgentId(), throwable.getMessage(), "SYNTHESIS_ERROR")));
    }

    /**
     * User reporting agent endpoints
     */
    @PostMapping("/report")
    @Operation(summary = "Process user report", 
               description = "Process multimodal user reports including images, videos, and text")
    public CompletableFuture<ResponseEntity<AgentResponse>> processUserReport(
            @RequestBody @Parameter(description = "User report data") Map<String, Object> request) {
        
        AgentRequest agentRequest = buildAgentRequest(request, "PROCESS_USER_REPORT");
        
        return userReportingAgent.processRequest(agentRequest)
            .thenApply(ResponseEntity::ok)
            .exceptionally(throwable -> ResponseEntity.internalServerError()
                .body(AgentResponse.error(agentRequest.getRequestId(), 
                      userReportingAgent.getAgentId(), throwable.getMessage(), "USER_REPORT_ERROR")));
    }

    @PostMapping("/report/media")
    @Operation(summary = "Analyze media content", 
               description = "Analyze images or videos from user reports")
    public CompletableFuture<ResponseEntity<AgentResponse>> analyzeMedia(
            @RequestParam @Parameter(description = "Media URL") String mediaUrl,
            @RequestParam(required = false) @Parameter(description = "Additional context") String context,
            @RequestParam(required = false) @Parameter(description = "Latitude") Double latitude,
            @RequestParam(required = false) @Parameter(description = "Longitude") Double longitude) {
        
        Map<String, Object> request = new HashMap<>();
        request.put("imageUrl", mediaUrl); // Assuming image for now
        if (context != null) request.put("textContent", context);
        if (latitude != null) request.put("latitude", latitude);
        if (longitude != null) request.put("longitude", longitude);
        
        AgentRequest agentRequest = buildAgentRequest(request, "ANALYZE_USER_MEDIA");
        
        return userReportingAgent.processRequest(agentRequest)
            .thenApply(ResponseEntity::ok)
            .exceptionally(throwable -> ResponseEntity.internalServerError()
                .body(AgentResponse.error(agentRequest.getRequestId(), 
                      userReportingAgent.getAgentId(), throwable.getMessage(), "MEDIA_ANALYSIS_ERROR")));
    }

    /**
     * Alert agent endpoints
     */
    @GetMapping("/alerts")
    @Operation(summary = "Check for active alerts", 
               description = "Monitor for serious alerts and emergency situations")
    public CompletableFuture<ResponseEntity<AgentResponse>> checkAlerts(
            @RequestParam(required = false) @Parameter(description = "Area name") String area) {
        
        Map<String, Object> request = new HashMap<>();
        if (area != null) request.put("area", area);
        
        AgentRequest agentRequest = buildAgentRequest(request, "CHECK_ALERTS");
        
        return alertAgent.processRequest(agentRequest)
            .thenApply(ResponseEntity::ok)
            .exceptionally(throwable -> ResponseEntity.internalServerError()
                .body(AgentResponse.error(agentRequest.getRequestId(), 
                      alertAgent.getAgentId(), throwable.getMessage(), "ALERT_CHECK_ERROR")));
    }

    @GetMapping("/alerts/critical")
    @Operation(summary = "Get critical alerts", 
               description = "Get only critical and high-priority alerts")
    public CompletableFuture<ResponseEntity<AgentResponse>> getCriticalAlerts() {
        
        AgentRequest agentRequest = buildAgentRequest(Map.of(), "GET_CRITICAL_ALERTS");
        
        return alertAgent.processRequest(agentRequest)
            .thenApply(ResponseEntity::ok)
            .exceptionally(throwable -> ResponseEntity.internalServerError()
                .body(AgentResponse.error(agentRequest.getRequestId(), 
                      alertAgent.getAgentId(), throwable.getMessage(), "CRITICAL_ALERTS_ERROR")));
    }

    @PostMapping("/emergency")
    @Operation(summary = "Emergency response coordination", 
               description = "Coordinate emergency response across multiple agents")
    public CompletableFuture<ResponseEntity<AgentResponse>> handleEmergency(
            @RequestBody @Parameter(description = "Emergency details") Map<String, Object> request) {
        
        AgentRequest agentRequest = buildAgentRequest(request, "EMERGENCY_RESPONSE");
        
        return coordinatorAgent.processRequest(agentRequest)
            .thenApply(ResponseEntity::ok)
            .exceptionally(throwable -> ResponseEntity.internalServerError()
                .body(AgentResponse.error(agentRequest.getRequestId(), 
                      coordinatorAgent.getAgentId(), throwable.getMessage(), "EMERGENCY_ERROR")));
    }

    /**
     * Health check endpoints
     */
    @GetMapping("/health")
    @Operation(summary = "Get agents health status", 
               description = "Check health status of all agents")
    public ResponseEntity<Map<String, Object>> getHealthStatus() {
        Map<String, Object> health = new HashMap<>();
        
        try {
            health.put("coordinator", coordinatorAgent.getHealthStatus().name());
            health.put("events", eventsAgent.getHealthStatus().name());
            health.put("predictive", predictiveAgent.getHealthStatus().name());
            health.put("mood_map", moodMapAgent.getHealthStatus().name());
            health.put("fusion", fusionAgent.getHealthStatus().name());
            health.put("user_reporting", userReportingAgent.getHealthStatus().name());
            health.put("alert", alertAgent.getHealthStatus().name());
            health.put("overall_status", "HEALTHY");
            health.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.ok(health);
            
        } catch (Exception e) {
            log.error("Health check failed", e);
            health.put("overall_status", "UNHEALTHY");
            health.put("error", e.getMessage());
            return ResponseEntity.status(503).body(health);
        }
    }

    /**
     * Agent information endpoint
     */
    @GetMapping("/info")
    @Operation(summary = "Get agents information", 
               description = "Get detailed information about all available agents")
    public ResponseEntity<Map<String, Object>> getAgentsInfo() {
        Map<String, Object> info = new HashMap<>();
        
        Map<String, Object> agents = new HashMap<>();
        agents.put("coordinator", Map.of(
            "id", coordinatorAgent.getAgentId(),
            "type", coordinatorAgent.getAgentType().name(),
            "description", coordinatorAgent.getDescription()
        ));
        agents.put("events", Map.of(
            "id", eventsAgent.getAgentId(),
            "type", eventsAgent.getAgentType().name(),
            "description", eventsAgent.getDescription()
        ));
        agents.put("predictive", Map.of(
            "id", predictiveAgent.getAgentId(),
            "type", predictiveAgent.getAgentType().name(),
            "description", predictiveAgent.getDescription()
        ));
        agents.put("mood_map", Map.of(
            "id", moodMapAgent.getAgentId(),
            "type", moodMapAgent.getAgentType().name(),
            "description", moodMapAgent.getDescription()
        ));
        agents.put("fusion", Map.of(
            "id", fusionAgent.getAgentId(),
            "type", fusionAgent.getAgentType().name(),
            "description", fusionAgent.getDescription()
        ));
        agents.put("user_reporting", Map.of(
            "id", userReportingAgent.getAgentId(),
            "type", userReportingAgent.getAgentType().name(),
            "description", userReportingAgent.getDescription()
        ));
        agents.put("alert", Map.of(
            "id", alertAgent.getAgentId(),
            "type", alertAgent.getAgentType().name(),
            "description", alertAgent.getDescription()
        ));
        
        info.put("agents", agents);
        info.put("total_agents", agents.size());
        info.put("version", "1.0.0");
        info.put("description", "City Data Overload - Agentic AI system for Bengaluru");
        
        return ResponseEntity.ok(info);
    }

    /**
     * Helper method to build AgentRequest from HTTP request parameters
     */
    private AgentRequest buildAgentRequest(Map<String, Object> requestData, String requestType) {
        AgentRequest.AgentRequestBuilder builder = AgentRequest.builder()
            .requestId(UUID.randomUUID().toString())
            .requestType(requestType)
            .timestamp(LocalDateTime.now())
            .parameters(requestData);
        
        // Extract common parameters
        if (requestData.containsKey("area")) {
            builder.area((String) requestData.get("area"));
        }
        if (requestData.containsKey("latitude")) {
            builder.latitude(((Number) requestData.get("latitude")).doubleValue());
        }
        if (requestData.containsKey("longitude")) {
            builder.longitude(((Number) requestData.get("longitude")).doubleValue());
        }
        if (requestData.containsKey("radiusKm")) {
            builder.radiusKm(((Number) requestData.get("radiusKm")).doubleValue());
        }
        if (requestData.containsKey("category")) {
            builder.category((String) requestData.get("category"));
        }
        if (requestData.containsKey("severity")) {
            builder.severity((String) requestData.get("severity"));
        }
        if (requestData.containsKey("maxResults")) {
            builder.maxResults(((Number) requestData.get("maxResults")).intValue());
        }
        if (requestData.containsKey("textContent")) {
            builder.textContent((String) requestData.get("textContent"));
        }
        if (requestData.containsKey("imageUrl")) {
            builder.imageUrl((String) requestData.get("imageUrl"));
        }
        if (requestData.containsKey("videoUrl")) {
            builder.videoUrl((String) requestData.get("videoUrl"));
        }
        if (requestData.containsKey("query")) {
            builder.query((String) requestData.get("query"));
        }
        if (requestData.containsKey("context")) {
            builder.context((String) requestData.get("context"));
        }
        
        return builder.build();
    }
} 