import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import '../providers/ndma_alerts_provider.dart';
import '../themes/app_theme.dart';

class RecentEventsScreen extends ConsumerWidget {
  const RecentEventsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final alertsAsync = ref.watch(recentNdmaAlertsProvider);
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Recent Events'),
        backgroundColor: theme.colorScheme.primary,
      ),
      body: alertsAsync.when(
        data: (alerts) => alerts.isEmpty
            ? _buildEmptyState()
            : ListView.builder(
                padding: const EdgeInsets.all(16),
                itemCount: alerts.length,
                itemBuilder: (context, index) {
                  final alert = alerts[index];
                  return _buildAlertCard(context, alert);
                },
              ),
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(Icons.error, size: 64, color: Colors.grey),
              const SizedBox(height: 16),
              const Text('Error loading disaster alerts'),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildEmptyState() {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const Icon(
            Icons.check_circle_outline,
            size: 80,
            color: Colors.green,
          ),
          const SizedBox(height: 24),
          Text(
            'No Recent Alerts',
            style: GoogleFonts.poppins(
              fontSize: 24,
              fontWeight: FontWeight.bold,
              color: Colors.grey[700],
            ),
          ),
          const SizedBox(height: 12),
          Text(
            'Great news! There are no recent disaster alerts in your area.',
            textAlign: TextAlign.center,
            style: GoogleFonts.poppins(
              fontSize: 16,
              color: Colors.grey[600],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildAlertCard(BuildContext context, alert) {
    final isActive = alert.isActive;
    final alertColor = _parseColor(alert.displayColor);
    
    return Container(
      margin: const EdgeInsets.only(bottom: 16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: isActive ? alertColor : Colors.grey[300]!,
          width: isActive ? 2 : 1,
        ),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.1),
            blurRadius: 8,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Header with disaster type and status
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: alertColor.withOpacity(0.1),
              borderRadius: const BorderRadius.vertical(top: Radius.circular(16)),
            ),
            child: Row(
              children: [
                Container(
                  padding: const EdgeInsets.all(8),
                  decoration: BoxDecoration(
                    color: alertColor,
                    shape: BoxShape.circle,
                  ),
                  child: Icon(
                    _getDisasterIcon(alert.disasterType),
                    color: Colors.white,
                    size: 20,
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        alert.disasterType,
                        style: GoogleFonts.poppins(
                          fontSize: 18,
                          fontWeight: FontWeight.bold,
                          color: Colors.grey[800],
                        ),
                      ),
                      Text(
                        _getSeverityText(alert.severityLevel),
                        style: GoogleFonts.poppins(
                          fontSize: 14,
                          fontWeight: FontWeight.w600,
                          color: alertColor,
                        ),
                      ),
                    ],
                  ),
                ),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                  decoration: BoxDecoration(
                    color: isActive ? Colors.red : Colors.grey,
                    borderRadius: BorderRadius.circular(20),
                  ),
                  child: Text(
                    isActive ? 'ACTIVE' : 'EXPIRED',
                    style: GoogleFonts.poppins(
                      fontSize: 12,
                      fontWeight: FontWeight.bold,
                      color: Colors.white,
                    ),
                  ),
                ),
              ],
            ),
          ),
          
          // Alert details
          Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  alert.areaDescription,
                  style: GoogleFonts.poppins(
                    fontSize: 16,
                    fontWeight: FontWeight.w600,
                    color: Colors.grey[800],
                  ),
                ),
                const SizedBox(height: 8),
                Text(
                  alert.getLocalizedWarningMessage(),
                  style: GoogleFonts.poppins(
                    fontSize: 14,
                    color: Colors.grey[700],
                    height: 1.4,
                  ),
                ),
                const SizedBox(height: 12),
                Row(
                  children: [
                    Icon(
                      Icons.access_time,
                      size: 16,
                      color: Colors.grey[600],
                    ),
                    const SizedBox(width: 4),
                    Text(
                      alert.timeRange,
                      style: GoogleFonts.poppins(
                        fontSize: 12,
                        color: Colors.grey[600],
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

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
      return Colors.orange; // Default color
    }
  }

  IconData _getDisasterIcon(String disasterType) {
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

  String _getSeverityText(severityLevel) {
    switch (severityLevel.toString()) {
      case 'SeverityLevel.severe':
        return 'Red Alert';
      case 'SeverityLevel.moderate':
        return 'Orange Alert';
      case 'SeverityLevel.minor':
        return 'Yellow Alert';
      default:
        return 'Alert';
    }
  }
}
