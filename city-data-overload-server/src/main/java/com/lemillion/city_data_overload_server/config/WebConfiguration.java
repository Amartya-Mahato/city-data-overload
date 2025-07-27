package com.lemillion.city_data_overload_server.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

/**
 * Web configuration for serving static files and handling CORS.
 * Ensures admin portal and other static resources are properly served.
 */
@Configuration
public class WebConfiguration implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serve admin portal files
        registry.addResourceHandler("/admin/**")
                .addResourceLocations("classpath:/static/admin/");
        
        // Serve general static files
        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/");
        
        // Serve root static files
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .setCachePeriod(3600); // Cache for 1 hour
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // Allow CORS for admin API endpoints
        registry.addMapping("/admin/api/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false);
        
        // Allow CORS for public API endpoints
        registry.addMapping("/api/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false);
    }
} 