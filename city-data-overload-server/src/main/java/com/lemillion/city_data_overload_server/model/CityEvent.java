package com.lemillion.city_data_overload_server.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Core model representing any city event in Bengaluru.
 * This unified model serves both BigQuery and Firestore operations.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class CityEvent {

    private String id;
    private String title;
    private String description;
    private String content;
    
    // Location data
    private LocationData location;
    
    // Temporal data
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expiresAt;
    
    // Categorization
    private EventCategory category;
    private EventSeverity severity;
    private EventSource source;
    
    // AI Analysis
    private SentimentData sentiment;
    private Double confidenceScore;
    private List<String> keywords;
    private String aiSummary;
    
    // Media attachments
    private List<MediaAttachment> mediaAttachments;
    
    // Social metrics
    private SocialMetrics socialMetrics;
    
    // Metadata
    private Map<String, Object> metadata;
    private String rawData;
    private Integer version;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    /**
     * Location data for the event
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationData {
        private Double latitude;
        private Double longitude;
        private String address;
        private String area;
        private String pincode;
        private String landmark;
        private Double radius; // in meters
    }

    /**
     * Sentiment analysis data
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SentimentData {
        private SentimentType type;
        private Double score; // -1.0 to 1.0
        private Double confidence;
    }

    /**
     * Media attachment data
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MediaAttachment {
        private String id;
        private MediaType type;
        private String url;
        private String storageUrl;
        private String description;
        private Map<String, Object> analysisResults;
    }

    /**
     * Social media metrics
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SocialMetrics {
        private Integer likes;
        private Integer shares;
        private Integer comments;
        private Integer retweets;
        private Double engagementRate;
        private Integer reach;
    }

    /**
     * Event categories
     */
    public enum EventCategory {
        TRAFFIC,
        CIVIC_ISSUE,
        CULTURAL_EVENT,
        EMERGENCY,
        INFRASTRUCTURE,
        WEATHER,
        PUBLIC_TRANSPORT,
        SAFETY,
        ENVIRONMENT,
        COMMUNITY,
        UTILITY,
        HEALTH,
        EDUCATION,
        POLICE,
        FIRE,
        OTHER
    }

    /**
     * Event severity levels
     */
    public enum EventSeverity {
        LOW,
        MODERATE,
        HIGH,
        CRITICAL
    }

    /**
     * Event sources
     */
    public enum EventSource {
        TWITTER,
        USER_REPORT,
        DATA_GOV_IN,
        OPENCITY,
        LINKEDIN,
        MANUAL,
        SYSTEM_GENERATED,
        OTHER,
        SERP,
        NEWS,
        SOCIAL_MEDIA,
        WEATHER_API,
        TRAFFIC_API,
        UTILITY_API,
        HEALTH_API,
        EDUCATION_API,
    }

    /**
     * Sentiment types
     */
    public enum SentimentType {
        POSITIVE,
        NEGATIVE,
        NEUTRAL,
        MIXED
    }

    /**
     * Media types
     */
    public enum MediaType {
        IMAGE,
        VIDEO,
        AUDIO,
        DOCUMENT
    }

    /**
     * Check if the event is expired based on current time
     */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * Check if the event is recent (within last 24 hours)
     */
    public boolean isRecent() {
        return timestamp != null && timestamp.isAfter(LocalDateTime.now().minusDays(7));
    }

    /**
     * Get distance from a given location (in meters)
     * Simple Haversine formula implementation
     */
    public Double getDistanceFrom(double lat, double lon) {
        if (location == null || location.latitude == null || location.longitude == null) {
            return null;
        }
        
        final int R = 6371000; // Earth's radius in meters
        double lat1Rad = Math.toRadians(location.latitude);
        double lat2Rad = Math.toRadians(lat);
        double deltaLatRad = Math.toRadians(lat - location.latitude);
        double deltaLonRad = Math.toRadians(lon - location.longitude);

        double a = Math.sin(deltaLatRad / 2) * Math.sin(deltaLatRad / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                Math.sin(deltaLonRad / 2) * Math.sin(deltaLonRad / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }
} 