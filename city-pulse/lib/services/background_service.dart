import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:ui';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:flutter_background_service/flutter_background_service.dart';
import 'package:flutter_background_service_android/flutter_background_service_android.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import '../models/ndma_alert.dart';
import '../services/ndma_service.dart';
import '../services/location_service.dart';

class BackgroundService {
  static const String _lastAlertCheckKey = 'lastAlertCheck';
  static const String _lastKnownAlertsKey = 'lastKnownAlerts';
  static FlutterLocalNotificationsPlugin? _notifications;

  // Initialize background service
  static Future<void> initialize() async {
    try {
      // Enable in debug mode and all platforms for testing
      if (Platform.isAndroid || Platform.isIOS) {
        await _initializeNotifications();
        await _initializeBackgroundService();
        print('🔄 Background service initialized successfully');
      } else {
        print('🚧 Background service disabled - unsupported platform');
      }
    } catch (e) {
      print('💥 Error initializing background service: $e');
    }
  }

  // Initialize local notifications
  static Future<void> _initializeNotifications() async {
    _notifications = FlutterLocalNotificationsPlugin();
    
    const AndroidInitializationSettings initializationSettingsAndroid =
        AndroidInitializationSettings('@mipmap/ic_launcher');
    
    const DarwinInitializationSettings initializationSettingsIOS =
        DarwinInitializationSettings(
      requestAlertPermission: true,
      requestBadgePermission: true,
      requestSoundPermission: true,
    );
    
    const InitializationSettings initializationSettings =
        InitializationSettings(
      android: initializationSettingsAndroid,
      iOS: initializationSettingsIOS,
    );
    
    await _notifications!.initialize(initializationSettings);
  }

  // Initialize background service
  static Future<void> _initializeBackgroundService() async {
    final service = FlutterBackgroundService();
    
    await service.configure(
      androidConfiguration: AndroidConfiguration(
        onStart: onStart,
        autoStart: true,
        isForegroundMode: false,
        notificationChannelId: 'disaster_alerts_bg',
        initialNotificationTitle: 'City Pulse Alert Monitor',
        initialNotificationContent: 'Monitoring for disaster alerts...',
        foregroundServiceNotificationId: 888,
      ),
      iosConfiguration: IosConfiguration(
        autoStart: true,
        onForeground: onStart,
        onBackground: onIosBackground,
      ),
    );
    
    await service.startService();
  }

  // Stop background service
  static Future<void> stop() async {
    final service = FlutterBackgroundService();
    service.invoke("stopService");
    print('❌ Background service stopped');
  }

  // Check if service is running
  static Future<bool> isRunning() async {
    final service = FlutterBackgroundService();
    return await service.isRunning();
  }

  // Background service entry point
  @pragma('vm:entry-point')
  static void onStart(ServiceInstance service) async {
    DartPluginRegistrant.ensureInitialized();
    
    if (service is AndroidServiceInstance) {
      service.on('setAsForeground').listen((event) {
        service.setAsForegroundService();
      });
      
      service.on('setAsBackground').listen((event) {
        service.setAsBackgroundService();
      });
    }
    
    service.on('stopService').listen((event) {
      service.stopSelf();
    });

    // Check for alerts every 15 minutes
    Timer.periodic(const Duration(minutes: 15), (timer) async {
      if (service is AndroidServiceInstance) {
        if (await service.isForegroundService()) {
          service.setForegroundNotificationInfo(
            title: "City Pulse Alert Monitor",
            content: "Checking for new disaster alerts...",
          );
        }
      }
      
      await checkAlertsInBackground();
      
      if (service is AndroidServiceInstance) {
        if (await service.isForegroundService()) {
          service.setForegroundNotificationInfo(
            title: "City Pulse Alert Monitor", 
            content: "Monitoring for disaster alerts...",
          );
        }
      }
    });
  }

  // iOS background handler
  @pragma('vm:entry-point')
  static Future<bool> onIosBackground(ServiceInstance service) async {
    WidgetsFlutterBinding.ensureInitialized();
    DartPluginRegistrant.ensureInitialized();
    
    await checkAlertsInBackground();
    
    return true;
  }

