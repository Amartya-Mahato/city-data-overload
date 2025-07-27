class Event {
  final String eventId;
  final String eventType;
  final String severity;
  final Location location;
  final String mediaURL;
  final DateTime timestamp;
  final String summary;
  final String description;
  final String reportedBy;
  final String category;
  final double impactRadius;

  Event({
    required this.eventId,
    required this.eventType,
    required this.severity,
    required this.location,
    required this.mediaURL,
    required this.timestamp,
    required this.summary,
    required this.description,
    required this.reportedBy,
    required this.category,
    required this.impactRadius,
  });

  factory Event.fromJson(Map<String, dynamic> json) {
    return Event(
      eventId: json['eventId'] ?? '',
      eventType: json['eventType'] ?? '',
      severity: json['severity'] ?? '',
      location: Location.fromJson(json['location'] ?? {}),
      mediaURL: json['mediaURL'] ?? '',
      timestamp: DateTime.parse(json['timestamp'] ?? DateTime.now().toIso8601String()),
      summary: json['summary'] ?? '',
      description: json['description'] ?? '',
      reportedBy: json['reportedBy'] ?? '',
      category: json['category'] ?? '',
      impactRadius: (json['impactRadius'] ?? 0.0).toDouble(),
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'eventId': eventId,
      'eventType': eventType,
      'severity': severity,
      'location': location.toJson(),
      'mediaURL': mediaURL,
      'timestamp': timestamp.toIso8601String(),
      'summary': summary,
      'description': description,
      'reportedBy': reportedBy,
      'category': category,
      'impactRadius': impactRadius,
    };
  }

  // Helper method to get severity color
  String get severityColor {
    switch (severity.toLowerCase()) {
      case 'high':
        return '#F44336';
      case 'medium':
        return '#FF9800';
      case 'low':
        return '#4CAF50';
      default:
        return '#9E9E9E';
    }
  }

  // Helper method to get category icon
  String get categoryIcon {
    switch (category.toLowerCase()) {
      case 'traffic':
        return 'traffic';
      case 'emergency':
        return 'warning';
      case 'weather':
        return 'cloud';
      case 'infrastructure':
        return 'construction';
      case 'utilities':
        return 'power';
      default:
        return 'info';
    }
  }
}

class Location {
  final double lat;
  final double lng;
  final String address;

  Location({
    required this.lat,
    required this.lng,
    required this.address,
  });

  factory Location.fromJson(Map<String, dynamic> json) {
    return Location(
      lat: (json['lat'] ?? 0.0).toDouble(),
      lng: (json['lng'] ?? 0.0).toDouble(),
      address: json['address'] ?? '',
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'lat': lat,
      'lng': lng,
      'address': address,
    };
  }
}
