# City Data Overload Server 🏙️

[![Java](https://img.shields.io/badge/Java-17-orange)](https://openjdk.java.net/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green)](https://spring.io/projects/spring-boot)
[![Google AI](https://img.shields.io/badge/Google%20AI-Vertex%20AI-blue)](https://cloud.google.com/vertex-ai)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

**Intelligent backend for Bengaluru city data management using Google AI technologies**

This is an agentic application built for the **Google AI Challenge** that provides a live, synthesized, and intelligent view of Bengaluru city data. The system uses **Google Vertex AI (Gemini)** to fuse disparate data sources, process multimodal citizen reports, and provide predictive insights.

## 🎯 Challenge Requirements

### ✅ **Fuse Disparate Data**
- Ingests real-time data from multiple sources
- Uses **Gemini AI** to synthesize 15 separate posts about one traffic jam into a single, clean summary
- Provides actionable advice (e.g., "Heavy traffic on Old Airport Road, consider alternative routes")

### ✅ **Enable Multimodal Citizen Reporting**
- Allows users to submit geo-tagged photos/videos of events
- Uses **Gemini's multimodal capabilities** to analyze media, categorize events, and describe situations
- Automatically plots events on a map with AI-generated descriptions

### ✅ **Create Predictive & Agentic Layer**
- Analyzes event streams to provide predictive alerts
- Powers intelligent notifications with AI-generated summaries
- Enables area-specific subscriptions and insights

### ✅ **Visualize the Pulse**
- Real-time, map-based data visualization
- **Sentiment analysis** for mood mapping
- Live updates via Server-Sent Events (SSE)

## 🏗️ Architecture

### **Multi-Agent System**
```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│ CoordinatorAgent│───▶│   FusionAgent    │───▶│ PredictiveAgent │
│                 │    │ (Gemini Synthesis)│    │ (AI Insights)   │
└─────────────────┘    └──────────────────┘    └─────────────────┘
         │                        │                        │
         ▼                        ▼                        ▼
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│UserReportingAgent│    │   MoodMapAgent   │    │   AlertAgent    │
│(Multimodal AI)  │    │(Sentiment AI)    │    │ (Critical Alerts)│
└─────────────────┘    └──────────────────┘    └─────────────────┘
```

### **Technology Stack**
- **Backend**: Spring Boot 3.x + Java 17
- **AI**: Google Vertex AI (Gemini 1.5 Pro)
- **Database**: BigQuery (analytics) + Firestore (real-time)
- **Cache**: Redis
- **Storage**: Google Cloud Storage
- **Real-time**: Server-Sent Events (SSE)
- **Resilience**: Resilience4j (Circuit Breakers, Retries)
- **Frontend**: Flutter (mobile app)

## 📋 Prerequisites

### **Required**
1. **Java 17+** - OpenJDK or Oracle JDK
2. **Maven 3.8+** - Build tool
3. **Google Cloud Project** - With billing enabled
4. **Redis Server** - For caching (local or cloud)

### **Google Cloud Setup**
1. Create a Google Cloud Project
2. Enable the following APIs:
   ```bash
   # Enable required APIs
   gcloud services enable aiplatform.googleapis.com
   gcloud services enable bigquery.googleapis.com
   gcloud services enable firestore.googleapis.com
   gcloud services enable storage.googleapis.com
   ```

3. Create a service account:
   ```bash
   # Create service account
   gcloud iam service-accounts create vertex-ai-agent \
     --description="Service account for City Data Overload" \
     --display-name="Vertex AI Agent"

   # Grant necessary roles
   gcloud projects add-iam-policy-binding YOUR_PROJECT_ID \
     --member="serviceAccount:vertex-ai-agent@YOUR_PROJECT_ID.iam.gserviceaccount.com" \
     --role="roles/aiplatform.user"

   gcloud projects add-iam-policy-binding YOUR_PROJECT_ID \
     --member="serviceAccount:vertex-ai-agent@YOUR_PROJECT_ID.iam.gserviceaccount.com" \
     --role="roles/bigquery.admin"

   gcloud projects add-iam-policy-binding YOUR_PROJECT_ID \
     --member="serviceAccount:vertex-ai-agent@YOUR_PROJECT_ID.iam.gserviceaccount.com" \
     --role="roles/datastore.user"

   # Download service account key
   gcloud iam service-accounts keys create service-account-key.json \
     --iam-account=vertex-ai-agent@YOUR_PROJECT_ID.iam.gserviceaccount.com
   ```

4. Place `service-account-key.json` in `src/main/resources/`

## 🗄️ Database Schemas

### **BigQuery Tables**

#### **1. city_events**
```sql
CREATE TABLE `your-project.city_data.city_events` (
  id STRING NOT NULL,
  title STRING,
  description STRING,
  content STRING,
  latitude FLOAT64,
  longitude FLOAT64,
  address STRING,
  area STRING,
  pincode STRING,
  landmark STRING,
  timestamp TIMESTAMP,
  expires_at TIMESTAMP,
  category STRING,  -- TRAFFIC, CIVIC_ISSUE, CULTURAL_EVENT, EMERGENCY, INFRASTRUCTURE, WEATHER, COMMUNITY
  severity STRING,  -- LOW, MODERATE, HIGH, CRITICAL
  source STRING,    -- USER_REPORT, SOCIAL_MEDIA, NEWS, GOVERNMENT
  sentiment_type STRING,    -- POSITIVE, NEGATIVE, NEUTRAL, MIXED
  sentiment_score FLOAT64,
  sentiment_confidence FLOAT64,
  confidence_score FLOAT64,
  keywords ARRAY<STRING>,
  ai_summary STRING,
  raw_data STRING,
  created_at TIMESTAMP,
  updated_at TIMESTAMP
);
```

#### **2. user_reports**
```sql
CREATE TABLE `your-project.city_data.user_reports` (
  id STRING NOT NULL,
  user_id STRING,
  content STRING,
  latitude FLOAT64,
  longitude FLOAT64,
  area STRING,
  media_urls ARRAY<STRING>,
  media_types ARRAY<STRING>,
  ai_analysis STRING,
  processed_event_id STRING,
  created_at TIMESTAMP,
  processing_status STRING
);
```

#### **3. predictions**
```sql
CREATE TABLE `your-project.city_data.predictions` (
  id STRING NOT NULL,
  area STRING,
  category STRING,
  prediction STRING,
  confidence FLOAT64,
  timeframe STRING,
  created_at TIMESTAMP,
  expires_at TIMESTAMP
);
```

### **Firestore Collections**

#### **1. /city_events/{eventId}**
```javascript
{
  id: "event_123",
  title: "Traffic jam on Old Airport Road",
  description: "Heavy traffic reported...",
  content: "User reported heavy traffic with image evidence",
  location: {
    latitude: 12.9716,
    longitude: 77.5946,
    area: "Bengaluru",
    address: "Old Airport Road"
  },
  category: "TRAFFIC",
  severity: "MODERATE",
  source: "USER_REPORT",
  sentiment: {
    type: "NEGATIVE",
    score: -0.6,
    confidence: 0.8
  },
  timestamp: "2024-01-15T10:30:00Z",
  ttl: "2024-01-15T12:30:00Z",  // Auto-deletion based on category
  aiSummary: "AI-generated summary with actionable insights",
  keywords: ["traffic", "jam", "delay"],
  confidenceScore: 0.85,
  mediaAttachments: [
    {
      id: "media_001",
      url: "gs://city-data-storage/reports/images/user123/...",
      type: "IMAGE"
    }
  ],
  metadata: {
    user_id: "user123",
    processing_agent: "user-reporting-agent"
  }
}
```

#### **2. /users/{userId}**
```javascript
{
  userId: "user123",
  reportIds: ["event_123", "event_456"],  // Array of event IDs
  createdAt: "2024-01-10T08:00:00Z",
  lastActiveAt: "2024-01-15T10:30:00Z",
  totalReports: 2,
  status: "ACTIVE"
}
```

#### **3. /user_reports/{reportId}**
```javascript
{
  id: "report_789",
  userId: "user123",
  eventId: "event_123",  // Link to city_events
  content: "Heavy traffic on main road with blocked lanes",
  latitude: 12.9716,
  longitude: 77.5946,
  area: "Koramangala",
  mediaUrls: [
    "gs://city-data-storage/reports/images/user123/...",
    "gs://city-data-storage/reports/videos/user123/..."
  ],
  aiSummary: "Traffic congestion with visual evidence",
  createdAt: "2024-01-15T10:30:00Z",
  ttl: "2024-01-16T10:30:00Z",  // Custom TTL (24h default)
  status: "ACTIVE"  // ACTIVE, DELETED, EXPIRED
}
```

#### **4. /active_alerts/{alertId}**
```javascript
{
  id: "alert_456",
  area: "HSR Layout",
  severity: "HIGH",
  message: "Multiple power cuts reported, potential grid issue",
  category: "INFRASTRUCTURE",
  timestamp: "2024-01-15T11:00:00Z",
  ttl: "2024-01-16T11:00:00Z",
  affectedAreas: ["HSR Layout", "Koramangala"]
}
```

#### **5. /mood_data/{areaId}**
```javascript
{
  area: "Indiranagar",
  overallMood: "POSITIVE",
  moodScore: 0.3,
  eventCounts: {
    positive: 15,
    negative: 8,
    neutral: 12
  },
  timestamp: "2024-01-15T11:00:00Z",
  ttl: "2024-01-15T11:30:00Z"
}
```

## 🚀 Quick Start

### **1. Clone and Setup**
```bash
git clone <repository-url>
cd city-data-overload-server

# Place your service account key
cp /path/to/service-account-key.json src/main/resources/
```

### **2. Configure Environment**
```bash
# Set environment variables
export GCP_PROJECT_ID=your-project-id
export REDIS_HOST=localhost
export REDIS_PORT=6379
```

### **3. Start Redis (Local)**
```bash
# Using Docker
docker run -d -p 6379:6379 redis:alpine

# Or install locally
brew install redis  # macOS
redis-server
```

### **4. Run Application**
```bash
# Using Maven
./mvnw spring-boot:run

# Or build and run
./mvnw clean package
java -jar target/city-data-overload-server-1.0.0.jar
```

### **5. Verify Setup**
```bash
# Check health
curl http://localhost:8080/api/v1/flutter/health

# View API documentation
open http://localhost:8080/swagger-ui/index.html
```

## 📱 Flutter API Endpoints

### **Base URL**: `/api/v1/flutter`

### **1. Map & Chat Page**
```http
# Get comprehensive city data (events + alerts + mood)
POST /map/comprehensive
Content-Type: application/json
{
  "latitude": 12.9716,
  "longitude": 77.5946,
  "area": "Bengaluru",
  "radius": 10.0
}

# AI-powered chat queries
POST /chat/query
Content-Type: application/json
{
  "query": "What's happening in Koramangala right now?",
  "latitude": 12.9279,
  "longitude": 77.6271,
  "userId": "user123"
}
```

### **2. Events Page**
```http
# Get synthesized events (AI-fused from multiple sources)
POST /events/synthesized
Content-Type: application/json
{
  "area": "HSR Layout",
  "city": "Bengaluru",
  "latitude": 12.9082,
  "longitude": 77.6476,
  "maxResults": 20
}
```

### **3. Alert Page**
```http
# Get predictive alerts for area
POST /alerts/predictive
Content-Type: application/json
{
  "area": "Whitefield",
  "city": "Bengaluru",
  "latitude": 12.9698,
  "longitude": 77.7500
}

# Get mood map data
POST /map/mood
Content-Type: application/json
{
  "area": "Indiranagar",
  "latitude": 12.9784,
  "longitude": 77.6408
}
```

### **4. Reporting Page**
```http
# Submit multimodal citizen report with TTL and user tracking
POST /reports/submit
Content-Type: multipart/form-data

content: "Water logging on main road after rain"
latitude: 12.9716
longitude: 77.5946
userId: "user123"              # Required for tracking
area: "Koramangala"            # Optional
ttlHours: 48                   # Optional (default: 24h, max: 720h/30days)
image: [file]                  # Optional - uploaded to Cloud Storage
video: [file]                  # Optional - uploaded to Cloud Storage

# Response includes AI analysis and media URLs
{
  "success": true,
  "reportId": "report_123",
  "eventId": "event_456",
  "processedEvent": {
    "id": "event_456",
    "title": "Water Logging Reported",
    "description": "AI-generated description from image analysis",
    "category": "INFRASTRUCTURE",
    "severity": "MODERATE",
    "aiSummary": "Water logging detected with visual evidence...",
    "confidenceScore": 0.85,
    "mediaAttachments": 2
  },
  "location": { "latitude": 12.9716, "longitude": 77.5946, "area": "Koramangala" },
  "userTracking": {
    "userId": "user123",
    "ttlHours": 48,
    "canDelete": true
  }
}

# Get user's report history with statistics
GET /reports/history/{userId}?limit=20

# Response includes detailed report history and user stats
{
  "success": true,
  "userId": "user123",
  "reports": [
    {
      "reportId": "report_123",
      "eventId": "event_456",
      "content": "Water logging report...",
      "aiSummary": "AI analysis...",
      "mediaUrls": ["gs://..."],
      "createdAt": "2024-01-15T10:30:00Z",
      "status": "ACTIVE"
    }
  ],
  "total": 5,
  "statistics": {
    "totalReports": 5,
    "activeReports": 3,
    "joinedAt": "2024-01-10T08:00:00Z"
  }
}

# Delete user's report (removes media files and tracking)
DELETE /reports/{reportId}?userId=user123

# Response confirms deletion
{
  "success": true,
  "message": "Report deleted successfully",
  "reportId": "report_123",
  "userId": "user123"
}
```

### **5. Real-time Streams**
```http
# Server-Sent Events for live updates
GET /stream/events      # Real-time city events
GET /stream/alerts      # Real-time alerts
GET /stream/mood        # Real-time mood updates
```

### **6. Utility Endpoints**
```http
# Get available Bengaluru areas
GET /bengaluru/areas

# System health check
GET /health
```

## 🔧 Configuration

### **Environment Variables**
```bash
# Google Cloud
GCP_PROJECT_ID=your-project-id
GCP_LOCATION=us-central1
GOOGLE_APPLICATION_CREDENTIALS=src/main/resources/service-account-key.json

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# Server
SERVER_PORT=8080
```

### **Model Configuration**
The system uses **Gemini 1.5 Pro** for:
- Text synthesis and summarization
- Multimodal image/video analysis
- Sentiment analysis
- Predictive insights generation

## 🚢 Deployment

### **Local Development**
```bash
./mvnw spring-boot:run
```

### **Google Cloud Run**
```bash
# Build container
./mvnw spring-boot:build-image

# Deploy to Cloud Run
gcloud run deploy city-data-overload \
  --source . \
  --platform managed \
  --region us-central1 \
  --allow-unauthenticated \
  --set-env-vars GCP_PROJECT_ID=your-project-id
```

### **Docker**
```bash
# Build image
docker build -t city-data-overload .

# Run container
docker run -p 8080:8080 \
  -e GCP_PROJECT_ID=your-project-id \
  -v $(pwd)/src/main/resources/service-account-key.json:/app/service-account-key.json \
  city-data-overload
```

## 🧪 Testing

### **API Testing**
```bash
# Install HTTPie
pip install httpie

# Test map endpoint
http POST localhost:8080/api/v1/flutter/map/comprehensive \
  latitude:=12.9716 longitude:=77.5946 area="Bengaluru"

# Test events endpoint
http POST localhost:8080/api/v1/flutter/events/synthesized \
  area="Koramangala" city="Bengaluru"

# Test health endpoint
http GET localhost:8080/api/v1/flutter/health
```

### **SSE Testing**
```bash
# Test real-time events stream
curl -N -H "Accept: text/event-stream" \
  http://localhost:8080/api/v1/flutter/stream/events
```

## 🎨 Flutter Integration

### **Dart Example**
```dart
import 'dart:convert';
import 'package:http/http.dart' as http;

class CityDataService {
  static const String baseUrl = 'http://localhost:8080/api/v1/flutter';
  
  // Get comprehensive city data for map
  Future<Map<String, dynamic>> getMapData(double lat, double lon) async {
    final response = await http.post(
      Uri.parse('$baseUrl/map/comprehensive'),
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({
        'latitude': lat,
        'longitude': lon,
        'area': 'Bengaluru',
        'radius': 10.0
      }),
    );
    return jsonDecode(response.body);
  }
  
  // Submit citizen report with image
  Future<Map<String, dynamic>> submitReport(
    String content, double lat, double lon, File? image
  ) async {
    var request = http.MultipartRequest(
      'POST', Uri.parse('$baseUrl/reports/submit')
    );
    
    request.fields['content'] = content;
    request.fields['latitude'] = lat.toString();
    request.fields['longitude'] = lon.toString();
    
    if (image != null) {
      request.files.add(await http.MultipartFile.fromPath('image', image.path));
    }
    
    final response = await request.send();
    final responseBody = await response.stream.bytesToString();
    return jsonDecode(responseBody);
  }
}

// Real-time events using SSE
import 'package:eventsource/eventsource.dart';

void connectToEventStream() {
  final eventSource = EventSource(
    url: Uri.parse('http://localhost:8080/api/v1/flutter/stream/events')
  );
  
  eventSource.listen((event) {
    final data = jsonDecode(event.data);
    print('New city event: ${data['event']['title']}');
  });
}
```

## 📊 Monitoring

### **Health Checks**
- Application health: `/api/v1/flutter/health`
- Agent status monitoring
- Redis connection status
- Real-time connection statistics

### **Logging**
- Structured logging with JSON format
- Google Cloud Logging integration
- Request/response tracing
- Performance metrics

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙋‍♂️ Support

For questions and support:
- Create an issue in the repository
- Contact the development team
- Check the API documentation at `/swagger-ui/index.html`

---

**Built with ❤️ for the Google AI Challenge - Managing City Data Overload**
