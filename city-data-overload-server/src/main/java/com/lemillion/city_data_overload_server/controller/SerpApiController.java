package com.lemillion.city_data_overload_server.controller;

import com.lemillion.city_data_overload_server.model.CityEvent;
import com.lemillion.city_data_overload_server.service.SerpApiDataFetcher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * REST Controller for SerpApi data fetching operations.
 * Provides endpoints for manual data collection, testing, and statistics.
 */
@RestController
@RequestMapping("/api/v1/serpapi")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "SerpApi Data Fetcher", description = "Endpoints for managing SerpApi data collection")
public class SerpApiController {

    private final SerpApiDataFetcher serpApiDataFetcher;

    @Operation(
        summary = "Fetch all category data",
        description = "Manually trigger data fetching for all categories from SerpApi"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Data fetch initiated successfully"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/fetch/all")
    public CompletableFuture<ResponseEntity<List<CityEvent>>> fetchAllCategoryData(
            @Parameter(description = "Location to fetch data for", example = "Bengaluru, Karnataka, India")
            @RequestParam(defaultValue = "Bengaluru, Karnataka, India") String location) {
        
        log.info("Manual fetch request for all categories, location: {}", location);
        
        return serpApiDataFetcher.fetchAllCategoryData(location)
            .thenApply(events -> {
                log.info("Manual fetch completed: {} events returned", events.size());
                return ResponseEntity.ok(events);
            })
            .exceptionally(throwable -> {
                log.error("Error in manual fetch for location: {}", location, throwable);
                return ResponseEntity.internalServerError().build();
            });
    }

    @Operation(
        summary = "Fetch specific categories",
        description = "Manually trigger data fetching for specific categories only"
    )
    @PostMapping("/fetch/categories")
    public CompletableFuture<ResponseEntity<List<CityEvent>>> fetchSpecificCategories(
            @Parameter(description = "Location to fetch data for", example = "Bengaluru, Karnataka, India")
            @RequestParam(defaultValue = "Bengaluru, Karnataka, India") String location,
            @Parameter(description = "Categories to fetch", example = "TRAFFIC,EMERGENCY")
            @RequestParam Set<CityEvent.EventCategory> categories) {
        
        log.info("Manual fetch request for categories: {}, location: {}", categories, location);
        
        return serpApiDataFetcher.fetchSpecificCategories(location, categories)
            .thenApply(events -> {
                log.info("Manual category fetch completed: {} events returned", events.size());
                return ResponseEntity.ok(events);
            })
            .exceptionally(throwable -> {
                log.error("Error in manual category fetch for location: {}", location, throwable);
                return ResponseEntity.internalServerError().build();
            });
    }

    @Operation(
        summary = "Fetch, process, and store data",
        description = "Complete pipeline: fetch data, process with AI, and store in Firestore. Extended timeout (5 minutes) for AI processing."
    )
    @PostMapping("/fetch/process-store")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> fetchProcessAndStore(
            @Parameter(description = "Location to fetch data for", example = "Bengaluru, Karnataka, India")
            @RequestParam(defaultValue = "Bengaluru, Karnataka, India") String location) {
        
        log.info("Manual fetch-process-store request for location: {}", location);
        
        return serpApiDataFetcher.fetchProcessAndStore(location)
            .thenApply(result -> {
                log.info("Manual fetch-process-store completed: {}", result.get("status"));
                return ResponseEntity.ok(result);
            })
            .exceptionally(throwable -> {
                log.error("Error in manual fetch-process-store for location: {}", location, throwable);
                Map<String, Object> errorResult = Map.of(
                    "status", "ERROR",
                    "location", location,
                    "error", throwable.getMessage(),
                    "timestamp", java.time.LocalDateTime.now(),
                    "suggestion", "The AI processing pipeline may take 2-5 minutes. Please try again or check logs."
                );
                return ResponseEntity.ok(errorResult); // Return 200 with error details for better UX
            });
    }

    @Operation(
        summary = "Fetch and process data asynchronously", 
        description = "Starts the AI processing pipeline and returns immediately with a task ID"
    )
    @PostMapping("/fetch/process-store/async")
    public ResponseEntity<Map<String, Object>> fetchProcessAndStoreAsync(
            @Parameter(description = "Location to fetch data for", example = "Bengaluru, Karnataka, India")
            @RequestParam(defaultValue = "Bengaluru, Karnataka, India") String location) {
        
        String taskId = "task_" + System.currentTimeMillis();
        log.info("Starting async fetch-process-store task: {} for location: {}", taskId, location);
        
        // Start processing asynchronously - fire and forget
        serpApiDataFetcher.fetchProcessAndStore(location)
            .thenAccept(result -> {
                log.info("Async task {} completed: {}", taskId, result.get("status"));
            })
            .exceptionally(throwable -> {
                log.error("Async task {} failed for location: {}", taskId, location, throwable);
                return null;
            });
        
        Map<String, Object> response = Map.of(
            "taskId", taskId,
            "status", "STARTED",
            "location", location,
            "message", "AI processing pipeline started. Check logs for progress.",
            "estimatedDuration", "2-5 minutes",
            "timestamp", java.time.LocalDateTime.now()
        );
        
        return ResponseEntity.accepted().body(response);
    }

    @Operation(
        summary = "Test SerpApi connection",
        description = "Test connectivity and authentication with SerpApi service"
    )
    @GetMapping("/test")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> testConnection() {
        log.info("SerpApi connection test requested");
        
        return serpApiDataFetcher.testConnection()
            .thenApply(result -> {
                boolean isSuccess = "SUCCESS".equals(result.get("status"));
                log.info("SerpApi connection test result: {}", result.get("status"));
                
                if (isSuccess) {
                    return ResponseEntity.ok(result);
                } else {
                    return ResponseEntity.status(503).body(result); // Service Unavailable
                }
            })
            .exceptionally(throwable -> {
                log.error("Error in SerpApi connection test", throwable);
                return ResponseEntity.internalServerError().build();
            });
    }

    @Operation(
        summary = "Get fetcher statistics",
        description = "Get information about available data fetchers and their configurations"
    )
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getFetcherStatistics() {
        log.debug("Fetcher statistics requested");
        
        try {
            Map<String, Object> stats = serpApiDataFetcher.getFetcherStatistics();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error retrieving fetcher statistics", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @Operation(
        summary = "Get supported categories",
        description = "Get list of all supported event categories for data fetching"
    )
    @GetMapping("/categories")
    public ResponseEntity<CityEvent.EventCategory[]> getSupportedCategories() {
        return ResponseEntity.ok(CityEvent.EventCategory.values());
    }

    @Operation(
        summary = "Health check",
        description = "Simple health check endpoint for the SerpApi service"
    )
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = Map.of(
            "status", "UP",
            "service", "SerpApi Data Fetcher",
            "timestamp", System.currentTimeMillis()
        );
        return ResponseEntity.ok(health);
    }
} 