import 'package:geolocator/geolocator.dart';
import '../models/event.dart';

class LocationService {
  // Default Bengaluru coordinates
  static final Location defaultBengaluruLocation = Location(
    lat: 12.9716,
    lng: 77.5946,
    address: 'Bengaluru, Karnataka, India',
  );

  /// Check if location services are enabled
  static Future<bool> isLocationServiceEnabled() async {
    return await Geolocator.isLocationServiceEnabled();
  }

  /// Request location permissions
  static Future<bool> requestLocationPermission() async {
    LocationPermission permission = await Geolocator.checkPermission();
    
    if (permission == LocationPermission.denied) {
      permission = await Geolocator.requestPermission();
      if (permission == LocationPermission.denied) {
        return false;
      }
    }
    
    if (permission == LocationPermission.deniedForever) {
      return false;
    }
    
    return true;
  }

  /// Get current location as Position object
  Future<Position> getCurrentPosition() async {
    try {
      final bool serviceEnabled = await Geolocator.isLocationServiceEnabled();
      if (!serviceEnabled) {
        // Return default Bengaluru coordinates as Position
        return Position(
          latitude: 12.9716,
          longitude: 77.5946,
          timestamp: DateTime.now(),
          accuracy: 0,
          altitude: 0,
          altitudeAccuracy: 0,
          heading: 0,
          headingAccuracy: 0,
          speed: 0,
          speedAccuracy: 0,
        );
      }

      final bool permissionGranted = await requestLocationPermission();
      if (!permissionGranted) {
        // Return default Bengaluru coordinates as Position
        return Position(
          latitude: 12.9716,
          longitude: 77.5946,
          timestamp: DateTime.now(),
          accuracy: 0,
          altitude: 0,
          altitudeAccuracy: 0,
          heading: 0,
          headingAccuracy: 0,
          speed: 0,
          speedAccuracy: 0,
        );
      }

      final Position position = await Geolocator.getCurrentPosition(
        desiredAccuracy: LocationAccuracy.high,
      );

      return position;
    } catch (e) {
      print('Error getting position: $e');
      // Return default Bengaluru coordinates as Position
      return Position(
        latitude: 12.9716,
        longitude: 77.5946,
        timestamp: DateTime.now(),
        accuracy: 0,
        altitude: 0,
        altitudeAccuracy: 0,
        heading: 0,
        headingAccuracy: 0,
        speed: 0,
        speedAccuracy: 0,
      );
    }
  }

  /// Get current location
  static Future<Location?> getCurrentLocation() async {
    try {
      final bool serviceEnabled = await isLocationServiceEnabled();
      if (!serviceEnabled) {
        return defaultBengaluruLocation;
      }

      final bool permissionGranted = await requestLocationPermission();
      if (!permissionGranted) {
        return defaultBengaluruLocation;
      }

      final Position position = await Geolocator.getCurrentPosition(
        desiredAccuracy: LocationAccuracy.high,
      );

      return Location(
        lat: position.latitude,
        lng: position.longitude,
        address: await getAddressFromCoordinates(position.latitude, position.longitude),
      );
    } catch (e) {
      print('Error getting location: $e');
      return defaultBengaluruLocation;
    }
  }

  /// Get address from coordinates (mock implementation)
  static Future<String> getAddressFromCoordinates(double lat, double lng) async {
    // In a real app, you would use geocoding service
    // For now, return a generic Bengaluru address
    return 'Bengaluru, Karnataka, India';
  }

  /// Calculate distance between two locations
  static double calculateDistance(Location loc1, Location loc2) {
    return Geolocator.distanceBetween(
      loc1.lat,
      loc1.lng,
      loc2.lat,
      loc2.lng,
    ) / 1000; // Convert to kilometers
  }

  /// Filter events by distance from user location
  static List<Event> filterEventsByDistance(
    List<Event> events,
    Location userLocation,
    double radiusKm,
  ) {
    return events.where((event) {
      final distance = calculateDistance(userLocation, event.location);
      return distance <= radiusKm;
    }).toList();
  }

  /// Sort events by distance from user location
  static List<Event> sortEventsByDistance(
    List<Event> events,
    Location userLocation,
  ) {
    events.sort((a, b) {
      final distanceA = calculateDistance(userLocation, a.location);
      final distanceB = calculateDistance(userLocation, b.location);
      return distanceA.compareTo(distanceB);
    });
    return events;
  }
}
