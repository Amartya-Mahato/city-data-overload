package com.lemillion.city_data_overload_server.service.fetcher;

import com.lemillion.city_data_overload_server.model.CityEvent;
import com.lemillion.city_data_overload_server.model.serpapi.SerpApiResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Base interface for all SerpApi data fetchers
 */
public interface BaseSerpApiFetcher {
    
    /**
     * Fetch raw data from SerpApi
     */
    CompletableFuture<SerpApiResponse> fetchRawData(String query, String location);
    
    /**
     * Convert SerpApi response to CityEvent objects
     */
    List<CityEvent> convertToCityEvents(SerpApiResponse response);
    
    /**
     * Get the category this fetcher handles
     */
    CityEvent.EventCategory getCategory();
    
    /**
     * Get default search queries for this category
     */
    List<String> getDefaultQueries();
    
    /**
     * Fetch and process data for this category
     */
    default CompletableFuture<List<CityEvent>> fetchCategoryData(String location) {
        return CompletableFuture.supplyAsync(() -> {
            List<CityEvent> allEvents = new java.util.ArrayList<>();
            
            for (String query : getDefaultQueries()) {
                try {
                    SerpApiResponse response = fetchRawData(query, location).get();
                    if (response != null) {
                        List<CityEvent> events = convertToCityEvents(response);
                        allEvents.addAll(events);
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Failed to fetch data for query: " + query, e);
                }
            }
            
            return allEvents;
        });
    }
    
    /**
     * Determine event severity based on keywords and content
     */
    default CityEvent.EventSeverity determineSeverity(String title, String content) {
        String combined = (title + " " + content).toLowerCase();
        
        if (combined.contains("emergency") || combined.contains("urgent") || 
            combined.contains("critical") || combined.contains("severe") ||
            combined.contains("major accident") || combined.contains("flood") ||
            combined.contains("fire") || combined.contains("bomb")) {
            return CityEvent.EventSeverity.CRITICAL;
        }
        
        if (combined.contains("alert") || combined.contains("warning") || 
            combined.contains("blocked") || combined.contains("disruption") ||
            combined.contains("outage") || combined.contains("breakdown")) {
            return CityEvent.EventSeverity.HIGH;
        }
        
        if (combined.contains("slow") || combined.contains("delayed") || 
            combined.contains("issue") || combined.contains("problem")) {
            return CityEvent.EventSeverity.MODERATE;
        }
        
        return CityEvent.EventSeverity.LOW;
    }
    
    /**
     * Extract location information from text
     */
    default CityEvent.LocationData extractLocation(String text, String defaultLocation) {
        // Simple location extraction - can be enhanced with NLP
        CityEvent.LocationData.LocationDataBuilder builder = CityEvent.LocationData.builder();
        
        if (text != null) {
            String lowerText = text.toLowerCase();
            
            // Common Bengaluru areas
            String[] areas = {
                "koramangala", "indiranagar", "whitefield", "electronic city", "btm layout",
                "jayanagar", "malleshwaram", "rajajinagar", "banashankari", "basavanagudi",
                "hsr layout", "sarjapur", "bellandur", "marathahalli", "hebbal", "yemalur",
                "kr puram", "bommanahalli", "jp nagar", "vijayanagar", "mg road", "brigade road",
                "commercial street", "chickpet", "shivajinagar", "majestic", "cantonment"
            };
            
            for (String area : areas) {
                if (lowerText.contains(area)) {
                    builder.area(area);
                    break;
                }
            }
            
            builder.address(defaultLocation);
        }
        
        return builder.build();
    }
    
    /**
     * Generate TTL based on event category and severity
     */
    default LocalDateTime calculateExpiration(CityEvent.EventCategory category, CityEvent.EventSeverity severity) {
        int hours;
        
        switch (severity) {
            case CRITICAL:
                hours = 2;
                break;
            case HIGH:
                hours = 6;
                break;
            case MODERATE:
                hours = 12;
                break;
            default:
                hours = 24;
        }
        
        // Adjust based on category
        switch (category) {
            case TRAFFIC:
                hours = Math.min(hours, 4); // Traffic events are usually short-lived
                break;
            case WEATHER:
                hours = Math.max(hours, 8); // Weather events can last longer
                break;
            case CULTURAL_EVENT:
                hours = Math.max(hours, 48); // Events last longer
                break;
        }
        
        return LocalDateTime.now().plusHours(hours);
    }
} 