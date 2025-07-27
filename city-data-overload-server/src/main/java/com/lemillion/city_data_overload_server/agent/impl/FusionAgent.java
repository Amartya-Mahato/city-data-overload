package com.lemillion.city_data_overload_server.agent.impl;

import com.lemillion.city_data_overload_server.agent.Agent;
import com.lemillion.city_data_overload_server.agent.AgentRequest;
import com.lemillion.city_data_overload_server.agent.AgentResponse;
import com.lemillion.city_data_overload_server.model.CityEvent;
import com.lemillion.city_data_overload_server.service.VertexAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Fusion Agent - Synthesizes disparate data sources into coherent summaries.
 * Responsible for fusing multiple related events into single, actionable insights.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FusionAgent implements Agent {

    private final VertexAiService vertexAiService;

    @Override
    public String getAgentId() {
        return "fusion-agent";
    }

    @Override
    public AgentType getAgentType() {
        return AgentType.SYNTHESIZER;
    }

    @Override
    public String getDescription() {
        return "Synthesizes disparate data sources into coherent, actionable summaries";
    }

    @Override
    public CompletableFuture<AgentResponse> processRequest(AgentRequest request) {
        long startTime = System.currentTimeMillis();
        
        log.info("Fusion agent processing request: {} of type: {}", 
                request.getRequestId(), request.getRequestType());
        
        try {
            return fuseData(request)
                .thenApply(fusedContent -> {
                    AgentResponse response = AgentResponse.success(
                        request.getRequestId(),
                        getAgentId(),
                        "Data fusion completed successfully"
                    );
                    
                    response.setSynthesizedContent(fusedContent);
                    response.setProcessingTimeMs(System.currentTimeMillis() - startTime);
                    
                    log.info("Fusion agent completed request: {} in {}ms", 
                            request.getRequestId(), response.getProcessingTimeMs());
                    
                    return response;
                })
                .exceptionally(throwable -> {
                    log.error("Fusion agent failed to process request: {}", 
                            request.getRequestId(), throwable);
                    return AgentResponse.error(
                        request.getRequestId(),
                        getAgentId(),
                        "Failed to fuse data: " + throwable.getMessage(),
                        "FUSION_ERROR"
                    );
                });
                
        } catch (Exception e) {
            log.error("Error in fusion agent request processing", e);
            return CompletableFuture.completedFuture(
                AgentResponse.error(request.getRequestId(), getAgentId(), 
                    "Fusion agent error: " + e.getMessage(), "FUSION_AGENT_ERROR")
            );
        }
    }

    @Override
    public boolean canHandle(String requestType) {
        return "FUSE_DATA".equals(requestType) || 
               "SYNTHESIZE_EVENTS".equals(requestType) ||
               "MERGE_SIMILAR_EVENTS".equals(requestType) ||
               "CREATE_SUMMARY".equals(requestType);
    }

    @Override
    public HealthStatus getHealthStatus() {
        try {
            // Test AI service with a simple synthesis task
            List<CityEvent> testEvents = createTestEvents();
            vertexAiService.synthesizeEvents(testEvents, "Health check test").get(
                java.util.concurrent.TimeUnit.SECONDS.toMillis(5), 
                java.util.concurrent.TimeUnit.MILLISECONDS
            );
            return HealthStatus.HEALTHY;
        } catch (Exception e) {
            log.warn("Fusion agent health check failed", e);
            return HealthStatus.DEGRADED;
        }
    }

    /**
     * Main data fusion logic
     */
    private CompletableFuture<String> fuseData(AgentRequest request) {
        // Extract events from request parameters
        List<CityEvent> events = extractEventsFromRequest(request);
        
        if (events.isEmpty()) {
            return CompletableFuture.completedFuture("No events provided for fusion");
        }
        
        // Group similar events for synthesis
        Map<String, List<CityEvent>> groupedEvents = groupSimilarEvents(events);
        
        // Synthesize each group and combine results
        List<CompletableFuture<String>> synthesisFutures = groupedEvents.entrySet().stream()
            .map(entry -> synthesizeEventGroup(entry.getKey(), entry.getValue(), request))
            .collect(Collectors.toList());
        
        return CompletableFuture.allOf(synthesisFutures.toArray(new CompletableFuture[0]))
            .thenApply(ignored -> {
                List<String> synthesizedGroups = synthesisFutures.stream()
                    .map(CompletableFuture::join)
                    .filter(result -> result != null && !result.trim().isEmpty())
                    .collect(Collectors.toList());
                
                if (synthesizedGroups.isEmpty()) {
                    return "Unable to synthesize provided events";
                }
                
                // Combine all synthesized groups into final summary
                return combineSynthesizedGroups(synthesizedGroups, request);
            });
    }

    /**
     * Extract events from request parameters
     */
    @SuppressWarnings("unchecked")
    private List<CityEvent> extractEventsFromRequest(AgentRequest request) {
        List<CityEvent> events = new ArrayList<>();
        
        // Check if events are provided in parameters
        if (request.getParameters() != null && request.getParameters().containsKey("events")) {
            Object eventsObj = request.getParameters().get("events");
            if (eventsObj instanceof List) {
                List<?> eventsList = (List<?>) eventsObj;
                for (Object eventObj : eventsList) {
                    if (eventObj instanceof CityEvent) {
                        events.add((CityEvent) eventObj);
                    }
                }
            }
        }
        
        // If no events in parameters, create mock events from text content
        if (events.isEmpty() && request.getTextContent() != null) {
            events.addAll(createEventsFromText(request.getTextContent(), request));
        }
        
        return events;
    }

    /**
     * Group similar events for synthesis
     */
    private Map<String, List<CityEvent>> groupSimilarEvents(List<CityEvent> events) {
        Map<String, List<CityEvent>> groups = new HashMap<>();
        
        for (CityEvent event : events) {
            String groupKey = determineGroupKey(event);
            groups.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(event);
        }
        
        // Merge small groups into larger ones if they're related
        return mergeRelatedGroups(groups);
    }

    /**
     * Determine group key for event clustering
     */
    private String determineGroupKey(CityEvent event) {
        StringBuilder keyBuilder = new StringBuilder();
        
        // Group by category
        if (event.getCategory() != null) {
            keyBuilder.append(event.getCategory().name()).append("_");
        }
        
        // Group by area
        if (event.getLocation() != null && event.getLocation().getArea() != null) {
            keyBuilder.append(event.getLocation().getArea().replaceAll("\\s+", "_")).append("_");
        }
        
        // Group by severity for critical events
        if (event.getSeverity() == CityEvent.EventSeverity.CRITICAL || 
            event.getSeverity() == CityEvent.EventSeverity.HIGH) {
            keyBuilder.append(event.getSeverity().name()).append("_");
        }
        
        String key = keyBuilder.toString();
        return key.isEmpty() ? "GENERAL" : key.substring(0, key.length() - 1);
    }

    /**
     * Merge small related groups
     */
    private Map<String, List<CityEvent>> mergeRelatedGroups(Map<String, List<CityEvent>> groups) {
        Map<String, List<CityEvent>> mergedGroups = new HashMap<>();
        
        for (Map.Entry<String, List<CityEvent>> entry : groups.entrySet()) {
            String key = entry.getKey();
            List<CityEvent> events = entry.getValue();
            
            // If group is too small, try to merge with related groups
            if (events.size() < 2) {
                String mergeKey = findMergeableGroup(key, mergedGroups.keySet());
                if (mergeKey != null) {
                    mergedGroups.get(mergeKey).addAll(events);
                } else {
                    mergedGroups.put(key, new ArrayList<>(events));
                }
            } else {
                mergedGroups.put(key, new ArrayList<>(events));
            }
        }
        
        return mergedGroups;
    }

    /**
     * Find a group that can be merged with the current key
     */
    private String findMergeableGroup(String key, Set<String> existingKeys) {
        String[] keyParts = key.split("_");
        
        for (String existingKey : existingKeys) {
            String[] existingParts = existingKey.split("_");
            
            // Check if they share category or area
            for (String keyPart : keyParts) {
                for (String existingPart : existingParts) {
                    if (keyPart.equals(existingPart)) {
                        return existingKey;
                    }
                }
            }
        }
        
        return null;
    }

    /**
     * Synthesize a group of similar events
     */
    private CompletableFuture<String> synthesizeEventGroup(
            String groupKey, List<CityEvent> events, AgentRequest request) {
        
        if (events.size() == 1) {
            // Single event, just format it nicely
            return CompletableFuture.completedFuture(formatSingleEvent(events.get(0)));
        }
        
        // Multiple events, use AI synthesis
        String context = buildSynthesisContext(groupKey, request);
        return vertexAiService.synthesizeEvents(events, context)
            .exceptionally(throwable -> {
                log.warn("AI synthesis failed for group {}, using fallback", groupKey, throwable);
                return fallbackSynthesis(events, groupKey);
            });
    }

    /**
     * Build context for AI synthesis
     */
    private String buildSynthesisContext(String groupKey, AgentRequest request) {
        StringBuilder context = new StringBuilder();
        
        context.append("Synthesizing events for group: ").append(groupKey).append(". ");
        
        if (request.getArea() != null) {
            context.append("Focus area: ").append(request.getArea()).append(". ");
        }
        
        if (request.getQuery() != null) {
            context.append("User query: ").append(request.getQuery()).append(". ");
        }
        
        context.append("Provide actionable information for Bengaluru citizens.");
        
        return context.toString();
    }

    /**
     * Combine multiple synthesized groups into final summary
     */
    private String combineSynthesizedGroups(List<String> synthesizedGroups, AgentRequest request) {
        if (synthesizedGroups.size() == 1) {
            return synthesizedGroups.get(0);
        }
        
        StringBuilder combined = new StringBuilder();
        
        // Add header
        if (request.getArea() != null) {
            combined.append("## Summary for ").append(request.getArea()).append("\n\n");
        } else {
            combined.append("## City Events Summary\n\n");
        }
        
        // Add each synthesized group
        for (int i = 0; i < synthesizedGroups.size(); i++) {
            combined.append("**").append(i + 1).append(".** ");
            combined.append(synthesizedGroups.get(i));
            if (i < synthesizedGroups.size() - 1) {
                combined.append("\n\n");
            }
        }
        
        return combined.toString();
    }

    /**
     * Format a single event nicely
     */
    private String formatSingleEvent(CityEvent event) {
        StringBuilder formatted = new StringBuilder();
        
        if (event.getTitle() != null) {
            formatted.append("**").append(event.getTitle()).append("**\n");
        }
        
        if (event.getDescription() != null) {
            formatted.append(event.getDescription()).append(" ");
        }
        
        if (event.getLocation() != null && event.getLocation().getArea() != null) {
            formatted.append("Location: ").append(event.getLocation().getArea()).append(". ");
        }
        
        if (event.getSeverity() != null && event.getSeverity() != CityEvent.EventSeverity.LOW) {
            formatted.append("Severity: ").append(event.getSeverity().name()).append(". ");
        }
        
        return formatted.toString().trim();
    }

    /**
     * Fallback synthesis when AI fails
     */
    private String fallbackSynthesis(List<CityEvent> events, String groupKey) {
        StringBuilder fallback = new StringBuilder();
        
        // Group summary
        fallback.append("Multiple events reported");
        
        if (groupKey.contains("_")) {
            String[] parts = groupKey.split("_");
            fallback.append(" related to ").append(String.join(" and ", parts));
        }
        
        fallback.append(":\n");
        
        // List events
        for (int i = 0; i < Math.min(events.size(), 5); i++) {
            CityEvent event = events.get(i);
            fallback.append("• ");
            
            if (event.getTitle() != null) {
                fallback.append(event.getTitle());
            } else if (event.getDescription() != null) {
                fallback.append(event.getDescription().substring(0, 
                    Math.min(50, event.getDescription().length()))).append("...");
            }
            
            if (event.getLocation() != null && event.getLocation().getArea() != null) {
                fallback.append(" (").append(event.getLocation().getArea()).append(")");
            }
            
            fallback.append("\n");
        }
        
        if (events.size() > 5) {
            fallback.append("... and ").append(events.size() - 5).append(" more events\n");
        }
        
        return fallback.toString();
    }

    /**
     * Create events from text content for synthesis
     */
    private List<CityEvent> createEventsFromText(String textContent, AgentRequest request) {
        // Split text into potential events (simple approach)
        String[] parts = textContent.split("\n\n|\\.\\s{2,}");
        
        List<CityEvent> events = new ArrayList<>();
        
        for (String part : parts) {
            if (part.trim().length() > 20) { // Minimum content length
                CityEvent event = CityEvent.builder()
                    .id(UUID.randomUUID().toString())
                    .title("Event from text")
                    .description(part.trim())
                    .timestamp(java.time.LocalDateTime.now())
                    .category(CityEvent.EventCategory.COMMUNITY)
                    .severity(CityEvent.EventSeverity.LOW)
                    .source(CityEvent.EventSource.MANUAL)
                    .build();
                
                // Add location if provided in request
                if (request.getArea() != null) {
                    event.setLocation(CityEvent.LocationData.builder()
                        .area(request.getArea())
                        .build());
                }
                
                events.add(event);
            }
        }
        
        return events;
    }

    /**
     * Create test events for health check
     */
    private List<CityEvent> createTestEvents() {
        return Arrays.asList(
            CityEvent.builder()
                .id("test-1")
                .title("Test Event 1")
                .description("Health check test event")
                .category(CityEvent.EventCategory.COMMUNITY)
                .build(),
            CityEvent.builder()
                .id("test-2")
                .title("Test Event 2")
                .description("Another health check test event")
                .category(CityEvent.EventCategory.COMMUNITY)
                .build()
        );
    }
} 