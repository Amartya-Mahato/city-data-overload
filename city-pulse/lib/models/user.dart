import 'package:flutter/material.dart';
import 'event.dart';

class User {
  final String userId;
  final String name;
  final String email;
  final Location location;
  final UserPreferences preferences;
  final int reputation;
  final int reportsSubmitted;

  User({
    required this.userId,
    required this.name,
    required this.email,
    required this.location,
    required this.preferences,
    required this.reputation,
    required this.reportsSubmitted,
  });

  factory User.fromJson(Map<String, dynamic> json) {
    return User(
      userId: json['userId'] ?? '',
      name: json['name'] ?? '',
      email: json['email'] ?? '',
      location: Location.fromJson(json['location'] ?? {}),
      preferences: UserPreferences.fromJson(json['preferences'] ?? {}),
      reputation: json['reputation'] ?? 0,
      reportsSubmitted: json['reportsSubmitted'] ?? 0,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'userId': userId,
      'name': name,
      'email': email,
      'location': location.toJson(),
      'preferences': preferences.toJson(),
      'reputation': reputation,
      'reportsSubmitted': reportsSubmitted,
    };
  }

  User copyWith({
    String? userId,
    String? name,
    String? email,
    Location? location,
    UserPreferences? preferences,
    int? reputation,
    int? reportsSubmitted,
  }) {
    return User(
      userId: userId ?? this.userId,
      name: name ?? this.name,
      email: email ?? this.email,
      location: location ?? this.location,
      preferences: preferences ?? this.preferences,
      reputation: reputation ?? this.reputation,
      reportsSubmitted: reportsSubmitted ?? this.reportsSubmitted,
    );
  }
}

class Report {
  final String id;
  final String title;
  final String description;
  final String category;
  final String severity;
  final DateTime submittedAt;
  final ReportStatus status;
  final String? feedback;
  final String submittedByUid;
  final double latitude;
  final double longitude;
  final String? imageUrl;

  Report({
    required this.id,
    required this.title,
    required this.description,
    required this.category,
    required this.severity,
    required this.submittedAt,
    required this.status,
    required this.submittedByUid,
    required this.latitude,
    required this.longitude,
    this.feedback,
    this.imageUrl,
  });

  factory Report.fromJson(Map<String, dynamic> json) {
    return Report(
      id: json['id'] ?? '',
      title: json['title'] ?? '',
      description: json['description'] ?? '',
      category: json['category'] ?? '',
      severity: json['severity'] ?? '',
      submittedAt: DateTime.parse(json['submittedAt'] ?? DateTime.now().toIso8601String()),
      status: ReportStatus.values.firstWhere(
        (status) => status.toString().split('.').last == json['status'],
        orElse: () => ReportStatus.submitted,
      ),
      submittedByUid: json['submittedByUid'] ?? '',
      latitude: (json['latitude'] ?? 0.0).toDouble(),
      longitude: (json['longitude'] ?? 0.0).toDouble(),
      feedback: json['feedback'],
      imageUrl: json['imageUrl'],
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'title': title,
      'description': description,
      'category': category,
      'severity': severity,
      'submittedAt': submittedAt.toIso8601String(),
      'status': status.toString().split('.').last,
      'submittedByUid': submittedByUid,
      'latitude': latitude,
      'longitude': longitude,
      'feedback': feedback,
      'imageUrl': imageUrl,
    };
  }
}

enum ReportStatus {
  submitted,
  pending,
  underReview,
  approved,
  rejected,
  resolved,
}

extension ReportStatusExtension on ReportStatus {
  String get displayName {
    switch (this) {
      case ReportStatus.submitted:
        return 'Submitted';
      case ReportStatus.pending:
        return 'Pending';
      case ReportStatus.underReview:
        return 'Under Review';
      case ReportStatus.approved:
        return 'Approved';
      case ReportStatus.rejected:
        return 'Rejected';
      case ReportStatus.resolved:
        return 'Resolved';
    }
  }

  Color get statusColor {
    switch (this) {
      case ReportStatus.submitted:
        return const Color(0xFF06B6D4); // Cyan
      case ReportStatus.pending:
        return const Color(0xFFF59E0B); // Orange
      case ReportStatus.underReview:
        return const Color(0xFF8B5CF6); // Purple
      case ReportStatus.approved:
        return const Color(0xFF22C55E); // Green
      case ReportStatus.rejected:
        return const Color(0xFFEF4444); // Red
      case ReportStatus.resolved:
        return const Color(0xFF10B981); // Emerald
    }
  }
}

class UserPreferences {
  final double alertRadius;
  final List<String> categories;
  final bool notificationEnabled;
  final bool darkMode;

  UserPreferences({
    required this.alertRadius,
    required this.categories,
    required this.notificationEnabled,
    required this.darkMode,
  });

  factory UserPreferences.fromJson(Map<String, dynamic> json) {
    return UserPreferences(
      alertRadius: (json['alertRadius'] ?? 5.0).toDouble(),
      categories: List<String>.from(json['categories'] ?? []),
      notificationEnabled: json['notificationEnabled'] ?? true,
      darkMode: json['darkMode'] ?? false,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'alertRadius': alertRadius,
      'categories': categories,
      'notificationEnabled': notificationEnabled,
      'darkMode': darkMode,
    };
  }

  UserPreferences copyWith({
    double? alertRadius,
    List<String>? categories,
    bool? notificationEnabled,
    bool? darkMode,
  }) {
    return UserPreferences(
      alertRadius: alertRadius ?? this.alertRadius,
      categories: categories ?? this.categories,
      notificationEnabled: notificationEnabled ?? this.notificationEnabled,
      darkMode: darkMode ?? this.darkMode,
    );
  }
}

class EventCategory {
  final String id;
  final String name;
  final String icon;
  final String color;

  EventCategory({
    required this.id,
    required this.name,
    required this.icon,
    required this.color,
  });

  factory EventCategory.fromJson(Map<String, dynamic> json) {
    return EventCategory(
      id: json['id'] ?? '',
      name: json['name'] ?? '',
      icon: json['icon'] ?? '',
      color: json['color'] ?? '',
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'name': name,
      'icon': icon,
      'color': color,
    };
  }
}
