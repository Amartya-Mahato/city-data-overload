package com.lemillion.city_data_overload_server.service;

import com.lemillion.city_data_overload_server.model.CityEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Cached service layer for frequently accessed city event data.
 * Implements intelligent caching strategies based on data access patterns.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CachedEventService {

    private final FirestoreService firestoreService;
    private final BigQueryService bigQueryService;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Get events by location with caching
     */
    @Cacheable(value = "locationEvents", keyGenerator = "locationBasedKeyGenerator")
    public CompletableFuture<List<CityEvent>> getEventsByLocation(
            double latitude, double longitude, double radiusKm, int maxResults) {
        
        log.debug("Cache miss - fetching events by location: lat={}, lon={}, radius={}km", 
                 latitude, longitude, radiusKm);
        
        return firestoreService.getEventsByLocation(latitude, longitude, radiusKm, maxResults)
            .thenApply(events -> {
                log.debug("Cached {} events for location: lat={}, lon={}", events.size(), latitude, longitude);
                return events;
            });
    }

    /**
     * Get events by category and severity with caching
     */
    @Cacheable(value = "events", keyGenerator = "locationBasedKeyGenerator")
    public CompletableFuture<List<CityEvent>> getEventsByCategoryAndSeverity(
            CityEvent.EventCategory category, CityEvent.EventSeverity severity, int maxResults) {
        
        log.debug("Cache miss - fetching events by category: {} and severity: {}", 
                 category, severity);
        
        return firestoreService.getEventsByCategoryAndSeverity(category, severity, maxResults)
            .thenApply(events -> {
                log.debug("Cached {} events for category: {} and severity: {}", 
                         events.size(), category, severity);
                return events;
            });
    }

    /**
     * Get recent events by area with caching
     */
    @Cacheable(value = "locationEvents", keyGenerator = "locationBasedKeyGenerator")
    public CompletableFuture<List<CityEvent>> getRecentEventsByArea(String area, int maxResults) {
        log.debug("Cache miss - fetching recent events for area: {}", area);
        
        return firestoreService.getRecentEventsByArea(area, maxResults)
            .thenApply(events -> {
                log.debug("Cached {} recent events for area: {}", events.size(), area);
                return events;
            });
    }

    /**
     * Get trending events with caching
     */
    @Cacheable(value = "trending", key = "'trending:' + #maxResults")
    public CompletableFuture<List<CityEvent>> getTrendingEvents(int maxResults) {
        log.debug("Cache miss - fetching trending events");
        
        // Implementation for trending events (could be based on engagement, recency, etc.)
        return firestoreService.getEventsByLocation(12.9716, 77.5946, 50.0, maxResults)
            .thenApply(events -> {
                // Sort by most recent and highest engagement
                events.sort((e1, e2) -> {
                    LocalDateTime time1 = e1.getTimestamp() != null ? e1.getTimestamp() : LocalDateTime.MIN;
                    LocalDateTime time2 = e2.getTimestamp() != null ? e2.getTimestamp() : LocalDateTime.MIN;
                    return time2.compareTo(time1);
                });
                
                log.debug("Cached {} trending events", events.size());
                return events.subList(0, Math.min(maxResults, events.size()));
            });
    }

    /**
     * Get area statistics with caching
     */
    @Cacheable(value = "areaStats")
    public CompletableFuture<Map<String, Object>> getAreaStatistics(String area) {
        log.debug("Cache miss - computing area statistics for: {}", area);
        
        return firestoreService.getRecentEventsByArea(area, 100)
            .thenApply(events -> {
                Map<String, Object> stats = Map.of(
                    "area", area,
                    "totalEvents", events.size(),
                    "categoryCounts", computeCategoryCounts(events),
                    "severityCounts", computeSeverityCounts(events),
                    "lastUpdated", LocalDateTime.now(),
                    "averageEventsPerDay", computeAverageEventsPerDay(events)
                );
                
                log.debug("Cached statistics for area: {} with {} events", area, events.size());
                return stats;
            });
    }

    /**
     * Store event and evict related caches
     */
    @CacheEvict(value = {"locationEvents", "events", "trending", "areaStats"}, allEntries = true)
    public CompletableFuture<String> storeCityEvent(CityEvent event) {
        log.debug("Storing event and evicting caches: {}", event.getId());
        
        return firestoreService.storeCityEvent(event)
            .thenApply(eventId -> {
                log.debug("Event stored and caches evicted for: {}", eventId);
                return eventId;
            });
    }

    /**
     * Cache agent responses temporarily
     */
    public void cacheAgentResponse(String requestId, Object response) {
        String key = "agent:response:" + requestId;
        redisTemplate.opsForValue().set(key, response, 5, TimeUnit.MINUTES);
        log.debug("Cached agent response for request: {}", requestId);
    }

    /**
     * Get cached agent response
     */
    public Object getCachedAgentResponse(String requestId) {
        String key = "agent:response:" + requestId;
        Object response = redisTemplate.opsForValue().get(key);
        
        if (response != null) {
            log.debug("Cache hit for agent response: {}", requestId);
        } else {
            log.debug("Cache miss for agent response: {}", requestId);
        }
        
        return response;
    }

    /**
     * Cache user session data
     */
    public void cacheUserSession(String userId, Map<String, Object> sessionData) {
        String key = "user:session:" + userId;
        redisTemplate.opsForValue().set(key, sessionData, 24, TimeUnit.HOURS);
        log.debug("Cached user session for user: {}", userId);
    }

    /**
     * Get cached user session
     */
    public Map<String, Object> getCachedUserSession(String userId) {
        String key = "user:session:" + userId;
        @SuppressWarnings("unchecked")
        Map<String, Object> sessionData = (Map<String, Object>) redisTemplate.opsForValue().get(key);
        
        if (sessionData != null) {
            log.debug("Cache hit for user session: {}", userId);
        } else {
            log.debug("Cache miss for user session: {}", userId);
        }
        
        return sessionData;
    }

    /**
     * Clear all caches (admin function)
     */
    @CacheEvict(value = {"locationEvents", "events", "trending", "areaStats", "predictions", "moodMap"}, 
                allEntries = true)
    public void clearAllCaches() {
        log.info("All caches cleared by admin request");
    }

    // Helper methods

    private Map<String, Long> computeCategoryCounts(List<CityEvent> events) {
        return events.stream()
            .filter(e -> e.getCategory() != null)
            .collect(java.util.stream.Collectors.groupingBy(
                e -> e.getCategory().name(),
                java.util.stream.Collectors.counting()
            ));
    }

    private Map<String, Long> computeSeverityCounts(List<CityEvent> events) {
        return events.stream()
            .filter(e -> e.getSeverity() != null)
            .collect(java.util.stream.Collectors.groupingBy(
                e -> e.getSeverity().name(),
                java.util.stream.Collectors.counting()
            ));
    }

    private double computeAverageEventsPerDay(List<CityEvent> events) {
        if (events.isEmpty()) return 0.0;
        
        LocalDateTime oldest = events.stream()
            .map(CityEvent::getTimestamp)
            .filter(java.util.Objects::nonNull)
            .min(LocalDateTime::compareTo)
            .orElse(LocalDateTime.now().minusDays(1));
        
        long daysDiff = java.time.Duration.between(oldest, LocalDateTime.now()).toDays();
        return daysDiff > 0 ? (double) events.size() / daysDiff : events.size();
    }
} 