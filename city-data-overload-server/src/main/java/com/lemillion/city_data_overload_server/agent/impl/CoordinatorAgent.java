package com.lemillion.city_data_overload_server.agent.impl;

import com.lemillion.city_data_overload_server.agent.Agent;
import com.lemillion.city_data_overload_server.agent.AgentRequest;
import com.lemillion.city_data_overload_server.agent.AgentResponse;
import com.lemillion.city_data_overload_server.agent.HandlerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Enhanced Coordinator Agent - Single entry point for Flutter application
 * 
 * Features:
 * - Dynamic routing through HandlerRegistry (no more hardcoded switch cases)
 * - Automatic handler discovery and registration
 * - Unified chat interface with structured JSON responses
 * - Contextual conversation capabilities
 * - Robust error handling and fallbacks
 * - Extensible architecture for adding new page handlers
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CoordinatorAgent implements Agent {

    // Dynamic handler registry for page routing
    private final HandlerRegistry handlerRegistry;
    @Override
    public String getAgentId() {
        return "coordinator-agent";
    }

    @Override
    public AgentType getAgentType() {
        return AgentType.COORDINATOR;
    }

    @Override
    public String getDescription() {
        return "Dynamic coordinator for Flutter app - routes requests through handler registry for extensible architecture";
    }

    @Override
    public boolean canHandle(String requestType) {
        return requestType.startsWith("FLUTTER_") || 
               "CHAT".equals(requestType) ||
               "GET_EVENTS".equals(requestType) ||
               "GET_ALERTS".equals(requestType) ||
               "SUBMIT_REPORT".equals(requestType);
    }

    @Override
    public HealthStatus getHealthStatus() {
        try {
            // Check if handler registry is healthy and has registered handlers
            Set<String> pageTypes = handlerRegistry.getRegisteredPageTypes();
            if (pageTypes.isEmpty()) {
                log.warn("No page handlers registered in HandlerRegistry");
                return HealthStatus.DEGRADED;
            }
            
            log.debug("Coordinator healthy with {} page types: {}", 
                    pageTypes.size(), pageTypes);
            return HealthStatus.HEALTHY;
            
        } catch (Exception e) {
            log.error("Coordinator health check failed", e);
            return HealthStatus.DEGRADED;
        }
    }

    @Override
    public CompletableFuture<AgentResponse> processRequest(AgentRequest request) {
        long startTime = System.currentTimeMillis();
        
        log.info("Flutter Coordinator processing: {} - Type: {}", 
                request.getRequestId(), request.getRequestType());
        
        try {
            // Route to appropriate handler through registry
            return handlerRegistry.routeRequest(request)
                .thenApply(response -> {
                    response.setProcessingTimeMs(System.currentTimeMillis() - startTime);
                    log.info("Flutter request completed: {} in {}ms by handler: {}", 
                            request.getRequestId(), response.getProcessingTimeMs(), response.getAgentId());
                    return response;
                })
                .exceptionally(throwable -> {
                    log.error("Flutter coordinator error for request: {}", 
                            request.getRequestId(), throwable);
                    return createErrorResponse(request, throwable);
                });
                
        } catch (Exception e) {
            log.error("Critical error in Flutter coordinator", e);
            return CompletableFuture.completedFuture(createErrorResponse(request, e));
        }
    }

    /**
     * Get registry statistics for monitoring and debugging
     */
    public Map<String, Object> getRegistryStats() {
        return handlerRegistry.getRegistryStats();
    }

    /**
     * Get all registered page types
     */
    public Set<String> getRegisteredPageTypes() {
        return handlerRegistry.getRegisteredPageTypes();
    }

    /**
     * Create standardized error response for Flutter
     */
    private AgentResponse createErrorResponse(AgentRequest request, Throwable throwable) {
        return AgentResponse.builder()
            .requestId(request.getRequestId())
            .agentId(getAgentId())
            .success(false)
            .message("I'm sorry, I encountered an issue while processing your request. Please try again or contact support if the problem persists.")
            .timestamp(LocalDateTime.now())
            .metadata(Map.of(
                "error_type", "PROCESSING_ERROR",
                "error_message", throwable.getMessage(),
                "suggested_action", "retry"
            ))
            .build();
    }
} 