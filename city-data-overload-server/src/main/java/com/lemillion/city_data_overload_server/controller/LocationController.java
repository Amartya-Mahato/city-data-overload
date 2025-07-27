package com.lemillion.city_data_overload_server.controller;

import com.lemillion.city_data_overload_server.model.SourceLocation;
import com.lemillion.city_data_overload_server.service.LocationService;
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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * REST Controller for location management.
 * Handles location registration from Flutter app and location-based operations.
 */
@RestController
@RequestMapping("/api/v1/locations")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Location Management", description = "Endpoints for managing source locations for data fetching")
public class LocationController {

    private final LocationService locationService;

    @Operation(
        summary = "Register a new location",
        description = "Register a new location from Flutter app for data fetching"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Location registered successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid location data"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/register")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> registerLocation(
            @RequestBody SourceLocation location) {
        
        log.info("Location registration request: {} at [{}, {}]", 
                location.getArea(), location.getLat(), location.getLng());
        
        // Validate required fields
        if (location.getLat() == null || location.getLng() == null) {
            return CompletableFuture.completedFuture(
                ResponseEntity.badRequest().body(Map.of(
                    "status", "ERROR",
                    "message", "Latitude and longitude are required"
                ))
            );
        }
        
        return locationService.registerLocation(location)
            .thenApply(locationId -> {
                log.info("Successfully registered location: {} with ID: {}", 
                        location.getShortName(), locationId);
                
                Map<String, Object> response = Map.of(
                    "status", "SUCCESS",
                    "message", "Location registered successfully",
                    "locationId", locationId,
                    "formattedLocation", location.getFormattedLocation()
                );
                return ResponseEntity.ok(response);
            })
            .exceptionally(throwable -> {
                log.error("Error registering location: {}", location.getShortName(), throwable);
                return ResponseEntity.internalServerError().body(Map.of(
                    "status", "ERROR",
                    "message", "Failed to register location: " + throwable.getMessage()
                ));
            });
    }

    @Operation(
        summary = "Get all active locations",
        description = "Retrieve all active locations for data fetching"
    )
    @GetMapping("/active")
    public CompletableFuture<ResponseEntity<List<SourceLocation>>> getActiveLocations() {
        log.debug("Request for all active locations");
        
        return locationService.getActiveLocations()
            .thenApply(locations -> {
                log.debug("Returning {} active locations", locations.size());
                return ResponseEntity.ok(locations);
            })
            .exceptionally(throwable -> {
                log.error("Error retrieving active locations", throwable);
                return ResponseEntity.internalServerError().build();
            });
    }

    @Operation(
        summary = "Get locations by priority",
        description = "Retrieve locations filtered by priority level"
    )
    @GetMapping("/priority/{priority}")
    public CompletableFuture<ResponseEntity<List<SourceLocation>>> getLocationsByPriority(
            @Parameter(description = "Priority level", example = "HIGH")
            @PathVariable SourceLocation.LocationPriority priority) {
        
        log.debug("Request for locations with priority: {}", priority);
        
        return locationService.getLocationsByPriority(priority)
            .thenApply(locations -> {
                log.debug("Returning {} locations for priority: {}", locations.size(), priority);
                return ResponseEntity.ok(locations);
            })
            .exceptionally(throwable -> {
                log.error("Error retrieving locations by priority: {}", priority, throwable);
                return ResponseEntity.internalServerError().build();
            });
    }

