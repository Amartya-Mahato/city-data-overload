import 'dart:convert';

class NdmaAlert {
  final String alertId;
  final Map<String, dynamic> areaJson; // GeoJSON MultiPolygon coordinates
  final String severityColor; // Color code for visual severity
  final String disasterType; // e.g., "Very Heavy Rain"
  final String warningMessage; // Warning message text
  final DateTime effectiveStartTime;
  final DateTime effectiveEndTime;
  final String areaDescription; // Districts affected 
  final Centroid centroid; // Coordinates for pin marker
  final String severity; // Original severity level
  final String actualLang; // Language code
  final String alertSource; // Source organization
  final double areaCovered; // Area covered in sq km

  NdmaAlert({
    required this.alertId,
    required this.areaJson,
    required this.severityColor,
    required this.disasterType,
    required this.warningMessage,
    required this.effectiveStartTime,
    required this.effectiveEndTime,
    required this.areaDescription,
    required this.centroid,
    required this.severity,
    this.actualLang = 'en',
    this.alertSource = 'NDMA',
    this.areaCovered = 0.0,
  });

  factory NdmaAlert.fromJson(Map<String, dynamic> json) {
    // Parse area_json from GeoJSON format
    Map<String, dynamic> areaJson = {};
    if (json['area_json'] != null) {
      if (json['area_json'] is String) {
        try {
          areaJson = jsonDecode(json['area_json']);
        } catch (e) {
          print('Error parsing area_json: $e');
          areaJson = _createDefaultPolygon();
        }
      } else if (json['area_json'] is Map) {
        areaJson = Map<String, dynamic>.from(json['area_json']);
      }
    } else {
      areaJson = _createDefaultPolygon();
    }

    // Parse warning message - it's a simple string in the real API
    String warningMessage = json['warning_message']?.toString() ?? 'Weather alert in the area';

    // Parse effective times from the actual API format
    DateTime effectiveStart = DateTime.now();
    DateTime effectiveEnd = DateTime.now().add(const Duration(hours: 24));
    
    try {
      if (json['effective_start_time'] != null) {
        // Parse format: "Sat Jul 26 19:00:00 IST 2025"
        effectiveStart = _parseNdmaDateTime(json['effective_start_time'].toString());
      }
      if (json['effective_end_time'] != null) {
        effectiveEnd = _parseNdmaDateTime(json['effective_end_time'].toString());
      }
    } catch (e) {
      print('Error parsing NDMA dates: $e');
      effectiveStart = DateTime.now();
      effectiveEnd = DateTime.now().add(const Duration(hours: 24));
    }

    // Parse centroid from string format: "lng,lat"
    Centroid centroid = Centroid(lat: 12.9716, lng: 77.5946); // Default to Bangalore
    if (json['centroid'] != null) {
      try {
        final centroidStr = json['centroid'].toString();
        final parts = centroidStr.split(',');
        if (parts.length >= 2) {
          centroid = Centroid(
            lat: double.parse(parts[1].trim()),
            lng: double.parse(parts[0].trim()),
          );
        }
      } catch (e) {
        print('Error parsing centroid: $e');
      }
    }

    // Parse severity and map NDMA values to our system
    String severity = _mapNdmaSeverity(json['severity']?.toString() ?? 'ALERT');
    
    return NdmaAlert(
      alertId: json['identifier']?.toString() ?? 
               json['alert_id']?.toString() ?? 
               DateTime.now().millisecondsSinceEpoch.toString(),
      areaJson: areaJson,
      severityColor: json['severity_color']?.toString() ?? 
                    _getSeverityColorFromLevel(severity),
      disasterType: json['disaster_type']?.toString() ?? 'Weather Alert',
      warningMessage: warningMessage,
      effectiveStartTime: effectiveStart,
      effectiveEndTime: effectiveEnd,
      areaDescription: json['area_description']?.toString() ?? 'Alert area',
      centroid: centroid,
      severity: severity,
      actualLang: json['actual_lang']?.toString() ?? 'en',
      alertSource: json['alert_source']?.toString() ?? 'NDMA',
      areaCovered: double.tryParse(json['area_covered']?.toString() ?? '0') ?? 0.0,
    );
  }

  // Parse NDMA datetime format: "Sat Jul 26 19:00:00 IST 2025"
  static DateTime _parseNdmaDateTime(String dateStr) {
    try {
      // Remove timezone info and parse
      final cleanDateStr = dateStr.replaceAll(' IST', '').trim();
      
      // Split into parts
      final parts = cleanDateStr.split(' ');
      if (parts.length >= 5) {
        final monthMap = {
          'Jan': 1, 'Feb': 2, 'Mar': 3, 'Apr': 4, 'May': 5, 'Jun': 6,
          'Jul': 7, 'Aug': 8, 'Sep': 9, 'Oct': 10, 'Nov': 11, 'Dec': 12
        };
        
        final month = monthMap[parts[1]] ?? DateTime.now().month;
        final day = int.parse(parts[2]);
        final year = int.parse(parts[4]);
        
        // Parse time
        final timeParts = parts[3].split(':');
        final hour = int.parse(timeParts[0]);
        final minute = int.parse(timeParts[1]);
        final second = int.parse(timeParts[2]);
        
        return DateTime(year, month, day, hour, minute, second);
      }
    } catch (e) {
      print('Error parsing NDMA date "$dateStr": $e');
    }
    
    return DateTime.now();
  }

