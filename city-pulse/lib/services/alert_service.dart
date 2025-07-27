import 'dart:async';
import 'dart:math';
import '../models/event.dart';
import '../models/ndma_alert.dart';
import '../models/user.dart';
import 'notification_service.dart';
import 'ndma_service.dart';
import 'location_service.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../providers/theme_provider.dart';
import '../providers/user_provider.dart';
import '../providers/ndma_alerts_provider.dart';

class AlertService {
  static final AlertService _instance = AlertService._internal();
  factory AlertService() => _instance;
  AlertService._internal();

  Timer? _alertTimer;
  final List<NdmaAlert> _activeAlerts = [];
  ProviderContainer? _providerContainer;
  bool _hasActiveHighSeverityAlerts = false;
  
  // User preferences for alert categories
  static const Map<String, String> _categoryTopics = {
    'Traffic': 'traffic_alerts',
    'Emergency': 'emergency_alerts',
    'Weather': 'weather_alerts',
    'Infrastructure': 'infrastructure_alerts',
    'Utilities': 'utilities_alerts',
  };

  // Initialize alert service
  Future<void> initialize({ProviderContainer? providerContainer}) async {
    await NotificationService.initialize();
    _providerContainer = providerContainer;
    _startAlertMonitoring();
  }

  // Start monitoring for alerts
  void _startAlertMonitoring() {
    _alertTimer = Timer.periodic(const Duration(minutes: 1), (timer) {
      _checkForNewAlerts();
    });
  }

  // Stop alert monitoring
  void stopAlertMonitoring() {
    _alertTimer?.cancel();
  }

  // Manual trigger for checking (public method)
  Future<void> checkNow() async {
    print('📱 Manual trigger: Starting foreground alert check...');
    await _checkForNewAlerts();
    print('📱 Manual trigger: Foreground alert check completed');
  }

  // Subscribe user to alert categories based on preferences
  Future<void> subscribeToAlertCategories(UserPreferences preferences) async {
    for (String category in preferences.categories) {
      final topic = _categoryTopics[category];
      if (topic != null) {
        await NotificationService.subscribeToTopic(topic);
        print('Subscribed to $category alerts');
      }
    }
  }

  // Unsubscribe from alert categories
  Future<void> unsubscribeFromAlertCategories(List<String> categories) async {
    for (String category in categories) {
      final topic = _categoryTopics[category];
      if (topic != null) {
        await NotificationService.unsubscribeFromTopic(topic);
        print('Unsubscribed from $category alerts');
      }
    }
  }

  // Check for new alerts from NDMA API
  Future<void> _checkForNewAlerts() async {
    try {
      // Get current location
      final location = await LocationService.getCurrentLocation();
      if (location == null) {
        print('Unable to get current location for NDMA alerts');
        return;
      }

      // Get user preferences for alert radius (default 300km for NDMA)
      double alertRadius = 300.0; // Default radius set to 300 km as per NDMA requirements
      if (_providerContainer != null) {
        try {
          final userState = _providerContainer!.read(userProvider);
          userState.whenData((user) {
            alertRadius = user.preferences.alertRadius;
          });
        } catch (e) {
          print('Error getting user preferences: $e');
        }
      }

      // Fetch alerts from NDMA API using new method
      final ndmaAlerts = await NdmaService.fetchNdmaAlerts(
        latitude: location.lat,
        longitude: location.lng,
        radiusKm: alertRadius,
      );

      // Update provider with new alerts
      if (_providerContainer != null) {
        try {
          // Manually update the provider with new data (if needed for real-time updates)
          // The provider will automatically refresh through its own mechanisms
          print('Updated NDMA alerts: ${ndmaAlerts.length} alerts found');
        } catch (e) {
          print('Error updating NDMA alerts provider: $e');
        }
      }

      // Process new alerts for notifications with detailed logging
      _activeAlerts.clear();
      print('🔄 Foreground: Processing ${ndmaAlerts.length} total alerts for notifications');
      
      for (int i = 0; i < ndmaAlerts.length; i++) {
        final alert = ndmaAlerts[i];
        print('   Alert ${i + 1}: ${alert.disasterType} - ${alert.severity} - Active: ${alert.isActive} - Area: ${alert.areaDescription}');
        
        // Process ALL alerts (not just active ones) for foreground notifications
        print('✅ Processing alert: ${alert.disasterType} (Active: ${alert.isActive})');
        await _processNewNdmaAlert(alert);
      }
      
      print('🔄 Foreground: Processed ${_activeAlerts.length} active alerts');

      // Update theme based on active alert presence
      _updateThemeForAlerts();

    } catch (e) {
      print('Error checking for NDMA alerts: $e');
    }
  }



  // Process new NDMA alert and send notification if needed
  Future<void> _processNewNdmaAlert(NdmaAlert alert) async {
    _activeAlerts.add(alert);
    
    // Update theme immediately when severe alert is added
    if (alert.severityLevel == SeverityLevel.severe) {
      _hasActiveHighSeverityAlerts = true;
      _updateThemeForAlerts();
    }
    
    // Check if user should receive notification for this disaster type
    final shouldNotify = await NotificationService.areNotificationsEnabledForCategory(alert.disasterType);
    
    print('🔔 Foreground: Should notify for ${alert.disasterType}? $shouldNotify');
    
    if (shouldNotify) {
      // Add timestamp to ensure duplicate notifications are shown
      final timestamp = DateTime.now();
      final timeStr = '${timestamp.hour.toString().padLeft(2, '0')}:${timestamp.minute.toString().padLeft(2, '0')}';
      
      print('📤 Foreground: Sending notification for ${alert.disasterType}');
      
      await NotificationService.showAlertNotification(
        title: '🚨 ${_getDisasterEmoji(alert.disasterType)} ${alert.disasterType} Alert',
        body: '${alert.getLocalizedWarningMessage()}\nArea: ${alert.areaDescription}\n\n⏰ Updated: $timeStr',
        alertType: alert.disasterType,
        severity: alert.severity,
      );
      
      print('✅ Foreground: Notification sent for ${alert.disasterType} in ${alert.areaDescription} at $timeStr');
    } else {
      print('❌ Foreground: Notification blocked for ${alert.disasterType}');
    }
  }

