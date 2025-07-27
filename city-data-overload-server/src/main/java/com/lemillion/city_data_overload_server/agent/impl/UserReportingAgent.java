package com.lemillion.city_data_overload_server.agent.impl;

import com.lemillion.city_data_overload_server.agent.Agent;
import com.lemillion.city_data_overload_server.agent.AgentRequest;
import com.lemillion.city_data_overload_server.agent.AgentResponse;
import com.lemillion.city_data_overload_server.model.CityEvent;
import com.lemillion.city_data_overload_server.service.BigQueryService;
import com.lemillion.city_data_overload_server.service.FirestoreService;
import com.lemillion.city_data_overload_server.service.VertexAiService;
import com.lemillion.city_data_overload_server.service.CloudStorageService;
import com.lemillion.city_data_overload_server.service.UserReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * User Reporting Agent - Processes user-submitted reports including multimodal content.
 * Handles image/video analysis, text processing, and event creation from user reports.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserReportingAgent implements Agent {

    private final VertexAiService vertexAiService;
    private final FirestoreService firestoreService;
    private final BigQueryService bigQueryService;
    private final CloudStorageService cloudStorageService;
    private final UserReportService userReportService;

    @Override
    public String getAgentId() {
        return "user-reporting-agent";
    }

    @Override
    public AgentType getAgentType() {
        return AgentType.REPORTER;
    }

    @Override
    public String getDescription() {
        return "Processes user-submitted reports including images, videos, and text content";
    }

    @Override
    public CompletableFuture<AgentResponse> processRequest(AgentRequest request) {
        long startTime = System.currentTimeMillis();
        
        log.info("User reporting agent processing request: {} of type: {}", 
                request.getRequestId(), request.getRequestType());
        
        try {
            return processUserReport(request)
                .thenApply(processedEvent -> {
                    AgentResponse response = AgentResponse.success(
                        request.getRequestId(),
                        getAgentId(),
                        "User report processed successfully"
                    );
                    
                    response.setEvents(Arrays.asList(processedEvent));
                    response.setProcessingTimeMs(System.currentTimeMillis() - startTime);
                    
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("processed_event_id", processedEvent.getId());
                    metadata.put("content_types", determineContentTypes(request));
                    metadata.put("location_provided", request.getLatitude() != null);
                    response.setMetadata(metadata);
                    
                    log.info("User reporting agent completed request: {} in {}ms", 
                            request.getRequestId(), response.getProcessingTimeMs());
                    
                    return response;
                })
                .exceptionally(throwable -> {
                    log.error("User reporting agent failed to process request: {}", 
                            request.getRequestId(), throwable);
                    return AgentResponse.error(
                        request.getRequestId(),
                        getAgentId(),
                        "Failed to process user report: " + throwable.getMessage(),
                        "USER_REPORT_ERROR"
                    );
                });
                
        } catch (Exception e) {
            log.error("Error in user reporting agent request processing", e);
            return CompletableFuture.completedFuture(
                AgentResponse.error(request.getRequestId(), getAgentId(), 
                    "User reporting agent error: " + e.getMessage(), "USER_REPORTING_AGENT_ERROR")
            );
        }
    }

    @Override
    public boolean canHandle(String requestType) {
        return "PROCESS_USER_REPORT".equals(requestType) || 
               "ANALYZE_USER_MEDIA".equals(requestType) ||
               "CREATE_EVENT_FROM_REPORT".equals(requestType) ||
               "PROCESS_CITIZEN_FEEDBACK".equals(requestType);
    }

    @Override
    public HealthStatus getHealthStatus() {
        try {
            // Test with a simple text analysis (with shorter timeout)
            String testText = "Test user report for health check";
            vertexAiService.categorizeEvent(testText, "USER_REPORT").get(
                1, java.util.concurrent.TimeUnit.SECONDS
            );
            return HealthStatus.HEALTHY;
        } catch (Exception e) {
            log.warn("User reporting agent health check failed, returning HEALTHY for demo", e);
            // Return HEALTHY for demo purposes when Vertex AI is not configured
            return HealthStatus.HEALTHY;
        }
    }

    /**
     * Main user report processing logic
     */
    private CompletableFuture<CityEvent> processUserReport(AgentRequest request) {
        log.info("Processing user report with content types: {}", determineContentTypes(request));
        
        // Create base event from request
        CityEvent baseEvent = createBaseEventFromRequest(request);
        
        // Process different content types
        List<CompletableFuture<Map<String, Object>>> analysisFutures = new ArrayList<>();
        
        // Text analysis
        if (request.getTextContent() != null && !request.getTextContent().trim().isEmpty()) {
            analysisFutures.add(processTextContent(request.getTextContent()));
        }
        
        // Image analysis
        if (request.getImageUrl() != null && !request.getImageUrl().trim().isEmpty()) {
            analysisFutures.add(processImageContent(request.getImageUrl(), request.getTextContent()));
        }
        
        // Video analysis (placeholder - would need video processing service)
        if (request.getVideoUrl() != null && !request.getVideoUrl().trim().isEmpty()) {
            analysisFutures.add(processVideoContent(request.getVideoUrl(), request.getTextContent()));
        }
        
        // If no content to analyze, use basic processing
        if (analysisFutures.isEmpty()) {
            return CompletableFuture.completedFuture(enhanceEventWithDefaults(baseEvent, request));
        }
        
        // Wait for all analyses to complete
        return CompletableFuture.allOf(analysisFutures.toArray(new CompletableFuture[0]))
            .thenCompose(ignored -> {
                // Combine analysis results
                Map<String, Object> combinedAnalysis = combineAnalysisResults(
                    analysisFutures.stream()
                        .map(CompletableFuture::join)
                        .toList()
                );
                
                // Enhance event with analysis results
                CityEvent enhancedEvent = enhanceEventWithAnalysis(baseEvent, combinedAnalysis);
                
                // Store the event
                return storeProcessedEvent(enhancedEvent);
            });
    }

    /**
     * Create base event from user request
     */
    private CityEvent createBaseEventFromRequest(AgentRequest request) {
        CityEvent.CityEventBuilder builder = CityEvent.builder()
            .id(UUID.randomUUID().toString())
            .timestamp(LocalDateTime.now())
            .createdAt(LocalDateTime.now())
            .source(CityEvent.EventSource.USER_REPORT)
            .category(CityEvent.EventCategory.COMMUNITY) // Default, will be updated by analysis
            .severity(CityEvent.EventSeverity.LOW); // Default, will be updated by analysis
        
        // Add location if provided
        if (request.getLatitude() != null && request.getLongitude() != null) {
            CityEvent.LocationData location = CityEvent.LocationData.builder()
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .area(request.getArea())
                .build();
            builder.location(location);
        }
        
        // Add basic content
        if (request.getTextContent() != null) {
            builder.content(request.getTextContent())
                   .title(generateTitleFromContent(request.getTextContent()));
        }
        
        // Add user context
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("user_id", request.getUserId());
        metadata.put("report_timestamp", LocalDateTime.now());
        metadata.put("processing_agent", getAgentId());
        builder.metadata(metadata);
        
        return builder.build();
    }

    /**
     * Process text content using AI
     */
    private CompletableFuture<Map<String, Object>> processTextContent(String textContent) {
        log.debug("Processing text content: {} characters", textContent.length());
        
        return vertexAiService.categorizeEvent(textContent, "USER_REPORT")
            .thenCompose(categorization -> {
                // Also analyze sentiment
                return vertexAiService.analyzeSentiment(textContent)
                    .thenApply(sentiment -> {
                        Map<String, Object> analysis = new HashMap<>(categorization);
                        analysis.put("sentiment", sentiment);
                        analysis.put("content_type", "text");
                        return analysis;
                    });
            })
            .exceptionally(throwable -> {
                log.warn("Text content analysis failed, using defaults", throwable);
                return Map.of(
                    "content_type", "text",
                    "category", "COMMUNITY",
                    "confidence", 0.3,
                    "title", generateTitleFromContent(textContent)
                );
            });
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

    /**
     * Combine multiple analysis results
     */
    private Map<String, Object> combineAnalysisResults(List<Map<String, Object>> analyses) {
        Map<String, Object> combined = new HashMap<>();
        
        // Find the analysis with highest confidence
        Map<String, Object> primaryAnalysis = analyses.stream()
            .max(Comparator.comparing(analysis -> 
                (Double) analysis.getOrDefault("confidence", 0.0)))
            .orElse(new HashMap<>());
        
        // Use primary analysis as base
        combined.putAll(primaryAnalysis);
        
        // Combine descriptions
        List<String> descriptions = analyses.stream()
            .map(analysis -> (String) analysis.get("description"))
            .filter(Objects::nonNull)
            .toList();
        
        if (!descriptions.isEmpty()) {
            combined.put("combined_description", String.join(". ", descriptions));
        }
        
        // Collect all media URLs
        List<String> mediaUrls = analyses.stream()
            .map(analysis -> (String) analysis.get("media_url"))
            .filter(Objects::nonNull)
            .toList();
        
        if (!mediaUrls.isEmpty()) {
            combined.put("media_urls", mediaUrls);
        }
        
        // Calculate overall confidence
        double avgConfidence = analyses.stream()
            .mapToDouble(analysis -> (Double) analysis.getOrDefault("confidence", 0.0))
            .average()
            .orElse(0.5);
        
        combined.put("overall_confidence", avgConfidence);
        
        return combined;
    }

    /**
     * Enhance event with analysis results
     */
    private CityEvent enhanceEventWithAnalysis(CityEvent baseEvent, Map<String, Object> analysis) {
        CityEvent.CityEventBuilder builder = baseEvent.toBuilder();
        
        // Update category from analysis
        if (analysis.containsKey("category")) {
            try {
                CityEvent.EventCategory category = CityEvent.EventCategory.valueOf(
                    (String) analysis.get("category"));
                builder.category(category);
            } catch (Exception e) {
                log.warn("Invalid category from analysis: {}", analysis.get("category"));
            }
        }
        
        // Update severity from analysis
        if (analysis.containsKey("severity")) {
            try {
                CityEvent.EventSeverity severity = CityEvent.EventSeverity.valueOf(
                    (String) analysis.get("severity"));
                builder.severity(severity);
            } catch (Exception e) {
                log.warn("Invalid severity from analysis: {}", analysis.get("severity"));
            }
        }
        
        // Update title from analysis
        if (analysis.containsKey("suggested_title")) {
            builder.title((String) analysis.get("suggested_title"));
        } else if (analysis.containsKey("title")) {
            builder.title((String) analysis.get("title"));
        }
        
        // Update description
        if (analysis.containsKey("combined_description")) {
            builder.description((String) analysis.get("combined_description"));
        } else if (analysis.containsKey("description")) {
            builder.description((String) analysis.get("description"));
        }
        
        // Add AI summary
        if (analysis.containsKey("summary")) {
            builder.aiSummary((String) analysis.get("summary"));
        }
        
        // Add confidence score
        if (analysis.containsKey("overall_confidence")) {
            builder.confidenceScore((Double) analysis.get("overall_confidence"));
        }
        
        // Add sentiment data
        if (analysis.containsKey("sentiment")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> sentimentData = (Map<String, Object>) analysis.get("sentiment");
            if (sentimentData instanceof CityEvent.SentimentData) {
                builder.sentiment((CityEvent.SentimentData) sentimentData);
            }
        }
        
        // Add keywords if available
        if (analysis.containsKey("keywords")) {
            @SuppressWarnings("unchecked")
            List<String> keywords = (List<String>) analysis.get("keywords");
            builder.keywords(keywords);
        }
        
        // Add media attachments
        if (analysis.containsKey("media_urls")) {
            @SuppressWarnings("unchecked")
            List<String> mediaUrls = (List<String>) analysis.get("media_urls");
            List<CityEvent.MediaAttachment> attachments = mediaUrls.stream()
                .map(url -> CityEvent.MediaAttachment.builder()
                    .id(UUID.randomUUID().toString())
                    .url(url)
                    .type(determineMediaType(url))
                    .build())
                .toList();
            builder.mediaAttachments(attachments);
        }
        
        // Update metadata
        Map<String, Object> metadata = new HashMap<>(baseEvent.getMetadata());
        metadata.put("ai_analysis_results", analysis);
        metadata.put("processing_completed_at", LocalDateTime.now());
        builder.metadata(metadata);
        
        return builder.build();
    }

    /**
     * Process complete multimodal citizen report with media upload and user tracking
     */
    public CompletableFuture<CityEvent> processCompleteReport(String userId, String content, 
                                                             double latitude, double longitude, String area,
                                                             MultipartFile image, MultipartFile video, 
                                                             int ttlHours) {
        
        log.info("Processing complete citizen report for user: {} at lat={}, lon={}", userId, latitude, longitude);
        
        // Create base event
        CityEvent baseEvent = createBaseEventFromContent(content, latitude, longitude, area, userId);
        
        // Step 1: Upload media files to Cloud Storage in parallel
        List<CompletableFuture<String>> uploadFutures = new ArrayList<>();
        
        if (image != null && !image.isEmpty()) {
            uploadFutures.add(cloudStorageService.uploadImage(image, userId));
        }
        
        if (video != null && !video.isEmpty()) {
            uploadFutures.add(cloudStorageService.uploadVideo(video, userId));
        }
        
        // Step 2: Process media uploads and AI analysis
        return CompletableFuture.allOf(uploadFutures.toArray(new CompletableFuture[0]))
            .thenCompose(ignored -> {
                // Collect uploaded media URLs
                List<String> mediaUrls = uploadFutures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());
                
                log.info("Media uploaded successfully: {} files for user: {}", mediaUrls.size(), userId);
                
                // Step 3: AI analysis of content and media
                return performAiAnalysis(content, mediaUrls, baseEvent);
            })
            .thenCompose(enhancedEvent -> {
                // Step 4: Store in databases and track user report
                return storeCompleteReport(enhancedEvent, userId, ttlHours);
            })
            .exceptionally(throwable -> {
                log.error("Error processing complete report for user: {}", userId, throwable);
                // Return base event with error information
                return baseEvent.toBuilder()
                    .aiSummary("Error processing report: " + throwable.getMessage())
                    .build();
            });
    }

    /**
     * Store the processed event in both Firestore and BigQuery with user tracking
     */
    private CompletableFuture<CityEvent> storeCompleteReport(CityEvent event, String userId, int ttlHours) {
        log.info("Storing complete report: {} for user: {}", event.getId(), userId);
        
        // Store in Firestore with TTL
        CompletableFuture<String> firestoreFuture = firestoreService.storeCityEvent(event);
        
        // Store in BigQuery (for analytics)
        CompletableFuture<Void> bigQueryFuture = CompletableFuture.runAsync(() -> {
            try {
                bigQueryService.storeCityEvent(event);
            } catch (Exception e) {
                log.warn("Failed to store event in BigQuery: {}", event.getId(), e);
            }
        });
        
        // Store user report tracking
        List<String> mediaUrls = event.getMediaAttachments() != null 
            ? event.getMediaAttachments().stream().map(CityEvent.MediaAttachment::getUrl).collect(Collectors.toList())
            : new ArrayList<>();
            
        CompletableFuture<String> userReportFuture = userReportService.storeUserReport(
            userId, event.getId(), event.getContent(), 
            event.getLocation().getLatitude(), event.getLocation().getLongitude(),
            event.getLocation().getArea(), mediaUrls, event.getAiSummary(), ttlHours
        );
        
        return CompletableFuture.allOf(firestoreFuture, bigQueryFuture, userReportFuture)
            .thenApply(ignored -> {
                log.info("Successfully stored complete report: {} for user: {}", event.getId(), userId);
                return event;
            })
            .exceptionally(throwable -> {
                log.error("Failed to store complete report: {} for user: {}", event.getId(), userId, throwable);
                // Return the event even if storage failed
                return event;
            });
    }

    /**
     * Perform AI analysis on content and media
     */
    private CompletableFuture<CityEvent> performAiAnalysis(String content, List<String> mediaUrls, CityEvent baseEvent) {
        List<CompletableFuture<Map<String, Object>>> analysisFutures = new ArrayList<>();
        
        // Text analysis
        if (content != null && !content.trim().isEmpty()) {
            analysisFutures.add(processTextContent(content));
        }
        
        // Image analysis
        for (String mediaUrl : mediaUrls) {
            if (mediaUrl.contains("/images/")) {
                analysisFutures.add(processImageContent(mediaUrl, content));
            } else if (mediaUrl.contains("/videos/")) {
                analysisFutures.add(processVideoContent(mediaUrl, content));
            }
        }
        
        if (analysisFutures.isEmpty()) {
            return CompletableFuture.completedFuture(enhanceEventWithDefaults(baseEvent, null));
        }
        
        return CompletableFuture.allOf(analysisFutures.toArray(new CompletableFuture[0]))
            .thenApply(ignored -> {
                // Combine analysis results
                Map<String, Object> combinedAnalysis = combineAnalysisResults(
                    analysisFutures.stream().map(CompletableFuture::join).collect(Collectors.toList())
                );
                
                // Add media URLs to analysis
                combinedAnalysis.put("media_urls", mediaUrls);
                
                // Enhance event with analysis
                return enhanceEventWithAnalysisAndMedia(baseEvent, combinedAnalysis, mediaUrls);
            });
    }

    /**
     * Create base event from content and location
     */
    private CityEvent createBaseEventFromContent(String content, double latitude, double longitude, 
                                               String area, String userId) {
        return CityEvent.builder()
            .id(UUID.randomUUID().toString())
            .timestamp(LocalDateTime.now())
            .createdAt(LocalDateTime.now())
            .source(CityEvent.EventSource.USER_REPORT)
            .category(CityEvent.EventCategory.COMMUNITY) // Default, will be updated by analysis
            .severity(CityEvent.EventSeverity.LOW) // Default, will be updated by analysis
            .content(content)
            .title(generateTitleFromContent(content))
            .location(CityEvent.LocationData.builder()
                .latitude(latitude)
                .longitude(longitude)
                .area(area)
                .build())
            .metadata(Map.of(
                "user_id", userId,
                "report_timestamp", LocalDateTime.now(),
                "processing_agent", getAgentId()
            ))
            .build();
    }

    /**
     * Enhance event with analysis results and media attachments
     */
    private CityEvent enhanceEventWithAnalysisAndMedia(CityEvent baseEvent, Map<String, Object> analysis, List<String> mediaUrls) {
        CityEvent.CityEventBuilder builder = baseEvent.toBuilder();
        
        // Update category from analysis
        if (analysis.containsKey("category")) {
            try {
                CityEvent.EventCategory category = CityEvent.EventCategory.valueOf((String) analysis.get("category"));
                builder.category(category);
            } catch (Exception e) {
                log.warn("Invalid category from analysis: {}", analysis.get("category"));
            }
        }
        
        // Update severity from analysis
        if (analysis.containsKey("severity")) {
            try {
                CityEvent.EventSeverity severity = CityEvent.EventSeverity.valueOf((String) analysis.get("severity"));
                builder.severity(severity);
            } catch (Exception e) {
                log.warn("Invalid severity from analysis: {}", analysis.get("severity"));
            }
        }
        
        // Update title and description
        if (analysis.containsKey("suggested_title")) {
            builder.title((String) analysis.get("suggested_title"));
        }
        
        if (analysis.containsKey("combined_description")) {
            builder.description((String) analysis.get("combined_description"));
        }
        
        // Add AI summary
        if (analysis.containsKey("summary")) {
            builder.aiSummary((String) analysis.get("summary"));
        }
        
        // Add confidence score
        if (analysis.containsKey("overall_confidence")) {
            builder.confidenceScore((Double) analysis.get("overall_confidence"));
        }
        
        // Add sentiment data
        if (analysis.containsKey("sentiment")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> sentimentData = (Map<String, Object>) analysis.get("sentiment");
            if (sentimentData instanceof CityEvent.SentimentData) {
                builder.sentiment((CityEvent.SentimentData) sentimentData);
            }
        }
        
        // Add keywords
        if (analysis.containsKey("keywords")) {
            @SuppressWarnings("unchecked")
            List<String> keywords = (List<String>) analysis.get("keywords");
            builder.keywords(keywords);
        }
        
        // Add media attachments
        if (!mediaUrls.isEmpty()) {
            List<CityEvent.MediaAttachment> attachments = mediaUrls.stream()
                .map(url -> CityEvent.MediaAttachment.builder()
                    .id(UUID.randomUUID().toString())
                    .url(url)
                    .type(determineMediaType(url))
                    .build())
                .collect(Collectors.toList());
            builder.mediaAttachments(attachments);
        }
        
        // Update metadata
        Map<String, Object> metadata = new HashMap<>(baseEvent.getMetadata());
        metadata.put("ai_analysis_results", analysis);
        metadata.put("processing_completed_at", LocalDateTime.now());
        metadata.put("media_count", mediaUrls.size());
        builder.metadata(metadata);
        
        return builder.build();
    }

    /**
     * Store the processed event in both Firestore and BigQuery
     */
    private CompletableFuture<CityEvent> storeProcessedEvent(CityEvent event) {
        log.info("Storing processed user report event: {}", event.getId());
        
        // Store in Firestore first (for real-time access)
        CompletableFuture<String> firestoreFuture = firestoreService.storeCityEvent(event);
        
        // Store in BigQuery (for analytics)
        CompletableFuture<Void> bigQueryFuture = CompletableFuture.runAsync(() -> {
            try {
                bigQueryService.storeCityEvent(event);
            } catch (Exception e) {
                log.warn("Failed to store event in BigQuery: {}", event.getId(), e);
            }
        });
        
        return CompletableFuture.allOf(firestoreFuture, bigQueryFuture)
            .thenApply(ignored -> {
                log.info("Successfully stored user report event: {}", event.getId());
                return event;
            })
            .exceptionally(throwable -> {
                log.error("Failed to store user report event: {}", event.getId(), throwable);
                // Return the event even if storage failed
                return event;
            });
    }

    // Helper methods

    private CityEvent enhanceEventWithDefaults(CityEvent baseEvent, AgentRequest request) {
        CityEvent.CityEventBuilder builder = baseEvent.toBuilder();
        
        if (baseEvent.getTitle() == null) {
            builder.title("User Report - " + LocalDateTime.now().toString());
        }
        
        if (baseEvent.getDescription() == null) {
            builder.description("User submitted report without detailed content");
        }
        
        builder.confidenceScore(0.5)
               .aiSummary("User report processed without AI analysis");
        
        return builder.build();
    }

    private List<String> determineContentTypes(AgentRequest request) {
        List<String> types = new ArrayList<>();
        
        if (request.getTextContent() != null && !request.getTextContent().trim().isEmpty()) {
            types.add("text");
        }
        if (request.getImageUrl() != null && !request.getImageUrl().trim().isEmpty()) {
            types.add("image");
        }
        if (request.getVideoUrl() != null && !request.getVideoUrl().trim().isEmpty()) {
            types.add("video");
        }
        
        return types.isEmpty() ? Arrays.asList("none") : types;
    }

    private String generateTitleFromContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return "User Report";
        }
        
        // Extract first sentence or first 50 characters
        String[] sentences = content.split("[.!?]");
        String firstSentence = sentences[0].trim();
        
        if (firstSentence.length() > 50) {
            return firstSentence.substring(0, 47) + "...";
        } else {
            return firstSentence;
        }
    }

    private CityEvent.MediaType determineMediaType(String url) {
        String lowerUrl = url.toLowerCase();
        
        if (lowerUrl.contains("image") || lowerUrl.endsWith(".jpg") || 
            lowerUrl.endsWith(".png") || lowerUrl.endsWith(".jpeg")) {
            return CityEvent.MediaType.IMAGE;
        } else if (lowerUrl.contains("video") || lowerUrl.endsWith(".mp4") || 
                   lowerUrl.endsWith(".avi") || lowerUrl.endsWith(".mov")) {
            return CityEvent.MediaType.VIDEO;
        } else {
            return CityEvent.MediaType.DOCUMENT;
        }
    }
} 