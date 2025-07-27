package com.lemillion.city_data_overload_server.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.lemillion.city_data_overload_server.model.SourceLocation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Service for managing source locations from Firestore.
 * Handles location registration, updates, and retrieval for data fetching.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LocationService {

    private final Firestore firestore;
    
    private static final String SOURCE_LOCATIONS_COLLECTION = "source_locations";

    /**
     * Get all active locations for data fetching
     */
    public CompletableFuture<List<SourceLocation>> getActiveLocations() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ApiFuture<QuerySnapshot> future = firestore.collection(SOURCE_LOCATIONS_COLLECTION)
                    .whereEqualTo("active", true)
                    .orderBy("priority", Query.Direction.ASCENDING)
                    .get();
                
                QuerySnapshot querySnapshot = future.get();
                
                List<SourceLocation> locations = querySnapshot.getDocuments().stream()
                    .map(this::convertDocumentToLocation)
                    .filter(Objects::nonNull)
                    .filter(SourceLocation::isValidForFetching)
                    .collect(Collectors.toList());
                
                log.info("Retrieved {} active locations for data fetching", locations.size());
                return locations;
                
            } catch (Exception e) {
                log.error("Error retrieving active locations from Firestore", e);
                throw new RuntimeException("Failed to get active locations", e);
            }
        });
    }

    /**
     * Get locations by priority level
     */
    public CompletableFuture<List<SourceLocation>> getLocationsByPriority(SourceLocation.LocationPriority priority) {
        return getActiveLocations()
            .thenApply(locations -> locations.stream()
                .filter(location -> location.getPriorityLevel() == priority)
                .collect(Collectors.toList()));
    }

    /**
     * Register a new location from Flutter app
     */
    public CompletableFuture<String> registerLocation(SourceLocation location) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Generate ID if not provided
                if (location.getId() == null || location.getId().trim().isEmpty()) {
                    location.setId(UUID.randomUUID().toString());
                }
                
                // Set registration metadata
                location.setRegisteredAt(LocalDateTime.now());
                location.setLastActiveAt(LocalDateTime.now());
                location.setActive(true);
                
                // Initialize stats
                if (location.getTotalFetches() == null) location.setTotalFetches(0);
                if (location.getTotalEvents() == null) location.setTotalEvents(0);
                if (location.getActiveUsers() == null) location.setActiveUsers(1);
                if (location.getPriority() == null) location.setPriority(2); // Default medium priority
                
                // Convert to Firestore document
                Map<String, Object> locationData = convertLocationToMap(location);
                
                DocumentReference docRef = firestore.collection(SOURCE_LOCATIONS_COLLECTION)
                    .document(location.getId());
                
                ApiFuture<WriteResult> future = docRef.set(locationData, SetOptions.merge());
                future.get();
                
                log.info("Successfully registered location: {} ({})", 
                        location.getShortName(), location.getId());
                return location.getId();
                
            } catch (Exception e) {
                log.error("Error registering location: {}", location.getShortName(), e);
                throw new RuntimeException("Failed to register location", e);
            }
        });
    }

    /**
     * Update location statistics and mark as recently fetched
     */
    public CompletableFuture<Void> updateLocationStats(String locationId, int eventCount) {
            try {
            DocumentReference docRef = firestore.collection(SOURCE_LOCATIONS_COLLECTION).document(locationId);
                
                Map<String, Object> updates = new HashMap<>();
                updates.put("lastFetchedAt", new Date());
                updates.put("totalFetches", FieldValue.increment(1));
            updates.put("totalEvents", FieldValue.increment(eventCount));
            updates.put("lastActiveAt", new Date());
                
            return CompletableFuture.runAsync(() -> {
                try {
                ApiFuture<WriteResult> future = docRef.update(updates);
                future.get();
                    log.debug("Updated location stats for: {} with {} events", locationId, eventCount);
                } catch (Exception e) {
                    log.error("Error updating location stats for: {}", locationId, e);
                }
            });
                
            } catch (Exception e) {
            log.error("Error preparing location stats update for: {}", locationId, e);
            return CompletableFuture.completedFuture(null);
            }
    }

    /**
     * Update user activity for a location
     */
    public CompletableFuture<Void> updateLocationActivity(String locationId, String userId) {
        return CompletableFuture.runAsync(() -> {
            try {
                DocumentReference docRef = firestore.collection(SOURCE_LOCATIONS_COLLECTION)
                    .document(locationId);
                
                Map<String, Object> updates = new HashMap<>();
                updates.put("lastActiveAt", new Date());
                
                // If userId is provided, we might want to track unique users
                if (userId != null && !userId.trim().isEmpty()) {
                    updates.put("userId", userId);
                }
                
                ApiFuture<WriteResult> future = docRef.update(updates);
                future.get();
                
            } catch (Exception e) {
                log.error("Error updating location activity: {}", locationId, e);
            }
        });
    }

    /**
     * Find existing location by coordinates (within radius)
     */
    public CompletableFuture<Optional<SourceLocation>> findLocationByCoordinates(double lat, double lng, double radiusKm) {
        return getActiveLocations()
            .thenApply(locations -> {
                return locations.stream()
                    .filter(location -> {
                        if (location.getLat() == null || location.getLng() == null) {
                            return false;
                        }
                        double distance = calculateDistance(lat, lng, location.getLat(), location.getLng());
                        return distance <= radiusKm;
                    })
                    .findFirst();
            });
    }

    /**
     * Deactivate old or unused locations
     */
    public CompletableFuture<Integer> deactivateOldLocations(int daysInactive) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Date cutoffDate = new Date(System.currentTimeMillis() - (daysInactive * 24L * 60 * 60 * 1000));
                
                ApiFuture<QuerySnapshot> future = firestore.collection(SOURCE_LOCATIONS_COLLECTION)
                    .whereEqualTo("active", true)
                    .whereLessThan("lastActiveAt", cutoffDate)
                    .get();
                
                QuerySnapshot querySnapshot = future.get();
                int deactivatedCount = 0;
                
                for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                    doc.getReference().update("active", false);
                    deactivatedCount++;
                }
                
                log.info("Deactivated {} locations inactive for {} days", deactivatedCount, daysInactive);
                return deactivatedCount;
                
            } catch (Exception e) {
                log.error("Error deactivating old locations", e);
                return 0;
            }
        });
    }

    /**
     * Get location statistics
     */
    public CompletableFuture<Map<String, Object>> getLocationStatistics() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Get all locations
                ApiFuture<QuerySnapshot> allFuture = firestore.collection(SOURCE_LOCATIONS_COLLECTION).get();
                QuerySnapshot allSnapshot = allFuture.get();
                
                // Get active locations
                ApiFuture<QuerySnapshot> activeFuture = firestore.collection(SOURCE_LOCATIONS_COLLECTION)
                    .whereEqualTo("active", true).get();
                QuerySnapshot activeSnapshot = activeFuture.get();
                
                Map<String, Object> stats = new HashMap<>();
                stats.put("totalLocations", allSnapshot.size());
                stats.put("activeLocations", activeSnapshot.size());
                stats.put("inactiveLocations", allSnapshot.size() - activeSnapshot.size());
                
                // Priority breakdown
                Map<String, Long> priorityBreakdown = activeSnapshot.getDocuments().stream()
                    .map(this::convertDocumentToLocation)
                    .filter(Objects::nonNull)
                    .collect(Collectors.groupingBy(
                        location -> location.getPriorityLevel().name(),
                        Collectors.counting()
                    ));
                
                stats.put("priorityBreakdown", priorityBreakdown);
                stats.put("timestamp", LocalDateTime.now());
                
                return stats;
                
            } catch (Exception e) {
                log.error("Error getting location statistics", e);
                return Map.of("error", e.getMessage());
            }
        });
    }

    /**
     * Check if a location should be fetched based on time window
     */
    public boolean shouldFetchLocation(String locationId, int timeWindowMinutes) {
        try {
            // Get location from Firestore directly
            DocumentReference docRef = firestore.collection(SOURCE_LOCATIONS_COLLECTION).document(locationId);
            ApiFuture<DocumentSnapshot> future = docRef.get();
            DocumentSnapshot doc = future.get();
            
            if (!doc.exists()) {
                return true; // Fetch if location not found
            }
            
            SourceLocation location = convertDocumentToLocation(doc);
            if (location == null) {
                return true; // Fetch if conversion failed
            }
            
            LocalDateTime lastFetched = location.getLastFetchedAt();
            if (lastFetched == null) {
                return true; // Fetch if never fetched before
            }
            
            LocalDateTime windowThreshold = LocalDateTime.now().minusMinutes(timeWindowMinutes);
            boolean shouldFetch = lastFetched.isBefore(windowThreshold);
            
            log.debug("Location {} last fetched at {}, window threshold {}, should fetch: {}", 
                    locationId, lastFetched, windowThreshold, shouldFetch);
            
            return shouldFetch;
                
        } catch (Exception e) {
            log.error("Error in shouldFetchLocation for {}", locationId, e);
            return true; // Default to fetching on error
        }
    }

    /**
     * Filter locations that should be fetched based on time window
     */
    public CompletableFuture<List<SourceLocation>> filterLocationsByTimeWindow(
            List<SourceLocation> locations, int timeWindowMinutes) {
        
        return CompletableFuture.supplyAsync(() -> {
            LocalDateTime windowThreshold = LocalDateTime.now().minusMinutes(timeWindowMinutes);
            
            List<SourceLocation> eligibleLocations = locations.stream()
                .filter(location -> {
                    LocalDateTime lastFetched = location.getLastFetchedAt();
                    boolean shouldFetch = lastFetched == null || lastFetched.isBefore(windowThreshold);
                    
                    if (!shouldFetch) {
                        log.debug("Skipping location {} - fetched {} minutes ago", 
                                location.getShortName(), 
                                lastFetched != null ? 
                                    java.time.Duration.between(lastFetched, LocalDateTime.now()).toMinutes() : "never");
                    }
                    
                    return shouldFetch;
                })
                .collect(Collectors.toList());
            
            log.info("Filtered {}/{} locations for fetch (time window: {} minutes)", 
                    eligibleLocations.size(), locations.size(), timeWindowMinutes);
            
            return eligibleLocations;
        });
    }

    /**
     * Reset the lastFetchedAt field for a location to make it eligible for immediate fetch
     */
    public CompletableFuture<Void> resetLocationTimeWindow(String locationId) {
        try {
            DocumentReference docRef = firestore.collection(SOURCE_LOCATIONS_COLLECTION).document(locationId);
            
            Map<String, Object> updates = new HashMap<>();
            updates.put("lastFetchedAt", null);
            
            return CompletableFuture.runAsync(() -> {
                try {
                    ApiFuture<WriteResult> future = docRef.update(updates);
                    future.get();
                    log.debug("Reset time window for location: {}", locationId);
                } catch (Exception e) {
                    log.error("Error resetting time window for location: {}", locationId, e);
                }
            });
                
        } catch (Exception e) {
            log.error("Error preparing time window reset for location: {}", locationId, e);
            return CompletableFuture.completedFuture(null);
        }
    }

    private SourceLocation convertDocumentToLocation(DocumentSnapshot doc) {
        try {
            Map<String, Object> data = doc.getData();
            if (data == null) return null;
            
            return SourceLocation.builder()
                .id(doc.getId())
                .area((String) data.get("area"))
                .lat(getDoubleValue(data.get("lat")))
                .lng(getDoubleValue(data.get("lng")))
                .city((String) data.get("city"))
                .state((String) data.get("state"))
                .country((String) data.get("country"))
                .pincode((String) data.get("pincode"))
                .landmark((String) data.get("landmark"))
                .active((Boolean) data.getOrDefault("active", true))
                .priority(getIntegerValue(data.get("priority")))
                .userId((String) data.get("userId"))
                .deviceId((String) data.get("deviceId"))
                .registeredAt(convertTimestamp(data.get("registeredAt")))
                .lastFetchedAt(convertTimestamp(data.get("lastFetchedAt")))
                .lastActiveAt(convertTimestamp(data.get("lastActiveAt")))
                .totalFetches(getIntegerValue(data.get("totalFetches")))
                .totalEvents(getIntegerValue(data.get("totalEvents")))
                .activeUsers(getIntegerValue(data.get("activeUsers")))
                .build();
                
        } catch (Exception e) {
            log.warn("Error converting document to SourceLocation: {}", doc.getId(), e);
            return null;
        }
    }

    private Map<String, Object> convertLocationToMap(SourceLocation location) {
        Map<String, Object> data = new HashMap<>();
        
        data.put("area", location.getArea());
        data.put("lat", location.getLat());
        data.put("lng", location.getLng());
        data.put("city", location.getCity());
        data.put("state", location.getState());
        data.put("country", location.getCountry());
        data.put("pincode", location.getPincode());
        data.put("landmark", location.getLandmark());
        data.put("active", location.getActive());
        data.put("priority", location.getPriority());
        data.put("userId", location.getUserId());
        data.put("deviceId", location.getDeviceId());
        
        if (location.getRegisteredAt() != null) {
            data.put("registeredAt", Date.from(location.getRegisteredAt().toInstant(ZoneOffset.UTC)));
        }
        if (location.getLastFetchedAt() != null) {
            data.put("lastFetchedAt", Date.from(location.getLastFetchedAt().toInstant(ZoneOffset.UTC)));
        }
        if (location.getLastActiveAt() != null) {
            data.put("lastActiveAt", Date.from(location.getLastActiveAt().toInstant(ZoneOffset.UTC)));
        }
        
        data.put("totalFetches", location.getTotalFetches());
        data.put("totalEvents", location.getTotalEvents());
        data.put("activeUsers", location.getActiveUsers());
        
        return data;
    }

    private Double getDoubleValue(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private Integer getIntegerValue(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private LocalDateTime convertTimestamp(Object timestamp) {
        if (timestamp == null) return null;
        if (timestamp instanceof Date) {
            return LocalDateTime.ofInstant(((Date) timestamp).toInstant(), ZoneOffset.UTC);
        }
        if (timestamp instanceof com.google.cloud.Timestamp) {
            return LocalDateTime.ofInstant(((com.google.cloud.Timestamp) timestamp).toDate().toInstant(), ZoneOffset.UTC);
        }
        return null;
    }

    private double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        final int R = 6371; // Earth's radius in kilometers
        double latDistance = Math.toRadians(lat2 - lat1);
        double lngDistance = Math.toRadians(lng2 - lng1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lngDistance / 2) * Math.sin(lngDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
} 