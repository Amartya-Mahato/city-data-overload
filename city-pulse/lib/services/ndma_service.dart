import 'dart:convert';
import 'package:http/http.dart' as http;
import '../models/event.dart';
import '../models/ndma_alert.dart';

class NdmaService {
  static const String _baseUrl = 'https://sachet.ndma.gov.in/cap_public_website/FetchLocationWiseAlerts';
  
  /// Fetch NDMA alerts with proper parsing for all required fields
 static Future<List<NdmaAlert>> fetchNdmaAlerts({
  required double latitude,
  required double longitude,
  required double radiusKm,
}) async {
  try {
    // Use both query params AND body (as shown in your Postman request)
    final uri = Uri.parse('$_baseUrl?lat=$latitude&long=$longitude&radius=${radiusKm.toInt()}');

    print('🌐 Fetching NDMA alerts from: $uri');
    print('📍 Request parameters: lat=$latitude, lng=$longitude, radius=${radiusKm}km');

    final response = await http.post(
      uri,
      headers: {
        'User-Agent': 'Mozilla/5.0',
        'Content-Type': 'application/json',
      },
      body: json.encode({
        'lat': latitude,
        'longi': longitude,    // Key change: 'longi' instead of 'long'
        'radius': radiusKm.toInt(),
      }),
    ).timeout(const Duration(seconds: 30));

    print('📡 NDMA API Response Status: ${response.statusCode}');
    
    if (response.statusCode == 200) {
      final responseBody = response.body;
      print('📄 Response body length: ${responseBody.length} characters');
      
      if (responseBody.trim().isEmpty) {
        print('⚠️ Empty response body from NDMA API');
        return [];
      }
      
      final data = json.decode(responseBody);
      print('✅ Successfully parsed NDMA JSON response');
      
      final alerts = await _parseNdmaAlertsResponseAsync(data);
      print('📝 Parsed ${alerts.length} alerts from NDMA API');
      
      // Validate parsed alerts
      for (final alert in alerts) {
        _validateAlertCoordinates(alert);
      }
      
      print('✅ Returning ${alerts.length} NDMA alerts');
      return alerts;
    } else {
      print('❌ NDMA API error: ${response.statusCode}');
      print('💬 Error response: ${response.body}');
      return [];
    }
  } catch (e, stackTrace) {
    print('💥 Exception fetching NDMA alerts: $e');
    print('📚 Stack trace: $stackTrace');
    return [];
  }
}

  /// Parse NDMA API response and convert to NdmaAlert objects (async version for performance)
  static Future<List<NdmaAlert>> _parseNdmaAlertsResponseAsync(dynamic data) async {
    final List<NdmaAlert> alerts = [];
    
    try {
      // Handle different possible response structures
      List<dynamic> alertsData = [];
      
      if (data is List) {
        alertsData = data;
        print('📋 Processing ${alertsData.length} alerts from List structure');
      } else if (data is Map && data.containsKey('alerts')) {
        alertsData = data['alerts'] as List? ?? [];
        print('📋 Processing ${alertsData.length} alerts from "alerts" key');
      } else if (data is Map && data.containsKey('data')) {
        alertsData = data['data'] as List? ?? [];
        print('📋 Processing ${alertsData.length} alerts from "data" key');
      } else {
        print('⚠️ Unexpected NDMA API response structure: ${data.runtimeType}');
        return [];
      }

      if (alertsData.isEmpty) {
        print('ℹ️ No alerts in NDMA API response');
        return [];
      }

      // Process each alert in parallel for better performance
      for (int i = 0; i < alertsData.length; i++) {
        final alertData = alertsData[i];
        
        if (alertData is! Map<String, dynamic>) {
          print('⚠️ Skipping invalid alert data at index $i: ${alertData.runtimeType}');
          continue;
        }

        try {
          final alert = NdmaAlert.fromJson(alertData);
          
          // Only add valid, active alerts
          if (alert.isActive && _isValidAlert(alert)) {
            alerts.add(alert);
            print('✅ Added alert: ${alert.disasterType} for ${alert.areaDescription}');
          } else {
            print('⚠️ Skipping inactive or invalid alert: ${alert.alertId}');
          }
        } catch (e) {
          print('❌ Error parsing alert at index $i: $e');
        }
      }

      print('🎯 Successfully parsed ${alerts.length} valid alerts');
      return alerts;
    } catch (e, stackTrace) {
      print('💥 Error parsing NDMA alerts response: $e');
      print('📚 Stack trace: $stackTrace');
      return [];
    }
  }

