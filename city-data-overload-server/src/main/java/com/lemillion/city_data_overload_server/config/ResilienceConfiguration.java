package com.lemillion.city_data_overload_server.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

/**
 * Resilience4j configuration for circuit breakers, retries, and timeouts.
 * Provides fault tolerance for external API calls and internal service communication.
 */
@Configuration
@Slf4j
public class ResilienceConfiguration {

    /**
     * Circuit breaker for Vertex AI service calls
     */
    @Bean("vertexAiCircuitBreaker")
    public CircuitBreaker vertexAiCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(50) // Open if >50% of calls fail
            .waitDurationInOpenState(Duration.ofSeconds(30)) // Wait 30s before half-open
            .minimumNumberOfCalls(5) // Need at least 5 calls to calculate failure rate
            .slidingWindowSize(10) // Consider last 10 calls
            .permittedNumberOfCallsInHalfOpenState(3) // Allow 3 test calls in half-open
            .recordExceptions(Exception.class) // Record all exceptions as failures
            .ignoreExceptions(IllegalArgumentException.class) // Don't count invalid input as failure
            .build();
        
        CircuitBreaker circuitBreaker = CircuitBreaker.of("vertexAi", config);
        
        circuitBreaker.getEventPublisher()
            .onStateTransition(event -> 
                log.info("Vertex AI Circuit Breaker state transition: {} -> {}", 
                        event.getStateTransition().getFromState(), 
                        event.getStateTransition().getToState()));
        
        return circuitBreaker;
    }

    /**
     * Circuit breaker for BigQuery service calls
     */
    @Bean("bigQueryCircuitBreaker")
    public CircuitBreaker bigQueryCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(60) // More lenient for database
            .waitDurationInOpenState(Duration.ofSeconds(45))
            .minimumNumberOfCalls(3)
            .slidingWindowSize(8)
            .permittedNumberOfCallsInHalfOpenState(2)
            .recordExceptions(Exception.class)
            .build();
        
        CircuitBreaker circuitBreaker = CircuitBreaker.of("bigQuery", config);
        
        circuitBreaker.getEventPublisher()
            .onStateTransition(event -> 
                log.info("BigQuery Circuit Breaker state transition: {} -> {}", 
                        event.getStateTransition().getFromState(), 
                        event.getStateTransition().getToState()));
        
        return circuitBreaker;
    }

    /**
     * Circuit breaker for Firestore service calls
     */
    @Bean("firestoreCircuitBreaker")
    public CircuitBreaker firestoreCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(70) // Very lenient for real-time DB
            .waitDurationInOpenState(Duration.ofSeconds(20))
            .minimumNumberOfCalls(3)
            .slidingWindowSize(6)
            .permittedNumberOfCallsInHalfOpenState(2)
            .recordExceptions(Exception.class)
            .build();
        
        CircuitBreaker circuitBreaker = CircuitBreaker.of("firestore", config);
        
        circuitBreaker.getEventPublisher()
            .onStateTransition(event -> 
                log.info("Firestore Circuit Breaker state transition: {} -> {}", 
                        event.getStateTransition().getFromState(), 
                        event.getStateTransition().getToState()));
        
        return circuitBreaker;
    }

    /**
     * Circuit breaker for external API calls (Twitter, Data.gov.in, etc.)
     */
    @Bean("externalApiCircuitBreaker")
    public CircuitBreaker externalApiCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(40) // Strict for external APIs
            .waitDurationInOpenState(Duration.ofMinutes(2)) // Wait longer for external APIs
            .minimumNumberOfCalls(5)
            .slidingWindowSize(15)
            .permittedNumberOfCallsInHalfOpenState(3)
            .recordExceptions(Exception.class, TimeoutException.class)
            .ignoreExceptions(IllegalArgumentException.class)
            .build();
        
        CircuitBreaker circuitBreaker = CircuitBreaker.of("externalApi", config);
        
        circuitBreaker.getEventPublisher()
            .onStateTransition(event -> 
                log.warn("External API Circuit Breaker state transition: {} -> {}", 
                        event.getStateTransition().getFromState(), 
                        event.getStateTransition().getToState()));
        
        return circuitBreaker;
    }

    /**
     * Retry configuration for Vertex AI calls
     */
    @Bean("vertexAiRetry")
    public Retry vertexAiRetry() {
        RetryConfig config = RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofSeconds(2))
            .retryExceptions(Exception.class)
            .ignoreExceptions(IllegalArgumentException.class)
            .build();
        
        Retry retry = Retry.of("vertexAi", config);
        
        retry.getEventPublisher()
            .onRetry(event -> 
                log.debug("Vertex AI retry attempt {} for: {}", 
                         event.getNumberOfRetryAttempts(), event.getLastThrowable().getMessage()));
        
        return retry;
    }

    /**
     * Retry configuration for database operations
     */
    @Bean("databaseRetry")
    public Retry databaseRetry() {
        RetryConfig config = RetryConfig.custom()
            .maxAttempts(4) // More attempts for database
            .waitDuration(Duration.ofMillis(500))
            .retryExceptions(Exception.class)
            .build();
        
        Retry retry = Retry.of("database", config);
        
        retry.getEventPublisher()
            .onRetry(event -> 
                log.debug("Database retry attempt {} for: {}", 
                         event.getNumberOfRetryAttempts(), event.getLastThrowable().getMessage()));
        
        return retry;
    }

    /**
     * Retry configuration for external API calls
     */
    @Bean("externalApiRetry")
    public Retry externalApiRetry() {
        RetryConfig config = RetryConfig.custom()
            .maxAttempts(5) // More attempts for potentially flaky external APIs
            .waitDuration(Duration.ofSeconds(1))
            .retryExceptions(Exception.class, TimeoutException.class)
            .ignoreExceptions(IllegalArgumentException.class)
            .build();
        
        Retry retry = Retry.of("externalApi", config);
        
        retry.getEventPublisher()
            .onRetry(event -> 
                log.debug("External API retry attempt {} for: {}", 
                         event.getNumberOfRetryAttempts(), event.getLastThrowable().getMessage()));
        
        return retry;
    }

    /**
     * Time limiter for long-running operations
     */
    @Bean("defaultTimeLimiter")
    public TimeLimiter defaultTimeLimiter() {
        TimeLimiterConfig config = TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofSeconds(30))
            .cancelRunningFuture(true)
            .build();
        
        return TimeLimiter.of("default", config);
    }

    /**
     * Time limiter for AI operations (longer timeout)
     */
    @Bean("aiTimeLimiter")
    public TimeLimiter aiTimeLimiter() {
        TimeLimiterConfig config = TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofMinutes(2)) // AI operations can take longer
            .cancelRunningFuture(true)
            .build();
        
        return TimeLimiter.of("ai", config);
    }

    /**
     * Time limiter for external API calls
     */
    @Bean("externalApiTimeLimiter")
    public TimeLimiter externalApiTimeLimiter() {
        TimeLimiterConfig config = TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofSeconds(15)) // Shorter timeout for external APIs
            .cancelRunningFuture(true)
            .build();
        
        return TimeLimiter.of("externalApi", config);
    }
} 