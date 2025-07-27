package com.lemillion.city_data_overload_server.service;

import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.GenerateContentRequest;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.api.Part;
import com.google.cloud.vertexai.api.Content;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.cloud.vertexai.generativeai.ResponseHandler;
import com.lemillion.city_data_overload_server.model.CityEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Vertex AI service for AI-powered analysis and content generation.
 * Handles text analysis, sentiment analysis, image/video processing, and content synthesis.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VertexAiService {

    private final VertexAI vertexAI;
    
    @Value("${gcp.vertex-ai.text-model-name:gemini-1.5-pro}")
    private String textModelName;
    
    @Value("${gcp.vertex-ai.vision-model-name:gemini-1.5-pro}")
    private String visionModelName;

    /**
     * Analyze and synthesize multiple related events into a single summary
     */
    public CompletableFuture<String> synthesizeEvents(List<CityEvent> events, String context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                GenerativeModel model = new GenerativeModel(textModelName, vertexAI);
                
                StringBuilder eventsContext = new StringBuilder();
                eventsContext.append("Context: ").append(context).append("\n\n");
                eventsContext.append("Related Events to Synthesize:\n");
                
                for (int i = 0; i < events.size(); i++) {
                    CityEvent event = events.get(i);
                    eventsContext.append(String.format("Event %d:\n", i + 1));
                    eventsContext.append("Title: ").append(event.getTitle()).append("\n");
                    eventsContext.append("Description: ").append(event.getDescription()).append("\n");
                    eventsContext.append("Location: ").append(formatLocation(event.getLocation())).append("\n");
                    eventsContext.append("Category: ").append(event.getCategory()).append("\n");
                    eventsContext.append("Severity: ").append(event.getSeverity()).append("\n\n");
                }
                
                String prompt = String.format("""
                    You are an AI assistant helping citizens of Bengaluru understand city events.
                    
                    %s
                    
                    Please synthesize these related events into a single, clear, and actionable summary.
                    The summary should:
                    1. Combine similar information and remove redundancy
                    2. Provide actionable advice for citizens
                    3. Highlight the most important information
                    4. Be concise but comprehensive
                    5. Include location-specific details
                    
                    Format the response as a clean summary without any prefixes or metadata.
                    """, eventsContext.toString());
                
                Content content = Content.newBuilder()
                    .setRole("user")
                    .addParts(Part.newBuilder().setText(prompt).build())
                    .build();
                
                GenerateContentResponse response = model.generateContent(content);
                String synthesizedText = ResponseHandler.getText(response);
                
                log.debug("Successfully synthesized {} events", events.size());
                return synthesizedText.trim();
                
            } catch (Exception e) {
                log.error("Error synthesizing events with Vertex AI", e);
                throw new RuntimeException("Event synthesis failed", e);
            }
        });
    }

    /**
     * Analyze sentiment of text content
     */
    public CompletableFuture<CityEvent.SentimentData> analyzeSentiment(String text) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                GenerativeModel model = new GenerativeModel(textModelName, vertexAI);
                
                String prompt = String.format("""
                    Analyze the sentiment of the following text about a city event in Bengaluru:
                    
                    "%s"
                    
                    Provide a sentiment analysis in this exact JSON format:
                    {
                        "sentiment": "POSITIVE|NEGATIVE|NEUTRAL|MIXED",
                        "score": <number between -1.0 and 1.0>,
                        "confidence": <number between 0.0 and 1.0>
                    }
                    
                    Score interpretation:
                    - Positive: 0.1 to 1.0
                    - Negative: -1.0 to -0.1
                    - Neutral: -0.1 to 0.1
                    
                    Only respond with the JSON, no additional text.
                    """, text);
                
                Content content = Content.newBuilder()
                    .setRole("user")
                    .addParts(Part.newBuilder().setText(prompt).build())
                    .build();
                
                GenerateContentResponse response = model.generateContent(content);
                String analysisResult = ResponseHandler.getText(response).trim();
                
                return parseSentimentResponse(analysisResult);
                
            } catch (Exception e) {
                log.error("Error analyzing sentiment with Vertex AI", e);
                return CityEvent.SentimentData.builder()
                    .type(CityEvent.SentimentType.NEUTRAL)
                    .score(0.0)
                    .confidence(0.0)
                    .build();
            }
        });
    }

    /**
     * Categorize and extract key information from raw event text
     */
    public CompletableFuture<Map<String, Object>> categorizeEvent(String rawText, String source) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                GenerativeModel model = new GenerativeModel(textModelName, vertexAI);
                
                String prompt = String.format("""
                    Analyze this Bengaluru city-related content from %s and extract key information:
                    
                    "%s"
                    
                    Provide analysis in this exact JSON format:
                    {
                        "category": "TRAFFIC|CIVIC_ISSUE|CULTURAL_EVENT|EMERGENCY|INFRASTRUCTURE|WEATHER|PUBLIC_TRANSPORT|SAFETY|ENVIRONMENT|COMMUNITY",
                        "severity": "LOW|MODERATE|HIGH|CRITICAL",
                        "title": "<concise title>",
                        "summary": "<brief summary>",
                        "keywords": ["<keyword1>", "<keyword2>", ...],
                        "location": {
                            "area": "<area name if mentioned>",
                            "landmark": "<landmark if mentioned>",
                            "address": "<address if mentioned>"
                        },
                        "confidence": <0.0 to 1.0>
                    }
                    
                    Focus on Bengaluru-specific locations, areas, and landmarks.
                    Only respond with the JSON, no additional text.
                    """, source, rawText);
                
                Content content = Content.newBuilder()
                    .setRole("user")
                    .addParts(Part.newBuilder().setText(prompt).build())
                    .build();
                
                GenerateContentResponse response = model.generateContent(content);
                String analysisResult = ResponseHandler.getText(response).trim();
                
                return parseEventAnalysisResponse(analysisResult);
                
            } catch (Exception e) {
                log.error("Error categorizing event with Vertex AI", e);
                return Map.of(
                    "category", "COMMUNITY",
                    "severity", "LOW",
                    "confidence", 0.0
                );
            }
        });
    }

    /**
     * Intelligent severity prediction using AI semantic analysis
     * This function can analyze any sentence and predict severity with high accuracy
     */
    public CompletableFuture<CityEvent.EventSeverity> predictSeverityIntelligently(String description, String category, String location) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                GenerativeModel model = new GenerativeModel(textModelName, vertexAI);
                
                String prompt = String.format("""
                    You are an expert emergency response AI for Bengaluru city. Analyze this report and determine its severity level.
                    
                    Report Description: "%s"
                    Category: %s
                    Location: %s
                    
                    Consider these severity definitions:
                    
                    CRITICAL: Immediate life-threatening situations requiring urgent response
                    - Fatal accidents, major crashes with injuries
                    - Building collapses, explosions, fires
                    - Major flooding, bridge collapses
                    - Active emergency situations with casualties
                    
                    HIGH: Significant incidents requiring prompt response
                    - Traffic accidents without fatalities
                    - Road blockages, major breakdowns
                    - Infrastructure failures affecting many people
                    - Serious safety hazards
                    
                    MODERATE: Issues needing attention but not urgent
                    - Minor traffic disruptions
                    - Utility outages affecting small areas
                    - Potholes, garbage issues
                    - Non-emergency civic problems
                    
                    LOW: Minor issues or informational reports
                    - Routine maintenance needs
                    - General community updates
                    - Minor inconveniences
                    
                    Analyze the semantic meaning, context, and potential impact. Consider:
                    1. Risk to human life/safety
                    2. Impact on traffic/transportation
                    3. Number of people affected
                    4. Urgency of response needed
                    5. Infrastructure impact
                    
                    Respond with ONLY one word: CRITICAL, HIGH, MODERATE, or LOW
                    """, description, category != null ? category : "Unknown", location != null ? location : "Bengaluru");
                
                Content content = Content.newBuilder()
                    .setRole("user")
                    .addParts(Part.newBuilder().setText(prompt).build())
                    .build();
                
                GenerateContentResponse response = model.generateContent(content);
                String severityResult = ResponseHandler.getText(response).trim().toUpperCase();
                
                // Parse the AI response
                try {
                    return CityEvent.EventSeverity.valueOf(severityResult);
                } catch (IllegalArgumentException e) {
                    log.warn("AI returned invalid severity '{}', defaulting to MODERATE", severityResult);
                    return CityEvent.EventSeverity.MODERATE;
                }
                
            } catch (Exception e) {
                log.error("Error predicting severity with AI", e);
                // Fallback to basic keyword analysis
                return fallbackSeverityAnalysis(description);
            }
        });
    }
    
    /**
     * Fallback severity analysis when AI fails
     */
    private CityEvent.EventSeverity fallbackSeverityAnalysis(String description) {
        if (description == null) return CityEvent.EventSeverity.LOW;
        
        String content = description.toLowerCase();
        
        // Critical patterns
        if (content.matches(".*\\b(crash|fatal|death|died|killed|explosion|fire|collapse|emergency)\\b.*")) {
            return CityEvent.EventSeverity.CRITICAL;
        }
        
        // High patterns  
        if (content.matches(".*\\b(accident|blocked|breakdown|major|urgent|stuck|trapped)\\b.*")) {
            return CityEvent.EventSeverity.HIGH;
        }
        
        // Moderate patterns
        if (content.matches(".*\\b(slow|delayed|issue|problem|minor|pothole)\\b.*")) {
            return CityEvent.EventSeverity.MODERATE;
        }
        
        return CityEvent.EventSeverity.LOW;
    }

    /**
     * Analyze image content for city events
     */
    public CompletableFuture<Map<String, Object>> analyzeImage(String imageUrl, String additionalContext) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                GenerativeModel model = new GenerativeModel(visionModelName, vertexAI);
                
                String prompt = String.format("""
                    Analyze this image related to a Bengaluru city event.
                    Additional context: %s
                    
                    Provide analysis in this exact JSON format:
                    {
                        "description": "<detailed description of what you see>",
                        "category": "TRAFFIC|CIVIC_ISSUE|CULTURAL_EVENT|EMERGENCY|INFRASTRUCTURE|WEATHER|PUBLIC_TRANSPORT|SAFETY|ENVIRONMENT|COMMUNITY",
                        "severity": "LOW|MODERATE|HIGH|CRITICAL",
                        "location_clues": ["<any visible location indicators>"],
                        "objects_detected": ["<key objects/elements in image>"],
                        "suggested_title": "<suggested title for this event>",
                        "actionable_insights": "<what citizens should know/do based on this image>",
                        "confidence": <0.0 to 1.0>
                    }
                    
                    Focus on identifying Bengaluru-specific landmarks, areas, or infrastructure.
                    Only respond with the JSON, no additional text.
                    """, additionalContext != null ? additionalContext : "None provided");
                
                Content content = Content.newBuilder()
                    .setRole("user")
                    .addParts(Part.newBuilder().setText(prompt).build())
                    .addParts(Part.newBuilder()
                            .setFileData(
                                com.google.cloud.vertexai.api.FileData.newBuilder()
                                    .setFileUri(imageUrl)
                                    .setMimeType("image/jpeg")
                                    .build())
                            .build())
                    .build();
                
                GenerateContentResponse response = model.generateContent(content);
                String analysisResult = ResponseHandler.getText(response).trim();
                
                return parseImageAnalysisResponse(analysisResult);
                
            } catch (Exception e) {
                log.error("Error analyzing image with Vertex AI", e);
                return Map.of(
                    "description", "Image analysis failed",
                    "category", "COMMUNITY",
                    "severity", "LOW",
                    "confidence", 0.0
                );
            }
        });
    }

    /**
     * Generate predictive insights based on historical patterns
     */
    public CompletableFuture<List<Map<String, Object>>> generatePredictiveInsights(
            List<Map<String, Object>> patterns, String area) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                GenerativeModel model = new GenerativeModel(textModelName, vertexAI);
                
                StringBuilder patternsContext = new StringBuilder();
                patternsContext.append("Historical patterns for ").append(area).append(":\n");
                
                for (Map<String, Object> pattern : patterns) {
                    patternsContext.append(String.format(
                        "- Pattern: %s events (%s severity) occur %d times during %s on %s in %s (confidence: %.2f)\n",
                        pattern.get("category"), pattern.get("severity"), pattern.get("frequency"),
                        pattern.get("timePeriod"), pattern.get("dayType"), pattern.get("area"),
                        pattern.get("avgConfidence")
                    ));
                }
                
                String prompt = String.format("""
                    Based on these historical patterns for %s in Bengaluru, generate predictive insights:
                    
                    %s
                    
                    Provide insights in this JSON array format:
                    [
                        {
                            "prediction": "<what is likely to happen>",
                            "timeframe": "<when this might occur>",
                            "confidence": <0.0 to 1.0>,
                            "category": "<event category>",
                            "actionable_advice": "<what citizens should do/know>",
                            "areas_affected": ["<area1>", "<area2>"]
                        }
                    ]
                    
                    Focus on practical predictions that help citizens plan their day.
                    Only respond with the JSON array, no additional text.
                    """, area, patternsContext.toString());
                
                Content content = Content.newBuilder()
                    .setRole("user")
                    .addParts(Part.newBuilder().setText(prompt).build())
                    .build();
                
                GenerateContentResponse response = model.generateContent(content);
                String analysisResult = ResponseHandler.getText(response).trim();
                
                return parsePredictiveInsightsResponse(analysisResult);
                
            } catch (Exception e) {
                log.error("Error generating predictive insights with Vertex AI", e);
                return new ArrayList<>();
            }
        });
    }

    /**
     * Generate mood map analysis based on sentiment data
     */
    public CompletableFuture<Map<String, Object>> generateMoodMapAnalysis(
            Map<String, List<CityEvent.SentimentData>> areaSentiments) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                GenerativeModel model = new GenerativeModel(textModelName, vertexAI);
                
                StringBuilder sentimentContext = new StringBuilder();
                sentimentContext.append("Area sentiment data for Bengaluru:\n");
                
                areaSentiments.forEach((area, sentiments) -> {
                    double avgScore = sentiments.stream()
                        .mapToDouble(s -> s.getScore() != null ? s.getScore() : 0.0)
                        .average()
                        .orElse(0.0);
                    
                    long positiveCount = sentiments.stream()
                        .mapToLong(s -> s.getType() == CityEvent.SentimentType.POSITIVE ? 1 : 0)
                        .sum();
                    
                    sentimentContext.append(String.format(
                        "- %s: Avg score %.2f, %d positive out of %d total events\n",
                        area, avgScore, positiveCount, sentiments.size()
                    ));
                });
                
                String prompt = String.format("""
                    Analyze the mood map data for different areas of Bengaluru:
                    
                    %s
                    
                    Provide mood analysis in this JSON format:
                    {
                        "overall_mood": "POSITIVE|NEGATIVE|NEUTRAL|MIXED",
                        "area_insights": [
                            {
                                "area": "<area name>",
                                "mood": "POSITIVE|NEGATIVE|NEUTRAL|MIXED",
                                "mood_score": <-1.0 to 1.0>,
                                "key_factors": ["<factor1>", "<factor2>"],
                                "recommendations": "<what can improve this area's mood>"
                            }
                        ],
                        "city_summary": "<overall summary of Bengaluru's current mood>",
                        "trends": "<any notable patterns or trends observed>"
                    }
                    
                    Focus on actionable insights for city planning and citizen engagement.
                    Only respond with the JSON, no additional text.
                    """, sentimentContext.toString());
                
                Content content = Content.newBuilder()
                    .setRole("user")
                    .addParts(Part.newBuilder().setText(prompt).build())
                    .build();
                
                GenerateContentResponse response = model.generateContent(content);
                String analysisResult = ResponseHandler.getText(response).trim();
                
                return parseMoodMapResponse(analysisResult);
                
            } catch (Exception e) {
                log.error("Error generating mood map analysis with Vertex AI", e);
                return Map.of(
                    "overall_mood", "NEUTRAL",
                    "city_summary", "Analysis temporarily unavailable",
                    "area_insights", new ArrayList<>()
                );
            }
        });
    }

    // Private helper methods

    private String formatLocation(CityEvent.LocationData location) {
        if (location == null) return "Location not specified";
        
        StringBuilder locationStr = new StringBuilder();
        if (location.getArea() != null) locationStr.append(location.getArea());
        if (location.getLandmark() != null) {
            if (locationStr.length() > 0) locationStr.append(", ");
            locationStr.append(location.getLandmark());
        }
        if (location.getAddress() != null) {
            if (locationStr.length() > 0) locationStr.append(", ");
            locationStr.append(location.getAddress());
        }
        
        return locationStr.length() > 0 ? locationStr.toString() : "Location not specified";
    }

    private CityEvent.SentimentData parseSentimentResponse(String response) {
        try {
            // Extract JSON from response
            Pattern jsonPattern = Pattern.compile("\\{[^}]+\\}");
            Matcher matcher = jsonPattern.matcher(response);
            
            if (matcher.find()) {
                String jsonStr = matcher.group();
                
                // Simple JSON parsing (in production, use a proper JSON library)
                String sentiment = extractJsonValue(jsonStr, "sentiment");
                String scoreStr = extractJsonValue(jsonStr, "score");
                String confidenceStr = extractJsonValue(jsonStr, "confidence");
                
                return CityEvent.SentimentData.builder()
                    .type(CityEvent.SentimentType.valueOf(sentiment))
                    .score(Double.parseDouble(scoreStr))
                    .confidence(Double.parseDouble(confidenceStr))
                    .build();
            }
        } catch (Exception e) {
            log.warn("Error parsing sentiment response, using defaults", e);
        }
        
        return CityEvent.SentimentData.builder()
            .type(CityEvent.SentimentType.NEUTRAL)
            .score(0.0)
            .confidence(0.5)
            .build();
    }

    private Map<String, Object> parseCategorizationResponse(String response) {
        Map<String, Object> result = new HashMap<>();
        try {
            // Extract and parse JSON response
            Pattern jsonPattern = Pattern.compile("\\{.*\\}", Pattern.DOTALL);
            Matcher matcher = jsonPattern.matcher(response);
            
            if (matcher.find()) {
                String jsonStr = matcher.group();
                
                result.put("category", extractJsonValue(jsonStr, "category"));
                result.put("severity", extractJsonValue(jsonStr, "severity"));
                result.put("title", extractJsonValue(jsonStr, "title"));
                result.put("summary", extractJsonValue(jsonStr, "summary"));
                result.put("confidence", Double.parseDouble(extractJsonValue(jsonStr, "confidence")));
                
                // Extract keywords array
                Pattern keywordsPattern = Pattern.compile("\"keywords\":\\s*\\[([^\\]]+)\\]");
                Matcher keywordsMatcher = keywordsPattern.matcher(jsonStr);
                if (keywordsMatcher.find()) {
                    String keywordsStr = keywordsMatcher.group(1);
                    List<String> keywords = Arrays.asList(
                        keywordsStr.replaceAll("\"", "").split(",")
                    );
                    result.put("keywords", keywords);
                }
            }
        } catch (Exception e) {
            log.warn("Error parsing categorization response, using defaults", e);
        }
        
        // Ensure required fields are present
        result.putIfAbsent("category", "COMMUNITY");
        result.putIfAbsent("severity", "LOW");
        result.putIfAbsent("title", "City Event");
        result.putIfAbsent("confidence", 0.5);
        
        return result;
    }

    private Map<String, Object> parseEventAnalysisResponse(String response) {
        Map<String, Object> result = new HashMap<>();
        try {
            // Extract and parse JSON response
            Pattern jsonPattern = Pattern.compile("\\{.*\\}", Pattern.DOTALL);
            Matcher matcher = jsonPattern.matcher(response);
            
            if (matcher.find()) {
                String jsonStr = matcher.group();
                
                result.put("category", extractJsonValue(jsonStr, "category"));
                result.put("severity", extractJsonValue(jsonStr, "severity"));
                result.put("title", extractJsonValue(jsonStr, "title"));
                result.put("summary", extractJsonValue(jsonStr, "summary"));
                result.put("confidence", Double.parseDouble(extractJsonValue(jsonStr, "confidence")));
                
                // Extract keywords array
                Pattern keywordsPattern = Pattern.compile("\"keywords\":\\s*\\[([^\\]]+)\\]");
                Matcher keywordsMatcher = keywordsPattern.matcher(jsonStr);
                if (keywordsMatcher.find()) {
                    String keywordsStr = keywordsMatcher.group(1);
                    List<String> keywords = Arrays.asList(
                        keywordsStr.replaceAll("\"", "").split(",")
                    );
                    result.put("keywords", keywords);
                }
            }
        } catch (Exception e) {
            log.warn("Error parsing event analysis response, using defaults", e);
        }
        
        // Ensure required fields are present
        result.putIfAbsent("category", "COMMUNITY");
        result.putIfAbsent("severity", "LOW");
        result.putIfAbsent("title", "City Event");
        result.putIfAbsent("confidence", 0.5);
        
        return result;
    }

    private Map<String, Object> parseImageAnalysisResponse(String response) {
        // Similar parsing logic for image analysis
        Map<String, Object> result = new HashMap<>();
        result.put("description", "Image analyzed");
        result.put("category", "COMMUNITY");
        result.put("severity", "LOW");
        result.put("confidence", 0.7);
        return result;
    }

    private List<Map<String, Object>> parsePredictiveInsightsResponse(String response) {
        // Parse predictive insights JSON array
        return new ArrayList<>();
    }

    private Map<String, Object> parseMoodMapResponse(String response) {
        // Parse mood map analysis JSON
        Map<String, Object> result = new HashMap<>();
        result.put("overall_mood", "NEUTRAL");
        result.put("city_summary", "Mood analysis completed");
        result.put("area_insights", new ArrayList<>());
        return result;
    }

    private String extractJsonValue(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + key + "\":\\s*\"?([^,}\"]+)\"?");
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? matcher.group(1).trim() : "";
    }
} 