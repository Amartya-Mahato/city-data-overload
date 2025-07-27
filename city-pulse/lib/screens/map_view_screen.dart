import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_maps_flutter/google_maps_flutter.dart';
import 'package:geolocator/geolocator.dart';
import 'package:permission_handler/permission_handler.dart';
import '../providers/events_provider.dart';
import '../models/event.dart';
import '../themes/app_theme.dart';

class MapViewScreen extends ConsumerStatefulWidget {
  const MapViewScreen({super.key});

  @override
  ConsumerState<MapViewScreen> createState() => _MapViewScreenState();
}

class _MapViewScreenState extends ConsumerState<MapViewScreen> {
  GoogleMapController? _mapController;
  Set<Marker> _markers = {};
  Map<String, BitmapDescriptor> _customIcons = {};
  // Loading state is handled by the eventsAsync.when() builder
  LatLng? _currentPosition;
  // Bengaluru coordinates as fallback
  static const LatLng _initialPosition = LatLng(12.9716, 77.5946);

  @override
  void initState() {
    super.initState();
    _loadCustomIcons();
    _getCurrentLocation();
  }

  Future<void> _loadCustomIcons() async {
    // Load custom icons for different event types
    _customIcons = {
      'fire': await _getBitmapDescriptorFromIcon(Icons.local_fire_department, Colors.red),
      'flood': await _getBitmapDescriptorFromIcon(Icons.water_damage, Colors.blue),
      'traffic': await _getBitmapDescriptorFromIcon(Icons.traffic, Colors.orange),
      'thunderstorm': await _getBitmapDescriptorFromIcon(Icons.thunderstorm, Colors.purple),
      'weather': await _getBitmapDescriptorFromIcon(Icons.cloud, Colors.blue),
      'earthquake': await _getBitmapDescriptorFromIcon(Icons.warning, Colors.brown),
      'power': await _getBitmapDescriptorFromIcon(Icons.power_off, Colors.amber),
      'news': await _getBitmapDescriptorFromIcon(Icons.article, Colors.green),
      'default': await _getBitmapDescriptorFromIcon(Icons.location_on, Colors.grey),
    };
  }

  Future<BitmapDescriptor> _getBitmapDescriptorFromIcon(IconData icon, Color color) async {
    final pictureRecorder = PictureRecorder();
    final canvas = Canvas(pictureRecorder);
    final textPainter = TextPainter(textDirection: TextDirection.ltr);
    final iconStr = String.fromCharCode(icon.codePoint);
    
    textPainter.text = TextSpan(
      text: iconStr,
      style: TextStyle(
        fontSize: 48.0,
        fontFamily: icon.fontFamily,
        color: color,
      ),
    );
    
    textPainter.layout();
    textPainter.paint(canvas, Offset.zero);
    
    final picture = pictureRecorder.endRecording();
    final image = await picture.toImage(80, 80);
    final bytes = await image.toByteData(format: ImageByteFormat.png);
    
    return BitmapDescriptor.fromBytes(bytes!.buffer.asUint8List());
  }

