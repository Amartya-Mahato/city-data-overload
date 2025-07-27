package com.lemillion.city_data_overload_server.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Landing page controller for City Data Overload Server.
 * Provides API documentation and system overview.
 */
@Controller
public class LandingPageController {

    @GetMapping("/")
    @ResponseBody
    public Map<String, Object> landingPage() {
        return Map.of(
            "service", "City Data Overload Server",
            "description", "Intelligent backend for Bengaluru city data management using Google AI",
            "version", "1.0.0",
            "challenge", "Google AI-powered agentic application for managing city data overload",
            "timestamp", LocalDateTime.now(),
            "documentation", Map.of(
                "swagger", "/swagger-ui/index.html",
                "apiDocs", "/v3/api-docs"
            ),
            "endpoints", Map.of(
                "flutter", Map.of(
                    "base", "/api/v1/flutter",
                    "mapChat", "POST /api/v1/flutter/map/comprehensive",
                    "chatQuery", "POST /api/v1/flutter/chat/query",
                    "events", "POST /api/v1/flutter/events/synthesized",
                    "alerts", "POST /api/v1/flutter/alerts/predictive",
                    "moodMap", "POST /api/v1/flutter/map/mood",
                    "reports", "POST /api/v1/flutter/reports/submit",
                    "health", "GET /api/v1/flutter/health",
                    "areas", "GET /api/v1/flutter/bengaluru/areas"
                ),
                "streams", Map.of(
                    "events", "GET /api/v1/flutter/stream/events",
                    "alerts", "GET /api/v1/flutter/stream/alerts",
                    "mood", "GET /api/v1/flutter/stream/mood"
                )
            ),
            "agents", List.of(
                "CoordinatorAgent - Orchestrates all agents for comprehensive analysis",
                "FusionAgent - Synthesizes disparate data sources using Gemini AI",
                "PredictiveAgent - Generates predictive insights and alerts",
                "UserReportingAgent - Processes multimodal citizen reports",
                "MoodMapAgent - Analyzes sentiment for mood mapping",
                "EventsAgent - Fetches city events from multiple sources",
                "AlertAgent - Monitors and manages critical alerts"
            ),
            "features", List.of(
                "✅ Multi-agent architecture with Google Vertex AI",
                "✅ Real-time data fusion using Gemini",
                "✅ Multimodal citizen reporting (image/video analysis)",
                "✅ Predictive alerting system",
                "✅ Sentiment-based mood mapping",
                "✅ Server-sent events for real-time updates",
                "✅ Redis caching for performance",
                "✅ Circuit breakers for resilience",
                "✅ BigQuery + Firestore data storage",
                "✅ Flutter-optimized API endpoints"
            ),
            "technologies", Map.of(
                "ai", "Google Vertex AI (Gemini)",
                "backend", "Spring Boot 3.x",
                "database", "BigQuery + Firestore",
                "cache", "Redis",
                "storage", "Google Cloud Storage",
                "streaming", "Server-Sent Events (SSE)",
                "resilience", "Resilience4j",
                "frontend", "Flutter (mobile app)"
            )
        );
    }

    @GetMapping("/api")
    @ResponseBody
    public Map<String, Object> apiOverview() {
        return Map.of(
            "message", "City Data Overload API",
            "version", "v1",
            "flutterEndpoints", "/api/v1/flutter",
            "documentation", "/swagger-ui/index.html",
            "health", "/api/v1/flutter/health",
            "timestamp", LocalDateTime.now()
        );
    }
} 