  /// Convert NDMA alerts to Event objects for backward compatibility
  static Future<List<Event>> convertNdmaAlertsToEvents(List<NdmaAlert> ndmaAlerts) async {
    final List<Event> events = [];
    
    for (final alert in ndmaAlerts) {
      try {
        final event = Event(
          eventId: alert.alertId,
          eventType: _categorizeDisasterType(alert.disasterType),
          severity: alert.severity,
          location: Location(
            lat: alert.centroid.lat,
            lng: alert.centroid.lng,
            address: alert.areaDescription,
          ),
          mediaURL: '',
          timestamp: alert.effectiveStartTime,
          summary: _createEventSummary(alert),
          description: alert.getLocalizedWarningMessage(),
          reportedBy: alert.alertSource,
          category: _categorizeDisasterType(alert.disasterType),
          impactRadius: _calculateImpactRadius(alert.severity),
        );
        
        events.add(event);
      } catch (e) {
        print('❌ Error converting NDMA alert to Event: $e');
      }
    }
    
    return events;
  }

  /// Create a summary for the event
  static String _createEventSummary(NdmaAlert alert) {
    final emoji = _getEmoji(alert.disasterType);
    return '$emoji ${alert.disasterType} Alert - ${alert.areaDescription}';
  }

  /// Get appropriate emoji for disaster type
  static String _getEmoji(String disasterType) {
    final type = disasterType.toLowerCase();
    if (type.contains('rain') || type.contains('flood')) {
      return '🌧️';
    } else if (type.contains('thunder') || type.contains('lightning')) {
      return '⛈️';
    } else if (type.contains('wind') || type.contains('cyclone')) {
      return '💨';
    } else if (type.contains('heat') || type.contains('temperature')) {
      return '🌡️';
    } else if (type.contains('fire')) {
      return '🔥';
    } else if (type.contains('earthquake')) {
      return '🏚️';
    } else {
      return '⚠️';
    }
  }

  /// Categorize disaster type into our event categories
  static String _categorizeDisasterType(String disasterType) {
    final type = disasterType.toLowerCase();
    
    if (type.contains('rain') || type.contains('flood') || type.contains('cyclone') || 
        type.contains('thunder') || type.contains('wind') || type.contains('storm')) {
      return 'Weather';
    } else if (type.contains('fire') || type.contains('accident') || type.contains('emergency')) {
      return 'Emergency';
    } else if (type.contains('earthquake') || type.contains('landslide')) {
      return 'Geological';
    } else if (type.contains('health') || type.contains('disease')) {
      return 'Health';
    } else {
      return 'Other';
    }
  }

  /// Check if an alert is valid
  static bool _isValidAlert(NdmaAlert alert) {
    // Check if alert has required fields
    if (alert.alertId.isEmpty || alert.disasterType.isEmpty) {
      return false;
    }
    
    // Check if coordinates are valid
    if (alert.centroid.lat == 0.0 && alert.centroid.lng == 0.0) {
      return false;
    }
    
    // Check if it's not expired
    if (alert.effectiveEndTime.isBefore(DateTime.now())) {
      return false;
    }
    
    return true;
  }

  /// Validate alert coordinates for Indian region
  static void _validateAlertCoordinates(NdmaAlert alert) {
    final lat = alert.centroid.lat;
    final lng = alert.centroid.lng;
    
    // Check if coordinates are within India's boundaries (rough check)
    if (lat < 6.0 || lat > 37.0 || lng < 68.0 || lng > 97.0) {
      print('⚠️ Alert coordinates outside India: lat=$lat, lng=$lng for ${alert.disasterType}');
    } else {
      print('✅ Alert coordinates validated for ${alert.disasterType}');
    }
  }

  /// Calculate impact radius based on severity
  static double _calculateImpactRadius(String severity) {
    switch (severity.toLowerCase()) {
      case 'high':
      case 'extreme':
      case 'severe':
        return 10.0; // 10km radius for high severity
      case 'medium':
      case 'moderate':
        return 5.0; // 5km radius for medium severity
      case 'low':
      case 'minor':
        return 2.0; // 2km radius for low severity
      default:
        return 5.0; // Default 5km radius
    }
  }

  /// Check if NDMA service is available
  static Future<bool> isServiceAvailable() async {
    try {
      final response = await http.head(
        Uri.parse(_baseUrl),
        headers: {'Accept': 'application/json'},
      ).timeout(const Duration(seconds: 10));
      
      return response.statusCode < 500; // Service available if not server error
    } catch (e) {
      print('NDMA service availability check failed: $e');
      return false;
    }
  }
}
