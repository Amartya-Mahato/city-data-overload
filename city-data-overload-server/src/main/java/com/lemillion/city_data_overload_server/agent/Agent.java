package com.lemillion.city_data_overload_server.agent;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Base interface for all City Data Overload agents.
 * Each agent is responsible for a specific domain of city data processing.
 */
public interface Agent {
    
    /**
     * Gets the unique identifier for this agent
     */
    String getAgentId();
    
    /**
     * Gets the agent type/category
     */
    AgentType getAgentType();
    
    /**
     * Gets a human-readable description of what this agent does
     */
    String getDescription();
    
    /**
     * Processes a request and returns a response asynchronously
     * 
     * @param request The request parameters
     * @return CompletableFuture containing the agent response
     */
    CompletableFuture<AgentResponse> processRequest(AgentRequest request);
    
    /**
     * Checks if this agent can handle the given request type
     * 
     * @param requestType The type of request
     * @return true if this agent can handle the request
     */
    boolean canHandle(String requestType);
    
    /**
     * Gets the current health status of the agent
     */
    HealthStatus getHealthStatus();
    
    /**
     * Agent types for categorization
     */
    enum AgentType {
        COORDINATOR,
        DATA_FETCHER,
        ANALYZER,
        PREDICTOR,
        REPORTER,
        ALERTER,
        SYNTHESIZER
    }
    
    /**
     * Health status of the agent
     */
    enum HealthStatus {
        HEALTHY,
        DEGRADED,
        UNHEALTHY
    }
} 