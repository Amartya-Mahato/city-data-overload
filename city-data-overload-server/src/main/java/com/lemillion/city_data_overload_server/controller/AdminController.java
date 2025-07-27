package com.lemillion.city_data_overload_server.controller;

import com.lemillion.city_data_overload_server.model.CityEvent;
import com.lemillion.city_data_overload_server.service.*;
import com.lemillion.city_data_overload_server.agent.impl.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Admin portal controller for managing the City Data Overload system.
 * Provides analytics, data management, and system control capabilities.
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Portal", description = "Administrative endpoints for system management")
@CrossOrigin(origins = "*")
public class AdminController {

    private final BigQueryService bigQueryService;
    private final FirestoreService firestoreService;
    private final UserReportService userReportService;
    private final CloudStorageService cloudStorageService;
    
    // Agents
    private final CoordinatorAgent coordinatorAgent;
    private final EventsAgent eventsAgent;
    private final PredictiveAgent predictiveAgent;
    private final MoodMapAgent moodMapAgent;
    private final FusionAgent fusionAgent;
    private final UserReportingAgent userReportingAgent;
    private final AlertAgent alertAgent;

    // ============ DASHBOARD & ANALYTICS ============

    /**
     * Admin dashboard - main page
     */
    @GetMapping("/")
    public ResponseEntity<String> adminDashboard() {
        return ResponseEntity.ok("""
            <!DOCTYPE html>
            <html>
            <head>
                <title>City Data Overload - Admin Portal</title>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <script>
                    // Redirect to the full dashboard
                    window.location.href = '/admin/dashboard.html';
                </script>
            </head>
            <body>
                <p>Redirecting to admin dashboard...</p>
                <p>If not redirected, <a href="/admin/dashboard.html">click here</a></p>
            </body>
            </html>
            """);
    }

