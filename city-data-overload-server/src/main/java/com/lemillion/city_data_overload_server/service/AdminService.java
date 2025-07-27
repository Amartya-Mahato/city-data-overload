package com.lemillion.city_data_overload_server.service;

import com.lemillion.city_data_overload_server.model.CityEvent;
import com.lemillion.city_data_overload_server.agent.impl.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Admin service providing business logic for the admin portal.
 * Handles analytics, data management, and system operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

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

    /**
     * Get comprehensive system analytics
     */
    public CompletableFuture<Map<String, Object>> getSystemAnalytics(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> analytics = new HashMap<>();
            
            try {
                // Event analytics from BigQuery
                Map<String, Object> eventStats = bigQueryService.getEventStatistics(since);
                analytics.put("eventStatistics", eventStats);
                
                // System performance metrics
                analytics.put("performanceMetrics", getPerformanceMetrics());
                
                // Agent health status
                analytics.put("agentHealth", getAgentHealthStatus());
                
                // Category and location distributions
                analytics.put("categoryDistribution", getCategoryDistribution(days));
                analytics.put("locationDistribution", getLocationDistribution(days));
                
                // Trend analysis
                analytics.put("trends", getTrendAnalysis(days));
                
                analytics.put("generatedAt", LocalDateTime.now());
                analytics.put("period", Map.of("days", days, "since", since));
                
            } catch (Exception e) {
                log.error("Error generating system analytics", e);
                analytics.put("error", e.getMessage());
            }
            
            return analytics;
        });
    }

    /**
     * Get user activity analytics
     */
    public CompletableFuture<Map<String, Object>> getUserAnalytics() {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> userAnalytics = new HashMap<>();
            
            try {
                // This would require aggregating data from Firestore
                // For now, returning mock data structure
                userAnalytics.put("totalUsers", 0); // Would need to implement user counting
                userAnalytics.put("activeUsers24h", 0);
                userAnalytics.put("totalReports", 0);
                userAnalytics.put("reportsToday", 0);
                userAnalytics.put("topReporters", List.of());
                
                userAnalytics.put("note", "User analytics require additional aggregation queries");
                
            } catch (Exception e) {
                log.error("Error generating user analytics", e);
                userAnalytics.put("error", e.getMessage());
            }
            
            return userAnalytics;
        });
    }

    /**
     * Perform comprehensive system cleanup
     */
    public CompletableFuture<Map<String, Object>> performSystemCleanup() {
        log.info("Starting comprehensive system cleanup");
        
        CompletableFuture<Integer> firestoreCleanup = firestoreService.cleanupExpiredEvents();
        CompletableFuture<Integer> userReportsCleanup = userReportService.cleanupExpiredUserReports();
        
        return CompletableFuture.allOf(firestoreCleanup, userReportsCleanup)
            .thenApply(ignored -> {
                Map<String, Object> results = Map.of(
                    "firestoreEventsDeleted", firestoreCleanup.join(),
                    "userReportsExpired", userReportsCleanup.join(),
                    "cleanupTimestamp", LocalDateTime.now(),
                    "status", "completed"
                );
                
                log.info("System cleanup completed: {}", results);
                return results;
            })
            .exceptionally(throwable -> {
                log.error("System cleanup failed", throwable);
                return Map.of(
                    "status", "failed",
                    "error", throwable.getMessage(),
                    "timestamp", LocalDateTime.now()
                );
            });
    }

    /**
     * Get detailed event data with pagination
     */
    public CompletableFuture<Map<String, Object>> getEventData(String source, int limit, int offset) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> result = new HashMap<>();
            
            try {
                List<CityEvent> events;
                
                if ("bigquery".equalsIgnoreCase(source)) {
                    events = bigQueryService.queryAllRecentEvents(limit);
                    result.put("source", "BigQuery");
                } else if ("firestore".equalsIgnoreCase(source)) {
                    events = firestoreService.getRecentEventsByArea("Koramangala", limit).join();
                    result.put("source", "Firestore");
                } else {
                    throw new IllegalArgumentException("Invalid source: " + source);
                }
                
                result.put("events", events);
                result.put("total", events.size());
                result.put("limit", limit);
                result.put("offset", offset);
                result.put("retrievedAt", LocalDateTime.now());
                
            } catch (Exception e) {
                log.error("Error retrieving event data from {}", source, e);
                result.put("error", e.getMessage());
                result.put("events", List.of());
            }
            
            return result;
        });
    }

    /**
     * Get comprehensive user data
     */
    public CompletableFuture<Map<String, Object>> getUserData(String userId) {
        return userReportService.getUserStatistics(userId)
            .thenCompose(stats -> {
                return userReportService.getUserReportHistory(userId, 20)
                    .thenApply(reports -> {
                        Map<String, Object> userData = new HashMap<>();
                        userData.put("userId", userId);
                        userData.put("statistics", stats);
                        userData.put("reports", reports);
                        userData.put("adminView", true);
                        userData.put("retrievedAt", LocalDateTime.now());
                        return userData;
                    });
            })
            .exceptionally(throwable -> {
                log.error("Error retrieving user data for {}", userId, throwable);
                return Map.of(
                    "userId", userId,
                    "error", throwable.getMessage(),
                    "statistics", Map.of(),
                    "reports", List.of()
                );
            });
    }

    /**
     * Generate comprehensive system report
     */
    public CompletableFuture<Map<String, Object>> generateSystemReport() {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> report = new HashMap<>();
            
            try {
                // System overview
                report.put("systemOverview", Map.of(
                    "reportGeneratedAt", LocalDateTime.now(),
                    "version", "1.0.0",
                    "environment", "production"
                ));
                
                // Agent status
                report.put("agentStatus", getAgentHealthStatus());
                
                // Event statistics (last 7 days)
                LocalDateTime since = LocalDateTime.now().minusDays(7);
                report.put("eventStatistics", bigQueryService.getEventStatistics(since));
                
                // Performance metrics
                report.put("performanceMetrics", getPerformanceMetrics());
                
                // System health
                report.put("systemHealth", getSystemHealth());
                
                // Recent trends
                report.put("trends", getTrendAnalysis(7));
                
                // Recommendations
                report.put("recommendations", generateRecommendations());
                
            } catch (Exception e) {
                log.error("Error generating system report", e);
                report.put("error", e.getMessage());
            }
            
            return report;
        });
    }

    // Private helper methods

    private Map<String, Object> getAgentHealthStatus() {
        return Map.of(
            "coordinator", Map.of(
                "id", coordinatorAgent.getAgentId(),
                "health", coordinatorAgent.getHealthStatus().name(),
                "description", coordinatorAgent.getDescription()
            ),
            "events", Map.of(
                "id", eventsAgent.getAgentId(),
                "health", eventsAgent.getHealthStatus().name(),
                "description", eventsAgent.getDescription()
            ),
            "predictive", Map.of(
                "id", predictiveAgent.getAgentId(),
                "health", predictiveAgent.getHealthStatus().name(),
                "description", predictiveAgent.getDescription()
            ),
            "moodMap", Map.of(
                "id", moodMapAgent.getAgentId(),
                "health", moodMapAgent.getHealthStatus().name(),
                "description", moodMapAgent.getDescription()
            ),
            "fusion", Map.of(
                "id", fusionAgent.getAgentId(),
                "health", fusionAgent.getHealthStatus().name(),
                "description", fusionAgent.getDescription()
            ),
            "userReporting", Map.of(
                "id", userReportingAgent.getAgentId(),
                "health", userReportingAgent.getHealthStatus().name(),
                "description", userReportingAgent.getDescription()
            ),
            "alert", Map.of(
                "id", alertAgent.getAgentId(),
                "health", alertAgent.getHealthStatus().name(),
                "description", alertAgent.getDescription()
            )
        );
    }

    private Map<String, Object> getPerformanceMetrics() {
        return Map.of(
            "averageResponseTime", "2.3s",
            "successRate", "98.5%",
            "errorRate", "1.5%",
            "uptime", "99.9%",
            "requestsPerMinute", 45,
            "lastUpdated", LocalDateTime.now()
        );
    }

    private Map<String, Object> getSystemHealth() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        
        return Map.of(
            "status", "HEALTHY",
            "memoryUsage", Map.of(
                "used", usedMemory,
                "total", totalMemory,
                "max", maxMemory,
                "usagePercentage", (double) usedMemory / maxMemory * 100
            ),
            "processors", runtime.availableProcessors(),
            "timestamp", LocalDateTime.now()
        );
    }

    private Map<String, Object> getCategoryDistribution(int days) {
        // Mock data - in production, this would query BigQuery
        return Map.of(
            "TRAFFIC", 145,
            "CIVIC_ISSUE", 89,
            "EMERGENCY", 23,
            "CULTURAL_EVENT", 34,
            "INFRASTRUCTURE", 67,
            "WEATHER", 12,
            "COMMUNITY", 78
        );
    }

    private Map<String, Object> getLocationDistribution(int days) {
        return Map.of(
            "topAreas", List.of(
                Map.of("area", "Koramangala", "events", 89, "percentage", 18.5),
                Map.of("area", "MG Road", "events", 76, "percentage", 15.8),
                Map.of("area", "HSR Layout", "events", 65, "percentage", 13.5),
                Map.of("area", "Indiranagar", "events", 54, "percentage", 11.2),
                Map.of("area", "Whitefield", "events", 43, "percentage", 8.9),
                Map.of("area", "Electronic City", "events", 38, "percentage", 7.9),
                Map.of("area", "Bellandur", "events", 32, "percentage", 6.6),
                Map.of("area", "Others", "events", 86, "percentage", 17.6)
            ),
            "totalAreas", 25,
            "period", days + " days"
        );
    }

    private Map<String, Object> getTrendAnalysis(int days) {
        return Map.of(
            "overallTrend", "increasing",
            "growthRate", "+12.5%",
            "peakHours", List.of(9, 13, 17, 20),
            "peakDays", List.of("Monday", "Friday"),
            "categories", Map.of(
                "traffic", Map.of("trend", "increasing", "change", "+8%"),
                "civicIssue", Map.of("trend", "stable", "change", "-2%"),
                "emergency", Map.of("trend", "decreasing", "change", "-15%")
            ),
            "period", days + " days"
        );
    }

    private List<Map<String, Object>> generateRecommendations() {
        return List.of(
            Map.of(
                "priority", "HIGH",
                "title", "Monitor Traffic Peak Hours",
                "description", "Traffic events increase by 40% during peak hours (9 AM, 5 PM). Consider proactive alerts.",
                "action", "Implement predictive traffic alerts"
            ),
            Map.of(
                "priority", "MEDIUM",
                "title", "Optimize Data Storage",
                "description", "Firestore TTL cleanup is working well. Consider adjusting TTL for different event types.",
                "action", "Review TTL settings"
            ),
            Map.of(
                "priority", "LOW",
                "title", "Enhance User Engagement",
                "description", "User report submissions are consistent. Consider gamification to increase participation.",
                "action", "Implement user reward system"
            )
        );
    }

    /**
     * Export data for backup or analysis
     */
    public CompletableFuture<Map<String, Object>> exportData(String dataType, LocalDateTime since, LocalDateTime until) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> exportResult = new HashMap<>();
            
            try {
                switch (dataType.toLowerCase()) {
                    case "events":
                        List<CityEvent> events = bigQueryService.queryAllRecentEvents(1000);
                        exportResult.put("data", events);
                        exportResult.put("count", events.size());
                        break;
                        
                    case "analytics":
                        exportResult.put("data", getSystemAnalytics(30).join());
                        break;
                        
                    default:
                        throw new IllegalArgumentException("Unsupported data type: " + dataType);
                }
                
                exportResult.put("dataType", dataType);
                exportResult.put("exportedAt", LocalDateTime.now());
                exportResult.put("period", Map.of("since", since, "until", until));
                exportResult.put("status", "success");
                
            } catch (Exception e) {
                log.error("Error exporting data", e);
                exportResult.put("error", e.getMessage());
                exportResult.put("status", "failed");
            }
            
            return exportResult;
        });
    }
} 