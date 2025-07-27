package com.lemillion.city_data_overload_server.service;

import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.Content;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.api.Part;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.cloud.vertexai.generativeai.ResponseHandler;
import com.lemillion.city_data_overload_server.model.CityEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class IntelligentSeverityService {
    
    private static final Logger log = LoggerFactory.getLogger(IntelligentSeverityService.class);
    
    private final VertexAI vertexAI;
    private final String textModelName;
    
    public IntelligentSeverityService(VertexAI vertexAI, 
                                     @Value("${gcp.vertex-ai.text-model-name}") String textModelName) {
        this.vertexAI = vertexAI;
        this.textModelName = textModelName;
    }
    
    /**
     * Intelligent severity prediction using AI semantic analysis.
     * This function can analyze ANY sentence and predict severity with high accuracy.
     * 
     * @param description The text description to analyze
     * @param category Optional category context (can be null)
     * @param location Optional location context (can be null)
     * @return CompletableFuture containing the predicted severity
     */
    public CompletableFuture<CityEvent.EventSeverity> predictSeverity(String description, String category, String location) {
        if (description == null || description.trim().isEmpty()) {
            return CompletableFuture.completedFuture(CityEvent.EventSeverity.LOW);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                GenerativeModel model = new GenerativeModel(textModelName, vertexAI);
                
                String prompt = buildSeverityAnalysisPrompt(description, category, location);
                
                Content content = Content.newBuilder()
                    .setRole("user")
                    .addParts(Part.newBuilder().setText(prompt).build())
                    .build();
                
                GenerateContentResponse response = model.generateContent(content);
                String severityResult = ResponseHandler.getText(response).trim().toUpperCase();
                
                // Parse AI response
                CityEvent.EventSeverity predictedSeverity = parseSeverityFromAI(severityResult);
                
                log.info("AI predicted severity '{}' for description: '{}'", 
                    predictedSeverity, description.substring(0, Math.min(50, description.length())));
                
                return predictedSeverity;
                
            } catch (Exception e) {
                log.error("AI severity prediction failed, using fallback analysis", e);
                return fallbackSeverityAnalysis(description);
            }
        });
    }
    
    /**
     * Synchronous version for immediate results (with fallback)
     */
    public CityEvent.EventSeverity predictSeveritySync(String description, String category, String location) {
        try {
            return predictSeverity(description, category, location).get();
        } catch (Exception e) {
            log.warn("Sync severity prediction failed, using fallback", e);
            return fallbackSeverityAnalysis(description);
        }
    }
    
    /**
     * Quick prediction for just description text
     */
    public CompletableFuture<CityEvent.EventSeverity> predictSeverity(String description) {
        return predictSeverity(description, null, null);
    }
    
    private String buildSeverityAnalysisPrompt(String description, String category, String location) {
        return String.format("""
            You are an expert emergency response AI for Bengaluru city. Analyze this report and determine its severity level.
            
            Report Description: "%s"
            Category: %s
            Location: %s
            
            SEVERITY LEVELS:
            
            🔴 CRITICAL: Immediate life-threatening situations requiring urgent response
            • Fatal accidents, major crashes with injuries/deaths
            • Building collapses, explosions, major fires
            • Major flooding, bridge collapses, landslides
            • Active emergencies with casualties or immediate danger
            • Terrorist incidents, violence, serious crimes
            
            🟠 HIGH: Significant incidents requiring prompt response  
            • Traffic accidents without fatalities but with injuries
            • Major road blockages, infrastructure failures
            • Serious safety hazards affecting many people
            • Significant utility outages, gas leaks
            • Minor fires, medical emergencies
            
            🟡 MODERATE: Issues needing attention but not urgent
            • Minor traffic disruptions, small accidents
            • Localized utility issues, minor breakdowns
            • Non-emergency civic problems, maintenance issues
            • Minor safety concerns, small-scale problems
            
            🟢 LOW: Minor issues or informational reports
            • Routine maintenance needs, general updates
            • Minor inconveniences, community information
            • Non-urgent civic feedback, suggestions
            
            ANALYSIS CRITERIA:
            1. Risk to human life and safety (highest priority)
            2. Severity of injuries or casualties 
            3. Number of people affected
            4. Infrastructure impact and accessibility
            5. Urgency of response needed
            6. Potential for escalation
            
            Analyze the SEMANTIC MEANING and CONTEXT. Consider words like:
            - "crash", "accident" with severity indicators
            - "injured", "hurt", "bleeding" vs "minor", "small"
            - "blocked", "stuck", "trapped" vs "slow", "delay"
            - "emergency", "urgent", "critical" vs "issue", "problem"
            
            Respond with EXACTLY ONE WORD: CRITICAL, HIGH, MODERATE, or LOW
            """, 
            description,
            category != null ? category : "Unknown", 
            location != null ? location : "Bengaluru"
        );
    }
    
    private CityEvent.EventSeverity parseSeverityFromAI(String aiResponse) {
        // Clean the response and try to extract severity
        String cleaned = aiResponse.replaceAll("[^A-Z]", "");
        
        if (cleaned.contains("CRITICAL")) return CityEvent.EventSeverity.CRITICAL;
        if (cleaned.contains("HIGH")) return CityEvent.EventSeverity.HIGH;
        if (cleaned.contains("MODERATE")) return CityEvent.EventSeverity.MODERATE;
        if (cleaned.contains("LOW")) return CityEvent.EventSeverity.LOW;
        
        // Try exact match
        try {
            return CityEvent.EventSeverity.valueOf(cleaned);
        } catch (IllegalArgumentException e) {
            log.warn("Could not parse AI severity response: '{}', defaulting to MODERATE", aiResponse);
            return CityEvent.EventSeverity.MODERATE;
        }
    }
    
    /**
     * Fallback analysis using pattern matching when AI fails
     */
    private CityEvent.EventSeverity fallbackSeverityAnalysis(String description) {
        if (description == null) return CityEvent.EventSeverity.LOW;
        
        String content = description.toLowerCase();
        
        // Critical patterns - immediate danger
        if (matchesPattern(content, "crash|fatal|death|died|killed|explosion|fire|collapse|emergency|bomb|violence|shooting")) {
            return CityEvent.EventSeverity.CRITICAL;
        }
        
        // High patterns - significant incidents
        if (matchesPattern(content, "accident|injured|hurt|blocked|breakdown|major|urgent|stuck|trapped|medical|ambulance")) {
            return CityEvent.EventSeverity.HIGH;
        }
        
        // Moderate patterns - attention needed
        if (matchesPattern(content, "slow|delayed|issue|problem|minor|disruption|outage|pothole|garbage")) {
            return CityEvent.EventSeverity.MODERATE;
        }
        
        return CityEvent.EventSeverity.LOW;
    }
    
    private boolean matchesPattern(String text, String pattern) {
        return text.matches(".*\\b(" + pattern + ")\\b.*");
    }
} 