import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/material.dart';
import 'dart:io';

class NotificationService {
  static final NotificationService _instance = NotificationService._internal();
  factory NotificationService() => _instance;
  NotificationService._internal();

  static final FlutterLocalNotificationsPlugin _localNotifications = 
      FlutterLocalNotificationsPlugin();
  static final FirebaseMessaging _firebaseMessaging = FirebaseMessaging.instance;

  // Initialize notification service
  static Future<void> initialize() async {
    // Initialize local notifications
    const androidSettings = AndroidInitializationSettings('@mipmap/ic_launcher');
    const iosSettings = DarwinInitializationSettings(
      requestAlertPermission: true,
      requestBadgePermission: true,
      requestSoundPermission: true,
    );
    
    const initSettings = InitializationSettings(
      android: androidSettings,
      iOS: iosSettings,
    );

    await _localNotifications.initialize(
      initSettings,
      onDidReceiveNotificationResponse: _onNotificationTapped,
    );

    // Request permissions for iOS
    if (Platform.isIOS) {
      await _firebaseMessaging.requestPermission(
        alert: true,
        badge: true,
        sound: true,
        provisional: false,
      );
    }

    // Request permission for Android 13+ (API 33+)
    if (Platform.isAndroid) {
      await _firebaseMessaging.requestPermission();
    }

    // Set up Firebase messaging
    await _setupFirebaseMessaging();
  }

  static Future<void> _setupFirebaseMessaging() async {
    // Get FCM token for this device
    final token = await _firebaseMessaging.getToken();
    print('FCM Token: $token');

    // Handle foreground messages
    FirebaseMessaging.onMessage.listen((RemoteMessage message) {
      print('Received foreground message: ${message.notification?.title}');
      _showLocalNotification(
        message.notification?.title ?? 'Alert',
        message.notification?.body ?? '',
        message.data,
      );
    });

    // Handle background messages
    FirebaseMessaging.onBackgroundMessage(_firebaseMessagingBackgroundHandler);

    // Handle notification taps when app is in background
    FirebaseMessaging.onMessageOpenedApp.listen((RemoteMessage message) {
      print('Notification opened app: ${message.notification?.title}');
      _handleNotificationTap(message.data);
    });
  }

  static Future<void> _showLocalNotification(
    String title,
    String body,
    Map<String, dynamic> data,
  ) async {
    const androidDetails = AndroidNotificationDetails(
      'city_pulse_alerts',
      'City Pulse Alerts',
      channelDescription: 'Notifications for city events and alerts',
      importance: Importance.high,
      priority: Priority.high,
      icon: '@mipmap/ic_launcher',
    );

    const iosDetails = DarwinNotificationDetails(
      presentAlert: true,
      presentBadge: true,
      presentSound: true,
    );

    const notificationDetails = NotificationDetails(
      android: androidDetails,
      iOS: iosDetails,
    );

    await _localNotifications.show(
      DateTime.now().millisecondsSinceEpoch ~/ 1000,
      title,
      body,
      notificationDetails,
      payload: data.toString(),
    );
  }

  static void _onNotificationTapped(NotificationResponse response) {
    print('Notification tapped: ${response.payload}');
    // Handle notification tap
    _handleNotificationTap({'payload': response.payload});
  }

  static void _handleNotificationTap(Map<String, dynamic> data) {
    // Navigate to appropriate screen based on notification data
    print('Handling notification tap: $data');
    // You can implement navigation logic here
  }

  // Send local notification for alerts
  static Future<void> showAlertNotification({
    required String title,
    required String body,
    required String alertType,
    required String severity,
  }) async {
    Color notificationColor = Colors.blue;
    String channelId = 'city_pulse_alerts';
    
    // Set color based on severity
    switch (severity.toLowerCase()) {
      case 'high':
        notificationColor = Colors.red;
        channelId = 'city_pulse_high_alerts';
        break;
      case 'medium':
        notificationColor = Colors.orange;
        channelId = 'city_pulse_medium_alerts';
        break;
      case 'low':
        notificationColor = Colors.green;
        channelId = 'city_pulse_low_alerts';
        break;
    }

    final androidDetails = AndroidNotificationDetails(
      channelId,
      'City Pulse ${severity.toUpperCase()} Alerts',
      channelDescription: 'Notifications for ${severity.toLowerCase()} priority city events',
      importance: severity.toLowerCase() == 'high' ? Importance.max : Importance.high,
      priority: severity.toLowerCase() == 'high' ? Priority.max : Priority.high,
      icon: '@mipmap/ic_launcher',
      color: notificationColor,
      playSound: true,
      enableVibration: true,
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

    // Generate unique ID within 32-bit range to ensure duplicate notifications are shown
    final uniqueId = (DateTime.now().millisecondsSinceEpoch % 2000000000) + (title.hashCode % 1000);
    
    await _localNotifications.show(
      uniqueId,
      title,
      body,
      notificationDetails,
      payload: '$alertType|$severity|${DateTime.now().millisecondsSinceEpoch}',
    );
    
    print('📱 Notification sent: ID=$uniqueId, Title=$title');
  }

  // Check if notifications are enabled for a category
  static Future<bool> areNotificationsEnabledForCategory(String category) async {
    // This would typically check user preferences from a database or shared preferences
    // For now, return true as default
    return true;
  }

  // Get FCM token for sending targeted notifications
  static Future<String?> getFCMToken() async {
    return await _firebaseMessaging.getToken();
  }

  // Subscribe to topic for category-based notifications
  static Future<void> subscribeToTopic(String topic) async {
    await _firebaseMessaging.subscribeToTopic(topic);
  }

  // Unsubscribe from topic
  static Future<void> unsubscribeFromTopic(String topic) async {
    await _firebaseMessaging.unsubscribeFromTopic(topic);
  }
}

// Background message handler
Future<void> _firebaseMessagingBackgroundHandler(RemoteMessage message) async {
  print('Handling background message: ${message.notification?.title}');
  // Handle background message here
}