  // Check for new alerts in background
  static Future<void> checkAlertsInBackground() async {
    try {
      print('🔍 Background: Checking for new alerts...');
      
      final prefs = await SharedPreferences.getInstance();
      final lastCheckTime = prefs.getInt(_lastAlertCheckKey) ?? 0;
      final now = DateTime.now().millisecondsSinceEpoch;
      
      // Only check if it's been more than 10 minutes since last check
      if (now - lastCheckTime < 600000) {
        print('⏰ Background: Skipping check - too recent');
        return;
      }
      
      // Use default Bangalore coordinates for background checks
      const lat = 12.9716;
      const lng = 77.5946;
      
      // Fetch current alerts
      final alerts = await NdmaService.fetchNdmaAlerts(
        latitude: lat,
        longitude: lng,
        radiusKm: 300.0,
      );
      
      print('🔍 Background: Found ${alerts.length} total alerts');
      
      // Log details about each alert
      for (int i = 0; i < alerts.length; i++) {
        final alert = alerts[i];
        print('   Alert ${i + 1}: ${alert.disasterType} - ${alert.severity} - Active: ${alert.isActive} - Area: ${alert.areaDescription}');
      }
      
      // COMMENTED OUT: Get previously known alerts to get notifications every time
      // final lastKnownAlertsJson = prefs.getString(_lastKnownAlertsKey) ?? '[]';
      // final lastKnownAlerts = (jsonDecode(lastKnownAlertsJson) as List)
      //     .map((json) => NdmaAlert.fromJson(json))
      //     .toList();
      
      // Send notifications for ALL severe alerts (not just new ones)
      // final newAlerts = alerts.where((alert) {
      //   return !lastKnownAlerts.any((known) => known.alertId == alert.alertId);
      // }).toList();
      
      final severeActiveAlerts = alerts.where((alert) => 
        alert.severityLevel == SeverityLevel.severe && alert.isActive).toList();
      
      print('🆕 Background: Found ${severeActiveAlerts.length} severe active alerts (only these get notifications)');
      
      // Send notifications for all severe active alerts
      for (final alert in severeActiveAlerts) {
        await _sendBackgroundNotification(alert);
      }
      
      // Update stored data
      await prefs.setInt(_lastAlertCheckKey, now);
      // COMMENTED OUT: Don't update last known alerts to get notifications every time
      // await prefs.setString(
      //   _lastKnownAlertsKey,
      //   jsonEncode(alerts.map((a) => a.toJson()).toList()),
      // );
      
      print('✅ Background: Alert check completed');
    } catch (e) {
      print('💥 Background: Error checking alerts: $e');
    }
  }

  // Send notification for new alert
  static Future<void> _sendBackgroundNotification(NdmaAlert alert) async {
    try {
      if (_notifications == null) {
        await _initializeNotifications();
      }
      
      // Create notification details
      final androidDetails = AndroidNotificationDetails(
        'disaster_alerts_background',
        'Background Disaster Alerts',
        channelDescription: 'Critical disaster alerts received in background',
        importance: Importance.high,
        priority: Priority.high,
        playSound: true,
        enableVibration: true,
        category: AndroidNotificationCategory.alarm,
        showWhen: true,
        styleInformation: const BigTextStyleInformation(''),
      );
      
      const iosDetails = DarwinNotificationDetails(
        presentAlert: true,
        presentBadge: true,
        presentSound: true,
        sound: 'default',
        categoryIdentifier: 'DISASTER_ALERT',
      );
      
      final notificationDetails = NotificationDetails(
        android: androidDetails,
        iOS: iosDetails,
      );
      
      // Create notification content with timestamp for uniqueness
      final disasterIcon = _getDisasterEmoji(alert.disasterType);
      final timestamp = DateTime.now();
      final timeStr = '${timestamp.hour.toString().padLeft(2, '0')}:${timestamp.minute.toString().padLeft(2, '0')}';
      final title = '🚨 $disasterIcon ${alert.disasterType} Alert';
      final body = '${alert.areaDescription}\n${alert.getLocalizedWarningMessage()}\n\n⏰ Updated: $timeStr';
      
      // Generate unique ID within 32-bit range to ensure duplicate notifications are shown
      final uniqueId = (DateTime.now().millisecondsSinceEpoch % 2000000000) + (alert.alertId.hashCode % 1000);
      
      await _notifications!.show(
        uniqueId,
        title,
        body,
        notificationDetails,
        payload: jsonEncode({
          'type': 'disaster_alert',
          'alertId': alert.alertId,
          'severity': alert.severity,
          'timestamp': DateTime.now().millisecondsSinceEpoch,
        }),
      );
      
      print('📲 Background: Sent notification ID=$uniqueId for ${alert.disasterType} in ${alert.areaDescription}');
    } catch (e) {
      print('💥 Background: Error sending notification: $e');
    }
  }

