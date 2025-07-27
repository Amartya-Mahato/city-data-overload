package com.lemillion.city_data_overload_server.agent;

import java.util.concurrent.CompletableFuture;

/**
 * Interface for handling specific Flutter page requests dynamically
 * This enables the CoordinatorAgent to route requests without hardcoded switch cases
 */
public interface FlutterPageHandler {
    
    /**
     * Get the page type this handler supports
     * @return the Flutter page type (e.g., "HOME", "EVENTS", "ALERTS")
     */
    String getPageType();
    
    /**
     * Get the priority of this handler (lower number = higher priority)
     * Used when multiple handlers might match the same page type
     * @return priority value (default: 100)
     */
    default int getPriority() {
        return 100;
    }
    
    /**
     * Check if this handler can process the given request
     * @param request the agent request to evaluate
     * @return true if this handler can process the request
     */
    boolean canHandle(AgentRequest request);
    
    /**
     * Process the Flutter page request
     * @param request the agent request to process
     * @return CompletableFuture containing the agent response
     */
    CompletableFuture<AgentResponse> handle(AgentRequest request);
    
    /**
     * Get a description of what this handler does
     * @return description string
     */
    String getDescription();
} 