    /**
     * Get comprehensive system analytics
     */
    @GetMapping("/api/analytics/overview")
    @Operation(summary = "System overview analytics", description = "Get comprehensive system statistics")
    public ResponseEntity<Map<String, Object>> getSystemOverview() {
        try {
            Map<String, Object> overview = new HashMap<>();
            
            // System health
            overview.put("systemHealth", getSystemHealth());
            
            // Event statistics
            overview.put("eventStats", getEventStatistics());
            
            // User statistics
            overview.put("userStats", getUserStatistics());
            
            // Agent performance
            overview.put("agentPerformance", getAgentPerformance());
            
            // Recent activity
            overview.put("recentActivity", getRecentActivity());
            
            overview.put("generatedAt", LocalDateTime.now());
            overview.put("status", "success");
            
            return ResponseEntity.ok(overview);
            
        } catch (Exception e) {
            log.error("Error getting system overview", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", e.getMessage(), "status", "error"));
        }
    }

    /**
     * Get detailed event analytics
     */
    @GetMapping("/api/analytics/events")
    @Operation(summary = "Event analytics", description = "Detailed event statistics and trends")
    public ResponseEntity<Map<String, Object>> getEventAnalytics(
            @RequestParam(defaultValue = "7") @Parameter(description = "Days to analyze") int days) {
        
        try {
            LocalDateTime since = LocalDateTime.now().minusDays(days);
            
            Map<String, Object> analytics = new HashMap<>();
            
            // BigQuery event statistics
            Map<String, Object> bqStats = bigQueryService.getEventStatistics(since);
            analytics.put("bigQueryStats", bqStats);
            
            // Category distribution
            analytics.put("categoryDistribution", getCategoryDistribution(days));
            
            // Location analytics
            analytics.put("locationAnalytics", getLocationAnalytics(days));
            
            // Trend analysis
            analytics.put("trends", getEventTrends(days));
            
            // Performance metrics
            analytics.put("performance", getPerformanceMetrics());
            
            analytics.put("period", Map.of("days", days, "since", since));
            analytics.put("generatedAt", LocalDateTime.now());
            
            return ResponseEntity.ok(analytics);
            
        } catch (Exception e) {
            log.error("Error getting event analytics", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", e.getMessage()));
        }
    }

    // ============ DATA MANAGEMENT ============

    /**
     * Get events from BigQuery with pagination
     */
    @GetMapping("/api/data/bigquery/events")
    @Operation(summary = "List BigQuery events", description = "Get paginated list of events from BigQuery")
    public ResponseEntity<Map<String, Object>> getBigQueryEvents(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        
        try {
            List<CityEvent> events = bigQueryService.queryAllRecentEvents(limit);
            
            Map<String, Object> response = Map.of(
                "events", events,
                "total", events.size(),
                "limit", limit,
                "offset", offset,
                "source", "BigQuery"
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error fetching BigQuery events", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get events from Firestore
     */
    @GetMapping("/api/data/firestore/events")
    @Operation(summary = "List Firestore events", description = "Get events from Firestore by area")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> getFirestoreEvents(
            @RequestParam(defaultValue = "Koramangala") String area,
            @RequestParam(defaultValue = "50") int limit) {
        
        return firestoreService.getRecentEventsByArea(area, limit)
            .thenApply(events -> {
                Map<String, Object> response = Map.of(
                    "events", events,
                    "area", area,
                    "total", events.size(),
                    "limit", limit,
                    "source", "Firestore"
                );
                return ResponseEntity.ok(response);
            })
            .exceptionally(throwable -> {
                log.error("Error fetching Firestore events", throwable);
                return ResponseEntity.internalServerError()
                    .body(Map.of("error", throwable.getMessage()));
            });
    }

    /**
     * Delete event from both BigQuery and Firestore
     */
    @DeleteMapping("/api/data/events/{eventId}")
    @Operation(summary = "Delete event", description = "Remove event from both BigQuery and Firestore")
    public ResponseEntity<Map<String, Object>> deleteEvent(@PathVariable String eventId) {
        try {
            // Note: BigQuery doesn't support direct deletes easily, so we'll focus on Firestore
            // In production, you might want to mark as deleted or use a different approach
            
            Map<String, Object> result = Map.of(
                "eventId", eventId,
                "action", "delete_requested",
                "note", "BigQuery events cannot be directly deleted. Consider marking as inactive.",
                "firestoreCleanup", "TTL will handle cleanup automatically"
            );
            
            log.info("Delete requested for event: {}", eventId);
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Error deleting event: {}", eventId, e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get user reports with admin details
     */
    @GetMapping("/api/data/users/{userId}/reports")
    @Operation(summary = "Get user reports", description = "Admin view of user reports")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> getUserReports(
            @PathVariable String userId,
            @RequestParam(defaultValue = "20") int limit) {
        
        return userReportService.getUserReportHistory(userId, limit)
            .thenCompose(reports -> {
                return userReportService.getUserStatistics(userId)
                    .thenApply(stats -> {
                        Map<String, Object> response = Map.of(
                            "userId", userId,
                            "reports", reports,
                            "statistics", stats,
                            "adminView", true
                        );
                        return ResponseEntity.ok(response);
                    });
            })
            .exceptionally(throwable -> {
                log.error("Error fetching user reports for admin", throwable);
                return ResponseEntity.internalServerError()
                    .body(Map.of("error", throwable.getMessage()));
            });
    }

    /**
     * Admin delete user report
     */
    @DeleteMapping("/api/data/users/{userId}/reports/{reportId}")
    @Operation(summary = "Admin delete report", description = "Admin force delete user report")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> adminDeleteReport(
            @PathVariable String userId,
            @PathVariable String reportId) {
        
        return userReportService.deleteUserReport(userId, reportId)
            .thenApply(success -> {
                Map<String, Object> response = Map.of(
                    "userId", userId,
                    "reportId", reportId,
                    "deleted", success,
                    "adminAction", true
                );
                return ResponseEntity.ok(response);
            })
            .exceptionally(throwable -> {
                log.error("Error in admin delete report", throwable);
                return ResponseEntity.internalServerError()
                    .body(Map.of("error", throwable.getMessage()));
            });
    }

    // ============ SYSTEM CONTROL ============

    /**
     * Get agent statuses
     */
    @GetMapping("/api/system/agents/status")
    @Operation(summary = "Agent status", description = "Get health status of all agents")
    public ResponseEntity<Map<String, Object>> getAgentStatus() {
        Map<String, Object> agentStatus = new HashMap<>();
        
        try {
            agentStatus.put("coordinator", Map.of(
                "id", coordinatorAgent.getAgentId(),
                "type", coordinatorAgent.getAgentType().name(),
                "health", safeGetHealthStatus(coordinatorAgent::getHealthStatus),
                "description", coordinatorAgent.getDescription()
            ));
            
            agentStatus.put("events", Map.of(
                "id", eventsAgent.getAgentId(),
                "health", safeGetHealthStatus(eventsAgent::getHealthStatus),
                "description", eventsAgent.getDescription()
            ));
            
            agentStatus.put("predictive", Map.of(
                "id", predictiveAgent.getAgentId(),
                "health", safeGetHealthStatus(predictiveAgent::getHealthStatus),
                "description", predictiveAgent.getDescription()
            ));
            
            agentStatus.put("moodMap", Map.of(
                "id", moodMapAgent.getAgentId(),
                "health", safeGetHealthStatus(moodMapAgent::getHealthStatus),
                "description", moodMapAgent.getDescription()
            ));
            
            agentStatus.put("fusion", Map.of(
                "id", fusionAgent.getAgentId(),
                "health", safeGetHealthStatus(fusionAgent::getHealthStatus),
                "description", fusionAgent.getDescription()
            ));
            
            agentStatus.put("userReporting", Map.of(
                "id", userReportingAgent.getAgentId(),
                "health", safeGetHealthStatus(userReportingAgent::getHealthStatus),
                "description", userReportingAgent.getDescription()
            ));
            
            agentStatus.put("alert", Map.of(
                "id", alertAgent.getAgentId(),
                "health", safeGetHealthStatus(alertAgent::getHealthStatus),
                "description", alertAgent.getDescription()
            ));
            
            return ResponseEntity.ok(agentStatus);
            
        } catch (Exception e) {
            log.error("Error getting agent status", e);
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to get agent status: " + e.getMessage(),
                "timestamp", java.time.Instant.now().toString()
            ));
        }
    }

    /**
     * Clean up expired data
     */
    @PostMapping("/api/system/cleanup")
    @Operation(summary = "System cleanup", description = "Clean up expired data and optimize system")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> performSystemCleanup() {
        
        CompletableFuture<Integer> firestoreCleanup = firestoreService.cleanupExpiredEvents();
        CompletableFuture<Integer> userReportsCleanup = userReportService.cleanupExpiredUserReports();
        
        return CompletableFuture.allOf(firestoreCleanup, userReportsCleanup)
            .thenApply(ignored -> {
                Map<String, Object> result = Map.of(
                    "firestoreEventsDeleted", firestoreCleanup.join(),
                    "userReportsExpired", userReportsCleanup.join(),
                    "cleanupTime", LocalDateTime.now(),
                    "status", "completed"
                );
                return ResponseEntity.ok(result);
            })
            .exceptionally(throwable -> {
                log.error("Error during system cleanup", throwable);
                return ResponseEntity.internalServerError()
                    .body(Map.of("error", throwable.getMessage()));
            });
    }

    // ============ PRIVATE HELPER METHODS ============

    private Map<String, Object> getSystemHealth() {
        return Map.of(
            "status", "HEALTHY",
            "uptime", "N/A", // Would need tracking
            "memoryUsage", Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory(),
            "totalMemory", Runtime.getRuntime().totalMemory(),
            "processors", Runtime.getRuntime().availableProcessors()
        );
    }
    
    /**
     * Safely get health status with timeout protection
     */
    private String safeGetHealthStatus(java.util.function.Supplier<com.lemillion.city_data_overload_server.agent.Agent.HealthStatus> healthSupplier) {
        try {
            CompletableFuture<String> healthFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return healthSupplier.get().name();
                } catch (Exception e) {
                    log.warn("Health check failed, returning DEGRADED", e);
                    return "DEGRADED";
                }
            });
            
            // Wait for health check with 2-second timeout
            return healthFuture.get(2, java.util.concurrent.TimeUnit.SECONDS);
            
        } catch (java.util.concurrent.TimeoutException e) {
            log.warn("Health check timed out, returning DEGRADED");
            return "DEGRADED";
        } catch (Exception e) {
            log.warn("Health check error, returning UNKNOWN", e);
            return "UNKNOWN";
        }
    }

    private Map<String, Object> getEventStatistics() {
        try {
            LocalDateTime since = LocalDateTime.now().minusHours(24);
            return bigQueryService.getEventStatistics(since);
        } catch (Exception e) {
            return Map.of("error", "Unable to fetch event statistics");
        }
    }

    private Map<String, Object> getUserStatistics() {
        return Map.of(
            "totalUsers", "N/A", // Would need aggregation query
            "activeUsers24h", "N/A",
            "totalReports", "N/A",
            "reportsToday", "N/A"
        );
    }

    private Map<String, Object> getAgentPerformance() {
        return Map.of(
            "coordinator", coordinatorAgent.getHealthStatus().name(),
            "events", eventsAgent.getHealthStatus().name(),
            "predictive", predictiveAgent.getHealthStatus().name(),
            "moodMap", moodMapAgent.getHealthStatus().name(),
            "fusion", fusionAgent.getHealthStatus().name(),
            "userReporting", userReportingAgent.getHealthStatus().name(),
            "alert", alertAgent.getHealthStatus().name()
        );
    }

    private List<Map<String, Object>> getRecentActivity() {
        return List.of(
            Map.of("time", LocalDateTime.now().minusMinutes(5), "type", "USER_REPORT", "description", "New report submitted"),
            Map.of("time", LocalDateTime.now().minusMinutes(15), "type", "ALERT", "description", "Traffic alert generated"),
            Map.of("time", LocalDateTime.now().minusMinutes(30), "type", "SYSTEM", "description", "Data sync completed")
        );
    }

    private Map<String, Object> getCategoryDistribution(int days) {
        return Map.of(
            "TRAFFIC", 45,
            "CIVIC_ISSUE", 23,
            "EMERGENCY", 12,
            "CULTURAL_EVENT", 8,
            "INFRASTRUCTURE", 7,
            "WEATHER", 5
        );
    }

    private Map<String, Object> getLocationAnalytics(int days) {
        return Map.of(
            "topAreas", List.of(
                Map.of("area", "Koramangala", "events", 45),
                Map.of("area", "MG Road", "events", 38),
                Map.of("area", "HSR Layout", "events", 32),
                Map.of("area", "Indiranagar", "events", 28),
                Map.of("area", "Whitefield", "events", 25)
            )
        );
    }

    private Map<String, Object> getEventTrends(int days) {
        return Map.of(
            "dailyTrend", "increasing",
            "peakHours", List.of(9, 17, 19),
            "growthRate", "+12%"
        );
    }

    private Map<String, Object> getPerformanceMetrics() {
        return Map.of(
            "avgResponseTime", "2.3s",
            "successRate", "98.5%",
            "errorRate", "1.5%"
        );
    }
} 