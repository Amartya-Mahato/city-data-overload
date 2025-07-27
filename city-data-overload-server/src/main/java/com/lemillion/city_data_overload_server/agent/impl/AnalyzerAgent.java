package com.lemillion.city_data_overload_server.agent.impl;

import com.lemillion.city_data_overload_server.agent.Agent;
import com.lemillion.city_data_overload_server.agent.AgentRequest;
import com.lemillion.city_data_overload_server.agent.AgentResponse;
import com.lemillion.city_data_overload_server.model.CityEvent;
import com.lemillion.city_data_overload_server.service.VertexAiService;
import com.lemillion.city_data_overload_server.service.FirestoreService;
import com.lemillion.city_data_overload_server.service.BigQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Analyzer Agent - Comprehensive data analysis and storage agent.
 * Analyzes data from aggregator and user submissions, fills missing parameters,
 * and saves to both Firestore and BigQuery.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyzerAgent implements Agent {

    private final VertexAiService vertexAiService;
    private final FirestoreService firestoreService;
    private final BigQueryService bigQueryService;

    private static final double MIN_CONFIDENCE_THRESHOLD = 0.3;
    private static final int MAX_PARALLEL_ANALYSIS = 10;

    @Override
    public String getAgentId() {
        return "analyzer-agent";
    }

    @Override
    public AgentType getAgentType() {
        return AgentType.ANALYZER;
    }

    @Override
    public String getDescription() {
        return "Comprehensive data analysis and storage agent that analyzes events from aggregator and user submissions, fills missing parameters, and saves to both Firestore and BigQuery";
    }

    @Override
    public boolean canHandle(String requestType) {
        return "ANALYZE_EVENTS".equals(requestType) || 
               "ANALYZE_USER_SUBMISSION".equals(requestType) ||
               "COMPREHENSIVE_ANALYSIS".equals(requestType);
    }

    @Override
    public HealthStatus getHealthStatus() {
        try {
            // Simple health check - could be enhanced with dependency checks
            return HealthStatus.HEALTHY;
        } catch (Exception e) {
            log.error("Health check failed", e);
            return HealthStatus.DEGRADED;
        }
    }

    @Override
    public CompletableFuture<AgentResponse> processRequest(AgentRequest request) {
        log.info("AnalyzerAgent processing request: {} with {} events", 
                request.getRequestId(), getEventCount(request));

        try {
            List<CityEvent> events = extractEventsFromRequest(request);
            
            if (events.isEmpty()) {
                return CompletableFuture.completedFuture(createEmptyResponse(request));
            }

            return analyzeAndStoreEvents(events, request)
                .thenApply(results -> createSuccessResponse(request, results))
                .exceptionally(throwable -> {
                    log.error("Error in AnalyzerAgent processing", throwable);
                    return createErrorResponse(request, throwable);
                });

        } catch (Exception e) {
            log.error("Error processing AnalyzerAgent request", e);
            return CompletableFuture.completedFuture(createErrorResponse(request, e));
        }
    }

    /**
     * Main analysis and storage pipeline
     */
    private CompletableFuture<List<Map<String, Object>>> analyzeAndStoreEvents(
            List<CityEvent> events, AgentRequest request) {
        
        log.info("Starting comprehensive analysis for {} events", events.size());

        // Process events in batches to avoid overwhelming the system
        List<List<CityEvent>> batches = createBatches(events, MAX_PARALLEL_ANALYSIS);
        
        List<CompletableFuture<List<Map<String, Object>>>> batchFutures = batches.stream()
            .map(batch -> processBatch(batch, request))
            .collect(Collectors.toList());

        return CompletableFuture.allOf(batchFutures.toArray(new CompletableFuture[0]))
            .thenApply(ignored -> batchFutures.stream()
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .collect(Collectors.toList()));
    }

    /**
     * Process a batch of events through the analysis pipeline
     */
    private CompletableFuture<List<Map<String, Object>>> processBatch(
            List<CityEvent> batch, AgentRequest request) {
        
        List<CompletableFuture<Map<String, Object>>> eventFutures = batch.stream()
            .map(event -> analyzeAndStoreEvent(event, request))
            .collect(Collectors.toList());

        return CompletableFuture.allOf(eventFutures.toArray(new CompletableFuture[0]))
            .thenApply(ignored -> eventFutures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList()));
    }

    /**
     * Comprehensive analysis and storage for a single event
     */
    private CompletableFuture<Map<String, Object>> analyzeAndStoreEvent(
            CityEvent event, AgentRequest request) {
        
        log.debug("Analyzing event: {}", event.getId());

        return enhanceEventWithAI(event, request)
            .thenCompose(enhancedEvent -> {
                return storeEventInBothSystems(enhancedEvent)
                    .thenApply(storageResults -> createEventResult(event, enhancedEvent, storageResults));
            })
            .exceptionally(throwable -> {
                log.error("Error analyzing event: {}", event.getId(), throwable);
                return createErrorResult(event, throwable);
            });
    }

    /**
     * Enhance event with comprehensive AI analysis to fill missing parameters
     */
    private CompletableFuture<CityEvent> enhanceEventWithAI(CityEvent event, AgentRequest request) {
        log.debug("AI enhancing event: {}", event.getId());

        List<CompletableFuture<Map<String, Object>>> analysisPromises = new ArrayList<>();

        // 1. Content analysis for missing fields using Vertex AI
        if (needsContentAnalysis(event)) {
            analysisPromises.add(analyzeEventContentWithAI(event));
        }

        // 2. Sentiment analysis using Vertex AI
        if (needsSentimentAnalysis(event)) {
            analysisPromises.add(analyzeSentimentWithAI(event));
        }

        // 3. Location analysis and enhancement
        if (needsLocationAnalysis(event)) {
            analysisPromises.add(analyzeLocationWithAI(event));
        }

        // 4. Severity and priority analysis using AI
        if (needsSeverityAnalysis(event)) {
            analysisPromises.add(analyzeSeverityWithAI(event));
        }

        // 5. Media analysis (for user-submitted content)
        if (hasMultimediaContent(event, request)) {
            analysisPromises.add(analyzeMultimediaWithAI(event, request));
        }

        // 6. Additional AI insights
        analysisPromises.add(generateAdditionalInsights(event));

        if (analysisPromises.isEmpty()) {
            return CompletableFuture.completedFuture(event);
        }

        return CompletableFuture.allOf(analysisPromises.toArray(new CompletableFuture[0]))
            .thenApply(ignored -> {
                List<Map<String, Object>> analyses = analysisPromises.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());
                
                return mergeAIAnalysesIntoEvent(event, analyses);
            });
    }

    /**
     * Analyze event content using Vertex AI categorization
     */
    private CompletableFuture<Map<String, Object>> analyzeEventContentWithAI(CityEvent event) {
        String content = buildAnalysisContent(event);
        String source = event.getSource() != null ? event.getSource().name() : "UNKNOWN";
        
        return vertexAiService.categorizeEvent(content, source)
            .thenApply(aiResult -> {
                Map<String, Object> analysis = new HashMap<>(aiResult);
                analysis.put("analysis_type", "content");
                analysis.put("ai_powered", true);
                return analysis;
            })
            .exceptionally(throwable -> {
                log.warn("AI content analysis failed for event: {}", event.getId(), throwable);
                return createFallbackContentAnalysis(event);
            });
    }

    /**
     * Analyze sentiment using Vertex AI
     */
    private CompletableFuture<Map<String, Object>> analyzeSentimentWithAI(CityEvent event) {
        String text = buildSentimentText(event);
        
        return vertexAiService.analyzeSentiment(text)
            .thenApply(sentimentData -> Map.of(
                "analysis_type", "sentiment",
                "sentiment", sentimentData,
                "ai_powered", true
            ))
            .exceptionally(throwable -> {
                log.warn("AI sentiment analysis failed for event: {}", event.getId(), throwable);
                return Map.of(
                    "analysis_type", "sentiment",
                    "sentiment", createDefaultSentiment(),
                    "ai_powered", false
                );
            });
    }

    /**
     * Analyze location with AI enhancement
     */
    private CompletableFuture<Map<String, Object>> analyzeLocationWithAI(CityEvent event) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> locationAnalysis = new HashMap<>();
            locationAnalysis.put("analysis_type", "location");

            try {
                CityEvent.LocationData currentLocation = event.getLocation();
                
                // If we have basic location info, enhance it with AI
                if (currentLocation != null) {
                    String locationContext = buildLocationContext(event);
                    
                    // Use AI to extract additional location information
                    return vertexAiService.categorizeEvent(locationContext, "LOCATION_ENHANCEMENT")
                        .thenApply(aiResult -> {
                            CityEvent.LocationData enhancedLocation = enhanceLocationWithAI(currentLocation, aiResult);
                            Map<String, Object> result = new HashMap<>();
                            result.put("analysis_type", "location");
                            result.put("enhanced_location", enhancedLocation);
                            result.put("ai_powered", true);
                            return result;
                        })
                        .exceptionally(throwable -> {
                            log.warn("AI location analysis failed for event: {}", event.getId(), throwable);
                            locationAnalysis.put("enhanced_location", currentLocation);
                            locationAnalysis.put("ai_powered", false);
                            return locationAnalysis;
                        })
                        .join();
                } else {
                    locationAnalysis.put("enhanced_location", createDefaultLocation());
                    locationAnalysis.put("ai_powered", false);
                }

            } catch (Exception e) {
                log.warn("Location analysis failed for event: {}", event.getId(), e);
                locationAnalysis.put("enhanced_location", event.getLocation());
                locationAnalysis.put("ai_powered", false);
            }

            return locationAnalysis;
        });
    }

    /**
     * Analyze severity using AI
     */
    private CompletableFuture<Map<String, Object>> analyzeSeverityWithAI(CityEvent event) {
        String severityContext = buildSeverityContext(event);
        
        return vertexAiService.categorizeEvent(severityContext, "SEVERITY_ANALYSIS")
            .thenApply(aiResult -> {
                Map<String, Object> severityAnalysis = new HashMap<>();
                severityAnalysis.put("analysis_type", "severity");
                
                // Extract severity from AI response
                String aiSeverity = (String) aiResult.getOrDefault("severity", "LOW");
                CityEvent.EventSeverity severity = parseAISeverity(aiSeverity);
                double confidence = (Double) aiResult.getOrDefault("confidence", 0.7);
                
                severityAnalysis.put("severity", severity);
                severityAnalysis.put("confidence", confidence);
                severityAnalysis.put("ai_powered", true);
                severityAnalysis.put("ai_reasoning", aiResult.getOrDefault("summary", "AI-determined severity"));
                
                return severityAnalysis;
            })
            .exceptionally(throwable -> {
                log.warn("AI severity analysis failed for event: {}", event.getId(), throwable);
                Map<String, Object> fallback = new HashMap<>();
                fallback.put("analysis_type", "severity");
                fallback.put("severity", determineSeverity(event));
                fallback.put("confidence", 0.5);
                fallback.put("ai_powered", false);
                return fallback;
            });
    }

    /**
     * Analyze multimedia content using Vertex AI
     */
    private CompletableFuture<Map<String, Object>> analyzeMultimediaWithAI(CityEvent event, AgentRequest request) {
        List<CompletableFuture<Map<String, Object>>> mediaAnalyses = new ArrayList<>();

        // Analyze images using Vertex AI
        if (request.getImageUrl() != null) {
            String imageContext = buildImageAnalysisContext(event);
            mediaAnalyses.add(vertexAiService.analyzeImage(request.getImageUrl(), imageContext)
                .thenApply(aiResult -> {
                    Map<String, Object> result = new HashMap<>(aiResult);
                    result.put("media_type", "image");
                    result.put("ai_powered", true);
                    return result;
                }));
        }

        // Video analysis (enhanced placeholder for future AI video analysis)
        if (request.getVideoUrl() != null) {
            mediaAnalyses.add(CompletableFuture.completedFuture(Map.of(
                "media_type", "video",
                "description", "Video analysis will be enhanced with AI in future versions",
                "confidence", 0.1,
                "ai_powered", false
            )));
        }

        if (mediaAnalyses.isEmpty()) {
            return CompletableFuture.completedFuture(Map.of("analysis_type", "multimedia", "ai_powered", false));
        }

        return CompletableFuture.allOf(mediaAnalyses.toArray(new CompletableFuture[0]))
            .thenApply(ignored -> {
                List<Map<String, Object>> results = mediaAnalyses.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());
                
                Map<String, Object> multimediaAnalysis = new HashMap<>();
                multimediaAnalysis.put("analysis_type", "multimedia");
                multimediaAnalysis.put("media_analyses", results);
                multimediaAnalysis.put("ai_powered", true);
                
                return multimediaAnalysis;
            });
    }

    /**
     * Generate additional AI insights about the event
     */
    private CompletableFuture<Map<String, Object>> generateAdditionalInsights(CityEvent event) {
        String insightContext = String.format(
            "Generate additional insights for this city event: %s in %s, category: %s",
            event.getTitle(),
            event.getLocation() != null ? event.getLocation().getArea() : "Bengaluru",
            event.getCategory()
        );

        return vertexAiService.categorizeEvent(insightContext, "INSIGHTS_GENERATION")
            .thenApply(aiResult -> {
                Map<String, Object> insights = new HashMap<>();
                insights.put("analysis_type", "additional_insights");
                insights.put("ai_keywords", aiResult.getOrDefault("keywords", List.of()));
                insights.put("ai_summary", aiResult.getOrDefault("summary", ""));
                insights.put("ai_confidence", aiResult.getOrDefault("confidence", 0.0));
                insights.put("ai_powered", true);
                return insights;
            })
            .exceptionally(throwable -> {
                log.debug("Additional insights generation failed for event: {}", event.getId());
                return Map.of(
                    "analysis_type", "additional_insights",
                    "ai_powered", false
                );
            });
    }

    /**
     * Merge all analyses into the event
     */
    private CityEvent mergeAnalysesIntoEvent(CityEvent originalEvent, List<Map<String, Object>> analyses) {
        CityEvent.CityEventBuilder builder = originalEvent.toBuilder();
        Map<String, Object> metadata = originalEvent.getMetadata() != null ? 
            new HashMap<>(originalEvent.getMetadata()) : new HashMap<>();

        for (Map<String, Object> analysis : analyses) {
            String analysisType = (String) analysis.get("analysis_type");
            
            switch (analysisType) {
                case "content" -> applyContentAnalysis(builder, analysis);
                case "sentiment" -> applySentimentAnalysis(builder, analysis);
                case "location" -> applyLocationAnalysis(builder, analysis);
                case "severity" -> applySeverityAnalysis(builder, analysis);
                case "multimedia" -> applyMultimediaAnalysis(builder, analysis, metadata);
            }
        }

        // Update metadata with analysis results
        metadata.put("ai_analysis_timestamp", LocalDateTime.now());
        metadata.put("analyzer_agent_version", "1.0");
        metadata.put("analysis_count", analyses.size());
        
        return builder
            .metadata(metadata)
            .updatedAt(LocalDateTime.now())
            .build();
    }

    /**
     * Merge all AI analyses into the event with enhanced metadata
     */
    private CityEvent mergeAIAnalysesIntoEvent(CityEvent originalEvent, List<Map<String, Object>> analyses) {
        CityEvent.CityEventBuilder builder = originalEvent.toBuilder();
        Map<String, Object> metadata = originalEvent.getMetadata() != null ? 
            new HashMap<>(originalEvent.getMetadata()) : new HashMap<>();

        // Track AI enhancements
        Map<String, Object> aiEnhancements = new HashMap<>();
        List<String> aiProcessedFields = new ArrayList<>();

        for (Map<String, Object> analysis : analyses) {
            String analysisType = (String) analysis.get("analysis_type");
            boolean aiPowered = (Boolean) analysis.getOrDefault("ai_powered", false);
            
            switch (analysisType) {
                case "content" -> {
                    applyContentAnalysis(builder, analysis);
                    if (aiPowered) aiProcessedFields.add("content");
                }
                case "sentiment" -> {
                    applySentimentAnalysis(builder, analysis);
                    if (aiPowered) aiProcessedFields.add("sentiment");
                }
                case "location" -> {
                    applyLocationAnalysis(builder, analysis);
                    if (aiPowered) aiProcessedFields.add("location");
                }
                case "severity" -> {
                    applySeverityAnalysis(builder, analysis);
                    if (aiPowered) aiProcessedFields.add("severity");
                }
                case "multimedia" -> {
                    applyMultimediaAnalysis(builder, analysis, metadata);
                    if (aiPowered) aiProcessedFields.add("multimedia");
                }
                case "additional_insights" -> {
                    if (aiPowered) {
                        aiEnhancements.put("ai_insights", analysis);
                        aiProcessedFields.add("insights");
                    }
                }
            }
        }

        // Enhanced metadata with AI processing information
        metadata.put("ai_analysis_timestamp", LocalDateTime.now());
        metadata.put("analyzer_agent_version", "2.0_ai_enhanced");
        metadata.put("analysis_count", analyses.size());
        metadata.put("ai_processed_fields", aiProcessedFields);
        metadata.put("ai_enhancements", aiEnhancements);
        metadata.put("vertex_ai_powered", !aiProcessedFields.isEmpty());
        
        return builder
            .metadata(metadata)
            .updatedAt(LocalDateTime.now())
            .build();
    }

    /**
     * Store event in both Firestore and BigQuery
     */
    private CompletableFuture<Map<String, Object>> storeEventInBothSystems(CityEvent event) {
        CompletableFuture<String> firestoreFuture = firestoreService.storeCityEvent(event);
        CompletableFuture<Void> bigQueryFuture = CompletableFuture.runAsync(() -> {
            try {
                bigQueryService.storeCityEvent(event);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        return CompletableFuture.allOf(firestoreFuture, bigQueryFuture)
            .thenApply(ignored -> {
                Map<String, Object> storageResults = new HashMap<>();
                
                try {
                    String firestoreId = firestoreFuture.join();
                    storageResults.put("firestore_success", true);
                    storageResults.put("firestore_id", firestoreId);
                } catch (Exception e) {
                    log.error("Firestore storage failed for event: {}", event.getId(), e);
                    storageResults.put("firestore_success", false);
                    storageResults.put("firestore_error", e.getMessage());
                }

                try {
                    bigQueryFuture.join();
                    storageResults.put("bigquery_success", true);
                } catch (Exception e) {
                    log.error("BigQuery storage failed for event: {}", event.getId(), e);
                    storageResults.put("bigquery_success", false);
                    storageResults.put("bigquery_error", e.getMessage());
                }

                return storageResults;
            });
    }

    // Helper methods for analysis conditions

    private boolean needsContentAnalysis(CityEvent event) {
        return event.getCategory() == null || 
               event.getTitle() == null || event.getTitle().isEmpty() ||
               event.getKeywords() == null || event.getKeywords().isEmpty();
    }

    private boolean needsSentimentAnalysis(CityEvent event) {
        return event.getSentiment() == null;
    }

    private boolean needsLocationAnalysis(CityEvent event) {
        return event.getLocation() == null || 
               (event.getLocation().getArea() == null && 
                event.getLocation().getAddress() == null);
    }

    private boolean needsSeverityAnalysis(CityEvent event) {
        return event.getSeverity() == null;
    }

    private boolean hasMultimediaContent(CityEvent event, AgentRequest request) {
        return request.getImageUrl() != null || request.getVideoUrl() != null;
    }

    // Helper methods for building analysis content

    private String buildAnalysisContent(CityEvent event) {
        StringBuilder content = new StringBuilder();
        if (event.getTitle() != null) content.append(event.getTitle()).append(" ");
        if (event.getDescription() != null) content.append(event.getDescription()).append(" ");
        if (event.getContent() != null) content.append(event.getContent());
        return content.toString().trim();
    }

    private String buildSentimentText(CityEvent event) {
        return buildAnalysisContent(event);
    }

    private String buildImageAnalysisContext(CityEvent event) {
        return String.format("Event: %s, Category: %s, Location: %s", 
            event.getTitle(),
            event.getCategory(),
            event.getLocation() != null ? event.getLocation().getArea() : "Unknown");
    }

    // Helper methods for applying analyses

    private void applyContentAnalysis(CityEvent.CityEventBuilder builder, Map<String, Object> analysis) {
        if (analysis.get("category") != null) {
            try {
                CityEvent.EventCategory category = CityEvent.EventCategory.valueOf(
                    analysis.get("category").toString());
                builder.category(category);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid category from analysis: {}", analysis.get("category"));
            }
        }

        if (analysis.get("title") != null) {
            builder.title(analysis.get("title").toString());
        }

        if (analysis.get("summary") != null) {
            builder.aiSummary(analysis.get("summary").toString());
        }

        if (analysis.get("keywords") instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> keywords = (List<String>) analysis.get("keywords");
            builder.keywords(keywords);
        }

        if (analysis.get("confidence") instanceof Number) {
            double confidence = ((Number) analysis.get("confidence")).doubleValue();
            builder.confidenceScore(confidence);
        }
    }

    private void applySentimentAnalysis(CityEvent.CityEventBuilder builder, Map<String, Object> analysis) {
        if (analysis.get("sentiment") instanceof CityEvent.SentimentData) {
            builder.sentiment((CityEvent.SentimentData) analysis.get("sentiment"));
        }
    }

    private void applyLocationAnalysis(CityEvent.CityEventBuilder builder, Map<String, Object> analysis) {
        if (analysis.get("enhanced_location") instanceof CityEvent.LocationData) {
            builder.location((CityEvent.LocationData) analysis.get("enhanced_location"));
        }
    }

    private void applySeverityAnalysis(CityEvent.CityEventBuilder builder, Map<String, Object> analysis) {
        if (analysis.get("severity") instanceof CityEvent.EventSeverity) {
            builder.severity((CityEvent.EventSeverity) analysis.get("severity"));
        }
    }

    private void applyMultimediaAnalysis(CityEvent.CityEventBuilder builder, 
                                       Map<String, Object> analysis, 
                                       Map<String, Object> metadata) {
        if (analysis.get("media_analyses") instanceof List) {
            metadata.put("multimedia_analysis", analysis.get("media_analyses"));
        }
    }

    // Fallback and default methods

    private Map<String, Object> createFallbackContentAnalysis(CityEvent event) {
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("category", event.getCategory() != null ? 
            event.getCategory() : CityEvent.EventCategory.COMMUNITY);
        fallback.put("confidence", 0.3);
        fallback.put("title", event.getTitle() != null ? event.getTitle() : "Untitled Event");
        fallback.put("keywords", List.of("bengaluru", "city", "event"));
        return fallback;
    }

    private CityEvent.SentimentData createDefaultSentiment() {
        return CityEvent.SentimentData.builder()
            .type(CityEvent.SentimentType.NEUTRAL)
            .score(0.0)
            .confidence(0.5)
            .build();
    }

    private CityEvent.LocationData enhanceLocationData(CityEvent.LocationData current, CityEvent event) {
        if (current == null) {
            return CityEvent.LocationData.builder()
                .area("Bengaluru")
                .build();
        }
        
        // Basic enhancement logic - could be extended with geocoding services
        CityEvent.LocationData.LocationDataBuilder builder = CityEvent.LocationData.builder()
            .latitude(current.getLatitude())
            .longitude(current.getLongitude())
            .address(current.getAddress())
            .area(current.getArea())
            .pincode(current.getPincode())
            .landmark(current.getLandmark());
        
        if (current.getArea() == null) {
            builder.area("Bengaluru"); // Default to Bengaluru
        }
        
        return builder.build();
    }

    private CityEvent.EventSeverity determineSeverity(CityEvent event) {
        // Severity determination logic based on content and category
        if (event.getCategory() == CityEvent.EventCategory.EMERGENCY) {
            return CityEvent.EventSeverity.CRITICAL;
        }
        
        String content = buildAnalysisContent(event).toLowerCase();
        if (content.contains("urgent") || content.contains("critical") || content.contains("emergency")) {
            return CityEvent.EventSeverity.HIGH;
        }
        
        if (content.contains("moderate") || content.contains("important")) {
            return CityEvent.EventSeverity.MODERATE;
        }
        
        return CityEvent.EventSeverity.LOW;
    }

    private double calculateSeverityConfidence(CityEvent event, CityEvent.EventSeverity severity) {
        // Simple confidence calculation - could be enhanced with ML models
        if (event.getCategory() == CityEvent.EventCategory.EMERGENCY && 
            severity == CityEvent.EventSeverity.CRITICAL) {
            return 0.9;
        }
        return 0.7;
    }

    // Utility methods

    private List<List<CityEvent>> createBatches(List<CityEvent> events, int batchSize) {
        List<List<CityEvent>> batches = new ArrayList<>();
        for (int i = 0; i < events.size(); i += batchSize) {
            batches.add(events.subList(i, Math.min(i + batchSize, events.size())));
        }
        return batches;
    }

    @SuppressWarnings("unchecked")
    private List<CityEvent> extractEventsFromRequest(AgentRequest request) {
        // Events can be passed through parameters
        if (request.getParameters() != null && request.getParameters().containsKey("events")) {
            Object eventsObj = request.getParameters().get("events");
            if (eventsObj instanceof List) {
                try {
                    return (List<CityEvent>) eventsObj;
                } catch (ClassCastException e) {
                    log.warn("Events parameter is not a List<CityEvent>", e);
                }
            }
        }
        return new ArrayList<>();
    }

    private int getEventCount(AgentRequest request) {
        return extractEventsFromRequest(request).size();
    }

    private Map<String, Object> createEventResult(CityEvent original, CityEvent enhanced, 
                                                 Map<String, Object> storageResults) {
        Map<String, Object> result = new HashMap<>();
        result.put("event_id", enhanced.getId());
        result.put("analysis_success", true);
        result.put("storage_results", storageResults);
        result.put("enhanced_fields", countEnhancedFields(original, enhanced));
        return result;
    }

    private Map<String, Object> createErrorResult(CityEvent event, Throwable error) {
        Map<String, Object> result = new HashMap<>();
        result.put("event_id", event.getId());
        result.put("analysis_success", false);
        result.put("error", error.getMessage());
        return result;
    }

    private int countEnhancedFields(CityEvent original, CityEvent enhanced) {
        int count = 0;
        
        if (original.getCategory() == null && enhanced.getCategory() != null) count++;
        if (original.getSentiment() == null && enhanced.getSentiment() != null) count++;
        if (original.getSeverity() == null && enhanced.getSeverity() != null) count++;
        if ((original.getKeywords() == null || original.getKeywords().isEmpty()) && 
            enhanced.getKeywords() != null && !enhanced.getKeywords().isEmpty()) count++;
        
        return count;
    }

    // Response methods

    private AgentResponse createEmptyResponse(AgentRequest request) {
        return AgentResponse.builder()
            .requestId(request.getRequestId())
            .agentId(getAgentId())
            .success(true)
            .analysisResults(Map.of("analyzed_events", List.of()))
            .message("No events to analyze")
            .timestamp(LocalDateTime.now())
            .processingTimeMs(0L)
            .build();
    }

    private AgentResponse createSuccessResponse(AgentRequest request, List<Map<String, Object>> results) {
        long successCount = results.stream()
            .mapToLong(result -> (Boolean) result.getOrDefault("analysis_success", false) ? 1 : 0)
            .sum();

        return AgentResponse.builder()
            .requestId(request.getRequestId())
            .agentId(getAgentId())
            .success(true)
            .analysisResults(Map.of(
                "analyzed_events", results,
                "total_events", results.size(),
                "successful_analyses", successCount,
                "success_rate", results.isEmpty() ? 0.0 : (double) successCount / results.size()
            ))
            .message(String.format("Successfully analyzed %d/%d events", successCount, results.size()))
            .timestamp(LocalDateTime.now())
            .processingTimeMs(System.currentTimeMillis())
            .build();
    }

    private AgentResponse createErrorResponse(AgentRequest request, Throwable throwable) {
        return AgentResponse.builder()
            .requestId(request.getRequestId())
            .agentId(getAgentId())
            .success(false)
            .message("Error during event analysis: " + throwable.getMessage())
            .timestamp(LocalDateTime.now())
            .processingTimeMs(System.currentTimeMillis())
            .build();
    }

    // Helper methods for AI analysis

    private String buildLocationContext(CityEvent event) {
        StringBuilder context = new StringBuilder();
        context.append("Extract and enhance location information from this event: ");
        if (event.getTitle() != null) context.append(event.getTitle()).append(" ");
        if (event.getDescription() != null) context.append(event.getDescription()).append(" ");
        if (event.getLocation() != null && event.getLocation().getArea() != null) {
            context.append("Current area: ").append(event.getLocation().getArea());
        }
        return context.toString();
    }

    private String buildSeverityContext(CityEvent event) {
        StringBuilder context = new StringBuilder();
        context.append("Determine the severity level for this city event: ");
        if (event.getTitle() != null) context.append(event.getTitle()).append(" ");
        if (event.getDescription() != null) context.append(event.getDescription()).append(" ");
        context.append("Category: ").append(event.getCategory());
        return context.toString();
    }

    private CityEvent.EventSeverity parseAISeverity(String aiSeverity) {
        try {
            return CityEvent.EventSeverity.valueOf(aiSeverity.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid AI severity: {}, defaulting to LOW", aiSeverity);
            return CityEvent.EventSeverity.LOW;
        }
    }

    private CityEvent.LocationData enhanceLocationWithAI(CityEvent.LocationData current, Map<String, Object> aiResult) {
        CityEvent.LocationData.LocationDataBuilder builder = CityEvent.LocationData.builder()
            .latitude(current.getLatitude())
            .longitude(current.getLongitude())
            .address(current.getAddress())
            .area(current.getArea())
            .pincode(current.getPincode())
            .landmark(current.getLandmark());

        // Enhance with AI-extracted information
        if (aiResult.containsKey("location")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> aiLocation = (Map<String, Object>) aiResult.get("location");
            
            if (current.getArea() == null && aiLocation.get("area") != null) {
                builder.area((String) aiLocation.get("area"));
            }
            if (current.getLandmark() == null && aiLocation.get("landmark") != null) {
                builder.landmark((String) aiLocation.get("landmark"));
            }
            if (current.getAddress() == null && aiLocation.get("address") != null) {
                builder.address((String) aiLocation.get("address"));
            }
        }

        return builder.build();
    }

    private CityEvent.LocationData createDefaultLocation() {
        return CityEvent.LocationData.builder()
            .area("Bengaluru")
            .build();
    }
} 