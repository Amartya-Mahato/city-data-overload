package com.lemillion.city_data_overload_server.service;

import com.google.cloud.bigquery.*;
import com.lemillion.city_data_overload_server.model.CityEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * BigQuery service for managing city events data warehouse operations.
 * Handles batch operations, analytics queries, and long-term storage.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BigQueryService {

    private final BigQuery bigQuery;
    
    @Value("${gcp.project-id}")
    private String projectId;
    
    private static final String DATASET_ID = "city_data_overload";
    private static final String EVENTS_TABLE_ID = "city_events";
    private static final String PREDICTIONS_TABLE_ID = "predictions";
    private static final String SENTIMENT_TABLE_ID = "sentiment_analysis";
    
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS z");
    private static final DateTimeFormatter SIMPLE_TIMESTAMP_FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Initialize BigQuery dataset and tables if they don't exist
     */
    public void initializeSchema() {
        try {
            createDatasetIfNotExists();
            createEventsTableIfNotExists();
            createPredictionsTableIfNotExists();
            createSentimentTableIfNotExists();
            log.info("BigQuery schema initialization completed successfully");
        } catch (Exception e) {
            log.error("Failed to initialize BigQuery schema", e);
            throw new RuntimeException("BigQuery initialization failed", e);
        }
    }

    /**
     * Store a single city event in BigQuery
     */
    public void storeCityEvent(CityEvent event) {
        try {
            TableId tableId = TableId.of(projectId, DATASET_ID, EVENTS_TABLE_ID);
            
            Map<String, Object> rowContent = convertEventToRowMap(event);
            
            InsertAllRequest insertRequest = InsertAllRequest.newBuilder(tableId)
                .addRow(UUID.randomUUID().toString(), rowContent)
                .build();
                
            InsertAllResponse response = bigQuery.insertAll(insertRequest);
            
            if (response.hasErrors()) {
                response.getInsertErrors().forEach((key, errors) -> {
                    log.error("BigQuery insert error for row {}: {}", key, errors);
                });
                throw new RuntimeException("Failed to insert event into BigQuery");
            }
            
            log.debug("Successfully stored event in BigQuery: {}", event.getId());
        } catch (Exception e) {
            log.error("Error storing city event in BigQuery: {}", event.getId(), e);
            throw new RuntimeException("BigQuery storage failed", e);
        }
    }

    /**
     * Store multiple city events in batch
     */
    public void storeCityEventsBatch(List<CityEvent> events) {
        if (events.isEmpty()) {
            return;
        }
        
        try {
            TableId tableId = TableId.of(projectId, DATASET_ID, EVENTS_TABLE_ID);
            
            InsertAllRequest.Builder requestBuilder = InsertAllRequest.newBuilder(tableId);
            
            for (CityEvent event : events) {
                Map<String, Object> rowContent = convertEventToRowMap(event);
                requestBuilder.addRow(UUID.randomUUID().toString(), rowContent);
            }
            
            InsertAllResponse response = bigQuery.insertAll(requestBuilder.build());
            
            if (response.hasErrors()) {
                response.getInsertErrors().forEach((key, errors) -> {
                    log.error("BigQuery batch insert error for row {}: {}", key, errors);
                });
                throw new RuntimeException("Failed to batch insert events into BigQuery");
            }
            
            log.info("Successfully stored {} events in BigQuery batch", events.size());
        } catch (Exception e) {
            log.error("Error storing city events batch in BigQuery", e);
            throw new RuntimeException("BigQuery batch storage failed", e);
        }
    }

    /**
     * Query events by location and time range
     */
    public List<CityEvent> queryEventsByLocationAndTime(
            double latitude, double longitude, double radiusKm,
            LocalDateTime startTime, LocalDateTime endTime, int maxResults) {
        
        // Use simpler distance calculation for now since BigQuery may not have geographic setup
        String query = """
            SELECT *
            FROM `%s.%s.%s`
            WHERE timestamp BETWEEN '%s' AND '%s'
            AND latitude BETWEEN %f AND %f
            AND longitude BETWEEN %f AND %f
            ORDER BY timestamp DESC
            LIMIT %d
            """.formatted(
                projectId, DATASET_ID, EVENTS_TABLE_ID,
                startTime.format(SIMPLE_TIMESTAMP_FORMATTER),
                endTime.format(SIMPLE_TIMESTAMP_FORMATTER),
                latitude - (radiusKm / 111.0), latitude + (radiusKm / 111.0), // Approximate lat range
                longitude - (radiusKm / (111.0 * Math.cos(Math.toRadians(latitude)))), 
                longitude + (radiusKm / (111.0 * Math.cos(Math.toRadians(latitude)))), // Approximate lng range
                maxResults
            );
            
        log.info("Executing BigQuery location query: {}", query);
        return executeQueryAndMapToEvents(query);
    }

    /**
     * Query events by category and severity
     */
    public List<CityEvent> queryEventsByCategoryAndSeverity(
            CityEvent.EventCategory category, CityEvent.EventSeverity severity,
            LocalDateTime since, int maxResults) {
        
        String categoryFilter = category != null ? 
            "AND category = '" + category.name() + "'" : "";
        
        String query = """
            SELECT *
            FROM `%s.%s.%s`
            WHERE severity = '%s'
            %s
            AND timestamp >= '%s'
            ORDER BY timestamp DESC
            LIMIT %d
            """.formatted(
                projectId, DATASET_ID, EVENTS_TABLE_ID,
                severity.name(),
                categoryFilter,
                since.format(SIMPLE_TIMESTAMP_FORMATTER),
                maxResults
            );
            
        log.info("Executing BigQuery category/severity query: {}", query);
        return executeQueryAndMapToEvents(query);
    }

    /**
     * Get event statistics for analytics
     */
    public Map<String, Object> getEventStatistics(LocalDateTime since) {
                    String query = """
            SELECT 
                category,
                severity,
                COUNT(*) as count,
                AVG(confidence_score) as avg_confidence,
                COUNT(DISTINCT area) as areas_affected
            FROM `%s.%s.%s`
            WHERE timestamp >= '%s'
            GROUP BY category, severity
            ORDER BY count DESC
            """.formatted(
                projectId, DATASET_ID, EVENTS_TABLE_ID,
                since.format(SIMPLE_TIMESTAMP_FORMATTER)
            );
            
        try {
            QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query).build();
            TableResult result = bigQuery.query(queryConfig);
            
            List<Map<String, Object>> stats = new ArrayList<>();
            for (FieldValueList row : result.iterateAll()) {
                stats.add(Map.of(
                    "category", row.get("category").getStringValue(),
                    "severity", row.get("severity").getStringValue(),
                    "count", row.get("count").getLongValue(),
                    "avgConfidence", row.get("avg_confidence").getDoubleValue(),
                    "areasAffected", row.get("areas_affected").getLongValue()
                ));
            }
            
            return Map.of(
                "statistics", stats,
                "totalEvents", stats.stream().mapToLong(s -> (Long) s.get("count")).sum(),
                "generatedAt", LocalDateTime.now()
            );
            
        } catch (Exception e) {
            log.error("Error executing statistics query", e);
            throw new RuntimeException("Failed to get event statistics", e);
        }
    }

    /**
     * Query for predictive analysis patterns
     */
    public List<Map<String, Object>> queryPredictivePatterns(
            CityEvent.EventCategory category, int daysPast) {

        if (category == null) {
            return new ArrayList<>();
        }

        if (daysPast <= 0) {
            return new ArrayList<>();
        }
        
        // More flexible query that groups by broader time patterns
        String query = """
            WITH time_patterns AS (
                SELECT 
                    category,
                    CASE 
                        WHEN EXTRACT(HOUR FROM timestamp) BETWEEN 6 AND 10 THEN 'morning_rush'
                        WHEN EXTRACT(HOUR FROM timestamp) BETWEEN 11 AND 16 THEN 'afternoon'
                        WHEN EXTRACT(HOUR FROM timestamp) BETWEEN 17 AND 21 THEN 'evening_rush'
                        ELSE 'off_peak'
                    END as time_period,
                    CASE 
                        WHEN EXTRACT(DAYOFWEEK FROM timestamp) IN (1, 7) THEN 'weekend'
                        ELSE 'weekday'
                    END as day_type,
                    area,
                    severity,
                    COUNT(*) as frequency,
                    AVG(confidence_score) as avg_confidence
                FROM `%s.%s.%s`
                WHERE category = '%s'
                AND timestamp >= TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL %d DAY)
                GROUP BY category, time_period, day_type, area, severity
            ),
            area_patterns AS (
                SELECT 
                    category,
                    time_period,
                    day_type,
                    'citywide' as area,
                    severity,
                    SUM(frequency) as frequency,
                    AVG(avg_confidence) as avg_confidence
                FROM time_patterns
                GROUP BY category, time_period, day_type, severity
            )
            SELECT * FROM time_patterns WHERE frequency >= 1
            UNION ALL
            SELECT * FROM area_patterns WHERE frequency >= 2
            ORDER BY frequency DESC
            """.formatted(
                projectId, DATASET_ID, EVENTS_TABLE_ID,
                category.name(), daysPast
            );
            
        try {
            QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query).build();
            TableResult result = bigQuery.query(queryConfig);
            
            List<Map<String, Object>> patterns = new ArrayList<>();
            for (FieldValueList row : result.iterateAll()) {
                patterns.add(Map.of(
                    "category", row.get("category").getStringValue(),
                    "timePeriod", row.get("time_period").getStringValue(),
                    "dayType", row.get("day_type").getStringValue(),
                    "area", row.get("area").getStringValue(),
                    "severity", row.get("severity").getStringValue(),
                    "frequency", row.get("frequency").getLongValue(),
                    "avgConfidence", row.get("avg_confidence").getDoubleValue()
                ));
            }
            
            log.info("Found {} patterns for category {} over {} days", patterns.size(), category, daysPast);
            
            // If we have very few patterns, add some general category patterns
            if (patterns.size() < 3) {
                patterns.addAll(queryGeneralCategoryPatterns(category, daysPast * 2));
            }
            
            return patterns;
            
        } catch (Exception e) {
            log.error("Error executing predictive patterns query", e);
            throw new RuntimeException("Failed to query predictive patterns", e);
        }
    }

    /**
     * Query for general category patterns when specific patterns are insufficient
     */
    private List<Map<String, Object>> queryGeneralCategoryPatterns(
            CityEvent.EventCategory category, int daysPast) {
        
        String query = """
            SELECT 
                category,
                'general' as time_period,
                'all_days' as day_type,
                'citywide' as area,
                severity,
                COUNT(*) as frequency,
                AVG(confidence_score) as avg_confidence
            FROM `%s.%s.%s`
            WHERE category = '%s'
            AND timestamp >= TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL %d DAY)
            GROUP BY category, severity
            HAVING COUNT(*) >= 1
            ORDER BY frequency DESC
            """.formatted(
                projectId, DATASET_ID, EVENTS_TABLE_ID,
                category.name(), daysPast
            );
            
        try {
            QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query).build();
            TableResult result = bigQuery.query(queryConfig);
            
            List<Map<String, Object>> patterns = new ArrayList<>();
            for (FieldValueList row : result.iterateAll()) {
                patterns.add(Map.of(
                    "category", row.get("category").getStringValue(),
                    "timePeriod", row.get("time_period").getStringValue(),
                    "dayType", row.get("day_type").getStringValue(),
                    "area", row.get("area").getStringValue(),
                    "severity", row.get("severity").getStringValue(),
                    "frequency", row.get("frequency").getLongValue(),
                    "avgConfidence", row.get("avg_confidence").getDoubleValue()
                ));
            }
            
            log.info("Found {} general patterns for category {}", patterns.size(), category);
            return patterns;
            
        } catch (Exception e) {
            log.warn("Error executing general patterns query for category {}", category, e);
            return new ArrayList<>();
        }
    }

    // Private helper methods

    private void createDatasetIfNotExists() {
        try {
            Dataset dataset = bigQuery.getDataset(DATASET_ID);
            if (dataset == null) {
                DatasetInfo datasetInfo = DatasetInfo.newBuilder(DATASET_ID)
                    .setDescription("City Data Overload - Bengaluru Events Dataset")
                    .setLocation("US")
                    .build();
                bigQuery.create(datasetInfo);
                log.info("Created BigQuery dataset: {}", DATASET_ID);
            }
        } catch (Exception e) {
            log.error("Error creating dataset", e);
            throw e;
        }
    }

    private void createEventsTableIfNotExists() {
        TableId tableId = TableId.of(DATASET_ID, EVENTS_TABLE_ID);
        Table table = bigQuery.getTable(tableId);
        
        if (table == null) {
            Schema schema = Schema.of(
                Field.of("id", StandardSQLTypeName.STRING),
                Field.of("title", StandardSQLTypeName.STRING),
                Field.of("description", StandardSQLTypeName.STRING),
                Field.of("content", StandardSQLTypeName.STRING),
                Field.of("latitude", StandardSQLTypeName.FLOAT64),
                Field.of("longitude", StandardSQLTypeName.FLOAT64),
                Field.of("address", StandardSQLTypeName.STRING),
                Field.of("area", StandardSQLTypeName.STRING),
                Field.of("pincode", StandardSQLTypeName.STRING),
                Field.of("timestamp", StandardSQLTypeName.TIMESTAMP),
                Field.of("expires_at", StandardSQLTypeName.TIMESTAMP),
                Field.of("category", StandardSQLTypeName.STRING),
                Field.of("severity", StandardSQLTypeName.STRING),
                Field.of("source", StandardSQLTypeName.STRING),
                Field.of("sentiment_type", StandardSQLTypeName.STRING),
                Field.of("sentiment_score", StandardSQLTypeName.FLOAT64),
                Field.of("confidence_score", StandardSQLTypeName.FLOAT64),
                Field.of("keywords", StandardSQLTypeName.STRING),
                Field.of("ai_summary", StandardSQLTypeName.STRING),
                Field.of("raw_data", StandardSQLTypeName.STRING),
                Field.of("created_at", StandardSQLTypeName.TIMESTAMP),
                Field.of("updated_at", StandardSQLTypeName.TIMESTAMP)
            );
            
            TableDefinition tableDefinition = StandardTableDefinition.of(schema);
            TableInfo tableInfo = TableInfo.newBuilder(tableId, tableDefinition)
                .setDescription("City events data for Bengaluru")
                .build();
                
            bigQuery.create(tableInfo);
            log.info("Created BigQuery table: {}", EVENTS_TABLE_ID);
        }
    }

    private void createPredictionsTableIfNotExists() {
        // Similar implementation for predictions table
        log.info("Predictions table creation - placeholder");
    }

    private void createSentimentTableIfNotExists() {
        // Similar implementation for sentiment table
        log.info("Sentiment table creation - placeholder");
    }

    private Map<String, Object> convertEventToRowMap(CityEvent event) {
        Map<String, Object> row = new java.util.HashMap<>();
        
        row.put("id", event.getId());
        row.put("title", event.getTitle());
        row.put("description", event.getDescription());
        row.put("content", event.getContent());
        
        if (event.getLocation() != null) {
            row.put("latitude", event.getLocation().getLatitude());
            row.put("longitude", event.getLocation().getLongitude());
            row.put("address", event.getLocation().getAddress());
            row.put("area", event.getLocation().getArea());
            row.put("pincode", event.getLocation().getPincode());
        }
        
        row.put("timestamp", event.getTimestamp() != null ? 
            event.getTimestamp().format(SIMPLE_TIMESTAMP_FORMATTER) : null);
        row.put("expires_at", event.getExpiresAt() != null ? 
            event.getExpiresAt().format(SIMPLE_TIMESTAMP_FORMATTER) : null);
        row.put("category", event.getCategory() != null ? event.getCategory().name() : null);
        row.put("severity", event.getSeverity() != null ? event.getSeverity().name() : null);
        row.put("source", event.getSource() != null ? event.getSource().name() : null);
        
        if (event.getSentiment() != null) {
            row.put("sentiment_type", event.getSentiment().getType() != null ? 
                event.getSentiment().getType().name() : null);
            row.put("sentiment_score", event.getSentiment().getScore());
        }
        
        row.put("confidence_score", event.getConfidenceScore());
        row.put("keywords", event.getKeywords() != null ? 
            String.join(",", event.getKeywords()) : null);
        row.put("ai_summary", event.getAiSummary());
        row.put("raw_data", event.getRawData());
        row.put("created_at", event.getCreatedAt() != null ? 
            event.getCreatedAt().format(SIMPLE_TIMESTAMP_FORMATTER) : null);
        row.put("updated_at", event.getUpdatedAt() != null ? 
            event.getUpdatedAt().format(SIMPLE_TIMESTAMP_FORMATTER) : null);
        
        return row;
    }

    /**
     * Get all recent events (simple query for testing)
     */
    public List<CityEvent> queryAllRecentEvents(int maxResults) {
        String query = """
            SELECT *
            FROM `%s.%s.%s`
            ORDER BY timestamp DESC
            LIMIT %d
            """.formatted(projectId, DATASET_ID, EVENTS_TABLE_ID, maxResults);
            
        log.info("Executing BigQuery recent events query: {}", query);
        return executeQueryAndMapToEvents(query);
    }

    private List<CityEvent> executeQueryAndMapToEvents(String query) {
        try {
            log.info("Executing BigQuery query: {}", query);
            QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query).build();
            TableResult result = bigQuery.query(queryConfig);
            
            List<CityEvent> events = new ArrayList<>();
            int rowCount = 0;
            
            for (FieldValueList row : result.iterateAll()) {
                try {
                    CityEvent event = mapRowToCityEvent(row);
                    if (event != null) {
                        events.add(event);
                        rowCount++;
                    }
                } catch (Exception e) {
                    log.warn("Failed to map row {} to CityEvent", rowCount, e);
                }
            }
            
            log.info("Successfully retrieved {} events from BigQuery", events.size());
            return events;
            
        } catch (Exception e) {
            log.error("Error executing BigQuery query: {}", query, e);
            // Return empty list instead of throwing to allow graceful fallback
            return new ArrayList<>();
        }
    }

    private CityEvent mapRowToCityEvent(FieldValueList row) {
        try {
            // Convert BigQuery row back to CityEvent object
            CityEvent.CityEventBuilder builder = CityEvent.builder()
                .id(getStringValue(row, "id"))
                .title(getStringValue(row, "title"))
                .description(getStringValue(row, "description"))
                .content(getStringValue(row, "content"));
                
            // Add location if present
            if (row.get("latitude") != null && !row.get("latitude").isNull()) {
                builder.location(CityEvent.LocationData.builder()
                    .latitude(row.get("latitude").getDoubleValue())
                    .longitude(row.get("longitude").getDoubleValue())
                    .address(getStringValue(row, "address"))
                    .area(getStringValue(row, "area"))
                    .pincode(getStringValue(row, "pincode"))
                    .build());
            }
            
            // Add timestamps with flexible parsing (handle both string and numeric formats)
            if (row.get("timestamp") != null && !row.get("timestamp").isNull()) {
                try {
                    FieldValue timestampField = row.get("timestamp");
                    LocalDateTime timestamp = null;
                    
                    if (timestampField.getAttribute() == FieldValue.Attribute.PRIMITIVE) {
                        // Check if it's a numeric timestamp (epoch seconds)
                        try {
                            double epochSeconds = timestampField.getDoubleValue();
                            timestamp = LocalDateTime.ofEpochSecond((long) epochSeconds, 0, java.time.ZoneOffset.UTC);
                        } catch (Exception e) {
                            // Try as string
                            String timestampStr = timestampField.getStringValue();
                            try {
                                // Try full format first (with microseconds and UTC)
                                timestamp = LocalDateTime.parse(timestampStr, TIMESTAMP_FORMATTER);
                            } catch (Exception e2) {
                                try {
                                    // Try simple format
                                    timestamp = LocalDateTime.parse(timestampStr, SIMPLE_TIMESTAMP_FORMATTER);
                                } catch (Exception e3) {
                                    log.warn("Could not parse timestamp string: {}", timestampStr);
                                }
                            }
                        }
                    }
                    
                    if (timestamp != null) {
                        builder.timestamp(timestamp);
                    }
                } catch (Exception e) {
                    log.warn("Could not process timestamp field", e);
                }
            }
            
            if (row.get("expires_at") != null && !row.get("expires_at").isNull()) {
                try {
                    FieldValue expiresAtField = row.get("expires_at");
                    LocalDateTime expiresAt = null;
                    
                    if (expiresAtField.getAttribute() == FieldValue.Attribute.PRIMITIVE) {
                        try {
                            double epochSeconds = expiresAtField.getDoubleValue();
                            expiresAt = LocalDateTime.ofEpochSecond((long) epochSeconds, 0, java.time.ZoneOffset.UTC);
                        } catch (Exception e) {
                            String expiresAtStr = expiresAtField.getStringValue();
                            try {
                                expiresAt = LocalDateTime.parse(expiresAtStr, TIMESTAMP_FORMATTER);
                            } catch (Exception e2) {
                                try {
                                    expiresAt = LocalDateTime.parse(expiresAtStr, SIMPLE_TIMESTAMP_FORMATTER);
                                } catch (Exception e3) {
                                    log.warn("Could not parse expires_at string: {}", expiresAtStr);
                                }
                            }
                        }
                    }
                    
                    if (expiresAt != null) {
                        builder.expiresAt(expiresAt);
                    }
                } catch (Exception e) {
                    log.warn("Could not process expires_at field", e);
                }
            }
            
            if (row.get("created_at") != null && !row.get("created_at").isNull()) {
                try {
                    FieldValue createdAtField = row.get("created_at");
                    LocalDateTime createdAt = null;
                    
                    if (createdAtField.getAttribute() == FieldValue.Attribute.PRIMITIVE) {
                        try {
                            double epochSeconds = createdAtField.getDoubleValue();
                            createdAt = LocalDateTime.ofEpochSecond((long) epochSeconds, 0, java.time.ZoneOffset.UTC);
                        } catch (Exception e) {
                            String createdAtStr = createdAtField.getStringValue();
                            try {
                                createdAt = LocalDateTime.parse(createdAtStr, TIMESTAMP_FORMATTER);
                            } catch (Exception e2) {
                                try {
                                    createdAt = LocalDateTime.parse(createdAtStr, SIMPLE_TIMESTAMP_FORMATTER);
                                } catch (Exception e3) {
                                    log.warn("Could not parse created_at string: {}", createdAtStr);
                                }
                            }
                        }
                    }
                    
                    if (createdAt != null) {
                        builder.createdAt(createdAt);
                    }
                } catch (Exception e) {
                    log.warn("Could not process created_at field", e);
                }
            }
            
            // Add enums
            builder.category(parseEnum(getStringValue(row, "category"), CityEvent.EventCategory.class))
                   .severity(parseEnum(getStringValue(row, "severity"), CityEvent.EventSeverity.class))
                   .source(parseEnum(getStringValue(row, "source"), CityEvent.EventSource.class));
            
            // Add sentiment data
            String sentimentType = getStringValue(row, "sentiment_type");
            Double sentimentScore = getDoubleValue(row, "sentiment_score");
            if (sentimentType != null || sentimentScore != null) {
                builder.sentiment(CityEvent.SentimentData.builder()
                    .type(parseEnum(sentimentType, CityEvent.SentimentType.class))
                    .score(sentimentScore)
                    .confidence(0.8) // Default confidence if not provided
                    .build());
            }
            
            // Add other fields
            builder.confidenceScore(getDoubleValue(row, "confidence_score"))
                   .aiSummary(getStringValue(row, "ai_summary"))
                   .rawData(getStringValue(row, "raw_data"));
            
            // Parse keywords from comma-separated string
            String keywordsStr = getStringValue(row, "keywords");
            if (keywordsStr != null && !keywordsStr.trim().isEmpty()) {
                builder.keywords(List.of(keywordsStr.split(",")));
            }
            
            CityEvent event = builder.build();
            log.debug("Successfully mapped BigQuery row to CityEvent: {}", event.getId());
            return event;
            
        } catch (Exception e) {
            log.error("Error mapping BigQuery row to CityEvent", e);
            // Return a basic event to avoid complete failure
            return CityEvent.builder()
                .id(getStringValue(row, "id"))
                .title(getStringValue(row, "title"))
                .description(getStringValue(row, "description"))
                .category(CityEvent.EventCategory.COMMUNITY)
                .severity(CityEvent.EventSeverity.LOW)
                .source(CityEvent.EventSource.SYSTEM_GENERATED)
                .timestamp(LocalDateTime.now())
                .build();
        }
    }

    private String getStringValue(FieldValueList row, String fieldName) {
        FieldValue field = row.get(fieldName);
        return (field != null && !field.isNull()) ? field.getStringValue() : null;
    }

    private Double getDoubleValue(FieldValueList row, String fieldName) {
        FieldValue field = row.get(fieldName);
        return (field != null && !field.isNull()) ? field.getDoubleValue() : null;
    }

    private <T extends Enum<T>> T parseEnum(String value, Class<T> enumClass) {
        if (value == null) return null;
        try {
            return Enum.valueOf(enumClass, value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}