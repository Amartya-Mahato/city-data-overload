package com.lemillion.city_data_overload_server.agent.impl;

import com.lemillion.city_data_overload_server.agent.Agent;
import com.lemillion.city_data_overload_server.agent.AgentRequest;
import com.lemillion.city_data_overload_server.agent.AgentResponse;
import com.lemillion.city_data_overload_server.model.CityEvent;
import com.lemillion.city_data_overload_server.service.BigQueryService;
import com.lemillion.city_data_overload_server.service.FirestoreService;
import com.lemillion.city_data_overload_server.service.VertexAiService; // Add VertexAI integration
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Alert Agent - Monitors for serious alerts and emergency situations.
 * Provides critical notifications and emergency response coordination.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AlertAgent implements Agent {

    /**
     * Enhanced Alert Agent - now with Vertex AI-powered alert generation
     */
    private final FirestoreService firestoreService;
    private final BigQueryService bigQueryService;
    private final VertexAiService vertexAiService; // Add VertexAI integration

    // Alert thresholds and configuration
    private static final Map<CityEvent.EventCategory, Integer> ALERT_THRESHOLDS = Map.of(
        CityEvent.EventCategory.EMERGENCY, 1,
        CityEvent.EventCategory.INFRASTRUCTURE, 3,
        CityEvent.EventCategory.TRAFFIC, 5,
        CityEvent.EventCategory.WEATHER, 2,
        CityEvent.EventCategory.SAFETY, 2,
        CityEvent.EventCategory.CIVIC_ISSUE, 4
    );

    private static final List<String> CRITICAL_KEYWORDS = Arrays.asList(
        "fire", "accident", "flood", "emergency", "evacuation", "blocked",
        "breakdown", "power cut", "water shortage", "gas leak", "collapse"
    );

    @Override
    public String getAgentId() {
        return "alert-agent";
    }

    @Override
    public AgentType getAgentType() {
        return AgentType.ALERTER;
    }

    @Override
    public String getDescription() {
        return "Monitors for serious alerts and emergency situations in the city";
    }

    @Override
    public CompletableFuture<AgentResponse> processRequest(AgentRequest request) {
        long startTime = System.currentTimeMillis();
        
        log.info("Alert agent processing request: {} of type: {}", 
                request.getRequestId(), request.getRequestType());
        
        try {
            return checkForAlerts(request)
                .thenApply(alerts -> {
                    AgentResponse response = AgentResponse.success(
                        request.getRequestId(),
                        getAgentId(),
                        String.format("Found %d active alerts", alerts.size())
                    );
                    
                    response.setAlerts(alerts);
                    response.setProcessingTimeMs(System.currentTimeMillis() - startTime);
                    
                    // Add alert metadata
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("total_alerts", alerts.size());
                    metadata.put("critical_alerts", countCriticalAlerts(alerts));
                    metadata.put("alert_areas", getAlertAreas(alerts));
                    metadata.put("check_timestamp", LocalDateTime.now());
                    response.setMetadata(metadata);
                    
                    log.info("Alert agent completed request: {} with {} alerts in {}ms", 
                            request.getRequestId(), alerts.size(), response.getProcessingTimeMs());
                    
                    return response;
                })
                .exceptionally(throwable -> {
                    log.error("Alert agent failed to process request: {}", 
                            request.getRequestId(), throwable);
                    return AgentResponse.error(
                        request.getRequestId(),
                        getAgentId(),
                        "Failed to check alerts: " + throwable.getMessage(),
                        "ALERT_CHECK_ERROR"
                    );
                });
                
        } catch (Exception e) {
            log.error("Error in alert agent request processing", e);
            return CompletableFuture.completedFuture(
                AgentResponse.error(request.getRequestId(), getAgentId(), 
                    "Alert agent error: " + e.getMessage(), "ALERT_AGENT_ERROR")
            );
        }
    }

    @Override
    public boolean canHandle(String requestType) {
        return "CHECK_ALERTS".equals(requestType) || 
               "MONITOR_EMERGENCIES".equals(requestType) ||
               "GET_CRITICAL_ALERTS".equals(requestType) ||
               "EMERGENCY_SCAN".equals(requestType);
    }

    @Override
    public HealthStatus getHealthStatus() {
        try {
            // Test alert checking with a simple query
            firestoreService.getActiveAlerts("Koramangala").get(
                java.util.concurrent.TimeUnit.SECONDS.toMillis(3), 
                java.util.concurrent.TimeUnit.MILLISECONDS
            );
            return HealthStatus.HEALTHY;
        } catch (Exception e) {
            log.warn("Alert agent health check failed", e);
            return HealthStatus.DEGRADED;
        }
    }

    /**
     * Main alert checking logic
     */
    private CompletableFuture<List<Map<String, Object>>> checkForAlerts(AgentRequest request) {
        List<CompletableFuture<List<Map<String, Object>>>> alertChecks = new ArrayList<>();
        
        // Check for active alerts in Firestore
        alertChecks.add(checkFirestoreAlerts(request));
        
        // Check for pattern-based alerts from recent events
        alertChecks.add(checkPatternBasedAlerts(request));
        
        // Check for severity-based alerts
        alertChecks.add(checkSeverityBasedAlerts(request));
        
        // Check for keyword-based emergency alerts
        alertChecks.add(checkKeywordBasedAlerts(request));
        
        return CompletableFuture.allOf(alertChecks.toArray(new CompletableFuture[0]))
            .thenApply(ignored -> {
                List<Map<String, Object>> allAlerts = new ArrayList<>();
                
                for (CompletableFuture<List<Map<String, Object>>> alertCheck : alertChecks) {
                    try {
                        List<Map<String, Object>> alerts = alertCheck.join();
                        allAlerts.addAll(alerts);
                    } catch (Exception e) {
                        log.warn("Alert check failed", e);
                    }
                }
                
                // Deduplicate and prioritize alerts
                return deduplicateAndPrioritizeAlerts(allAlerts);
            });
    }

    /**
     * Check for active alerts in Firestore
     */
    private CompletableFuture<List<Map<String, Object>>> checkFirestoreAlerts(AgentRequest request) {
        String area = request.getArea() != null ? request.getArea() : "Koramangala";
        
        return firestoreService.getActiveAlerts(area)
            .thenApply(firestoreAlerts -> {
                log.debug("Found {} active alerts in Firestore for area: {}", 
                         firestoreAlerts.size(), area);
                
                return firestoreAlerts.stream()
                    .map(alert -> enhanceAlertWithMetadata(alert, "FIRESTORE_ACTIVE"))
                    .collect(Collectors.toList());
            })
            .exceptionally(throwable -> {
                log.warn("Failed to check Firestore alerts for area: {}", area, throwable);
                return new ArrayList<>();
            });
    }

    /**
     * Check for pattern-based alerts (multiple events in same area/category)
     */
    private CompletableFuture<List<Map<String, Object>>> checkPatternBasedAlerts(AgentRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<Map<String, Object>> patternAlerts = new ArrayList<>();
                
                // Check each alert category for unusual patterns
                for (Map.Entry<CityEvent.EventCategory, Integer> entry : ALERT_THRESHOLDS.entrySet()) {
                    CityEvent.EventCategory category = entry.getKey();
                    int threshold = entry.getValue();
                    
                    // Get recent events for this category
                    List<CityEvent> recentEvents = bigQueryService.queryEventsByCategoryAndSeverity(
                        category, 
                        CityEvent.EventSeverity.MODERATE,
                        LocalDateTime.now().minusDays(1),
                        20
                    );
                    
                    // Group by area and check thresholds
                    Map<String, List<CityEvent>> eventsByArea = recentEvents.stream()
                        .filter(event -> event.getLocation() != null && 
                                        event.getLocation().getArea() != null)
                        .collect(Collectors.groupingBy(
                            event -> event.getLocation().getArea()
                        ));
                    
                    for (Map.Entry<String, List<CityEvent>> areaEvents : eventsByArea.entrySet()) {
                        String area = areaEvents.getKey();
                        List<CityEvent> events = areaEvents.getValue();
                        
                        if (events.size() >= threshold) {
                            Map<String, Object> alert = createPatternAlert(category, area, events);
                            patternAlerts.add(alert);
                        }
                    }
                }
                
                log.debug("Generated {} pattern-based alerts", patternAlerts.size());
                return patternAlerts;
                
            } catch (Exception e) {
                log.warn("Failed to check pattern-based alerts", e);
                return new ArrayList<>();
            }
        });
    }

    /**
     * Check for severity-based alerts (high/critical severity events)
     */
    private CompletableFuture<List<Map<String, Object>>> checkSeverityBasedAlerts(AgentRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<Map<String, Object>> severityAlerts = new ArrayList<>();
                
                // Check for critical events
                List<CityEvent> criticalEvents = bigQueryService.queryEventsByCategoryAndSeverity(
                    null, // All categories
                    CityEvent.EventSeverity.CRITICAL,
                    LocalDateTime.now().minusDays(7),
                    10
                );
                
                for (CityEvent event : criticalEvents) {
                    // Filter by location if specified
                    if (isEventRelevantToRequest(event, request)) {
                        Map<String, Object> alert = createSeverityAlert(event, "CRITICAL");
                        severityAlerts.add(alert);
                    }
                }
                
                // Check for high severity events
                List<CityEvent> highEvents = bigQueryService.queryEventsByCategoryAndSeverity(
                    null, // All categories
                    CityEvent.EventSeverity.HIGH,
                    LocalDateTime.now().minusDays(3),
                    20  // Increased limit to account for filtering
                );
                
                for (CityEvent event : highEvents) {
                    // Filter by location if specified
                    if (isEventRelevantToRequest(event, request)) {
                        Map<String, Object> alert = createSeverityAlert(event, "HIGH");
                        severityAlerts.add(alert);
                    }
                }
                
                log.debug("Generated {} severity-based alerts", severityAlerts.size());
                return severityAlerts;
                
            } catch (Exception e) {
                log.warn("Failed to check severity-based alerts", e);
                return new ArrayList<>();
            }
        });
    }

    /**
     * Check for keyword-based emergency alerts
     */
    private CompletableFuture<List<Map<String, Object>>> checkKeywordBasedAlerts(AgentRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<Map<String, Object>> keywordAlerts = new ArrayList<>();
                
                // This would typically involve text analysis of recent events
                // For now, we'll create a placeholder implementation
                
                // In a real implementation, you would:
                // 1. Query recent events from the last hour
                // 2. Analyze their content for critical keywords
                // 3. Generate alerts for events containing emergency keywords
                
                log.debug("Keyword-based alert checking completed (placeholder)");
                return keywordAlerts;
                
            } catch (Exception e) {
                log.warn("Failed to check keyword-based alerts", e);
                return new ArrayList<>();
            }
        });
    }

    /**
     * Create a pattern-based alert
     */
    private Map<String, Object> createPatternAlert(
            CityEvent.EventCategory category, String area, List<CityEvent> events) {
        
        Map<String, Object> alert = new HashMap<>();
        alert.put("id", UUID.randomUUID().toString());
        alert.put("type", "PATTERN_ALERT");
        alert.put("category", category.name());
        alert.put("area", area);
        alert.put("severity", "HIGH");
        alert.put("title", String.format("Multiple %s events in %s", category.name(), area));
        alert.put("message", String.format(
            "%d %s events reported in %s within the last 2 hours. Possible pattern detected.",
            events.size(), category.name(), area
        ));
        alert.put("event_count", events.size());
        alert.put("related_events", events.stream()
            .limit(5)
            .map(CityEvent::getId)
            .collect(Collectors.toList()));
        alert.put("created_at", LocalDateTime.now());
        alert.put("expires_at", LocalDateTime.now().plusHours(6));
        alert.put("priority", "HIGH");
        
        // Add actionable advice
        alert.put("actionable_advice", generatePatternAdvice(category, area));
        
        return alert;
    }

    /**
     * Create a severity-based alert
     */
    private Map<String, Object> createSeverityAlert(CityEvent event, String alertLevel) {
        Map<String, Object> alert = new HashMap<>();
        alert.put("id", UUID.randomUUID().toString());
        alert.put("type", "SEVERITY_ALERT");
        alert.put("category", event.getCategory() != null ? event.getCategory().name() : "UNKNOWN");
        alert.put("area", event.getLocation() != null ? 
                  event.getLocation().getArea() : "Unknown");
        alert.put("severity", alertLevel);
        alert.put("title", event.getTitle() != null ? 
                  event.getTitle() : "High Priority Event");
        alert.put("message", event.getDescription() != null ? 
                  event.getDescription() : "Critical event requires attention");
        alert.put("related_event_id", event.getId());
        alert.put("created_at", LocalDateTime.now());
        alert.put("expires_at", LocalDateTime.now().plusHours(4));
        alert.put("priority", alertLevel);
        
        // Add location information
        if (event.getLocation() != null) {
            alert.put("latitude", event.getLocation().getLatitude());
            alert.put("longitude", event.getLocation().getLongitude());
            alert.put("address", event.getLocation().getAddress());
        }
        
        // Add actionable advice
        alert.put("actionable_advice", generateSeverityAdvice(event));
        
        return alert;
    }

    /**
     * Enhance alert with additional metadata
     */
    private Map<String, Object> enhanceAlertWithMetadata(Map<String, Object> alert, String source) {
        Map<String, Object> enhanced = new HashMap<>(alert);
        enhanced.put("source", source);
        enhanced.put("verified", true); // Firestore alerts are pre-verified
        enhanced.put("last_updated", LocalDateTime.now());
        return enhanced;
    }

    /**
     * Deduplicate and prioritize alerts
     */
    private List<Map<String, Object>> deduplicateAndPrioritizeAlerts(
            List<Map<String, Object>> alerts) {
        
        // Remove duplicates based on area, category, and type
        Map<String, Map<String, Object>> uniqueAlerts = new HashMap<>();
        
        for (Map<String, Object> alert : alerts) {
            String key = generateAlertKey(alert);
            
            // Keep the alert with higher priority
            if (!uniqueAlerts.containsKey(key) || 
                isHigherPriority(alert, uniqueAlerts.get(key))) {
                uniqueAlerts.put(key, alert);
            }
        }
        
        // Sort by priority and recency
        return uniqueAlerts.values().stream()
            .sorted((a, b) -> {
                // First by priority
                int priorityComparison = comparePriority(
                    (String) a.get("priority"), 
                    (String) b.get("priority")
                );
                
                if (priorityComparison != 0) {
                    return priorityComparison;
                }
                
                // Then by creation time (most recent first)
                LocalDateTime timeA = (LocalDateTime) a.get("created_at");
                LocalDateTime timeB = (LocalDateTime) b.get("created_at");
                return timeB.compareTo(timeA);
            })
            .limit(20) // Limit to top 20 alerts
            .collect(Collectors.toList());
    }

    /**
     * Check for alerts using AI-powered analysis
     */
    private CompletableFuture<List<Map<String, Object>>> checkForActiveAlerts(AgentRequest request) {
        log.info("Checking for AI-powered alerts for area: {}", request.getArea());
        
        // Get recent events to analyze for alert patterns
        return getRecentEventsForAlerts(request)
            .thenCompose(recentEvents -> {
                if (recentEvents.isEmpty()) {
                    return generateGeneralAlerts(request);
                }
                
                // Use Vertex AI to analyze patterns and generate intelligent alerts
                return analyzeEventsForAlertsWithAI(recentEvents, request);
            })
            .exceptionally(throwable -> {
                log.error("AI alert analysis failed, using fallback", throwable);
                return generateBasicTimeBasedAlerts(request);
            });
    }

    /**
     * Get recent events to analyze for alert patterns
     */
    private CompletableFuture<List<CityEvent>> getRecentEventsForAlerts(AgentRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Get events from last 24 hours for pattern analysis
                List<CityEvent> recentEvents = new ArrayList<>();
                
                // Query Firestore for recent events using the new getRecentEvents method
                try {
                    recentEvents.addAll(firestoreService.getRecentEvents(24, 1000).get());
                } catch (Exception e) {
                    log.warn("Failed to fetch events from Firestore", e);
                }
                
                log.debug("Found {} recent events for alert analysis", recentEvents.size());
                return recentEvents;
                
            } catch (Exception e) {
                log.error("Error fetching recent events for alerts", e);
                return new ArrayList<>();
            }
        });
    }

    /**
     * Analyze events using Vertex AI to generate intelligent alerts
     */
    private CompletableFuture<List<Map<String, Object>>> analyzeEventsForAlertsWithAI(
            List<CityEvent> recentEvents, AgentRequest request) {
        
        log.debug("Analyzing {} events with AI for alert generation", recentEvents.size());
        
        // Create context for AI analysis
        String alertContext = buildAlertAnalysisContext(recentEvents, request);
        
        return vertexAiService.categorizeEvent(alertContext, "ALERT_GENERATION")
            .thenApply(aiResult -> {
                List<Map<String, Object>> aiAlerts = extractAlertsFromAIResult(aiResult, request);
                
                // Enhance AI alerts with real-time context
                return enhanceAlertsWithContext(aiAlerts, request);
            })
            .exceptionally(throwable -> {
                log.warn("AI alert generation failed, using pattern-based alerts", throwable);
                return generatePatternBasedAlerts(recentEvents, request);
            });
    }

    /**
     * Build context for AI alert analysis
     */
    private String buildAlertAnalysisContext(List<CityEvent> events, AgentRequest request) {
        StringBuilder context = new StringBuilder();
        context.append("Analyze these recent city events and generate relevant alerts for residents of ");
        context.append(request.getArea() != null ? request.getArea() : "Bengaluru").append(":\n\n");
        
        // Summarize recent events
        Map<CityEvent.EventCategory, Long> categoryCount = events.stream()
            .collect(Collectors.groupingBy(CityEvent::getCategory, Collectors.counting()));
        
        Map<CityEvent.EventSeverity, Long> severityCount = events.stream()
            .collect(Collectors.groupingBy(CityEvent::getSeverity, Collectors.counting()));
        
        context.append("Recent event patterns:\n");
        categoryCount.forEach((category, count) -> 
            context.append("- ").append(category).append(": ").append(count).append(" events\n"));
        
        context.append("\nSeverity distribution:\n");
        severityCount.forEach((severity, count) -> 
            context.append("- ").append(severity).append(": ").append(count).append(" events\n"));
        
        // Add sample events for context
        context.append("\nSample recent events:\n");
        events.stream().limit(3).forEach(event -> {
            context.append("- ").append(event.getTitle()).append(" (")
                   .append(event.getCategory()).append(", ")
                   .append(event.getSeverity()).append(")\n");
        });
        
        context.append("\nGenerate actionable alerts that help residents stay safe and informed.");
        
        return context.toString();
    }

    /**
     * Extract alerts from AI analysis result
     */
    private List<Map<String, Object>> extractAlertsFromAIResult(Map<String, Object> aiResult, AgentRequest request) {
        List<Map<String, Object>> alerts = new ArrayList<>();
        
        String aiSummary = (String) aiResult.getOrDefault("summary", "");
        String aiCategory = (String) aiResult.getOrDefault("category", "SAFETY");
        String aiSeverity = (String) aiResult.getOrDefault("severity", "MODERATE");
        Double confidence = (Double) aiResult.getOrDefault("confidence", 0.7);
        
        // Create primary AI-generated alert using HashMap builder
        Map<String, Object> primaryAlert = new HashMap<>();
        primaryAlert.put("id", "ai_alert_" + System.currentTimeMillis());
        primaryAlert.put("title", generateAlertTitle(aiCategory, request.getArea()));
        primaryAlert.put("message", aiSummary);
        primaryAlert.put("type", aiCategory.toUpperCase());
        primaryAlert.put("severity", aiSeverity.toUpperCase());
        primaryAlert.put("confidence", confidence);
        primaryAlert.put("area", request.getArea() != null ? request.getArea() : "Bengaluru");
        primaryAlert.put("timestamp", LocalDateTime.now());
        primaryAlert.put("ai_generated", true);
        primaryAlert.put("source", "pattern_analysis");
        primaryAlert.put("actionable_advice", generateActionableAdvice(aiCategory, aiSummary));
        
        alerts.add(primaryAlert);
        
        // Generate additional category-specific alerts based on AI analysis
        if (aiCategory.contains("TRAFFIC") && confidence > 0.6) {
            alerts.add(createTrafficAlert(request, confidence));
        }
        
        if (aiCategory.contains("WEATHER") && confidence > 0.5) {
            alerts.add(createWeatherAlert(request, confidence));
        }
        
        return alerts;
    }

    /**
     * Generate alert title based on category and area
     */
    private String generateAlertTitle(String category, String area) {
        String areaName = area != null ? area : "Bengaluru";
        
        return switch (category.toUpperCase()) {
            case "TRAFFIC" -> "Traffic Advisory for " + areaName;
            case "WEATHER" -> "Weather Alert for " + areaName;
            case "EMERGENCY" -> "Emergency Alert - " + areaName;
            case "INFRASTRUCTURE" -> "Infrastructure Notice - " + areaName;
            case "SAFETY" -> "Safety Advisory - " + areaName;
            default -> "Community Alert - " + areaName;
        };
    }

    /**
     * Generate actionable advice based on category and message
     */
    private String generateActionableAdvice(String category, String message) {
        return switch (category.toUpperCase()) {
            case "TRAFFIC" -> "Consider alternative routes, use public transport, or adjust travel times.";
            case "WEATHER" -> "Stay indoors if possible, carry umbrellas, and avoid waterlogged areas.";
            case "EMERGENCY" -> "Follow official instructions, stay safe, and keep emergency contacts handy.";
            case "INFRASTRUCTURE" -> "Plan accordingly and use alternative facilities if needed.";
            case "SAFETY" -> "Stay alert, avoid risky areas, and inform family/friends of your location.";
            default -> "Stay informed and follow local guidance.";
        };
    }

    /**
     * Enhance alerts with real-time context
     */
    private List<Map<String, Object>> enhanceAlertsWithContext(List<Map<String, Object>> alerts, AgentRequest request) {
        return alerts.stream()
            .map(alert -> enhanceSingleAlert(alert, request))
            .collect(Collectors.toList());
    }

    /**
     * Enhance a single alert with additional context
     */
    private Map<String, Object> enhanceSingleAlert(Map<String, Object> alert, AgentRequest request) {
        Map<String, Object> enhanced = new HashMap<>(alert);
        
        // Add time-based urgency
        LocalDateTime now = LocalDateTime.now();
        int currentHour = now.getHour();
        
        String alertType = (String) alert.get("type");
        if ("TRAFFIC".equals(alertType)) {
            if ((currentHour >= 7 && currentHour <= 10) || (currentHour >= 17 && currentHour <= 21)) {
                enhanced.put("urgency", "HIGH");
                enhanced.put("timing_note", "Rush hour traffic conditions");
            } else {
                enhanced.put("urgency", "MODERATE");
            }
        }
        
        // Add location-specific enhancements
        if (request.getArea() != null) {
            enhanced.put("localized_advice", generateLocalizedAdvice(alertType, request.getArea()));
        }
        
        // Add expiry time
        enhanced.put("expires_at", now.plusHours(6)); // Alerts expire in 6 hours
        enhanced.put("alert_agent_version", "2.0_ai_enhanced");
        
        return enhanced;
    }

    /**
     * Generate localized advice for specific areas
     */
    private String generateLocalizedAdvice(String alertType, String area) {
        StringBuilder advice = new StringBuilder();
        
        if ("TRAFFIC".equals(alertType)) {
            // Area-specific traffic advice
            switch (area.toLowerCase()) {
                case "koramangala", "hsr layout", "btm layout" -> 
                    advice.append("Consider using Namma Metro Purple Line. ");
                case "whitefield", "marathahalli", "kundalahalli" -> 
                    advice.append("BMTC Volvo services available on ORR. ");
                case "electronic city", "bommanahalli" -> 
                    advice.append("Nice Road and Hosur Road are main alternatives. ");
                default -> 
                    advice.append("Check metro and bus connectivity. ");
            }
        }
        
        advice.append("Stay updated with real-time conditions.");
        return advice.toString();
    }

    /**
     * Generate general alerts when no recent events are available
     */
    private CompletableFuture<List<Map<String, Object>>> generateGeneralAlerts(AgentRequest request) {
        log.debug("Generating general AI alerts for area: {}", request.getArea());
        
        // Create a minimal context for general alerts
        String generalContext = String.format(
            "Generate general safety and awareness alerts for residents of %s, Bengaluru. " +
            "Focus on common urban challenges and helpful reminders.",
            request.getArea() != null ? request.getArea() : "Bengaluru"
        );
        
        return vertexAiService.categorizeEvent(generalContext, "GENERAL_ALERTS")
            .thenApply(aiResult -> {
                List<Map<String, Object>> generalAlerts = new ArrayList<>();
                
                Map<String, Object> generalAlert = new HashMap<>();

                generalAlert.put("id", "general_ai_alert_" + System.currentTimeMillis());
                generalAlert.put("title", "Community Advisory");
                generalAlert.put("message", aiResult.getOrDefault("summary", "Stay informed about local conditions"));
                generalAlert.put("type", "COMMUNITY");
                generalAlert.put("severity", "LOW");
                generalAlert.put("confidence", 0.6);
                generalAlert.put("area", request.getArea() != null ? request.getArea() : "Bengaluru");
                generalAlert.put("timestamp", LocalDateTime.now());
                generalAlert.put("ai_generated", true);
                generalAlert.put("source", "general_advisory");
                generalAlert.put("actionable_advice", "Stay connected with community updates and local news");
                
                // Create AI-generated general alert
                generalAlerts.add(
                    generalAlert
                );
                
                return generalAlerts;
            })
            .exceptionally(throwable -> {
                log.warn("General AI alerts failed, using basic alerts", throwable);
                return generateBasicTimeBasedAlerts(request);
            });
    }

    /**
     * Generate pattern-based alerts when AI fails
     */
    private List<Map<String, Object>> generatePatternBasedAlerts(List<CityEvent> events, AgentRequest request) {
        List<Map<String, Object>> patternAlerts = new ArrayList<>();
        
        // Analyze patterns manually
        long trafficEvents = events.stream()
            .filter(e -> e.getCategory() == CityEvent.EventCategory.TRAFFIC)
            .count();
        
        long emergencyEvents = events.stream()
            .filter(e -> e.getCategory() == CityEvent.EventCategory.EMERGENCY)
            .count();
        
        long highSeverityEvents = events.stream()
            .filter(e -> e.getSeverity() == CityEvent.EventSeverity.HIGH || 
                        e.getSeverity() == CityEvent.EventSeverity.CRITICAL)
            .count();
        
        // Generate alerts based on patterns
        if (trafficEvents >= 3) {
            patternAlerts.add(createTrafficAlert(request, 0.8));
        }
        
        if (emergencyEvents >= 1) {
            Map<String, Object> emergencyAlert = new HashMap<>();
            emergencyAlert.put("id", "pattern_emergency_" + System.currentTimeMillis());
            emergencyAlert.put("title", "Emergency Activity Alert");
            emergencyAlert.put("message", "Increased emergency activity reported in your area. Stay alert and avoid affected zones.");
            emergencyAlert.put("type", "EMERGENCY");
            emergencyAlert.put("severity", "HIGH");
            emergencyAlert.put("confidence", 0.9);
            emergencyAlert.put("area", request.getArea() != null ? request.getArea() : "Bengaluru");
            emergencyAlert.put("timestamp", LocalDateTime.now());
            emergencyAlert.put("ai_generated", false);
            emergencyAlert.put("source", "pattern_detection");
            patternAlerts.add(emergencyAlert);
        }
        
        if (highSeverityEvents >= 2) {
            Map<String, Object> severityAlert = new HashMap<>();
            severityAlert.put("id", "pattern_severity_" + System.currentTimeMillis());
            severityAlert.put("title", "High Severity Activity");
            severityAlert.put("message", "Multiple high-severity events detected. Exercise caution and stay informed.");
            severityAlert.put("type", "SAFETY");
            severityAlert.put("severity", "MODERATE");
            severityAlert.put("confidence", 0.7);
            severityAlert.put("area", request.getArea() != null ? request.getArea() : "Bengaluru");
            severityAlert.put("timestamp", LocalDateTime.now());
            severityAlert.put("ai_generated", false);
            severityAlert.put("source", "pattern_detection");
            patternAlerts.add(severityAlert);
        }
        
        return patternAlerts;
    }

    /**
     * Create traffic-specific alert
     */
    private Map<String, Object> createTrafficAlert(AgentRequest request, double confidence) {
        LocalDateTime now = LocalDateTime.now();
        int currentHour = now.getHour();
        
        String message;
        String severity;
        
        if ((currentHour >= 7 && currentHour <= 10) || (currentHour >= 17 && currentHour <= 21)) {
            message = "Heavy traffic conditions expected during rush hours. Plan for extra travel time.";
            severity = "HIGH";
        } else {
            message = "Moderate traffic conditions reported. Consider alternative routes if needed.";
            severity = "MODERATE";
        }
        
        Map<String, Object> trafficAlert = new HashMap<>();
        trafficAlert.put("id", "traffic_alert_" + System.currentTimeMillis());
        trafficAlert.put("title", "Traffic Advisory");
        trafficAlert.put("message", message);
        trafficAlert.put("type", "TRAFFIC");
        trafficAlert.put("severity", severity);
        trafficAlert.put("confidence", confidence);
        trafficAlert.put("area", request.getArea() != null ? request.getArea() : "Bengaluru");
        trafficAlert.put("timestamp", now);
        trafficAlert.put("ai_generated", confidence > 0.7);
        trafficAlert.put("source", "traffic_analysis");
        trafficAlert.put("actionable_advice", "Use public transport, check real-time traffic apps, or delay non-urgent travel");
        return trafficAlert;
    }

    /**
     * Create weather-specific alert
     */
    private Map<String, Object> createWeatherAlert(AgentRequest request, double confidence) {
        // This would normally integrate with weather APIs
        Map<String, Object> weatherAlert = new HashMap<>();
        weatherAlert.put("id", "weather_alert_" + System.currentTimeMillis());
        weatherAlert.put("title", "Weather Advisory");
        weatherAlert.put("message", "Monitor weather conditions and be prepared for changes. Check local forecasts.");
        weatherAlert.put("type", "WEATHER");
        weatherAlert.put("severity", "LOW");
        weatherAlert.put("confidence", confidence);
        weatherAlert.put("area", request.getArea() != null ? request.getArea() : "Bengaluru");
        weatherAlert.put("timestamp", LocalDateTime.now());
        weatherAlert.put("ai_generated", true);
        weatherAlert.put("source", "weather_analysis");
        weatherAlert.put("actionable_advice", "Carry umbrellas, avoid flood-prone areas during heavy rain");
        return weatherAlert;
    }

    /**
     * Basic time-based alerts as final fallback
     */
    private List<Map<String, Object>> generateBasicTimeBasedAlerts(AgentRequest request) {
        List<Map<String, Object>> basicAlerts = new ArrayList<>();
        
        LocalDateTime now = LocalDateTime.now();
        int currentHour = now.getHour();
        
        // Rush hour traffic alert
        if ((currentHour >= 7 && currentHour <= 10) || (currentHour >= 17 && currentHour <= 21)) {
            basicAlerts.add(Map.of(
                "id", "basic_traffic_" + System.currentTimeMillis(),
                "title", "Rush Hour Traffic",
                "message", "Heavy traffic expected during rush hours. Plan accordingly.",
                "type", "TRAFFIC",
                "severity", "MODERATE",
                "confidence", 0.8,
                "area", request.getArea() != null ? request.getArea() : "Bengaluru",
                "timestamp", now,
                "ai_generated", false,
                "source", "time_based"
            ));
        }
        
        // General safety reminder
        basicAlerts.add(Map.of(
            "id", "basic_safety_" + System.currentTimeMillis(),
            "title", "Stay Informed",
            "message", "Stay updated with local conditions and community news.",
            "type", "SAFETY",
            "severity", "LOW",
            "confidence", 0.9,
            "area", request.getArea() != null ? request.getArea() : "Bengaluru",
            "timestamp", now,
            "ai_generated", false,
            "source", "general_advisory"
        ));
        
        return basicAlerts;
    }

    // Helper methods

    private String generateAlertKey(Map<String, Object> alert) {
        return String.format("%s_%s_%s", 
            alert.get("area"), 
            alert.get("category"), 
            alert.get("type")
        );
    }

    private boolean isHigherPriority(Map<String, Object> alert1, Map<String, Object> alert2) {
        String priority1 = (String) alert1.get("priority");
        String priority2 = (String) alert2.get("priority");
        
        return comparePriority(priority1, priority2) < 0; // < 0 means alert1 has higher priority
    }

    private int comparePriority(String priority1, String priority2) {
        Map<String, Integer> priorityOrder = Map.of(
            "CRITICAL", 0,
            "HIGH", 1,
            "MEDIUM", 2,
            "LOW", 3
        );
        
        int order1 = priorityOrder.getOrDefault(priority1, 3);
        int order2 = priorityOrder.getOrDefault(priority2, 3);
        
        return Integer.compare(order1, order2);
    }

    private long countCriticalAlerts(List<Map<String, Object>> alerts) {
        return alerts.stream()
            .mapToLong(alert -> "CRITICAL".equals(alert.get("priority")) ? 1 : 0)
            .sum();
    }

    private List<String> getAlertAreas(List<Map<String, Object>> alerts) {
        return alerts.stream()
            .map(alert -> (String) alert.get("area"))
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
    }

    private String generatePatternAdvice(CityEvent.EventCategory category, String area) {
        return switch (category) {
            case TRAFFIC -> String.format("Avoid %s area if possible. Consider alternative routes.", area);
            case EMERGENCY -> String.format("Stay away from %s. Follow official emergency guidelines.", area);
            case INFRASTRUCTURE -> String.format("Infrastructure issues in %s. Plan accordingly.", area);
            case WEATHER -> String.format("Weather conditions affecting %s. Take necessary precautions.", area);
            case SAFETY -> String.format("Safety concerns in %s. Exercise extra caution.", area);
            case CIVIC_ISSUE -> String.format("Multiple civic issues reported in %s. Contact local authorities.", area);
            default -> String.format("Multiple events in %s. Stay informed and plan accordingly.", area);
        };
    }

    private String generateSeverityAdvice(CityEvent event) {
        if (event.getCategory() == null) {
            return "Monitor the situation and follow official updates.";
        }
        
        return switch (event.getCategory()) {
            case EMERGENCY -> "Follow emergency protocols. Evacuate if instructed.";
            case TRAFFIC -> "Find alternative routes. Allow extra travel time.";
            case WEATHER -> "Take weather precautions. Stay indoors if necessary.";
            case SAFETY -> "Avoid the area. Report to authorities if necessary.";
            case INFRASTRUCTURE -> "Service disruptions possible. Plan alternatives.";
            default -> "Stay informed and follow official guidance.";
        };
    }
    
    /**
     * Check if an event is relevant to the request location
     */
    private boolean isEventRelevantToRequest(CityEvent event, AgentRequest request) {
        if (event.getLocation() == null) {
            return false;
        }
        
        if (request.getArea() != null) {
            String eventArea = event.getLocation().getArea();
            if (eventArea != null && eventArea.toLowerCase().contains(request.getArea().toLowerCase())) {
                return true;
            }
        }
        
        if (request.getLatitude() != null && request.getLongitude() != null) {
            double distance = calculateDistance(request.getLatitude(), request.getLongitude(), 
                                              event.getLocation().getLatitude(), event.getLocation().getLongitude());
            double radiusKm = request.getRadiusKm() != null ? request.getRadiusKm() : 15.0;
            return distance <= radiusKm;
        }
        
        return true;
    }
    
    /**
     * Calculate distance between two coordinates in kilometers
     */
    private double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        final int R = 6371; // Radius of the earth in km
        
        double latDistance = Math.toRadians(lat2 - lat1);
        double lngDistance = Math.toRadians(lng2 - lng1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lngDistance / 2) * Math.sin(lngDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return R * c;
    }
} 