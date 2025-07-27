package com.lemillion.city_data_overload_server.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Model representing a source location for data fetching.
 * Corresponds to documents in the 'source_locations' Firestore collection.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class SourceLocation {

    private String id;
    private String area;
    private Double lat;
    private Double lng;
    private String city;
    private String state;
    private String country;
    private String pincode;
    private String landmark;
    private Boolean active;
    private Integer priority; // 1=High, 2=Medium, 3=Low
    
    // User who registered this location
    private String userId;
    private String deviceId;
    
    // Registration metadata
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime registeredAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastFetchedAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastActiveAt;
    
    // Stats
    private Integer totalFetches;
    private Integer totalEvents;
    private Integer activeUsers;
    
    /**
     * Get formatted location string for SerpApi
     */
    public String getFormattedLocation() {
        StringBuilder location = new StringBuilder();
        
        if (area != null && !area.trim().isEmpty()) {
            location.append(area.trim());
        }
        
        if (city != null && !city.trim().isEmpty()) {
            if (location.length() > 0) location.append(", ");
            location.append(city.trim());
        }
        
        if (state != null && !state.trim().isEmpty()) {
            if (location.length() > 0) location.append(", ");
            location.append(state.trim());
        }
        
        if (country != null && !country.trim().isEmpty()) {
            if (location.length() > 0) location.append(", ");
            location.append(country.trim());
        }
        
        return location.toString();
    }
    
    /**
     * Get short location identifier for logging
     */
    public String getShortName() {
        if (area != null && !area.trim().isEmpty()) {
            return area.trim();
        } else if (city != null && !city.trim().isEmpty()) {
            return city.trim();
        }
        return "Unknown";
    }
    
    /**
     * Check if location is valid for data fetching
     */
    public boolean isValidForFetching() {
        return active != null && active && 
               lat != null && lng != null &&
               (area != null || city != null) &&
               getFormattedLocation().length() > 0;
    }
    
    /**
     * Get priority level for scheduling
     */
    public LocationPriority getPriorityLevel() {
        if (priority == null) return LocationPriority.MEDIUM;
        
        return switch (priority) {
            case 1 -> LocationPriority.HIGH;
            case 2 -> LocationPriority.MEDIUM;
            case 3 -> LocationPriority.LOW;
            default -> LocationPriority.MEDIUM;
        };
    }
    
    public enum LocationPriority {
        HIGH(1, 900000),    // 15 minutes
        MEDIUM(2, 1800000), // 30 minutes  
        LOW(3, 3600000);    // 60 minutes
        
        private final int value;
        private final long intervalMs;
        
        LocationPriority(int value, long intervalMs) {
            this.value = value;
            this.intervalMs = intervalMs;
        }
        
        public int getValue() { return value; }
        public long getIntervalMs() { return intervalMs; }
    }
} 