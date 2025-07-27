package com.lemillion.city_data_overload_server.service;

import com.lemillion.city_data_overload_server.model.CityEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Server-Sent Events service for real-time frontend updates.
 * Manages SSE connections and broadcasts city data updates to connected clients.
 */
@Service
@Slf4j
public class EventStreamService {

    // Store active SSE connections by type
    private final Map<String, CopyOnWriteArrayList<SseEmitter>> connections = new ConcurrentHashMap<>();
    
    // Connection timeout (30 minutes)
    private static final long SSE_TIMEOUT = 30 * 60 * 1000L;

    /**
     * Create new SSE connection for city events
     */
    public SseEmitter createEventStream(String streamType) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        
        // Initialize connection list if not exists
        connections.computeIfAbsent(streamType, k -> new CopyOnWriteArrayList<>()).add(emitter);
        
        // Handle connection cleanup
        emitter.onCompletion(() -> removeConnection(streamType, emitter));
        emitter.onTimeout(() -> removeConnection(streamType, emitter));
        emitter.onError((throwable) -> {
            log.warn("SSE connection error for stream type: {}", streamType, throwable);
            removeConnection(streamType, emitter);
        });
        
        // Send initial connection confirmation
        try {
            emitter.send(SseEmitter.event()
                .name("connected")
                .data(Map.of(
                    "message", "Connected to " + streamType + " stream",
                    "timestamp", LocalDateTime.now(),
                    "streamType", streamType
                )));
        } catch (IOException e) {
            log.error("Failed to send initial SSE message", e);
            removeConnection(streamType, emitter);
        }
        
        log.info("New SSE connection created for stream type: {} (total: {})", 
                streamType, connections.get(streamType).size());
        
        return emitter;
    }

    /**
     * Broadcast new city event to all connected clients
     */
    public void broadcastCityEvent(CityEvent event) {
        Map<String, Object> eventData = Map.of(
            "type", "cityEvent",
            "event", event,
            "timestamp", LocalDateTime.now()
        );
        
        broadcast("events", "newEvent", eventData);
        log.debug("Broadcasted city event: {} to events stream", event.getId());
    }

    /**
     * Broadcast emergency alert to all connected clients
     */
    public void broadcastAlert(Map<String, Object> alert) {
        Map<String, Object> alertData = Map.of(
            "type", "alert",
            "alert", alert,
            "timestamp", LocalDateTime.now(),
            "priority", alert.getOrDefault("severity", "MODERATE")
        );
        
        broadcast("alerts", "newAlert", alertData);
        log.info("Broadcasted alert to alerts stream: {}", alert.get("id"));
    }

    /**
     * Broadcast mood map update
     */
    public void broadcastMoodUpdate(String area, Map<String, Object> moodData) {
        Map<String, Object> updateData = Map.of(
            "type", "moodUpdate",
            "area", area,
            "moodData", moodData,
            "timestamp", LocalDateTime.now()
        );
        
        broadcast("mood", "moodUpdate", updateData);
        log.debug("Broadcasted mood update for area: {}", area);
    }

    /**
     * Broadcast predictive insights
     */
    public void broadcastPrediction(Map<String, Object> prediction) {
        Map<String, Object> predictionData = Map.of(
            "type", "prediction",
            "prediction", prediction,
            "timestamp", LocalDateTime.now()
        );
        
        broadcast("predictions", "newPrediction", predictionData);
        log.debug("Broadcasted prediction: {}", prediction.get("category"));
    }

    /**
     * Broadcast area statistics update
     */
    public void broadcastAreaStats(String area, Map<String, Object> stats) {
        Map<String, Object> statsData = Map.of(
            "type", "areaStats",
            "area", area,
            "stats", stats,
            "timestamp", LocalDateTime.now()
        );
        
        broadcast("events", "areaStatsUpdate", statsData);
        log.debug("Broadcasted area stats for: {}", area);
    }

    /**
     * Broadcast system status update
     */
    public void broadcastSystemStatus(String status, String message) {
        Map<String, Object> statusData = Map.of(
            "type", "systemStatus",
            "status", status,
            "message", message,
            "timestamp", LocalDateTime.now()
        );
        
        // Broadcast to all stream types
        connections.keySet().forEach(streamType -> 
            broadcast(streamType, "systemStatus", statusData));
        
        log.info("Broadcasted system status: {} - {}", status, message);
    }

    /**
     * Get connection statistics
     */
    public Map<String, Object> getConnectionStats() {
        Map<String, Integer> connectionCounts = new ConcurrentHashMap<>();
        connections.forEach((streamType, emitters) -> 
            connectionCounts.put(streamType, emitters.size()));
        
        return Map.of(
            "totalConnections", connections.values().stream()
                .mapToInt(CopyOnWriteArrayList::size).sum(),
            "connectionsByType", connectionCounts,
            "timestamp", LocalDateTime.now()
        );
    }

    /**
     * Close all connections (admin function)
     */
    public void closeAllConnections() {
        connections.forEach((streamType, emitters) -> {
            emitters.forEach(emitter -> {
                try {
                    emitter.send(SseEmitter.event()
                        .name("serverShutdown")
                        .data(Map.of("message", "Server is shutting down")));
                    emitter.complete();
                } catch (IOException e) {
                    log.warn("Error closing SSE connection", e);
                }
            });
            emitters.clear();
        });
        
        log.info("All SSE connections closed");
    }

    // Private helper methods

    private void broadcast(String streamType, String eventName, Object data) {
        CopyOnWriteArrayList<SseEmitter> emitters = connections.get(streamType);
        
        if (emitters == null || emitters.isEmpty()) {
            log.debug("No active connections for stream type: {}", streamType);
            return;
        }
        
        // Create list of failed emitters to remove
        CopyOnWriteArrayList<SseEmitter> failedEmitters = new CopyOnWriteArrayList<>();
        
        emitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(data));
            } catch (IOException e) {
                log.warn("Failed to send SSE message to client", e);
                failedEmitters.add(emitter);
            }
        });
        
        // Remove failed connections
        failedEmitters.forEach(emitter -> removeConnection(streamType, emitter));
        
        log.debug("Broadcasted {} to {} active connections in stream: {}", 
                 eventName, emitters.size() - failedEmitters.size(), streamType);
    }

    private void removeConnection(String streamType, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> emitters = connections.get(streamType);
        if (emitters != null) {
            emitters.remove(emitter);
            log.debug("Removed SSE connection from stream type: {} (remaining: {})", 
                     streamType, emitters.size());
        }
        
        try {
            emitter.complete();
        } catch (Exception e) {
            log.debug("Error completing SSE emitter", e);
        }
    }
} 