import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:google_maps_flutter/google_maps_flutter.dart';
import 'dart:ui' as ui;
import 'package:flutter/services.dart';
import '../providers/user_provider.dart';
import '../providers/chat_providers.dart';
import '../providers/ndma_alerts_provider.dart';
import '../models/chat_message.dart';
import '../models/ndma_alert.dart';
import '../models/event.dart'; // Import for Location class
import '../services/location_service.dart';
import '../themes/app_theme.dart';
import '../components/sos_button.dart';

class DashboardPage extends ConsumerStatefulWidget {
  const DashboardPage({super.key});

  @override
  ConsumerState<DashboardPage> createState() => _DashboardPageState();
}

enum MapViewMode {
  alerts,
  traffic,
}

class _DashboardPageState extends ConsumerState<DashboardPage>
    with TickerProviderStateMixin {
  late AnimationController _chatController;
  late Animation<double> _chatHeightAnimation;
  late Animation<double> _chatOpacityAnimation;
  late TextEditingController _messageController;
  late ScrollController _chatScrollController;

  
  bool _isChatExpanded = true;  // Start expanded
  double _minChatHeight = 120;
  double _maxChatHeight = 500;
  MapViewMode _currentMapMode = MapViewMode.alerts;
  
  Set<Marker> _markers = {};
  Set<Circle> _circles = {};
  List<NdmaAlert> _alerts = [];
  Location? _currentLocation;
  GoogleMapController? _mapController;

  @override
  void initState() {
    super.initState();
    _initializeResponsiveSizes();
    _initializeAnimations();
    _loadEventsAndLocation();
  }
  
  void _initializeResponsiveSizes() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final screenHeight = MediaQuery.of(context).size.height;
      final screenWidth = MediaQuery.of(context).size.width;
      
      // Responsive sizing based on screen size
      if (screenWidth < 600) { // Mobile
        _minChatHeight = 100;
        _maxChatHeight = screenHeight * 0.6; // 60% of screen height
      } else if (screenWidth < 1024) { // Tablet
        _minChatHeight = 120;
        _maxChatHeight = screenHeight * 0.5; // 50% of screen height
      } else { // Desktop
        _minChatHeight = 140;
        _maxChatHeight = screenHeight * 0.4; // 40% of screen height
      }
      
      // Ensure minimum safe area
      final bottomPadding = MediaQuery.of(context).padding.bottom;
      _maxChatHeight = (_maxChatHeight - bottomPadding - 50).clamp(200.0, screenHeight * 0.7);
      
      setState(() {
        _initializeAnimations();
      });
    });
  }
  
  void _initializeAnimations() {
    _chatController = AnimationController(
      duration: const Duration(milliseconds: 300),
      vsync: this,
    );
    
    _chatHeightAnimation = Tween<double>(
      begin: _minChatHeight,
      end: _maxChatHeight,
    ).animate(CurvedAnimation(
      parent: _chatController,
      curve: Curves.easeInOut,
    ));
    
    _chatOpacityAnimation = Tween<double>(
      begin: 0.95,
      end: 1.0,
    ).animate(CurvedAnimation(
      parent: _chatController,
      curve: Curves.easeInOut,
    ));
    
    _messageController = TextEditingController();
    _chatScrollController = ScrollController();
    
    // Start with chat expanded
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _chatController.forward();
    });
  }
  
  Future<void> _loadEventsAndLocation() async {
    try {
      // Get current location
      _currentLocation = await LocationService.getCurrentLocation();
      
      // Load NDMA alerts
      ref.read(ndmaAlertsProvider.notifier).loadAlerts();
      
      setState(() {});
    } catch (e) {
      print('Error loading location: $e');
    }
  }
  
  Future<void> _createMarkersAndCircles() async {
    print('🔄 Creating markers and circles for ${_alerts.length} alerts');
    
    try {
      // Process markers and circles in parallel for better performance
      final futures = <Future>[];
      final markers = <Marker>{};
      final circles = <Circle>{};
      
      // Process alerts in batches to avoid blocking the main thread
      const batchSize = 5;
      for (int i = 0; i < _alerts.length; i += batchSize) {
        final batch = _alerts.skip(i).take(batchSize);
        
        for (final alert in batch) {
          futures.add(_processAlert(alert, markers, circles));
        }
        
        // Wait for current batch to complete before processing next batch
        await Future.wait(futures);
        futures.clear();
        
        // Yield control to prevent blocking the main thread
        if (i + batchSize < _alerts.length) {
          await Future.delayed(const Duration(milliseconds: 10));
        }
      }

      // Add current location marker
      if (_currentLocation != null) {
        final locationMarker = Marker(
          markerId: const MarkerId('current_location'),
          position: LatLng(_currentLocation!.lat, _currentLocation!.lng),
          icon: await _createCustomMarkerIcon(Colors.blue, Icons.my_location),
          infoWindow: InfoWindow(
            title: 'Your Location',
            snippet: _currentLocation!.address,
          ),
        );
        markers.add(locationMarker);
      }
      
      print('✅ Final result: ${markers.length} markers, ${circles.length} circles');
      
      setState(() {
        _markers = markers;
        _circles = circles;
      });
    } catch (e) {
      print('💥 Error creating markers and circles: $e');
    }
  }

  Future<void> _processAlert(NdmaAlert alert, Set<Marker> markers, Set<Circle> circles) async {
    try {
      final alertColor = _parseColor(alert.displayColor);
      
      print('🔍 Processing alert: ${alert.alertId} - ${alert.disasterType}');
      print('📍 Centroid: ${alert.centroid.lat}, ${alert.centroid.lng}');
      
      // Create multiple layered circles for glow effect
      final glowCircles = _createGlowCircles(alert, alertColor);
      circles.addAll(glowCircles);
      print('✅ ${glowCircles.length} glow circles added successfully for ${alert.alertId}');
      
      // Create marker at centroid
      final marker = Marker(
        markerId: MarkerId(alert.alertId),
        position: LatLng(alert.centroid.lat, alert.centroid.lng),
        infoWindow: InfoWindow(
          title: '${_getDisasterIcon(alert.disasterType)} ${alert.disasterType}',
          snippet: '${alert.areaDescription} till ${alert.timeRange}',
        ),
        icon: await _createCustomMarkerIcon(
          alertColor, 
          _getMarkerIconByDisasterType(alert.disasterType)
        ),
      );
      markers.add(marker);
      print('✅ Marker created for ${alert.alertId}');
    } catch (e) {
      print('💥 Error processing alert ${alert.alertId}: $e');
    }
  }

  // Create multiple layered circles for glow effect
  List<Circle> _createGlowCircles(NdmaAlert alert, Color alertColor) {
    // Determine base radius based on severity
    double baseRadius = 1000; // Default radius in meters
    
    // Parse severity from alert properties or use color intensity
    final severityLevel = _getSeverityLevel(alert, alertColor);
    
    switch (severityLevel) {
      case 'high':
        baseRadius = 2500; // Larger radius for high severity
        break;
      case 'medium':
        baseRadius = 1800; // Medium radius
        break;
      case 'low':
        baseRadius = 1200; // Smaller radius for low severity
        break;
    }
    
    final center = LatLng(alert.centroid.lat, alert.centroid.lng);
    final circles = <Circle>[];
    
    // Create outer glow circle (largest, most transparent)
    circles.add(Circle(
      circleId: CircleId('${alert.alertId}_glow_outer'),
      center: center,
      radius: baseRadius * 1.3,
      fillColor: alertColor.withOpacity(0.08), // Very transparent for outer glow
      strokeColor: alertColor.withOpacity(0.3),
      strokeWidth: 1,
    ));
    
    // Create middle glow circle
    circles.add(Circle(
      circleId: CircleId('${alert.alertId}_glow_middle'),
      center: center,
      radius: baseRadius * 1.15,
      fillColor: alertColor.withOpacity(0.15), // Medium transparency
      strokeColor: alertColor.withOpacity(0.5),
      strokeWidth: 2,
    ));
    
    // Create main circle (core)
    circles.add(Circle(
      circleId: CircleId('${alert.alertId}_core'),
      center: center,
      radius: baseRadius,
      fillColor: alertColor.withOpacity(0.3), // Core visibility
      strokeColor: alertColor,
      strokeWidth: 3,
    ));
    
    return circles;
  }

  // Determine severity level based on alert properties and color
  String _getSeverityLevel(NdmaAlert alert, Color alertColor) {
    // Check if alert has severity in properties
    if (alert.areaJson.containsKey('properties') && 
        alert.areaJson['properties'] is Map) {
      final properties = alert.areaJson['properties'] as Map;
      if (properties.containsKey('severity')) {
        final severity = properties['severity'].toString().toLowerCase();
        if (severity.contains('high') || severity.contains('severe') || severity.contains('extreme')) {
          return 'high';
        } else if (severity.contains('medium') || severity.contains('moderate')) {
          return 'medium';
        } else if (severity.contains('low') || severity.contains('minor')) {
          return 'low';
        }
      }
    }
    
    // Fallback: determine severity by color intensity
    final red = alertColor.red;
    final green = alertColor.green;
    final blue = alertColor.blue;
    
    // High severity: red-ish colors
    if (red > 200 && green < 100 && blue < 100) {
      return 'high';
    }
    // Medium severity: orange/yellow-ish colors
    else if (red > 150 && green > 100 && blue < 150) {
      return 'medium';
    }
    // Low severity: green-ish or other colors
    else {
      return 'low';
    }
  }



  // Parse color string to Color object
  Color _parseColor(String colorString) {
    if (colorString.isEmpty) return Colors.orange;
    
    try {
      String hexColor = colorString;
      if (hexColor.startsWith('#')) {
        hexColor = hexColor.substring(1);
      }
      if (hexColor.length == 6) {
        hexColor = 'FF$hexColor'; // Add alpha if not present
      }
      return Color(int.parse(hexColor, radix: 16));
    } catch (e) {
      print('Error parsing color $colorString: $e');
      return Colors.orange; // Default color
    }
  }
  
  Future<BitmapDescriptor> _createCustomMarkerIcon(Color color, IconData icon) async {
    // Create custom marker icon with specific event icons
    try {
      final pictureRecorder = ui.PictureRecorder();
      final canvas = Canvas(pictureRecorder);
      const double size = 100.0;
      
      // Draw circular background
      final backgroundPaint = Paint()
        ..color = color
        ..style = PaintingStyle.fill;
      
      canvas.drawCircle(
        const Offset(size / 2, size / 2),
        size / 2 - 2,
        backgroundPaint,
      );
      
      // Draw white border
      final borderPaint = Paint()
        ..color = Colors.white
        ..style = PaintingStyle.stroke
        ..strokeWidth = 4.0;
      
      canvas.drawCircle(
        const Offset(size / 2, size / 2),
        size / 2 - 2,
        borderPaint,
      );
      
      // Draw icon
      final textPainter = TextPainter(
        text: TextSpan(
          text: String.fromCharCode(icon.codePoint),
          style: TextStyle(
            fontSize: size * 0.5,
            fontFamily: icon.fontFamily ?? 'MaterialIcons',
            color: Colors.white,
          ),
        ),
        textDirection: TextDirection.ltr,
      );
      
      textPainter.layout();
      textPainter.paint(
        canvas,
        Offset(
          (size - textPainter.width) / 2,
          (size - textPainter.height) / 2,
        ),
      );
      
      final picture = pictureRecorder.endRecording();
      final image = await picture.toImage(size.toInt(), size.toInt());
      final bytes = await image.toByteData(format: ui.ImageByteFormat.png);
      
      return BitmapDescriptor.fromBytes(bytes!.buffer.asUint8List());
    } catch (e) {
      print('Error creating custom marker: $e');
      // Fallback to default marker
      return BitmapDescriptor.defaultMarkerWithHue(_getHueFromColor(color));
    }
  }
  
  double _getHueFromColor(Color color) {
    if (color == Colors.red) return BitmapDescriptor.hueRed;
    if (color == Colors.orange) return BitmapDescriptor.hueOrange;
    if (color == Colors.yellow) return BitmapDescriptor.hueYellow;
    if (color == Colors.green) return BitmapDescriptor.hueGreen;
    if (color == Colors.blue) return BitmapDescriptor.hueBlue;
    if (color == Colors.purple) return BitmapDescriptor.hueViolet;
    return BitmapDescriptor.hueRed;
  }
  
  // Get disaster type icon for markers
  IconData _getMarkerIconByDisasterType(String disasterType) {
    switch (disasterType.toLowerCase()) {
      case 'very heavy rain':
      case 'heavy rain':
      case 'rainfall':
      case 'rain':
        return Icons.water_drop;
      case 'flood':
      case 'flooding':
        return Icons.flood;
      case 'thunderstorm':
      case 'lightning':
        return Icons.flash_on;
      case 'cyclone':
      case 'hurricane':
      case 'storm':
        return Icons.cyclone;
      case 'heat wave':
      case 'extreme heat':
        return Icons.wb_sunny;
      case 'cold wave':
      case 'extreme cold':
        return Icons.ac_unit;
      case 'drought':
        return Icons.water_damage;
      case 'fire':
      case 'wildfire':
      case 'forest fire':
        return Icons.local_fire_department;
      case 'earthquake':
      case 'seismic':
        return Icons.landscape;
      case 'landslide':
      case 'avalanche':
        return Icons.terrain;
      case 'tsunami':
        return Icons.waves;
      case 'strong wind':
      case 'high wind':
      case 'wind':
        return Icons.air;
      case 'hail':
      case 'hailstorm':
        return Icons.grain;
      case 'fog':
      case 'dense fog':
        return Icons.cloud;
      default:
        return Icons.warning;
    }
  }

  // Get disaster type emoji icon for display
  String _getDisasterIcon(String disasterType) {
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
      case 'cold wave':
      case 'extreme cold':
        return '🥶';
      case 'drought':
        return '🏜️';
      case 'fire':
      case 'wildfire':
      case 'forest fire':
        return '🔥';
      case 'earthquake':
      case 'seismic':
        return '🏔️';
      case 'landslide':
      case 'avalanche':
        return '⛰️';
      case 'tsunami':
        return '🌊';
      case 'strong wind':
      case 'high wind':
      case 'wind':
        return '💨';
      case 'hail':
      case 'hailstorm':
        return '🧊';
      case 'fog':
      case 'dense fog':
        return '🌫️';
      default:
        return '⚠️';
    }
  }

  // Switch between map modes (alerts vs traffic)
  void _switchMapMode(MapViewMode mode) {
    setState(() {
      _currentMapMode = mode;
    });
    
    print('🗺️ Switched to ${mode == MapViewMode.alerts ? 'Alerts' : 'Traffic'} view');
  }

  // Build map toggle button
  Widget _buildMapToggleButton({
    required IconData icon,
    required String label,
    required bool isActive,
    required VoidCallback onTap,
  }) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              icon,
              size: 18,
              color: isActive ? AppTheme.primaryPurple : Colors.grey[600],
            ),
            const SizedBox(width: 6),
            Text(
              label,
              style: GoogleFonts.poppins(
                fontSize: 12,
                fontWeight: isActive ? FontWeight.w600 : FontWeight.w400,
                color: isActive ? AppTheme.primaryPurple : Colors.grey[600],
              ),
            ),
          ],
        ),
      ),
    );
  }

  @override
  void dispose() {
    _chatController.dispose();
    _messageController.dispose();
    _chatScrollController.dispose();
    super.dispose();
  }

  void _toggleChat() {
    setState(() {
      _isChatExpanded = !_isChatExpanded;
    });
    
    if (_isChatExpanded) {
      _chatController.forward();
    } else {
      _chatController.reverse();
    }
  }

  void _sendMessage(String text) {
    if (text.trim().isEmpty) return;

    final message = ChatMessage(
      id: DateTime.now().millisecondsSinceEpoch.toString(),
      content: text,
      isUser: true,
      timestamp: DateTime.now(),
    );

    ref.read(chatMessageProvider.notifier).addMessage(message);
    _messageController.clear();

    // Auto-scroll to bottom
    Future.delayed(const Duration(milliseconds: 100), () {
      if (_chatScrollController.hasClients) {
        _chatScrollController.animateTo(
          _chatScrollController.position.maxScrollExtent,
          duration: const Duration(milliseconds: 300),
          curve: Curves.easeInOut,
        );
      }
    });

    // Simulate AI response
    Future.delayed(const Duration(milliseconds: 1000), () {
      final aiResponse = ChatMessage(
        id: DateTime.now().millisecondsSinceEpoch.toString(),
        content: _generateAIResponse(text),
        isUser: false,
        timestamp: DateTime.now(),
        type: MessageType.suggestion,
      );
      ref.read(chatMessageProvider.notifier).addMessage(aiResponse);

      // Auto-scroll to bottom again
      Future.delayed(const Duration(milliseconds: 100), () {
        if (_chatScrollController.hasClients) {
          _chatScrollController.animateTo(
            _chatScrollController.position.maxScrollExtent,
            duration: const Duration(milliseconds: 300),
            curve: Curves.easeInOut,
          );
        }
      });
    });
  }

  String _generateAIResponse(String userMessage) {
    final message = userMessage.toLowerCase();
    
    if (message.contains('alert') || message.contains('disaster') || message.contains('emergency')) {
      final activeAlerts = _alerts.where((alert) => alert.isActive).toList();
      if (activeAlerts.isNotEmpty) {
        final severestAlert = activeAlerts.first;
        return 'I found ${activeAlerts.length} active disaster alert(s) in your area. The most severe is: ${severestAlert.getLocalizedWarningMessage()}';
      } else {
        return 'Good news! There are currently no active disaster alerts in your area. Stay safe and keep monitoring for updates.';
      }
    } else if (message.contains('weather') || message.contains('rain') || message.contains('storm')) {
      final weatherAlerts = _alerts.where((alert) => 
        alert.isActive && (
          alert.disasterType.toLowerCase().contains('rain') ||
          alert.disasterType.toLowerCase().contains('storm') ||
          alert.disasterType.toLowerCase().contains('thunder')
        )
      ).toList();
      
      if (weatherAlerts.isNotEmpty) {
        return 'Weather alert: ${weatherAlerts.first.getLocalizedWarningMessage()} Please take necessary precautions.';
      } else {
        return 'No active weather alerts in your area currently. Weather conditions appear normal.';
      }
    } else if (message.contains('flood') || message.contains('water')) {
      final floodAlerts = _alerts.where((alert) => 
        alert.isActive && alert.disasterType.toLowerCase().contains('flood')
      ).toList();
      
      if (floodAlerts.isNotEmpty) {
        return 'Flood alert: ${floodAlerts.first.getLocalizedWarningMessage()} Avoid waterlogged areas and stay safe.';
      } else {
        return 'No flood alerts in your area currently. Water levels are normal.';
      }
    } else if (message.contains('fire') || message.contains('wildfire')) {
      final fireAlerts = _alerts.where((alert) => 
        alert.isActive && alert.disasterType.toLowerCase().contains('fire')
      ).toList();
      
      if (fireAlerts.isNotEmpty) {
        return 'Fire alert: ${fireAlerts.first.getLocalizedWarningMessage()} Please evacuate if advised and stay away from affected areas.';
      } else {
        return 'No fire alerts in your area currently. Fire risk appears low.';
      }
    } else {
      return 'I\'m your disaster alert assistant! Ask me about current alerts, weather conditions, floods, fires, or any disaster-related concerns in your area.';
    }
  }

  @override
  Widget build(BuildContext context) {
    final userAsync = ref.watch(userProvider);
    final chatMessages = ref.watch(chatMessageProvider);
    final alertsAsync = ref.watch(ndmaAlertsProvider);
    final theme = Theme.of(context);
    final isDarkMode = theme.brightness == Brightness.dark;
    
    // Update alerts, markers and polygons when alerts change
    alertsAsync.whenData((alerts) {
      if (_alerts != alerts) {
        _alerts = alerts;
        _createMarkersAndCircles();
      }
    });
    
    final screenHeight = MediaQuery.of(context).size.height;
    final statusBarHeight = MediaQuery.of(context).padding.top;
    
    return Scaffold(
      body: Stack(
        children: [
          // Full screen map
          Positioned.fill(
            child: Container(
              color: Theme.of(context).brightness == Brightness.dark 
                  ? Colors.grey[900] 
                  : Colors.grey[100],
              child: Stack(
                children: [
                  GoogleMap(
                    initialCameraPosition: CameraPosition(
                      target: _currentLocation != null 
                        ? LatLng(_currentLocation!.lat, _currentLocation!.lng)
                        : const LatLng(12.9716, 77.5946), // Bengaluru coordinates
                      zoom: 15.0,
                    ),
                    mapType: MapType.normal,
                    zoomControlsEnabled: true,
                    myLocationEnabled: true,
                    myLocationButtonEnabled: true,
                    compassEnabled: true,
                    trafficEnabled: _currentMapMode == MapViewMode.traffic,
                    markers: _currentMapMode == MapViewMode.alerts ? _markers : {},
                    circles: _currentMapMode == MapViewMode.alerts ? _circles : {},
                    onMapCreated: (GoogleMapController controller) {
                      _mapController = controller;
                  // Apply dark mode style if needed
                  if (Theme.of(context).brightness == Brightness.dark) {
                    controller.setMapStyle('''[
                                {
                                  "elementType": "geometry",
                                  "stylers": [
                                    {
                                      "color": "#212121"
                                    }
                                  ]
                                },
                                {
                                  "elementType": "labels.icon",
                                  "stylers": [
                                    {
                                      "visibility": "off"
                                    }
                                  ]
                                },
                                {
                                  "elementType": "labels.text.fill",
                                  "stylers": [
                                    {
                                      "color": "#757575"
                                    }
                                  ]
                                },
                                {
                                  "elementType": "labels.text.stroke",
                                  "stylers": [
                                    {
                                      "color": "#212121"
                                    }
                                  ]
                                },
                                {
                                  "featureType": "administrative",
                                  "elementType": "geometry",
                                  "stylers": [
                                    {
                                      "color": "#757575"
                                    }
                                  ]
                                },
                                {
                                  "featureType": "road",
                                  "elementType": "geometry.fill",
                                  "stylers": [
                                    {
                                      "color": "#2c2c2c"
                                    }
                                  ]
                                },
                                {
                                  "featureType": "road.arterial",
                                  "elementType": "geometry",
                                  "stylers": [
                                    {
                                      "color": "#373737"
                                    }
                                  ]
                                },
                                {
                                  "featureType": "road.highway",
                                  "elementType": "geometry",
                                  "stylers": [
                                    {
                                      "color": "#3c3c3c"
                                    }
                                  ]
                                },
                                {
                                  "featureType": "water",
                                  "elementType": "geometry",
                                  "stylers": [
                                    {
                                      "color": "#000000"
                                    }
                                  ]
                                }
                              ]''');
                  }
                  // Move camera to current location with better zoom if available
                  if (_currentLocation != null) {
                    controller.animateCamera(
                      CameraUpdate.newCameraPosition(
                        CameraPosition(
                          target: LatLng(_currentLocation!.lat, _currentLocation!.lng),
                          zoom: 15.0, // Better zoom for showing current location
                        ),
                      ),
                    );
                  }
                },
              ),
              
              // Map view toggle buttons
              Positioned(
                top: 60,
                right: 16,
                child: Container(
                  decoration: BoxDecoration(
                    color: Colors.white,
                    borderRadius: BorderRadius.circular(8),
                    boxShadow: [
                      BoxShadow(
                        color: Colors.black.withOpacity(0.1),
                        blurRadius: 4,
                        offset: const Offset(0, 2),
                      ),
                    ],
                  ),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      _buildMapToggleButton(
                        icon: Icons.warning_amber,
                        label: 'Alerts',
                        isActive: _currentMapMode == MapViewMode.alerts,
                        onTap: () => _switchMapMode(MapViewMode.alerts),
                      ),
                      Container(height: 1, color: Colors.grey[300]),
                      _buildMapToggleButton(
                        icon: Icons.traffic,
                        label: 'Traffic',
                        isActive: _currentMapMode == MapViewMode.traffic,
                        onTap: () => _switchMapMode(MapViewMode.traffic),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
          
          // Collapsible chat interface at bottom
          Positioned(
            bottom: 0,
            left: 0,
            right: 0,
            child: AnimatedBuilder(
              animation: _chatHeightAnimation,
              builder: (context, child) {
                final bottomPadding = MediaQuery.of(context).padding.bottom;
                final isDarkMode = Theme.of(context).brightness == Brightness.dark;
                return Container(
                  height: _chatHeightAnimation.value + bottomPadding,
                  decoration: BoxDecoration(
                    color: (isDarkMode 
                        ? Theme.of(context).colorScheme.surface 
                        : Colors.white).withOpacity(_chatOpacityAnimation.value),
                    borderRadius: const BorderRadius.vertical(
                      top: Radius.circular(24),
                    ),
                    boxShadow: [
                      BoxShadow(
                        color: Colors.black.withOpacity(0.15),
                        blurRadius: 20,
                        spreadRadius: 5,
                        offset: const Offset(0, -5),
                      ),
                    ],
                  ),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      // Drag handle and header
                      GestureDetector(
                        onTap: _toggleChat,
                        onPanUpdate: (details) {
                          // Handle swipe gestures
                          final dy = details.delta.dy;
                          if (dy.abs() > 2) { // Threshold to prevent accidental swipes
                            if (dy < -5 && !_isChatExpanded) {
                              // Swipe up to expand
                              _toggleChat();
                            } else if (dy > 5 && _isChatExpanded) {
                              // Swipe down to collapse
                              _toggleChat();
                            }
                          }
                        },
                        child: Container(
                          width: double.infinity,
                          padding: const EdgeInsets.symmetric(vertical: 12),
                          decoration: const BoxDecoration(
                            borderRadius: BorderRadius.vertical(
                              top: Radius.circular(24),
                            ),
                          ),
                          child: Column(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              // Drag handle
                              Container(
                                width: 50,
                                height: 4,
                                decoration: BoxDecoration(
                                  color: Colors.grey[400],
                                  borderRadius: BorderRadius.circular(2),
                                ),
                              ),
                              const SizedBox(height: 12),
                              // Chat header
                              Row(
                                mainAxisAlignment: MainAxisAlignment.center,
                                children: [
                                  Icon(
                                    Icons.chat_bubble_outline,
                                    color: AppTheme.primaryPurple,
                                    size: 20,
                                  ),
                                  const SizedBox(width: 8),
                                  Text(
                                    'Ask about your area',
                                    style: GoogleFonts.poppins(
                                      fontSize: 16,
                                      fontWeight: FontWeight.w600,
                                      color: AppTheme.primaryPurple,
                                    ),
                                  ),
                                  const SizedBox(width: 8),
                                  AnimatedRotation(
                                    turns: _isChatExpanded ? 0.5 : 0,
                                    duration: const Duration(milliseconds: 300),
                                    child: Icon(
                                      Icons.keyboard_arrow_up,
                                      color: AppTheme.primaryPurple,
                                    ),
                                  ),
                                ],
                              ),
                            ],
                          ),
                        ),
                      ),
                      
                      // Chat messages (only visible when expanded)
                      if (_isChatExpanded)
                        Expanded(
                          child: Container(
                            padding: const EdgeInsets.symmetric(horizontal: 16),
                            child: chatMessages.isEmpty
                                ? SingleChildScrollView(
                                    child: _buildWelcomeMessageArea(),
                                  )
                                : ListView.builder(
                                    controller: _chatScrollController,
                                    itemCount: chatMessages.length,
                                    itemBuilder: (context, index) {
                                      final message = chatMessages[index];
                                      return _buildChatBubble(message);
                                    },
                                  ),
                          ),
                        ),
                      
                      // Chat input
                      Container(
                        padding: EdgeInsets.only(
                          left: 16,
                          right: 16,
                          top: 16,
                bottom: 8 + MediaQuery.of(context).padding.bottom,
                        ),
                        decoration: BoxDecoration(
                          color: isDarkMode ? Colors.grey[800] : Colors.grey[50],
                          border: Border(
                            top: BorderSide(
                              color: isDarkMode ? Colors.grey[700]! : Colors.grey[200]!,
                              width: 1,
                            ),
                          ),
                        ),
                        child: Row(
                          children: [
                            Expanded(
                              child: Container(
                                decoration: BoxDecoration(
                                  color: isDarkMode ? Colors.grey[700] : Colors.white,
                                  borderRadius: BorderRadius.circular(25),
                                  boxShadow: [
                                    BoxShadow(
                                      color: Colors.grey.withOpacity(0.1),
                                      blurRadius: 5,
                                      offset: const Offset(0, 2),
                                    ),
                                  ],
                                ),
                                child: TextField(
                                  controller: _messageController,
                                  style: TextStyle(
                                    color: isDarkMode ? Colors.white : Colors.black,
                                  ),
                                  decoration: InputDecoration(
                                    hintText: 'Ask about disaster alerts, weather, floods...',
                                    hintStyle: GoogleFonts.poppins(
                                      color: isDarkMode ? Colors.grey[400] : Colors.grey[500],
                                      fontSize: 14,
                                    ),
                                    border: OutlineInputBorder(
                                      borderRadius: BorderRadius.circular(25),
                                      borderSide: BorderSide.none,
                                    ),
                                    contentPadding: const EdgeInsets.symmetric(
                                      horizontal: 20,
                                      vertical: 12,
                                    ),
                                  ),
                                  onSubmitted: _sendMessage,
                                ),
                              ),
                            ),
                            const SizedBox(width: 12),
                            GestureDetector(
                              onTap: () => _sendMessage(_messageController.text),
                              child: Container(
                                width: 48,
                                height: 48,
                                decoration: BoxDecoration(
                                  gradient: const LinearGradient(
                                    colors: [AppTheme.primaryPurple, AppTheme.primaryBlue],
                                  ),
                                  shape: BoxShape.circle,
                                  boxShadow: [
                                    BoxShadow(
                                      color: AppTheme.primaryPurple.withOpacity(0.3),
                                      blurRadius: 8,
                                      offset: const Offset(0, 4),
                                    ),
                                  ],
                                ),
                                child: const Icon(
                                  Icons.send,
                                  color: Colors.white,
                                  size: 20,
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                );
              },
            ),
          ),
          
          // SOS button overlay - positioned above chat pane with state preservation
          AnimatedBuilder(
            animation: _chatHeightAnimation,
            builder: (context, child) {
              final bottomPadding = MediaQuery.of(context).padding.bottom;
              final chatHeight = _chatHeightAnimation.value + bottomPadding;
              
              return Positioned(
                bottom: chatHeight + 30, // Optimal space above chat pane (30px)
                right: 16,
                child: child!,
              );
            },
            child: const SosButton(),
          ),
        ],
      ),
    );
  }

  Widget _buildWelcomeCard(String userName) {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          colors: [Colors.white, Colors.white70],
        ),
        borderRadius: BorderRadius.circular(20),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.1),
            blurRadius: 15,
            offset: const Offset(0, 5),
          ),
        ],
      ),
      child: Row(
        children: [
          Container(
            width: 50,
            height: 50,
            decoration: BoxDecoration(
              gradient: const LinearGradient(
                colors: [AppTheme.primaryPurple, AppTheme.primaryBlue],
              ),
              shape: BoxShape.circle,
            ),
            child: Center(
              child: Text(
                userName.isNotEmpty ? userName[0].toUpperCase() : 'G',
                style: GoogleFonts.poppins(
                  fontSize: 20,
                  fontWeight: FontWeight.bold,
                  color: Colors.white,
                ),
              ),
            ),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Welcome back, $userName!',
                  style: GoogleFonts.poppins(
                    fontSize: 16,
                    fontWeight: FontWeight.bold,
                    color: AppTheme.primaryPurple,
                  ),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
                const SizedBox(height: 4),
                Text(
                  'Stay updated with real-time city insights',
                  style: GoogleFonts.poppins(
                    fontSize: 13,
                    color: Colors.grey[600],
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildChatBubble(ChatMessage message) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      child: Row(
        mainAxisAlignment: message.isUser
            ? MainAxisAlignment.end
            : MainAxisAlignment.start,
        children: [
          if (!message.isUser)
            Container(
              width: 32,
              height: 32,
              margin: const EdgeInsets.only(right: 8),
              decoration: const BoxDecoration(
                gradient: LinearGradient(
                  colors: [AppTheme.primaryPurple, AppTheme.primaryBlue],
                ),
                shape: BoxShape.circle,
              ),
              child: const Icon(
                Icons.smart_toy,
                color: Colors.white,
                size: 16,
              ),
            ),
          Flexible(
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              decoration: BoxDecoration(
                gradient: message.isUser
                    ? const LinearGradient(
                        colors: [AppTheme.primaryPurple, AppTheme.primaryBlue],
                      )
                    : null,
                color: message.isUser ? null : Colors.grey[100],
                borderRadius: BorderRadius.circular(20),
                boxShadow: [
                  BoxShadow(
                    color: Colors.grey.withOpacity(0.1),
                    blurRadius: 5,
                    offset: const Offset(0, 2),
                  ),
                ],
              ),
              child: Text(
                message.content,
                style: GoogleFonts.poppins(
                  fontSize: 14,
                  color: message.isUser ? Colors.white : Colors.grey[800],
                  height: 1.3,
                ),
              ),
            ),
          ),
          if (message.isUser)
            Container(
              width: 32,
              height: 32,
              margin: const EdgeInsets.only(left: 8),
              decoration: const BoxDecoration(
                color: AppTheme.accentCyan,
                shape: BoxShape.circle,
              ),
              child: const Icon(
                Icons.person,
                color: Colors.white,
                size: 16,
              ),
            ),
        ],
      ),
    );
  }

  Widget _buildWelcomeMessageArea() {
    final userAsync = ref.watch(userProvider);
    
    return LayoutBuilder(
      builder: (context, constraints) {
        final isCompact = constraints.maxHeight < 200;
        
        return Column(
          mainAxisSize: MainAxisSize.min,
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              width: isCompact ? 60 : 80,
              height: isCompact ? 60 : 80,
              decoration: BoxDecoration(
                gradient: const LinearGradient(
                  colors: [AppTheme.primaryPurple, AppTheme.primaryBlue],
                ),
                shape: BoxShape.circle,
                boxShadow: [
                  BoxShadow(
                    color: AppTheme.primaryPurple.withOpacity(0.3),
                    blurRadius: 15,
                    offset: const Offset(0, 5),
                  ),
                ],
              ),
              child: Icon(
                Icons.chat_bubble_outline,
                color: Colors.white,
                size: isCompact ? 25 : 35,
              ),
            ),
            SizedBox(height: isCompact ? 16 : 24),
            userAsync.when(
              data: (user) => Text(
                'Hello ${user.name}! 👋',
                style: GoogleFonts.poppins(
                  fontSize: isCompact ? 16 : 18,
                  fontWeight: FontWeight.bold,
                  color: AppTheme.primaryPurple,
                ),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
              loading: () => Text(
                'Hello! 👋',
                style: GoogleFonts.poppins(
                  fontSize: isCompact ? 16 : 18,
                  fontWeight: FontWeight.bold,
                  color: AppTheme.primaryPurple,
                ),
              ),
              error: (error, _) => Text(
                'Hello! 👋',
                style: GoogleFonts.poppins(
                  fontSize: isCompact ? 16 : 18,
                  fontWeight: FontWeight.bold,
                  color: AppTheme.primaryPurple,
                ),
              ),
            ),
            if (!isCompact) ...[
              const SizedBox(height: 12),
              Text(
                'I\'m your City Pulse Assistant',
                style: GoogleFonts.poppins(
                  fontSize: 14,
                  fontWeight: FontWeight.w500,
                  color: Colors.grey[700],
                ),
              ),
              const SizedBox(height: 8),
              Text(
                'Ask me about disaster alerts, weather warnings,\nflood conditions, fire alerts, or any emergency updates.',
                textAlign: TextAlign.center,
                style: GoogleFonts.poppins(
                  fontSize: 14,
                  color: Colors.grey[600],
                  height: 1.4,
                ),
              ),
              const SizedBox(height: 32),
            ] else ...[
              const SizedBox(height: 8),
              Text(
                'Ask about disaster alerts and city updates',
                textAlign: TextAlign.center,
                style: GoogleFonts.poppins(
                  fontSize: 12,
                  color: Colors.grey[600],
                ),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
              const SizedBox(height: 16),
            ],
                          // Suggestion chips
              Flexible(
                child: SingleChildScrollView(
                  child: Wrap(
                    spacing: 6,
                    runSpacing: 6,
                    children: [
                      _buildSuggestionChip('🚨 Active alerts', () => _sendMessage('show active alerts')),
                      _buildSuggestionChip('🌧️ Weather alerts', () => _sendMessage('weather alerts')),
                      if (!isCompact) ...[
                        _buildSuggestionChip('🌊 Flood warnings', () => _sendMessage('flood alerts')),
                        _buildSuggestionChip('🔥 Fire alerts', () => _sendMessage('fire alerts')),
                      ],
                    ],
                  ),
                ),
              ),
          ],
        );
      },
    );
  }

  Widget _buildSuggestionChip(String text, VoidCallback onTap) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
        constraints: const BoxConstraints(maxWidth: 140), // Prevent overflow
        decoration: BoxDecoration(
          gradient: LinearGradient(
            colors: [AppTheme.primaryPurple.withOpacity(0.1), AppTheme.primaryBlue.withOpacity(0.1)],
          ),
          borderRadius: BorderRadius.circular(20),
          border: Border.all(
            color: AppTheme.primaryPurple.withOpacity(0.3),
            width: 1,
          ),
        ),
        child: Text(
          text,
          style: GoogleFonts.poppins(
            fontSize: 11,
            fontWeight: FontWeight.w500,
            color: AppTheme.primaryPurple,
          ),
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
        ),
      ),
    );
  }
}
