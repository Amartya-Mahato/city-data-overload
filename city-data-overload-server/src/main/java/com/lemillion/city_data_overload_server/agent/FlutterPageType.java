package com.lemillion.city_data_overload_server.agent;

import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark Flutter page handler classes for automatic discovery
 * Handlers annotated with this will be automatically registered in the HandlerRegistry
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface FlutterPageType {
    
    /**
     * The page type this handler supports
     * @return page type string (e.g., "HOME", "EVENTS", "ALERTS")
     */
    String value();
    
    /**
     * Priority of this handler (lower number = higher priority)
     * @return priority value (default: 100)
     */
    int priority() default 100;
    
    /**
     * Description of what this handler does
     * @return description string
     */
    String description() default "";
} 