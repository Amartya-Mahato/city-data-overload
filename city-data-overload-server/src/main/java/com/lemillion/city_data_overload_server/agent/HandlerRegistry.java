package com.lemillion.city_data_overload_server.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Registry for automatically discovering and managing Flutter page handlers
 * This enables dynamic routing without hardcoded switch cases
 */
@Service
@Slf4j
public class HandlerRegistry {
    
    @Autowired
    private ApplicationContext applicationContext;
    
    private final Map<String, List<FlutterPageHandler>> handlers = new HashMap<>();
    private final Map<String, FlutterPageHandler> primaryHandlers = new HashMap<>();
    
    /**
     * Initialize and register all handlers found in the application context
     */
    @PostConstruct
    public void initializeHandlers() {
        log.info("Initializing Flutter page handler registry...");
        
        // Find all beans implementing FlutterPageHandler
        Map<String, FlutterPageHandler> handlerBeans = applicationContext.getBeansOfType(FlutterPageHandler.class);
        
        for (Map.Entry<String, FlutterPageHandler> entry : handlerBeans.entrySet()) {
            registerHandler(entry.getValue());
        }
        
        log.info("Registered {} Flutter page handlers for {} page types", 
                handlerBeans.size(), handlers.size());
        
        // Log registered handlers for debugging
        handlers.forEach((pageType, handlerList) -> {
            log.debug("Page type '{}' has {} handler(s): {}", 
                    pageType, 
                    handlerList.size(),
                    handlerList.stream()
                            .map(h -> h.getClass().getSimpleName())
                            .collect(Collectors.joining(", ")));
        });
    }
    
    /**
     * Register a handler for a specific page type
     */
    private void registerHandler(FlutterPageHandler handler) {
        String pageType = handler.getPageType();
        
        if (pageType == null || pageType.trim().isEmpty()) {
            log.warn("Handler {} has null or empty page type, skipping registration", 
                    handler.getClass().getSimpleName());
            return;
        }
        
        pageType = pageType.toUpperCase();
        
        // Add to handlers list
        handlers.computeIfAbsent(pageType, k -> new ArrayList<>()).add(handler);
        
        // Sort handlers by priority (lower number = higher priority)
        handlers.get(pageType).sort(Comparator.comparingInt(FlutterPageHandler::getPriority));
        
        // Set primary handler (highest priority one)
        if (!primaryHandlers.containsKey(pageType) || 
            handler.getPriority() < primaryHandlers.get(pageType).getPriority()) {
            primaryHandlers.put(pageType, handler);
        }
        
        log.debug("Registered handler {} for page type '{}' with priority {}", 
                handler.getClass().getSimpleName(), pageType, handler.getPriority());
    }
    
    /**
     * Route request to appropriate handler based on page type
     */
    public CompletableFuture<AgentResponse> routeRequest(AgentRequest request) {
        String pageType = determinePageType(request);
        
        log.debug("Routing request {} to page type: {}", request.getRequestId(), pageType);
        
        // Get handlers for this page type
        List<FlutterPageHandler> pageHandlers = handlers.get(pageType);
        
        if (pageHandlers == null || pageHandlers.isEmpty()) {
            log.warn("No handlers found for page type: {}, falling back to HOME", pageType);
            pageHandlers = handlers.get("HOME");
            
            if (pageHandlers == null || pageHandlers.isEmpty()) {
                return CompletableFuture.completedFuture(createNoHandlerErrorResponse(request, pageType));
            }
        }
        
        // Find the first handler that can handle this request
        for (FlutterPageHandler handler : pageHandlers) {
            if (handler.canHandle(request)) {
                log.debug("Request {} will be handled by {}", 
                        request.getRequestId(), handler.getClass().getSimpleName());
                return handler.handle(request);
            }
        }
        
        // If no specific handler can handle it, use the primary handler
        FlutterPageHandler primaryHandler = primaryHandlers.get(pageType);
        if (primaryHandler != null) {
            log.debug("Using primary handler {} for request {}", 
                    primaryHandler.getClass().getSimpleName(), request.getRequestId());
            return primaryHandler.handle(request);
        }
        
        return CompletableFuture.completedFuture(createNoHandlerErrorResponse(request, pageType));
    }
    
    /**
     * Determine page type from request
     */
    private String determinePageType(AgentRequest request) {
        String requestType = request.getRequestType();
        Map<String, Object> params = request.getParameters();
        
        // Check request type first
        if (requestType != null) {
            if (requestType.contains("EVENTS")) return "EVENTS";
            if (requestType.contains("ALERTS")) return "ALERTS";
            if (requestType.contains("REPORT")) return "REPORTING";
            if (requestType.contains("CHAT")) return "CHAT";
        }
        
        // Check parameters
        if (params != null && params.get("page") != null) {
            String page = params.get("page").toString().toUpperCase();
            if (handlers.containsKey(page)) {
                return page;
            }
        }
        
        return "HOME"; // Default
    }
    
    /**
     * Get all registered page types
     */
    public Set<String> getRegisteredPageTypes() {
        return new HashSet<>(handlers.keySet());
    }
    
    /**
     * Get all handlers for a specific page type
     */
    public List<FlutterPageHandler> getHandlersForPageType(String pageType) {
        return handlers.getOrDefault(pageType.toUpperCase(), List.of());
    }
    
    /**
     * Get primary handler for a page type
     */
    public Optional<FlutterPageHandler> getPrimaryHandler(String pageType) {
        return Optional.ofNullable(primaryHandlers.get(pageType.toUpperCase()));
    }
    
    /**
     * Get registry statistics for monitoring
     */
    public Map<String, Object> getRegistryStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total_page_types", handlers.size());
        stats.put("total_handlers", handlers.values().stream().mapToInt(List::size).sum());
        stats.put("page_types", new ArrayList<>(handlers.keySet()));
        
        Map<String, Object> handlerDetails = new HashMap<>();
        handlers.forEach((pageType, handlerList) -> {
            handlerDetails.put(pageType, handlerList.stream()
                    .map(h -> Map.of(
                            "class", h.getClass().getSimpleName(),
                            "priority", h.getPriority(),
                            "description", h.getDescription()
                    ))
                    .collect(Collectors.toList()));
        });
        stats.put("handler_details", handlerDetails);
        
        return stats;
    }
    
    /**
     * Create error response when no handler is found
     */
    private AgentResponse createNoHandlerErrorResponse(AgentRequest request, String pageType) {
        return AgentResponse.builder()
                .requestId(request.getRequestId())
                .agentId("handler-registry")
                .success(false)
                .message("No handler available for page type: " + pageType)
                .timestamp(java.time.LocalDateTime.now())
                .metadata(Map.of(
                        "error_type", "NO_HANDLER_FOUND",
                        "requested_page_type", pageType,
                        "available_page_types", new ArrayList<>(handlers.keySet())
                ))
                .build();
    }
} 