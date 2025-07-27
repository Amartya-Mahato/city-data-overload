package com.lemillion.city_data_overload_server.config;

import com.lemillion.city_data_overload_server.agent.AgentResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

/**
 * Global exception handler for City Data Overload application.
 * Provides consistent error responses across all endpoints.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handle validation errors
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(
            MethodArgumentNotValidException ex, WebRequest request) {
        
        log.warn("Validation error on request: {}", request.getDescription(false), ex);
        
        Map<String, Object> errors = new HashMap<>();
        errors.put("timestamp", LocalDateTime.now());
        errors.put("status", HttpStatus.BAD_REQUEST.value());
        errors.put("error", "Validation Failed");
        errors.put("path", request.getDescription(false));
        
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            fieldErrors.put(fieldName, errorMessage);
        });
        errors.put("field_errors", fieldErrors);
        
        return ResponseEntity.badRequest().body(errors);
    }

    /**
     * Handle completion exceptions from async operations
     */
    @ExceptionHandler(CompletionException.class)
    public ResponseEntity<AgentResponse> handleCompletionException(
            CompletionException ex, WebRequest request) {
        
        log.error("Async operation failed: {}", request.getDescription(false), ex);
        
        Throwable cause = ex.getCause();
        if (cause instanceof TimeoutException) {
            return handleTimeoutException((TimeoutException) cause, request);
        }
        
        AgentResponse response = AgentResponse.error(
            "unknown", 
            "system", 
            "Async operation failed: " + (cause != null ? cause.getMessage() : ex.getMessage()),
            "COMPLETION_ERROR"
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * Handle timeout exceptions
     */
    @ExceptionHandler(TimeoutException.class)
    public ResponseEntity<AgentResponse> handleTimeoutException(
            TimeoutException ex, WebRequest request) {
        
        log.error("Operation timed out: {}", request.getDescription(false), ex);
        
        AgentResponse response = AgentResponse.error(
            "timeout", 
            "system", 
            "Operation timed out. Please try again later.",
            "TIMEOUT_ERROR"
        );
        
        return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body(response);
    }

    /**
     * Handle illegal argument exceptions
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(
            IllegalArgumentException ex, WebRequest request) {
        
        log.warn("Invalid argument: {}", request.getDescription(false), ex);
        
        Map<String, Object> error = new HashMap<>();
        error.put("timestamp", LocalDateTime.now());
        error.put("status", HttpStatus.BAD_REQUEST.value());
        error.put("error", "Invalid Argument");
        error.put("message", ex.getMessage());
        error.put("path", request.getDescription(false));
        
        return ResponseEntity.badRequest().body(error);
    }

    /**
     * Handle runtime exceptions
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<AgentResponse> handleRuntimeException(
            RuntimeException ex, WebRequest request) {
        
        log.error("Runtime error: {}", request.getDescription(false), ex);
        
        // Check if it's a known agent error
        String errorCode = determineErrorCode(ex);
        
        AgentResponse response = AgentResponse.error(
            "runtime-error", 
            "system", 
            "An unexpected error occurred: " + ex.getMessage(),
            errorCode
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * Handle generic exceptions
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(
            Exception ex, WebRequest request) {
        
        log.error("Unexpected error: {}", request.getDescription(false), ex);
        
        Map<String, Object> error = new HashMap<>();
        error.put("timestamp", LocalDateTime.now());
        error.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        error.put("error", "Internal Server Error");
        error.put("message", "An unexpected error occurred. Please try again later.");
        error.put("path", request.getDescription(false));
        
        // Only include exception details in development
        if (isDevelopmentMode()) {
            error.put("exception", ex.getClass().getSimpleName());
            error.put("exception_message", ex.getMessage());
        }
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    /**
     * Handle service unavailable exceptions (when agents are unhealthy)
     */
    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<AgentResponse> handleServiceUnavailableException(
            ServiceUnavailableException ex, WebRequest request) {
        
        log.error("Service unavailable: {}", request.getDescription(false), ex);
        
        AgentResponse response = AgentResponse.error(
            "service-unavailable", 
            ex.getAgentId(), 
            "Service temporarily unavailable: " + ex.getMessage(),
            "SERVICE_UNAVAILABLE"
        );
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    // Helper methods

    private String determineErrorCode(RuntimeException ex) {
        String message = ex.getMessage();
        if (message == null) {
            return "UNKNOWN_ERROR";
        }
        
        message = message.toLowerCase();
        
        if (message.contains("bigquery")) {
            return "BIGQUERY_ERROR";
        } else if (message.contains("firestore")) {
            return "FIRESTORE_ERROR";
        } else if (message.contains("vertex") || message.contains("ai")) {
            return "AI_SERVICE_ERROR";
        } else if (message.contains("storage")) {
            return "STORAGE_ERROR";
        } else if (message.contains("network") || message.contains("connection")) {
            return "NETWORK_ERROR";
        } else {
            return "RUNTIME_ERROR";
        }
    }

    private boolean isDevelopmentMode() {
        String profile = System.getProperty("spring.profiles.active");
        return "dev".equals(profile) || "development".equals(profile);
    }

    /**
     * Custom exception for service unavailable scenarios
     */
    public static class ServiceUnavailableException extends RuntimeException {
        private final String agentId;
        
        public ServiceUnavailableException(String agentId, String message) {
            super(message);
            this.agentId = agentId;
        }
        
        public ServiceUnavailableException(String agentId, String message, Throwable cause) {
            super(message, cause);
            this.agentId = agentId;
        }
        
        public String getAgentId() {
            return agentId;
        }
    }
} 