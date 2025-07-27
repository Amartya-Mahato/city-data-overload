import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../models/ndma_alert.dart';
import '../services/ndma_service.dart';
import '../services/location_service.dart';

// NDMA alerts state provider - Main source of truth for all disaster alerts
final ndmaAlertsProvider = StateNotifierProvider<NdmaAlertsNotifier, AsyncValue<List<NdmaAlert>>>((ref) {
  return NdmaAlertsNotifier();
});

// Active alerts provider - Only shows currently active alerts
final activeNdmaAlertsProvider = Provider<AsyncValue<List<NdmaAlert>>>((ref) {
  final alertsAsync = ref.watch(ndmaAlertsProvider);
  
  return alertsAsync.when(
    data: (alerts) {
      final activeAlerts = alerts.where((alert) => alert.isActive).toList();
      // Sort by severity (severe first) and then by start time
      activeAlerts.sort((a, b) {
        final severityComparison = b.severityLevel.index.compareTo(a.severityLevel.index);
        if (severityComparison != 0) return severityComparison;
        return a.effectiveStartTime.compareTo(b.effectiveStartTime);
      });
      return AsyncValue.data(activeAlerts);
    },
    loading: () => const AsyncValue.loading(),
    error: (error, stackTrace) => AsyncValue.error(error, stackTrace),
  );
});

// Recent alerts provider - Shows all alerts sorted by time
final recentNdmaAlertsProvider = Provider<AsyncValue<List<NdmaAlert>>>((ref) {
  final alertsAsync = ref.watch(ndmaAlertsProvider);
  
  return alertsAsync.when(
    data: (alerts) {
      final sortedAlerts = List<NdmaAlert>.from(alerts);
      // Sort by effective start time (newest first)
      sortedAlerts.sort((a, b) => b.effectiveStartTime.compareTo(a.effectiveStartTime));
      return AsyncValue.data(sortedAlerts);
    },
    loading: () => const AsyncValue.loading(),
    error: (error, stackTrace) => AsyncValue.error(error, stackTrace),
  );
});

// Filter state provider for NDMA alerts
final ndmaAlertFiltersProvider = StateNotifierProvider<NdmaAlertFiltersNotifier, NdmaAlertFilters>((ref) {
  return NdmaAlertFiltersNotifier();
});

// Filtered alerts provider
final filteredNdmaAlertsProvider = Provider<AsyncValue<List<NdmaAlert>>>((ref) {
  final alertsAsync = ref.watch(ndmaAlertsProvider);
  final filters = ref.watch(ndmaAlertFiltersProvider);
  
  return alertsAsync.when(
    data: (alerts) {
      List<NdmaAlert> filteredAlerts = alerts;
      
      // Filter by disaster type
      if (filters.selectedDisasterTypes.isNotEmpty) {
        filteredAlerts = filteredAlerts.where((alert) {
          return filters.selectedDisasterTypes.contains(alert.disasterType);
        }).toList();
      }
      
      // Filter by severity level
      if (filters.selectedSeverityLevels.isNotEmpty) {
        filteredAlerts = filteredAlerts.where((alert) {
          return filters.selectedSeverityLevels.contains(alert.severityLevel);
        }).toList();
      }
      
      // Filter by active status
      if (filters.showOnlyActive) {
        filteredAlerts = filteredAlerts.where((alert) => alert.isActive).toList();
      }
      
      // Sort by effective start time (newest first)
      filteredAlerts.sort((a, b) => b.effectiveStartTime.compareTo(a.effectiveStartTime));
      
      return AsyncValue.data(filteredAlerts);
    },
    loading: () => const AsyncValue.loading(),
    error: (error, stackTrace) => AsyncValue.error(error, stackTrace),
  );
});

class NdmaAlertsNotifier extends StateNotifier<AsyncValue<List<NdmaAlert>>> {
  NdmaAlertsNotifier() : super(const AsyncValue.loading()) {
    loadAlerts();
  }

  Future<void> loadAlerts() async {
    try {
      state = const AsyncValue.loading();
      
      // Get current location
      final location = await LocationService.getCurrentLocation();
      if (location == null) {
        state = AsyncValue.error('Unable to get current location', StackTrace.current);
        return;
      }
      
      // Fetch NDMA alerts
      final alerts = await NdmaService.fetchNdmaAlerts(
        latitude: location.lat,
        longitude: location.lng,
        radiusKm: 300.0, // 300km radius as per NDMA requirements
      );
      
      print('Loaded ${alerts.length} NDMA alerts');
      state = AsyncValue.data(alerts);
    } catch (error, stackTrace) {
      print('Error loading NDMA alerts: $error');
      state = AsyncValue.error(error, stackTrace);
    }
  }

  Future<void> refreshAlerts() async {
    await loadAlerts();
  }

  // Get alert by ID
  NdmaAlert? getAlertById(String alertId) {
    return state.whenOrNull(
      data: (alerts) => alerts.firstWhere(
        (alert) => alert.alertId == alertId,
        orElse: () => NdmaAlert(
          alertId: '',
          areaJson: {},
          severityColor: '',
          disasterType: '',
          warningMessage: '',
          effectiveStartTime: DateTime.now(),
          effectiveEndTime: DateTime.now(),
          areaDescription: '',
          centroid: Centroid(lat: 0, lng: 0),
          severity: '',
        ),
      ),
    );
  }

  // Get alerts by severity level
  List<NdmaAlert> getAlertsBySeverity(SeverityLevel severity) {
    return state.whenOrNull(
      data: (alerts) => alerts.where((alert) => alert.severityLevel == severity).toList(),
    ) ?? [];
  }

  // Get alerts by disaster type
  List<NdmaAlert> getAlertsByDisasterType(String disasterType) {
    return state.whenOrNull(
      data: (alerts) => alerts.where((alert) => alert.disasterType == disasterType).toList(),
    ) ?? [];
  }
}

class NdmaAlertFiltersNotifier extends StateNotifier<NdmaAlertFilters> {
  NdmaAlertFiltersNotifier() : super(NdmaAlertFilters());

  void updateDisasterTypes(List<String> disasterTypes) {
    state = state.copyWith(selectedDisasterTypes: disasterTypes);
  }

  void updateSeverityLevels(List<SeverityLevel> severityLevels) {
    state = state.copyWith(selectedSeverityLevels: severityLevels);
  }

  void setShowOnlyActive(bool showOnlyActive) {
    state = state.copyWith(showOnlyActive: showOnlyActive);
  }

  void clearFilters() {
    state = NdmaAlertFilters();
  }
}

class NdmaAlertFilters {
  final List<String> selectedDisasterTypes;
  final List<SeverityLevel> selectedSeverityLevels;
  final bool showOnlyActive;

  NdmaAlertFilters({
    this.selectedDisasterTypes = const [],
    this.selectedSeverityLevels = const [],
    this.showOnlyActive = false,
  });

  NdmaAlertFilters copyWith({
    List<String>? selectedDisasterTypes,
    List<SeverityLevel>? selectedSeverityLevels,
    bool? showOnlyActive,
  }) {
    return NdmaAlertFilters(
      selectedDisasterTypes: selectedDisasterTypes ?? this.selectedDisasterTypes,
      selectedSeverityLevels: selectedSeverityLevels ?? this.selectedSeverityLevels,
      showOnlyActive: showOnlyActive ?? this.showOnlyActive,
    );
  }
} 