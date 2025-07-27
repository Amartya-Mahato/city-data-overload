package com.lemillion.city_data_overload_server.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis configuration for caching frequently accessed city data.
 * Provides caching for events, predictions, sentiment analysis, and user sessions.
 */
@Configuration
@EnableCaching
@Slf4j
public class RedisConfiguration extends CachingConfigurerSupport {

    /**
     * Custom key generator for cache keys with location and time context
     */
    @Bean("locationBasedKeyGenerator")
    @Override
    public KeyGenerator keyGenerator() {
        return (target, method, params) -> {
            StringBuilder keyBuilder = new StringBuilder();
            keyBuilder.append(target.getClass().getSimpleName())
                     .append(":")
                     .append(method.getName());
            
            for (Object param : params) {
                if (param != null) {
                    keyBuilder.append(":").append(param.toString());
                }
            }
            
            String key = keyBuilder.toString();
            log.debug("Generated cache key: {}", key);
            return key;
        };
    }

    /**
     * Redis template configuration with JSON serialization
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Create Jackson serializer
        Jackson2JsonRedisSerializer<Object> jackson2JsonRedisSerializer = new Jackson2JsonRedisSerializer<>(Object.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        objectMapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance, ObjectMapper.DefaultTyping.NON_FINAL);
        objectMapper.registerModule(new JavaTimeModule());
        jackson2JsonRedisSerializer.setObjectMapper(objectMapper);

        // Set serializers
        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringRedisSerializer);
        template.setHashKeySerializer(stringRedisSerializer);
        template.setValueSerializer(jackson2JsonRedisSerializer);
        template.setHashValueSerializer(jackson2JsonRedisSerializer);
        template.setDefaultSerializer(jackson2JsonRedisSerializer);
        template.afterPropertiesSet();

        return template;
    }

    /**
     * Cache manager with different TTL configurations for different data types
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultCacheConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(30))
            .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(createJsonSerializer()));

        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        
        // Events cache - 15 minutes TTL
        cacheConfigurations.put("events", defaultCacheConfig.entryTtl(Duration.ofMinutes(15)));
        
        // Location-based events - 10 minutes TTL
        cacheConfigurations.put("locationEvents", defaultCacheConfig.entryTtl(Duration.ofMinutes(10)));
        
        // Predictions cache - 1 hour TTL
        cacheConfigurations.put("predictions", defaultCacheConfig.entryTtl(Duration.ofHours(1)));
        
        // Mood map data - 30 minutes TTL
        cacheConfigurations.put("moodMap", defaultCacheConfig.entryTtl(Duration.ofMinutes(30)));
        
        // User sessions - 24 hours TTL
        cacheConfigurations.put("userSessions", defaultCacheConfig.entryTtl(Duration.ofHours(24)));
        
        // Agent responses - 5 minutes TTL
        cacheConfigurations.put("agentResponses", defaultCacheConfig.entryTtl(Duration.ofMinutes(5)));
        
        // Trending data - 20 minutes TTL
        cacheConfigurations.put("trending", defaultCacheConfig.entryTtl(Duration.ofMinutes(20)));
        
        // Area statistics - 45 minutes TTL
        cacheConfigurations.put("areaStats", defaultCacheConfig.entryTtl(Duration.ofMinutes(45)));

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultCacheConfig)
            .withInitialCacheConfigurations(cacheConfigurations)
            .build();
    }

    /**
     * Create JSON serializer for Redis values
     */
    private Jackson2JsonRedisSerializer<Object> createJsonSerializer() {
        Jackson2JsonRedisSerializer<Object> serializer = new Jackson2JsonRedisSerializer<>(Object.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        objectMapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance, ObjectMapper.DefaultTyping.NON_FINAL);
        objectMapper.registerModule(new JavaTimeModule());
        serializer.setObjectMapper(objectMapper);
        return serializer;
    }
} 