  @override
  void dispose() {
    _mapController?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final eventsAsync = ref.watch(eventsProvider);
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('City Map'),
        actions: [
          IconButton(
            icon: const Icon(Icons.filter_list),
            onPressed: () => _showFilterDialog(context),
          ),
          IconButton(
            icon: const Icon(Icons.my_location),
            onPressed: () => _goToCurrentLocation(),
          ),
        ],
      ),
      body: Stack(
        children: [
          // Google Maps
          GoogleMap(
            onMapCreated: (GoogleMapController controller) {
              _mapController = controller;
              // Add a slight delay to ensure map is fully loaded
              Future.delayed(const Duration(milliseconds: 500), () {
                if (_currentPosition != null) {
                  _goToLocation(_currentPosition!);
                }
              });
            },
            initialCameraPosition: CameraPosition(
              target: _currentPosition ?? _initialPosition,
              zoom: _currentPosition != null ? 14.0 : 11.0,
            ),
            mapType: MapType.normal,
            zoomControlsEnabled: true,
            myLocationEnabled: true,
            myLocationButtonEnabled: true,
            compassEnabled: true,
            markers: _markers,
          ),
          // Loading overlay
          eventsAsync.when(
            data: (events) {
              WidgetsBinding.instance.addPostFrameCallback((_) {
                _updateMarkers(events);
              });
              return const SizedBox();
            },
            loading: () => Container(
              color: Colors.black.withOpacity(0.3),
              child: const Center(
                child: CircularProgressIndicator(),
              ),
            ),
            error: (error, _) => Container(
              color: Colors.black.withOpacity(0.3),
              child: Center(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    const Icon(Icons.error, size: 64, color: Colors.white),
                    const SizedBox(height: 16),
                    Text(
                      'Error loading map data',
                      style: theme.textTheme.bodyLarge?.copyWith(color: Colors.white),
                    ),
                    const SizedBox(height: 8),
                    ElevatedButton(
                      onPressed: () {
                        ref.read(eventsProvider.notifier).refreshEvents();
                      },
                      child: const Text('Retry'),
                    ),
                  ],
                ),
              ),
            ),
          ),
          // Legend
          Positioned(
            top: 16,
            left: 16,
            child: Card(
              child: Padding(
                padding: const EdgeInsets.all(12),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      'Legend',
                      style: theme.textTheme.titleSmall?.copyWith(
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    const SizedBox(height: 8),
                    _buildLegendItem('High Severity', AppTheme.alertRed),
                    _buildLegendItem('Medium Severity', AppTheme.warningOrange),
                    _buildLegendItem('Low Severity', AppTheme.successGreen),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  void _updateMarkers(List<Event> events) {
    setState(() {
      _markers = events.map((event) {
        return Marker(
          markerId: MarkerId(event.eventId),
          position: LatLng(event.location.lat, event.location.lng),
          onTap: () => _showEventDetails(event),
          icon: _getCustomIcon(event.eventType.toLowerCase()),
          infoWindow: InfoWindow(
            title: event.eventType,
            snippet: event.summary,
            onTap: () => _showEventDetails(event),
          ),
        );
      }).toSet();
      
      // Loading state is handled by the eventsAsync.when() builder
    });
  }

  BitmapDescriptor _getCustomIcon(String eventType) {
    // Map event types to icon keys
    final iconMap = {
      'fire': 'fire',
      'flood': 'flood',
      'traffic': 'traffic',
      'thunderstorm': 'thunderstorm',
      'weather': 'weather',
      'earthquake': 'earthquake',
      'power outage': 'power',
      'news': 'news',
    };
    
    final key = iconMap[eventType.toLowerCase()] ?? 'default';
    return _customIcons[key] ?? _customIcons['default']!;
  }

  Widget _buildLegendItem(String label, Color color) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 2),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 12,
            height: 12,
            decoration: BoxDecoration(
              color: color,
              shape: BoxShape.circle,
            ),
          ),
          const SizedBox(width: 8),
          Text(
            label,
            style: const TextStyle(fontSize: 12),
          ),
        ],
      ),
    );
  }

  IconData _getEventIcon(String eventType) {
    switch (eventType.toLowerCase()) {
      case 'traffic jam':
      case 'traffic':
        return Icons.traffic;
      case 'flood':
        return Icons.water_damage;
      case 'road closure':
        return Icons.construction;
      case 'thunderstorm':
      case 'weather':
        return Icons.thunderstorm;
      case 'earthquake':
        return Icons.warning;
      case 'power outage':
        return Icons.power_off;
      case 'fire':
        return Icons.local_fire_department;
      case 'rain':
        return Icons.umbrella;
      case 'thunder':
        return Icons.flash_on;
      default:
        return Icons.location_on;
    }
  }

  Future<void> _getCurrentLocation() async {
    try {
      // Check location permission
      final permission = await Permission.location.request();
      if (permission.isDenied) {
        // Handle the case where user denied the permission
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Location permission is required for better experience')),
          );
        }
        return;
      }

      // Get current position
      final position = await Geolocator.getCurrentPosition(
        desiredAccuracy: LocationAccuracy.high,
      );
      
      if (mounted) {
        setState(() {
          _currentPosition = LatLng(position.latitude, position.longitude);
        });
        
        // Move camera to current location
        await _goToLocation(_currentPosition!);
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Could not get location: ${e.toString()}')),
        );
      }
    }
  }

  Future<void> _goToLocation(LatLng position) async {
    if (_mapController != null) {
      await _mapController!.animateCamera(
        CameraUpdate.newCameraPosition(
          CameraPosition(
            target: position,
            zoom: 14.0,
          ),
        ),
      );
    }
  }

  Future<void> _goToCurrentLocation() async {
    if (_currentPosition != null) {
      await _goToLocation(_currentPosition!);
    } else {
      await _getCurrentLocation();
    }
    _mapController?.animateCamera(
      CameraUpdate.newCameraPosition(
        const CameraPosition(
          target: _initialPosition,
          zoom: 11.0,
        ),
      ),
    );
  }

  void _showFilterDialog(BuildContext context) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Filter Events'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            CheckboxListTile(
              title: const Text('High Severity'),
              value: true,
              onChanged: (value) {
                // TODO: Implement filter logic
              },
            ),
            CheckboxListTile(
              title: const Text('Medium Severity'),
              value: true,
              onChanged: (value) {
                // TODO: Implement filter logic
              },
            ),
            CheckboxListTile(
              title: const Text('Low Severity'),
              value: true,
              onChanged: (value) {
                // TODO: Implement filter logic
              },
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancel'),
          ),
          ElevatedButton(
            onPressed: () {
              // TODO: Apply filters
              Navigator.pop(context);
            },
            child: const Text('Apply'),
          ),
        ],
      ),
    );
  }

  void _showEventDetails(dynamic event) {
    showModalBottomSheet(
      context: context,
      builder: (context) => Container(
        padding: const EdgeInsets.all(16),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              event.eventType,
              style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 8),
            Text(event.summary),
            const SizedBox(height: 16),
            Text(
              'Location: ${event.location.address}',
              style: const TextStyle(fontWeight: FontWeight.w600),
            ),
            const SizedBox(height: 8),
            Text('Severity: ${event.severity}'),
            const SizedBox(height: 8),
            Text('Category: ${event.category}'),
            const SizedBox(height: 16),
            SizedBox(
              width: double.infinity,
              child: ElevatedButton(
                onPressed: () {
                  Navigator.pop(context);
                  // Center map on event
                  _mapController?.animateCamera(
                    CameraUpdate.newCameraPosition(
                      CameraPosition(
                        target: LatLng(event.location.lat, event.location.lng),
                        zoom: 15.0,
                      ),
                    ),
                  );
                },
                child: const Text('Center on Map'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
