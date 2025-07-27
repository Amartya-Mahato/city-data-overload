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
 * Fetcher for traffic-related data from SerpApi
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TrafficDataFetcher implements BaseSerpApiFetcher {

    private final SerpApiConfig serpApiConfig;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Override
    public CompletableFuture<SerpApiResponse> fetchRawData(String query, String location) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Build the URL with parameters
                String url = UriComponentsBuilder.fromHttpUrl("https://serpapi.com/search")
                    .queryParam("engine", "google")
                    .queryParam("q", query + " " + location)
                    .queryParam("location", location)
                    .queryParam("hl", serpApiConfig.getDefaultLanguage())
                    .queryParam("gl", serpApiConfig.getDefaultCountry())
                    .queryParam("google_domain", serpApiConfig.getGoogle().getDomain())
                    .queryParam("num", serpApiConfig.getMaxResults())
                    .queryParam("tbm", "nws") // News search for traffic updates
                    .queryParam("tbs", "qdr:d") // Last day
                    .queryParam("api_key", serpApiConfig.getApiKey())
                    .build()
                    .toUriString();
                
                // Make HTTP request
                String jsonResponse = restTemplate.getForObject(url, String.class);
                
                if (jsonResponse == null) {
                    log.warn("Received null response from SerpApi for query: {}", query);
                    return null;
                }
                
                // Parse JSON response
                SerpApiResponse response = objectMapper.readValue(jsonResponse, SerpApiResponse.class);
                
                // Handle "no results" case gracefully
                if (response != null && response.hasError()) {
                    String errorMsg = response.getErrorMessage();
                    if (errorMsg != null && errorMsg.contains("Google hasn't returned any results")) {
                        log.debug("No traffic results found for query: {} - this is normal", query);
                        // Return an empty response instead of null
                        response = new SerpApiResponse();
                    } else {
                        log.warn("SerpApi error for traffic query '{}': {}", query, errorMsg);
                    }
                }
                
                return response;
                
            } catch (Exception e) {
                log.error("Error fetching traffic data for query: {}", query, e);
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

        // Process news results
        if (response.getNewsResults() != null) {
            for (SerpApiResponse.NewsResult newsResult : response.getNewsResults()) {
                CityEvent event = createEventFromNews(newsResult);
                if (event != null) {
                    events.add(event);
                }
            }
        }

        // Process organic results for traffic-related content
        if (response.getOrganicResults() != null) {
            for (SerpApiResponse.OrganicResult organicResult : response.getOrganicResults()) {
                if (isTrafficRelated(organicResult.getTitle(), organicResult.getSnippet())) {
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
        return CityEvent.EventCategory.TRAFFIC;
    }

    @Override
    public List<String> getDefaultQueries() {
        return Arrays.asList(
            "traffic jam Bengaluru today",
            "road block Bangalore",
            "traffic alert Bengaluru",
            "road closure Bangalore",
            "traffic update Bengaluru BMTC",
            "Metro closure Bangalore",
            "accident Outer Ring Road",
            "traffic signal issue Bengaluru",
            "road construction Bangalore",
            "traffic police updates Bengaluru"
        );
    }

    private CityEvent createEventFromNews(SerpApiResponse.NewsResult newsResult) {
        try {
            String title = newsResult.getTitle();
            String snippet = newsResult.getSnippet() != null ? newsResult.getSnippet() : "";
            
            CityEvent.EventSeverity severity = determineSeverity(title, snippet);
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
                    .metadata(createMetadata(newsResult))
                    .rawData(newsResult.toString())
                    .version(1)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
                    
        } catch (Exception e) {
            log.error("Error creating event from news result: {}", newsResult.getTitle(), e);
            return null;
        }
    }

    private CityEvent createEventFromOrganic(SerpApiResponse.OrganicResult organicResult) {
        try {
            String title = organicResult.getTitle();
            String snippet = organicResult.getSnippet() != null ? organicResult.getSnippet() : "";
            
            CityEvent.EventSeverity severity = determineSeverity(title, snippet);
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
                    .confidenceScore(0.7)
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
            log.error("Error creating event from organic result: {}", organicResult.getTitle(), e);
            return null;
        }
    }

    private boolean isTrafficRelated(String title, String snippet) {
        String combined = (title + " " + snippet).toLowerCase();
        String[] trafficKeywords = {
            "traffic", "road", "vehicle", "accident", "jam", "block", "closure", 
            "congestion", "metro", "bus", "bmtc", "signal", "junction", "highway",
            "expressway", "flyover", "underpass", "parking", "toll"
        };
        
        for (String keyword : trafficKeywords) {
            if (combined.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private List<String> extractKeywords(String title, String content) {
        Set<String> keywords = new HashSet<>();
        String combined = (title + " " + content).toLowerCase();
        
        String[] trafficKeywords = {
            "traffic", "jam", "accident", "road", "closure", "block", "congestion",
            "metro", "bus", "signal", "junction", "highway", "flyover", "parking"
        };
        
        for (String keyword : trafficKeywords) {
            if (combined.contains(keyword)) {
                keywords.add(keyword);
            }
        }
        
        return new ArrayList<>(keywords);
    }

    private Map<String, Object> createMetadata(Object sourceResult) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("fetcher", "TrafficDataFetcher");
        metadata.put("fetchTime", LocalDateTime.now().toString());
        metadata.put("category", "TRAFFIC");
        
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