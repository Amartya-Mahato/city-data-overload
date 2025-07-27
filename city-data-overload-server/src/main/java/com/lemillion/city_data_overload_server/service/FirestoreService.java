package com.lemillion.city_data_overload_server.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.lemillion.city_data_overload_server.model.CityEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Firestore service for real-time city events operations.
 * Handles fast queries, real-time updates, and TTL-based caching.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FirestoreService {

    private final Firestore firestore;
    
    private static final String EVENTS_COLLECTION = "city_events";
    private static final String ACTIVE_EVENTS_COLLECTION = "active_events";
    private static final String PREDICTIONS_COLLECTION = "predictions";
    private static final String SENTIMENT_COLLECTION = "sentiment_data";
    private static final String ALERTS_COLLECTION = "active_alerts";

    /**
     * Store a city event with TTL in Firestore
     */
    public CompletableFuture<String> storeCityEvent(CityEvent event) {
        try {
            Map<String, Object> eventData = convertEventToFirestoreMap(event);
            
            // Add TTL based on category
            Date expiryDate = calculateTTL(event.getCategory());
            eventData.put("ttl", expiryDate);
            eventData.put("createdAt", new Date());
            
            DocumentReference docRef = firestore.collection(EVENTS_COLLECTION).document(event.getId());
            
            return toCompletableFuture(docRef.set(eventData))
                .thenApply(writeResult -> {
                    log.debug("Successfully stored event in Firestore: {}", event.getId());
                    return event.getId();
                })
                .exceptionally(throwable -> {
                    log.error("Error storing event in Firestore: {}", event.getId(), throwable);
                    throw new RuntimeException("Firestore storage failed", throwable);
                });
                
        } catch (Exception e) {
            log.error("Error preparing event for Firestore storage: {}", event.getId(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Store multiple events in batch
     */
    public CompletableFuture<List<String>> storeCityEventsBatch(List<CityEvent> events) {
        if (events.isEmpty()) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }

        try {
            WriteBatch batch = firestore.batch();
            List<String> eventIds = new ArrayList<>();
            
            for (CityEvent event : events) {
                Map<String, Object> eventData = convertEventToFirestoreMap(event);
                Date expiryDate = calculateTTL(event.getCategory());
                eventData.put("ttl", expiryDate);
                eventData.put("createdAt", new Date());
                
                DocumentReference docRef = firestore.collection(EVENTS_COLLECTION).document(event.getId());
                batch.set(docRef, eventData);
                eventIds.add(event.getId());
            }
            
            return toCompletableFuture(batch.commit())
                .thenApply(writeResults -> {
                    log.info("Successfully stored {} events in Firestore batch", events.size());
                    return eventIds;
                })
                .exceptionally(throwable -> {
                    log.error("Error storing events batch in Firestore", throwable);
                    throw new RuntimeException("Firestore batch storage failed", throwable);
                });
                
        } catch (Exception e) {
            log.error("Error preparing events batch for Firestore", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Get events by location with radius filtering
     */
    public CompletableFuture<List<CityEvent>> getEventsByLocation(
            double latitude, double longitude, double radiusKm, int maxResults) {
        
        try {
            // Create geohash bounds for efficient querying
            CollectionReference eventsRef = firestore.collection(EVENTS_COLLECTION);
            
            // Query with geographic constraints and time filtering
            Query query = eventsRef
                .whereGreaterThan("ttl", new Date()) // Only non-expired events
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(maxResults * 2); // Get more to filter by distance
            
            return toCompletableFuture(query.get())
                .thenApply(querySnapshot -> {
                    List<CityEvent> events = new ArrayList<>();
                    
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        try {
                            CityEvent event = convertFirestoreDocToEvent(doc);
                            
                            // Filter by distance
                            if (event.getLocation() != null && 
                                event.getLocation().getLatitude() != null &&
                                event.getLocation().getLongitude() != null) {
                                
                                Double distance = event.getDistanceFrom(latitude, longitude);
                                if (distance != null && distance <= radiusKm * 1000) {
                                    events.add(event);
                                }
                            }
                            
                            if (events.size() >= maxResults) {
                                break;
                            }
                            
                        } catch (Exception e) {
                            log.warn("Error converting document to event: {}", doc.getId(), e);
                        }
                    }
                    
                    log.debug("Retrieved {} events by location from Firestore", events.size());
                    return events;
                })
                .exceptionally(throwable -> {
                    log.error("Error querying events by location from Firestore", throwable);
                    throw new RuntimeException("Firestore location query failed", throwable);
                });
                
        } catch (Exception e) {
            log.error("Error preparing location query for Firestore", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Get events by category and severity
     */
    public CompletableFuture<List<CityEvent>> getEventsByCategoryAndSeverity(
            CityEvent.EventCategory category, CityEvent.EventSeverity severity, int maxResults) {
        
        try {
            Query query = firestore.collection(EVENTS_COLLECTION)
                .whereEqualTo("category", category.name())
                .whereEqualTo("severity", severity.name())
                .whereGreaterThan("ttl", new Date())
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(maxResults);
                
            return toCompletableFuture(query.get())
                .thenApply(querySnapshot -> {
                    List<CityEvent> events = querySnapshot.getDocuments().stream()
                        .map(this::convertFirestoreDocToEvent)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
                        
                    log.debug("Retrieved {} events by category/severity from Firestore", events.size());
                    return events;
                })
                .exceptionally(throwable -> {
                    log.error("Error querying events by category/severity from Firestore", throwable);
                    throw new RuntimeException("Firestore category query failed", throwable);
                });
                
        } catch (Exception e) {
            log.error("Error preparing category query for Firestore", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Get recent events for a specific area
     */
    public CompletableFuture<List<CityEvent>> getRecentEventsByArea(String area, int maxResults) {
        try {
            Query query = firestore.collection(EVENTS_COLLECTION)
                .whereEqualTo("area", area)
                .whereGreaterThan("ttl", new Date())
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(maxResults);
                
            return toCompletableFuture(query.get())
                .thenApply(querySnapshot -> {
                    List<CityEvent> events = querySnapshot.getDocuments().stream()
                        .map(this::convertFirestoreDocToEvent)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
                        
                    log.debug("Retrieved {} recent events for area {} from Firestore", events.size(), area);
                    return events;
                })
                .exceptionally(throwable -> {
                    log.error("Error querying recent events by area from Firestore", throwable);
                    throw new RuntimeException("Firestore area query failed", throwable);
                });
                
        } catch (Exception e) {
            log.error("Error preparing area query for Firestore", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Store active alert with TTL
     */
    public CompletableFuture<String> storeActiveAlert(Map<String, Object> alertData) {
        try {
            alertData.put("createdAt", new Date());
            alertData.put("ttl", new Date(System.currentTimeMillis() + (24 * 60 * 60 * 1000))); // 24 hours TTL
            
            String alertId = UUID.randomUUID().toString();
            DocumentReference docRef = firestore.collection(ALERTS_COLLECTION).document(alertId);
            
            return toCompletableFuture(docRef.set(alertData))
                .thenApply(writeResult -> {
                    log.debug("Successfully stored alert in Firestore: {}", alertId);
                    return alertId;
                })
                .exceptionally(throwable -> {
                    log.error("Error storing alert in Firestore: {}", alertId, throwable);
                    throw new RuntimeException("Alert storage failed", throwable);
                });
                
        } catch (Exception e) {
            log.error("Error preparing alert for Firestore storage", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Get active alerts for a location
     */
    public CompletableFuture<List<Map<String, Object>>> getActiveAlerts(String area) {
        try {
            Query query = firestore.collection(ALERTS_COLLECTION)
                .whereEqualTo("area", area)
                .whereGreaterThan("ttl", new Date())
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(10);
                
            return toCompletableFuture(query.get())
                .thenApply(querySnapshot -> {
                    List<Map<String, Object>> alerts = querySnapshot.getDocuments().stream()
                        .map(doc -> {
                            Map<String, Object> data = new HashMap<>(doc.getData());
                            data.put("id", doc.getId());
                            return data;
                        })
                        .collect(Collectors.toList());
                        
                    log.debug("Retrieved {} active alerts for area {} from Firestore", alerts.size(), area);
                    return alerts;
                })
                .exceptionally(throwable -> {
                    log.error("Error querying active alerts from Firestore", throwable);
                    throw new RuntimeException("Alert query failed", throwable);
                });
                
        } catch (Exception e) {
            log.error("Error preparing alert query for Firestore", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Update event sentiment data
     */
    public CompletableFuture<Void> updateEventSentiment(String eventId, CityEvent.SentimentData sentiment) {
        try {
            DocumentReference docRef = firestore.collection(EVENTS_COLLECTION).document(eventId);
            
            Map<String, Object> updates = Map.of(
                "sentimentType", sentiment.getType().name(),
                "sentimentScore", sentiment.getScore(),
                "sentimentConfidence", sentiment.getConfidence(),
                "updatedAt", new Date()
            );
            
            return toCompletableFuture(docRef.update(updates))
                .thenApply(writeResult -> {
                    log.debug("Successfully updated sentiment for event: {}", eventId);
                    return (Void) null;
                })
                .exceptionally(throwable -> {
                    log.error("Error updating sentiment for event: {}", eventId, throwable);
                    throw new RuntimeException("Sentiment update failed", throwable);
                });
                
        } catch (Exception e) {
            log.error("Error preparing sentiment update for event: {}", eventId, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Real-time listener for events in a specific area
     */
    public ListenerRegistration listenToAreaEvents(String area, com.google.cloud.firestore.EventListener<QuerySnapshot> listener) {
        Query query = firestore.collection(EVENTS_COLLECTION)
            .whereEqualTo("area", area)
            .whereGreaterThan("ttl", new Date())
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50);
            
        return query.addSnapshotListener(listener);
    }

    /**
     * Clean up expired events (called by scheduled job)
     */
    public CompletableFuture<Integer> cleanupExpiredEvents() {
        try {
            Query expiredQuery = firestore.collection(EVENTS_COLLECTION)
                .whereLessThan("ttl", new Date())
                .limit(100);
                
            return toCompletableFuture(expiredQuery.get())
                .thenCompose((QuerySnapshot querySnapshot) -> {
                    if (querySnapshot.isEmpty()) {
                        return CompletableFuture.completedFuture(0);
                    }
                    
                    WriteBatch batch = firestore.batch();
                    int deleteCount = 0;
                    
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        batch.delete(doc.getReference());
                        deleteCount++;
                    }
                    
                    final int finalDeleteCount = deleteCount;
                    return toCompletableFuture(batch.commit())
                        .thenApply(writeResults -> {
                            log.info("Cleaned up {} expired events from Firestore", finalDeleteCount);
                            return finalDeleteCount;
                        });
                })
                .exceptionally(throwable -> {
                    log.error("Error cleaning up expired events from Firestore", throwable);
                    throw new RuntimeException("Cleanup failed", throwable);
                });
                
        } catch (Exception e) {
            log.error("Error preparing cleanup query for Firestore", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    // Private helper methods

    /**
     * Convert ApiFuture to CompletableFuture
     */
    private <T> CompletableFuture<T> toCompletableFuture(ApiFuture<T> apiFuture) {
        CompletableFuture<T> completableFuture = new CompletableFuture<>();
        apiFuture.addListener(() -> {
            try {
                completableFuture.complete(apiFuture.get());
            } catch (Exception e) {
                completableFuture.completeExceptionally(e);
            }
        }, Runnable::run);
        return completableFuture;
    }

    private Date calculateTTL(CityEvent.EventCategory category) {
        long currentTime = System.currentTimeMillis();
        long ttlMillis;
        
        // TTL based on event category (from application.yml config)
        switch (category) {
            case TRAFFIC -> ttlMillis = 2 * 60 * 60 * 1000; // 2 hours
            case WEATHER -> ttlMillis = 6 * 60 * 60 * 1000; // 6 hours
            case EMERGENCY -> ttlMillis = 24 * 60 * 60 * 1000; // 24 hours
            case CULTURAL_EVENT -> ttlMillis = 24 * 60 * 60 * 1000; // 1 day
            case INFRASTRUCTURE -> ttlMillis = 15 * 24 * 60 * 60 * 1000; // 15 days
            case CIVIC_ISSUE -> ttlMillis = 30 * 24 * 60 * 60 * 1000; // 30 days
            default -> ttlMillis = 24 * 60 * 60 * 1000; // Default 24 hours
        }
        
        return new Date(currentTime + ttlMillis);
    }

    private Map<String, Object> convertEventToFirestoreMap(CityEvent event) {
        Map<String, Object> data = new HashMap<>();
        
        data.put("id", event.getId());
        data.put("title", event.getTitle());
        data.put("description", event.getDescription());
        data.put("content", event.getContent());
        
        if (event.getLocation() != null) {
            data.put("latitude", event.getLocation().getLatitude());
            data.put("longitude", event.getLocation().getLongitude());
            data.put("address", event.getLocation().getAddress());
            data.put("area", event.getLocation().getArea());
            data.put("pincode", event.getLocation().getPincode());
            data.put("landmark", event.getLocation().getLandmark());
        }
        
        if (event.getTimestamp() != null) {
            data.put("timestamp", Date.from(event.getTimestamp().toInstant(ZoneOffset.UTC)));
        }
        
        if (event.getExpiresAt() != null) {
            data.put("expiresAt", Date.from(event.getExpiresAt().toInstant(ZoneOffset.UTC)));
        }
        
        data.put("category", event.getCategory() != null ? event.getCategory().name() : null);
        data.put("severity", event.getSeverity() != null ? event.getSeverity().name() : null);
        data.put("source", event.getSource() != null ? event.getSource().name() : null);
        
        if (event.getSentiment() != null) {
            data.put("sentimentType", event.getSentiment().getType() != null ? 
                event.getSentiment().getType().name() : null);
            data.put("sentimentScore", event.getSentiment().getScore());
            data.put("sentimentConfidence", event.getSentiment().getConfidence());
        }
        
        data.put("confidenceScore", event.getConfidenceScore());
        data.put("keywords", event.getKeywords());
        data.put("aiSummary", event.getAiSummary());
        data.put("rawData", event.getRawData());
        
        if (event.getCreatedAt() != null) {
            data.put("createdAt", Date.from(event.getCreatedAt().toInstant(ZoneOffset.UTC)));
        }
        
        if (event.getUpdatedAt() != null) {
            data.put("updatedAt", Date.from(event.getUpdatedAt().toInstant(ZoneOffset.UTC)));
        }
        
        return data;
    }

    private CityEvent convertFirestoreDocToEvent(DocumentSnapshot doc) {
        try {
            if (!doc.exists()) {
                return null;
            }
            
            Map<String, Object> data = doc.getData();
            if (data == null) {
                return null;
            }
            
            CityEvent.CityEventBuilder builder = CityEvent.builder()
                .id(doc.getId())
                .title((String) data.get("title"))
                .description((String) data.get("description"))
                .content((String) data.get("content"));
            
            // Location data
            if (data.containsKey("latitude") && data.get("latitude") != null) {
                builder.location(CityEvent.LocationData.builder()
                    .latitude(((Number) data.get("latitude")).doubleValue())
                    .longitude(((Number) data.get("longitude")).doubleValue())
                    .address((String) data.get("address"))
                    .area((String) data.get("area"))
                    .pincode((String) data.get("pincode"))
                    .landmark((String) data.get("landmark"))
                    .build());
            }
            
            // Timestamps
            if (data.get("timestamp") instanceof Date) {
                Date timestamp = (Date) data.get("timestamp");
                builder.timestamp(LocalDateTime.ofInstant(timestamp.toInstant(), ZoneOffset.UTC));
            }
            
            if (data.get("expiresAt") instanceof Date) {
                Date expiresAt = (Date) data.get("expiresAt");
                builder.expiresAt(LocalDateTime.ofInstant(expiresAt.toInstant(), ZoneOffset.UTC));
            }
            
            // Enums
            builder.category(parseEnum((String) data.get("category"), CityEvent.EventCategory.class))
                   .severity(parseEnum((String) data.get("severity"), CityEvent.EventSeverity.class))
                   .source(parseEnum((String) data.get("source"), CityEvent.EventSource.class));
            
            // Sentiment
            if (data.containsKey("sentimentType") && data.get("sentimentType") != null) {
                builder.sentiment(CityEvent.SentimentData.builder()
                    .type(parseEnum((String) data.get("sentimentType"), CityEvent.SentimentType.class))
                    .score(data.get("sentimentScore") != null ? 
                        ((Number) data.get("sentimentScore")).doubleValue() : null)
                    .confidence(data.get("sentimentConfidence") != null ? 
                        ((Number) data.get("sentimentConfidence")).doubleValue() : null)
                    .build());
            }
            
            // Other fields
            builder.confidenceScore(data.get("confidenceScore") != null ? 
                    ((Number) data.get("confidenceScore")).doubleValue() : null)
                   .aiSummary((String) data.get("aiSummary"))
                   .rawData((String) data.get("rawData"));
            
            // Keywords
            Object keywordsObj = data.get("keywords");
            if (keywordsObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> keywords = (List<String>) keywordsObj;
                builder.keywords(keywords);
            }
            
            return builder.build();
            
        } catch (Exception e) {
            log.error("Error converting Firestore document to CityEvent: {}", doc.getId(), e);
            return null;
        }
    }

    /**
     * Get recent events from the last specified hours
     */
    public CompletableFuture<List<CityEvent>> getRecentEvents(int hoursBack, int maxResults) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Calculate the timestamp for filtering
                LocalDateTime cutoffTime = LocalDateTime.now().minusHours(hoursBack);
                Date cutoffDate = Date.from(cutoffTime.toInstant(ZoneOffset.UTC));
                
                // Query Firestore for events after the cutoff time
                Query query = firestore.collection(EVENTS_COLLECTION)
                    .whereGreaterThan("timestamp", cutoffDate)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(maxResults);
                
                QuerySnapshot querySnapshot = query.get().get();
                
                List<CityEvent> events = new ArrayList<>();
                for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                    CityEvent event = convertFirestoreDocToEvent(doc);
                    if (event != null) {
                        events.add(event);
                    }
                }
                
                log.debug("Retrieved {} recent events from last {} hours", events.size(), hoursBack);
                return events;
                
            } catch (Exception e) {
                log.error("Error retrieving recent events from Firestore", e);
                return new ArrayList<>();
            }
        });
    }

    private <T extends Enum<T>> T parseEnum(String value, Class<T> enumClass) {
        if (value == null) return null;
        try {
            return Enum.valueOf(enumClass, value);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid enum value {} for class {}", value, enumClass.getSimpleName());
            return null;
        }
    }
} 