    @Operation(
        summary = "Find location by coordinates",
        description = "Find existing location within specified radius of given coordinates"
    )
    @GetMapping("/nearby")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> findNearbyLocation(
            @Parameter(description = "Latitude", example = "12.9716")
            @RequestParam double lat,
            @Parameter(description = "Longitude", example = "77.5946")
            @RequestParam double lng,
            @Parameter(description = "Search radius in kilometers", example = "5.0")
            @RequestParam(defaultValue = "5.0") double radiusKm) {
        
        log.debug("Searching for location near [{}, {}] within {} km", lat, lng, radiusKm);
        
        return locationService.findLocationByCoordinates(lat, lng, radiusKm)
            .thenApply(locationOpt -> {
                if (locationOpt.isPresent()) {
                    SourceLocation location = locationOpt.get();
                    log.debug("Found nearby location: {}", location.getShortName());
                    
                    Map<String, Object> response = Map.of(
                        "found", true,
                        "location", location,
                        "message", "Found existing location nearby"
                    );
                    return ResponseEntity.ok(response);
                } else {
                    log.debug("No nearby location found");
                    Map<String, Object> response = Map.of(
                        "found", false,
                        "message", "No location found within specified radius"
                    );
                    return ResponseEntity.ok(response);
                }
            })
            .exceptionally(throwable -> {
                log.error("Error searching for nearby location", throwable);
                return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to search for nearby location"
                ));
            });
    }

    @Operation(
        summary = "Update user activity",
        description = "Update user activity timestamp for a location"
    )
    @PostMapping("/{locationId}/activity")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> updateActivity(
            @Parameter(description = "Location ID")
            @PathVariable String locationId,
            @Parameter(description = "User ID (optional)")
            @RequestParam(required = false) String userId) {
        
        log.debug("Updating activity for location: {}", locationId);
        
        return locationService.updateLocationActivity(locationId, userId)
            .thenApply(v -> {
                Map<String, Object> response = Map.of(
                    "status", "SUCCESS",
                    "message", "Activity updated successfully"
                );
                return ResponseEntity.ok(response);
            })
            .exceptionally(throwable -> {
                log.error("Error updating location activity: {}", locationId, throwable);
                return ResponseEntity.internalServerError().body(Map.of(
                    "status", "ERROR",
                    "message", "Failed to update activity"
                ));
            });
    }

    @Operation(
        summary = "Get location statistics",
        description = "Get comprehensive statistics about all locations"
    )
    @GetMapping("/stats")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> getLocationStatistics() {
        log.debug("Request for location statistics");
        
        return locationService.getLocationStatistics()
            .thenApply(stats -> {
                log.debug("Returning location statistics");
                return ResponseEntity.ok(stats);
            })
            .exceptionally(throwable -> {
                log.error("Error getting location statistics", throwable);
                return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to retrieve statistics"
                ));
            });
    }

    @Operation(
        summary = "Deactivate old locations",
        description = "Deactivate locations that haven't been active for specified days"
    )
    @PostMapping("/cleanup")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> deactivateOldLocations(
            @Parameter(description = "Number of days of inactivity", example = "30")
            @RequestParam(defaultValue = "30") int daysInactive) {
        
        log.info("Deactivating locations inactive for {} days", daysInactive);
        
        return locationService.deactivateOldLocations(daysInactive)
            .thenApply(count -> {
                log.info("Deactivated {} old locations", count);
                Map<String, Object> response = Map.of(
                    "status", "SUCCESS",
                    "message", "Cleanup completed",
                    "deactivatedCount", count
                );
                return ResponseEntity.ok(response);
            })
            .exceptionally(throwable -> {
                log.error("Error during location cleanup", throwable);
                return ResponseEntity.internalServerError().body(Map.of(
                    "status", "ERROR",
                    "message", "Cleanup failed"
                ));
            });
    }

    @Operation(
        summary = "Get available priority levels",
        description = "Get list of available location priority levels"
    )
    @GetMapping("/priorities")
    public ResponseEntity<SourceLocation.LocationPriority[]> getAvailablePriorities() {
        return ResponseEntity.ok(SourceLocation.LocationPriority.values());
    }

    @Operation(
        summary = "Health check",
        description = "Simple health check for location service"
    )
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = Map.of(
            "status", "UP",
            "service", "Location Management",
            "timestamp", System.currentTimeMillis()
        );
        return ResponseEntity.ok(health);
    }
} 