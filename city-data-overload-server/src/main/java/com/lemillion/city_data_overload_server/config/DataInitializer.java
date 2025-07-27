package com.lemillion.city_data_overload_server.config;

import com.lemillion.city_data_overload_server.service.BigQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Data initializer for City Data Overload application.
 * Sets up BigQuery schema and performs initial data setup on startup.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements ApplicationRunner {

    private final BigQueryService bigQueryService;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("Starting City Data Overload data initialization...");
        
        try {
            // Initialize BigQuery schema
            log.info("Initializing BigQuery schema...");
            bigQueryService.initializeSchema();
            log.info("BigQuery schema initialization completed successfully");
            
            // Additional initialization tasks can be added here
            // For example:
            // - Seed data for testing
            // - Cache warming
            // - External service health checks
            
            log.info("City Data Overload data initialization completed successfully");
            
        } catch (Exception e) {
            log.error("Failed to initialize City Data Overload data", e);
            // In production, you might want to fail fast here
            // throw new RuntimeException("Data initialization failed", e);
        }
    }
} 