package com.lemillion.city_data_overload_server.model.serpapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Base DTO for SerpApi responses
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SerpApiResponse {
    
    @JsonProperty("search_metadata")
    private SearchMetadata searchMetadata;
    
    @JsonProperty("search_parameters")
    private SearchParameters searchParameters;
    
    @JsonProperty("search_information")
    private SearchInformation searchInformation;
    
    @JsonProperty("organic_results")
    private List<OrganicResult> organicResults;
    
    @JsonProperty("news_results")
    private List<NewsResult> newsResults;
    
    @JsonProperty("local_results")
    private List<LocalResult> localResults;
    
    @JsonProperty("knowledge_graph")
    private KnowledgeGraph knowledgeGraph;
    
    @JsonProperty("related_searches")
    private List<RelatedSearch> relatedSearches;
    
    // Error can be either a String or Map depending on the API response
    private Object error;
    
    /**
     * Check if there's an error in the response
     */
    public boolean hasError() {
        return error != null;
    }
    
    /**
     * Get error message as string
     */
    public String getErrorMessage() {
        if (error == null) {
            return null;
        }
        if (error instanceof String) {
            return (String) error;
        }
        if (error instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> errorMap = (Map<String, Object>) error;
            return errorMap.toString();
        }
        return error.toString();
    }
    
    /**
     * Get error as Map if it's a complex error object
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getErrorMap() {
        if (error instanceof Map) {
            return (Map<String, Object>) error;
        }
        return null;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SearchMetadata {
        private String id;
        private String status;
        @JsonProperty("json_endpoint")
        private String jsonEndpoint;
        @JsonProperty("created_at")
        private String createdAt;
        @JsonProperty("processed_at")
        private String processedAt;
        @JsonProperty("total_time_taken")
        private Double totalTimeTaken;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SearchParameters {
        private String engine;
        private String q;
        private String location;
        private String hl;
        private String gl;
        @JsonProperty("google_domain")
        private String googleDomain;
        private String tbm;
        private String tbs;
        private Integer num;
        private Integer start;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SearchInformation {
        @JsonProperty("organic_results_state")
        private String organicResultsState;
        @JsonProperty("query_displayed")
        private String queryDisplayed;
        @JsonProperty("total_results")
        private Long totalResults;
        @JsonProperty("time_taken_displayed")
        private Double timeTakenDisplayed;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OrganicResult {
        private Integer position;
        private String title;
        private String link;
        private String redirect;
        private String displayed_link;
        private String snippet;
        @JsonProperty("snippet_highlighted_words")
        private List<String> snippetHighlightedWords;
        @JsonProperty("cached_page_link")
        private String cachedPageLink;
        @JsonProperty("rich_snippet")
        private Map<String, Object> richSnippet;
        private Map<String, Object> sitelinks;
        private String date;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NewsResult {
        private Integer position;
        private String title;
        private String link;
        private String source;
        private String date;
        private String snippet;
        private String thumbnail;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LocalResult {
        private Integer position;
        private String title;
        @JsonProperty("place_id")
        private String placeId;
        @JsonProperty("data_id")
        private String dataId;
        @JsonProperty("data_cid")
        private String dataCid;
        @JsonProperty("reviews_link")
        private String reviewsLink;
        @JsonProperty("reviews_id")
        private String reviewsId;
        private String type;
        private String address;
        private String hours;
        private String phone;
        private Double rating;
        private Integer reviews;
        private String price;
        private String description;
        @JsonProperty("service_options")
        private Map<String, Object> serviceOptions;
        private GpsCoordinates gps_coordinates;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GpsCoordinates {
        private Double latitude;
        private Double longitude;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class KnowledgeGraph {
        private String title;
        private String type;
        private String description;
        private String source;
        private List<Map<String, Object>> attributes;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RelatedSearch {
        private String query;
        private String link;
    }
} 