  // Get disaster emoji for notification
  static String _getDisasterEmoji(String disasterType) {
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

  // Manual alert check (for testing)
  static Future<void> checkNow() async {
    await checkAlertsInBackground();
  }

  // Function that sends notifications for ALL alerts (not just severe ones)
  static Future<void> testAlertsWithNotifications() async {
    try {
          print('📡 Background: Starting comprehensive alert check...');
    
    final prefs = await SharedPreferences.getInstance();
      
      // Use default Bangalore coordinates for testing
      const lat = 12.9716;
      const lng = 77.5946;
      
      // Fetch current alerts
      final alerts = await NdmaService.fetchNdmaAlerts(
        latitude: lat,
        longitude: lng,
        radiusKm: 300.0,
      );
      
      print('📡 Background: Found ${alerts.length} total alerts');
      
      if (alerts.isEmpty) {
        // Send a test notification to confirm the system works
        if (_notifications == null) {
          await _initializeNotifications();
        }
        
        await _notifications!.show(
          999999,
          '📍 No Alerts Found',
          'No NDMA alerts found in your area. All clear!',
          const NotificationDetails(
            android: AndroidNotificationDetails(
              'test_channel',
              'Test Notifications',
              channelDescription: 'Test notifications for debugging',
              importance: Importance.high,
              priority: Priority.high,
            ),
            iOS: DarwinNotificationDetails(
              presentAlert: true,
              presentBadge: true,
              presentSound: true,
            ),
          ),
        );
        print('📡 Background: Sent "No alerts found" notification');
        return;
      }
      
      // Send notifications for ALL alerts (not just severe ones)
      for (int i = 0; i < alerts.length; i++) {
        final alert = alerts[i];
        print('📡 Background: Processing alert ${i + 1}: ${alert.disasterType} - ${alert.severity} - Active: ${alert.isActive}');
        
        // Send notification regardless of severity
        await _sendBackgroundTestNotification(alert, i + 1);
      }
      
      print('📡 Background: Sent ${alerts.length} notifications');
      
    } catch (e) {
      print('💥 Background: Error in alert check: $e');
      
      // Send error notification
      if (_notifications == null) {
        await _initializeNotifications();
      }
      
      await _notifications!.show(
        999998,
        '❌ Alert Check Error',
        'Error fetching alerts: ${e.toString()}',
        const NotificationDetails(
          android: AndroidNotificationDetails(
            'test_channel',
            'Test Notifications',
            channelDescription: 'Test notifications for debugging',
            importance: Importance.high,
            priority: Priority.high,
          ),
          iOS: DarwinNotificationDetails(
            presentAlert: true,
            presentBadge: true,
            presentSound: true,
          ),
        ),
      );
    }
  }

  // Send background notification for any alert (not just severe)
  static Future<void> _sendBackgroundTestNotification(NdmaAlert alert, int index) async {
    try {
      if (_notifications == null) {
        await _initializeNotifications();
      }
      
      // Create notification details
      final androidDetails = AndroidNotificationDetails(
        'background_alerts',
        'Background NDMA Alerts',
        channelDescription: 'Background notifications for NDMA alerts',
        importance: Importance.high,
        priority: Priority.high,
        playSound: true,
        enableVibration: true,
        showWhen: true,
      );
      
      const iosDetails = DarwinNotificationDetails(
        presentAlert: true,
        presentBadge: true,
        presentSound: true,
      );
      
      final notificationDetails = NotificationDetails(
        android: androidDetails,
        iOS: iosDetails,
      );
      
      // Create notification content with test prefix
      final disasterIcon = _getDisasterEmoji(alert.disasterType);
      final timestamp = DateTime.now();
      final timeStr = '${timestamp.hour.toString().padLeft(2, '0')}:${timestamp.minute.toString().padLeft(2, '0')}';
      final title = '🚨 ${index}: $disasterIcon ${alert.disasterType} Alert';
      final body = 'Severity: ${alert.severity}\nArea: ${alert.areaDescription}\nActive: ${alert.isActive ? "Yes" : "No"}\n\n⏰ Updated: $timeStr';
      
      // Generate unique ID within 32-bit range
      final uniqueId = (DateTime.now().millisecondsSinceEpoch % 2000000000) + index;
      
      await _notifications!.show(
        uniqueId,
        title,
        body,
        notificationDetails,
        payload: jsonEncode({
          'type': 'background_alert',
          'alertId': alert.alertId,
          'severity': alert.severity,
          'timestamp': DateTime.now().millisecondsSinceEpoch,
        }),
      );
      
      print('📡 Background: Sent notification ID=$uniqueId for ${alert.disasterType}');
      
    } catch (e) {
      print('💥 Background: Error sending notification: $e');
    }
  }
} 