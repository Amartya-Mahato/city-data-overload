package com.lemillion.city_data_overload_server.service.fetcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemillion.city_data_overload_server.config.SerpApiConfig;
import com.lemillion.city_data_overload_server.model.CityEvent;
import com.lemillion.city_data_overload_server.model.serpapi.SerpApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Fetcher for emergency-related data from SerpApi
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmergencyDataFetcher implements BaseSerpApiFetcher {

    private final SerpApiConfig serpApiConfig;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Override
    public CompletableFuture<SerpApiResponse> fetchRawData(String query, String location) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = UriComponentsBuilder.fromHttpUrl("https://serpapi.com/search")
                    .queryParam("engine", "google")
                    .queryParam("q", query + " " + location)
                    .queryParam("location", location)
                    .queryParam("hl", serpApiConfig.getDefaultLanguage())
                    .queryParam("gl", serpApiConfig.getDefaultCountry())
                    .queryParam("google_domain", serpApiConfig.getGoogle().getDomain())
                    .queryParam("num", serpApiConfig.getMaxResults())
                    .queryParam("tbm", "nws") // News search for emergency updates
                    .queryParam("tbs", "qdr:h") // Last hour for emergency data
                    .queryParam("api_key", serpApiConfig.getApiKey())
                    .build()
                    .toUriString();
                
                String jsonResponse = restTemplate.getForObject(url, String.class);
                
                if (jsonResponse == null) {
                    log.warn("Received null response from SerpApi for emergency query: {}", query);
                    return null;
                }
                
                SerpApiResponse response = objectMapper.readValue(jsonResponse, SerpApiResponse.class);
                
                // Handle "no results" case gracefully
                if (response != null && response.hasError()) {
                    String errorMsg = response.getErrorMessage();
                    if (errorMsg != null && errorMsg.contains("Google hasn't returned any results")) {
                        log.debug("No emergency results found for query: {} - this is normal", query);
                        // Return an empty response instead of null
                        response = new SerpApiResponse();
                    } else {
                        log.warn("SerpApi error for emergency query '{}': {}", query, errorMsg);
                    }
                }
                
                return response;
                
            } catch (Exception e) {
                log.error("Error fetching emergency data for query: {}", query, e);
                return null;
            }
        });
    }

    @Override
    public List<CityEvent> convertToCityEvents(SerpApiResponse response) {
        List<CityEvent> events = new ArrayList<>();
        
        if (response == null) {
            return events;
        }

        // Process news results for emergency content
        if (response.getNewsResults() != null) {
            for (SerpApiResponse.NewsResult newsResult : response.getNewsResults()) {
                if (isEmergencyRelated(newsResult.getTitle(), newsResult.getSnippet())) {
                    CityEvent event = createEventFromNews(newsResult);
                    if (event != null) {
                        events.add(event);
                    }
                }
            }
        }

        // Process organic results for emergency-related content
        if (response.getOrganicResults() != null) {
            for (SerpApiResponse.OrganicResult organicResult : response.getOrganicResults()) {
                if (isEmergencyRelated(organicResult.getTitle(), organicResult.getSnippet())) {
                    CityEvent event = createEventFromOrganic(organicResult);
                    if (event != null) {
                        events.add(event);
                    }
                }
            }
        }

        return events;
    }

    @Override
    public CityEvent.EventCategory getCategory() {
        return CityEvent.EventCategory.EMERGENCY;
    }

    @Override
    public List<String> getDefaultQueries() {
        return Arrays.asList(
            "emergency alert Bengaluru today",
            "fire emergency Bangalore",
            "police emergency Bengaluru",
            "ambulance emergency Bangalore",
            "disaster alert Bengaluru",
            "bomb threat Bangalore",
            "terrorist alert Bengaluru",
            "chemical leak emergency Bangalore",
            "gas leak emergency Bengaluru",
            "building collapse Bangalore",
            "flood emergency Bengaluru",
            "earthquake alert Bangalore"
        );
    }

    private CityEvent createEventFromNews(SerpApiResponse.NewsResult newsResult) {
        try {
            String title = newsResult.getTitle();
            String snippet = newsResult.getSnippet() != null ? newsResult.getSnippet() : "";
            
            CityEvent.EventSeverity severity = determineSeverity(title, snippet);
            // Emergency events should have higher severity by default
            if (severity == CityEvent.EventSeverity.LOW) {
                severity = CityEvent.EventSeverity.HIGH;
            }
            
            CityEvent.LocationData location = extractLocation(title + " " + snippet, serpApiConfig.getDefaultLocation());

            return CityEvent.builder()
                    .id(UUID.randomUUID().toString())
                    .title(title)
                    .description(snippet)
                    .content(snippet)
                    .location(location)
                    .timestamp(LocalDateTime.now())
                    .expiresAt(calculateExpiration(getCategory(), severity))
                    .category(getCategory())
                    .severity(severity)
                    .source(CityEvent.EventSource.SERP)
                    .confidenceScore(0.9) // Emergency data usually high confidence
                    .keywords(extractKeywords(title, snippet))
                    .socialMetrics(CityEvent.SocialMetrics.builder()
                            .likes(0)
                            .shares(0)
                            .comments(0)
                            .build())
                    .metadata(createMetadata(newsResult))
                    .rawData(newsResult.toString())
                    .version(1)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
                    
        } catch (Exception e) {
            log.error("Error creating event from emergency news result: {}", newsResult.getTitle(), e);
            return null;
        }
    }

    private CityEvent createEventFromOrganic(SerpApiResponse.OrganicResult organicResult) {
        try {
            String title = organicResult.getTitle();
            String snippet = organicResult.getSnippet() != null ? organicResult.getSnippet() : "";
            
            CityEvent.EventSeverity severity = determineSeverity(title, snippet);
            if (severity == CityEvent.EventSeverity.LOW) {
                severity = CityEvent.EventSeverity.MODERATE;
            }
            
            CityEvent.LocationData location = extractLocation(title + " " + snippet, serpApiConfig.getDefaultLocation());

            return CityEvent.builder()
                    .id(UUID.randomUUID().toString())
                    .title(title)
                    .description(snippet)
                    .content(snippet)
                    .location(location)
                    .timestamp(LocalDateTime.now())
                    .expiresAt(calculateExpiration(getCategory(), severity))
                    .category(getCategory())
                    .severity(severity)
                    .source(CityEvent.EventSource.SERP)
                    .confidenceScore(0.8)
                    .keywords(extractKeywords(title, snippet))
                    .socialMetrics(CityEvent.SocialMetrics.builder()
                            .likes(0)
                            .shares(0)
                            .comments(0)
                            .build())
                    .metadata(createMetadata(organicResult))
                    .rawData(organicResult.toString())
                    .version(1)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
                    
        } catch (Exception e) {
            log.error("Error creating event from emergency organic result: {}", organicResult.getTitle(), e);
            return null;
        }
    }

    private boolean isEmergencyRelated(String title, String snippet) {
        String combined = (title + " " + snippet).toLowerCase();
        String[] emergencyKeywords = {
            "emergency", "urgent", "alert", "fire", "police", "ambulance", "bomb", 
            "terrorist", "explosion", "disaster", "evacuation", "rescue", "critical",
            "danger", "hazard", "chemical", "gas leak", "collapse", "flood", 
            "earthquake", "accident", "medical emergency", "cardiac arrest"
        };
        
        for (String keyword : emergencyKeywords) {
            if (combined.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private List<String> extractKeywords(String title, String content) {
        Set<String> keywords = new HashSet<>();
        String combined = (title + " " + content).toLowerCase();
        
        String[] emergencyKeywords = {
            "emergency", "fire", "police", "ambulance", "disaster", "alert", "urgent",
            "evacuation", "rescue", "bomb", "terrorist", "explosion", "critical"
        };
        
        for (String keyword : emergencyKeywords) {
            if (combined.contains(keyword)) {
                keywords.add(keyword);
            }
        }
        
        return new ArrayList<>(keywords);
    }

    private Map<String, Object> createMetadata(Object sourceResult) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("fetcher", "EmergencyDataFetcher");
        metadata.put("fetchTime", LocalDateTime.now().toString());
        metadata.put("category", "EMERGENCY");
        metadata.put("priority", "HIGH");
        
        if (sourceResult instanceof SerpApiResponse.NewsResult) {
            SerpApiResponse.NewsResult news = (SerpApiResponse.NewsResult) sourceResult;
            metadata.put("source_url", news.getLink());
            metadata.put("source_type", "news");
            metadata.put("news_source", news.getSource());
        } else if (sourceResult instanceof SerpApiResponse.OrganicResult) {
            SerpApiResponse.OrganicResult organic = (SerpApiResponse.OrganicResult) sourceResult;
            metadata.put("source_url", organic.getLink());
            metadata.put("source_type", "organic");
            metadata.put("displayed_link", organic.getDisplayed_link());
        }
        
        return metadata;
    }
} 