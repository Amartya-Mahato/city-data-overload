package com.lemillion.city_data_overload_server.agent;

import com.lemillion.city_data_overload_server.model.CityEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Response object returned by agents after processing
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentResponse {
    
    private String requestId;
    private String agentId;
    private boolean success;
    private String message;
    private LocalDateTime timestamp;
    private Long processingTimeMs;
    
    // Response data
    private List<CityEvent> events;
    private String synthesizedContent;
    private Map<String, Object> analysisResults;
    private List<Map<String, Object>> predictions;
    private Map<String, Object> sentimentData;
    private List<Map<String, Object>> alerts;
    
    // Metadata
    private Map<String, Object> metadata;
    private Double confidence;
    private String errorCode;
    private List<String> warnings;
    
    // Pagination
    private Integer totalResults;
    private Integer pageSize;
    private String nextPageToken;
    
    /**
     * Creates a successful response
     */
    public static AgentResponse success(String requestId, String agentId, String message) {
        return AgentResponse.builder()
            .requestId(requestId)
            .agentId(agentId)
            .success(true)
            .message(message)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    /**
     * Creates an error response
     */
    public static AgentResponse error(String requestId, String agentId, String message, String errorCode) {
        return AgentResponse.builder()
            .requestId(requestId)
            .agentId(agentId)
            .success(false)
            .message(message)
            .errorCode(errorCode)
            .timestamp(LocalDateTime.now())
            .build();
    }
} 