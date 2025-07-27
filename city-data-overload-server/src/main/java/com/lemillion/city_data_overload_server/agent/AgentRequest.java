package com.lemillion.city_data_overload_server.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Request object passed to agents for processing
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRequest {
    
    private String requestId;
    private String requestType;
    private String userId;
    private LocalDateTime timestamp;
    
    // Location context
    private Double latitude;
    private Double longitude;
    private String area;
    private Double radiusKm;
    
    // Query parameters
    private Map<String, Object> parameters;
    private String query;
    private String context;
    
    // Filters
    private String category;
    private String severity;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer maxResults;
    
    // Media content
    private String imageUrl;
    private String videoUrl;
    private String textContent;
    
    // Metadata
    private Map<String, Object> metadata;
    private Integer priority;
    private Long timeoutMs;
} 