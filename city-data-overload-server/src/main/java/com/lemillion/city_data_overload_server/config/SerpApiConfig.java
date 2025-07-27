package com.lemillion.city_data_overload_server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for SerpApi settings
 */
@Configuration
@ConfigurationProperties(prefix = "serpapi")
@Data
public class SerpApiConfig {
    
    private String apiKey;
    private String defaultLocation = "Bengaluru, Karnataka, India";
    private String defaultLanguage = "en";
    private String defaultCountry = "in";
    private int maxResults = 10;
    private int timeoutMs = 10000;
    
    // Search engine configurations
    private GoogleConfig google = new GoogleConfig();
    private NewsConfig news = new NewsConfig();
    
    // Scheduler configurations  
    private SchedulerConfig scheduler = new SchedulerConfig();
    
    @Data
    public static class GoogleConfig {
        private String domain = "google.co.in";
        private boolean safeSearch = false;
        private String dateRange = "d"; // Last day
    }
    
    @Data
    public static class NewsConfig {
        private String sortBy = "date";
        private String timeRange = "24h";
        private boolean includeDuplicates = false;
    }
    
    @Data
    public static class SchedulerConfig {
        private boolean enabled = false;
        private long highPriorityInterval = 900000;    // 15 minutes
        private long standardInterval = 3600000;       // 1 hour
        private long healthCheckInterval = 1800000;    // 30 minutes
        private long emergencyMonitoringInterval = 300000; // 5 minutes
        
        private TimeWindowsConfig timeWindows = new TimeWindowsConfig();
        
        @Data
        public static class TimeWindowsConfig {
            private int highPriority = 10;        // 10 minutes
            private int standard = 45;            // 45 minutes
            private int emergencyMonitoring = 3;  // 3 minutes  
            private int healthCheck = 25;         // 25 minutes
        }
    }
    
    public String getApiKey() {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("SerpApi API key is not configured. Please set serpapi.api-key in application.yml");
        }
        return apiKey;
    }
} 