  // Get emoji for disaster type
  String _getDisasterEmoji(String disasterType) {
    switch (disasterType.toLowerCase()) {
      case 'very heavy rain':
      case 'heavy rain':
      case 'rainfall':
      case 'rain':
        return '🌧️';
      case 'flood':
      case 'flooding':
        return '🌊';
      case 'thunderstorm':
      case 'lightning':
        return '⛈️';
      case 'cyclone':
      case 'hurricane':
      case 'storm':
        return '🌪️';
      case 'heat wave':
      case 'extreme heat':
        return '🌡️';
      case 'fire':
      case 'wildfire':
      case 'forest fire':
        return '🔥';
      case 'earthquake':
      case 'seismic':
        return '🏔️';
      case 'tsunami':
        return '🌊';
      default:
        return '⚠️';
    }
  }

  // Send a test notification
  Future<void> sendTestNotification() async {
    await NotificationService.showAlertNotification(
      title: '🧪 Test Notification',
      body: 'This is a test notification from City Pulse. Your alerts are working!',
      alertType: 'Test',
      severity: 'Medium',
    );
  }

  // Send emergency alert to all users
  Future<void> sendEmergencyAlert({
    required String title,
    required String message,
    required String location,
  }) async {
    await NotificationService.showAlertNotification(
      title: '🚨 EMERGENCY: $title',
      body: '$message Location: $location',
      alertType: 'Emergency',
      severity: 'High',
    );
  }

  // Get active NDMA alerts
  List<NdmaAlert> getActiveAlerts() {
    return List.unmodifiable(_activeAlerts);
  }

  // Clear expired alerts
  void clearExpiredAlerts() {
    final now = DateTime.now();
    final removedCount = _activeAlerts.length;
    _activeAlerts.removeWhere((alert) => !alert.isActive);
    
    // Update theme if alerts were removed
    if (removedCount != _activeAlerts.length) {
      _updateThemeForAlerts();
    }
  }

  // Get FCM token for server-side notifications
  Future<String?> getFCMToken() async {
    return await NotificationService.getFCMToken();
  }

  // Get NDMA alert statistics
  Map<String, int> getAlertStatistics() {
    final stats = <String, int>{};
    for (final alert in _activeAlerts) {
      stats[alert.disasterType] = (stats[alert.disasterType] ?? 0) + 1;
    }
    return stats;
  }

  // Update theme based on current NDMA alerts
  void _updateThemeForAlerts() {
    if (_providerContainer == null) return;
    
    try {
      final hasSevereAlerts = _activeAlerts.any(
        (alert) => alert.severityLevel == SeverityLevel.severe
      );
      
      _hasActiveHighSeverityAlerts = hasSevereAlerts;
      
      final themeNotifier = _providerContainer!.read(dynamicThemeProvider.notifier);
      
      if (hasSevereAlerts) {
        // Find the most severe alert to determine theme
        final severestAlert = _activeAlerts.firstWhere(
          (alert) => alert.severityLevel == SeverityLevel.severe,
          orElse: () => _activeAlerts.first,
        );
        
        themeNotifier.updateThemeForAlert(
          severestAlert.disasterType,
          severestAlert.severity,
        );
        
        // Also update to emergency red theme
        themeNotifier.setEmergencyTheme(true);
      } else {
        themeNotifier.clearAlert();
        themeNotifier.setEmergencyTheme(false);
      }
    } catch (e) {
      print('Error updating theme for NDMA alerts: $e');
    }
  }
  
  // Get NDMA alerts for a specific location
  Future<List<NdmaAlert>> getNdmaAlertsForLocation({
    required double latitude,
    required double longitude,
    required double radiusKm,
  }) async {
    try {
      return await NdmaService.fetchNdmaAlerts(
        latitude: latitude,
        longitude: longitude,
        radiusKm: radiusKm,
      );
    } catch (e) {
      print('Error fetching NDMA alerts: $e');
      return [];
    }
  }
  
  // Check if there are active high severity alerts
  bool hasActiveHighSeverityAlerts() {
    return _hasActiveHighSeverityAlerts;
  }
  
  // Manually trigger alert check
  Future<void> checkForAlertsNow() async {
    await _checkForNewAlerts();
  }

  // Clean up resources
  void dispose() {
    _alertTimer?.cancel();
    _activeAlerts.clear();
    _hasActiveHighSeverityAlerts = false;
    
    // Clear emergency theme on dispose
    if (_providerContainer != null) {
      try {
        final themeNotifier = _providerContainer!.read(dynamicThemeProvider.notifier);
        themeNotifier.clearAlert();
        themeNotifier.setEmergencyTheme(false);
      } catch (e) {
        print('Error clearing theme on dispose: $e');
      }
    }
  }
}