  // Map NDMA severity values to our system
  static String _mapNdmaSeverity(String ndmaSeverity) {
    switch (ndmaSeverity.toUpperCase()) {
      case 'ALERT':
      case 'SEVERE':
      case 'EXTREME':
        return 'High';
      case 'MODERATE':
      case 'MEDIUM':
        return 'Medium';
      case 'MINOR':
      case 'LOW':
        return 'Low';
      default:
        return 'Medium';
    }
  }

  // Create a default polygon when area_json is missing
  static Map<String, dynamic> _createDefaultPolygon() {
    return {
      'type': 'Feature',
      'geometry': {
        'type': 'MultiPolygon',
        'coordinates': [
          [
            [
              [77.5, 12.9],
              [77.6, 12.9],
              [77.6, 13.0],
              [77.5, 13.0],
              [77.5, 12.9],
            ]
          ]
        ]
      },
      'properties': {}
    };
  }

  Map<String, dynamic> toJson() {
    return {
      'alert_id': alertId,
      'area_json': areaJson,
      'severity_color': severityColor,
      'disaster_type': disasterType,
      'warning_message': warningMessage,
      'effective_start_time': effectiveStartTime.toIso8601String(),
      'effective_end_time': effectiveEndTime.toIso8601String(),
      'area_description': areaDescription,
      'centroid': centroid.toJson(),
      'severity': severity,
      'actual_lang': actualLang,
      'alert_source': alertSource,
      'area_covered': areaCovered,
    };
  }

  // Get warning message (now a simple string)
  String getLocalizedWarningMessage() {
    return warningMessage.isNotEmpty ? warningMessage : 'Alert for $areaDescription';
  }

  // Check if alert is currently active
  bool get isActive {
    final now = DateTime.now();
    return now.isAfter(effectiveStartTime) && now.isBefore(effectiveEndTime);
  }

  // Get severity level as enum
  SeverityLevel get severityLevel {
    switch (severity.toLowerCase()) {
      case 'high':
      case 'severe':
      case 'extreme':
        return SeverityLevel.severe;
      case 'medium':
      case 'moderate':
        return SeverityLevel.moderate;
      case 'low':
      case 'minor':
        return SeverityLevel.minor;
      default:
        return SeverityLevel.moderate;
    }
  }

  // Get display color for severity
  String get displayColor {
    if (severityColor.isNotEmpty) {
      return severityColor;
    }
    return _getSeverityColorFromLevel(severity);
  }

  // Static method to get severity color from level
  static String _getSeverityColorFromLevel(String severity) {
    switch (severity.toLowerCase()) {
      case 'high':
      case 'severe':
      case 'extreme':
        return '#FF0000'; // Red
      case 'medium':
      case 'moderate':
        return '#FFA500'; // Orange  
      case 'low':
      case 'minor':
        return '#FFFF00'; // Yellow
      default:
        return '#FFA500'; // Default orange
    }
  }

  // Get formatted time range
  String get timeRange {
    final start = effectiveStartTime;
    final end = effectiveEndTime;
    
    if (start.day == end.day && start.month == end.month && start.year == end.year) {
      // Same day
      return '${_formatTime(start)} - ${_formatTime(end)}, ${_formatDate(start)}';
    } else {
      // Different days
      return '${_formatDateTime(start)} - ${_formatDateTime(end)}';
    }
  }

  String _formatTime(DateTime dateTime) {
    final hour = dateTime.hour > 12 ? dateTime.hour - 12 : (dateTime.hour == 0 ? 12 : dateTime.hour);
    final minute = dateTime.minute.toString().padLeft(2, '0');
    final period = dateTime.hour >= 12 ? 'PM' : 'AM';
    return '$hour:$minute $period';
  }

  String _formatDate(DateTime dateTime) {
    final months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    final days = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
    return '${days[dateTime.weekday % 7]}, ${months[dateTime.month - 1]} ${dateTime.day}';
  }

  String _formatDateTime(DateTime dateTime) {
    return '${_formatTime(dateTime)}, ${_formatDate(dateTime)}';
  }
}

class Centroid {
  final double lat;
  final double lng;

  Centroid({
    required this.lat,
    required this.lng,
  });

  factory Centroid.fromJson(Map<String, dynamic> json) {
    return Centroid(
      lat: json['lat']?.toDouble() ?? json['latitude']?.toDouble() ?? 0.0,
      lng: json['lng']?.toDouble() ?? json['longitude']?.toDouble() ?? 0.0,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'lat': lat,
      'lng': lng,
    };
  }
}

enum SeverityLevel {
  minor,
  moderate, 
  severe,
} 