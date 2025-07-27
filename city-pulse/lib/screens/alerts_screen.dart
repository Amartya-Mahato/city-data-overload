import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import '../providers/ndma_alerts_provider.dart';
import '../components/alert_banner.dart';
import '../services/notification_service.dart';

class AlertsScreen extends ConsumerWidget {
  const AlertsScreen({super.key});

  // Send notifications for currently displayed alerts
  void _testNotificationsForAlerts(BuildContext context, List<dynamic> alerts) async {
    // Show loading indicator
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Row(
          children: [
            const SizedBox(
              width: 20,
              height: 20,
              child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
            ),
            const SizedBox(width: 16),
            Text("Sending notifications for ${alerts.length} alerts..."),
          ],
        ),
        duration: const Duration(seconds: 3),
        backgroundColor: Colors.orange,
      ),
    );

    try {
      int sentCount = 0;
      
      // Send notification for each alert displayed
      for (int i = 0; i < alerts.length; i++) {
        final alert = alerts[i];
        final timestamp = DateTime.now();
        final timeStr = '${timestamp.hour.toString().padLeft(2, '0')}:${timestamp.minute.toString().padLeft(2, '0')}';
        
        await NotificationService.showAlertNotification(
          title: '🚨 ${alert.disasterType} Alert ${i + 1}',
          body: '${alert.getLocalizedWarningMessage()}\nArea: ${alert.areaDescription}\nSeverity: ${alert.severity}\n\n⏰ Updated: $timeStr',
          alertType: alert.disasterType,
          severity: alert.severity,
        );
        
        sentCount++;
        // Small delay to ensure notifications don't overlap
        await Future.delayed(const Duration(milliseconds: 100));
      }

      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('✅ Sent $sentCount notifications! Check your notification panel.'),
            backgroundColor: Colors.green,
            duration: const Duration(seconds: 4),
          ),
        );
      }
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('❌ Failed to send notifications: ${e.toString()}'),
            backgroundColor: Colors.red,
            duration: const Duration(seconds: 4),
          ),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final alertsAsync = ref.watch(activeNdmaAlertsProvider);
    
    return Scaffold(
      appBar: AppBar(
        title: const Text('Alerts'),
      ),
      floatingActionButton: alertsAsync.when(
        data: (alerts) => alerts.isNotEmpty ? FloatingActionButton.extended(
          onPressed: () => _testNotificationsForAlerts(context, alerts),
          icon: const Icon(Icons.notification_important),
          label: const Text('Send Notifications'),
          backgroundColor: Colors.orange,
        ) : null,
        loading: () => null,
        error: (_, __) => null,
      ),
      body: alertsAsync.when(
        data: (alerts) => RefreshIndicator(
          onRefresh: () async {
            ref.read(ndmaAlertsProvider.notifier).refreshAlerts();
          },
          child: alerts.isEmpty
              ? const Center(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Icon(Icons.check_circle_outline, size: 64, color: Colors.green),
                      SizedBox(height: 16),
                      Text(
                        'No Active Alerts',
                        style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                      ),
                      SizedBox(height: 8),
                      Text(
                        'Great! There are no active disaster alerts in your area.',
                        style: TextStyle(fontSize: 14, color: Colors.grey),
                        textAlign: TextAlign.center,
                      ),
                    ],
                  ),
                )
              : ListView.builder(
                  padding: const EdgeInsets.all(16),
                  itemCount: alerts.length,
                  itemBuilder: (context, index) {
                    final alert = alerts[index];
                    return _buildAlertCard(context, alert);
                  },
                ),
        ),
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, stackTrace) => Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(Icons.error, size: 64, color: Colors.grey),
              const SizedBox(height: 16),
              const Text('Error loading alerts'),
              const SizedBox(height: 8),
              ElevatedButton(
                onPressed: () => ref.read(ndmaAlertsProvider.notifier).refreshAlerts(),
                child: const Text('Retry'),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildAlertCard(BuildContext context, alert) {
    final alertColor = _parseColor(alert.displayColor);
    
    return Container(
      margin: const EdgeInsets.only(bottom: 16),
      constraints: const BoxConstraints(
        maxWidth: double.infinity,
      ),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: alertColor, width: 2),
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
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                      ),
                      const SizedBox(height: 2),
                      Text(
                        _getSeverityText(alert.severityLevel),
                        style: GoogleFonts.poppins(
                          fontSize: 14,
                          fontWeight: FontWeight.w600,
                          color: alertColor,
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 8),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                  decoration: BoxDecoration(
                    color: Colors.red,
                    borderRadius: BorderRadius.circular(20),
                  ),
                  child: Text(
                    'ACTIVE',
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
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
                const SizedBox(height: 8),
                Text(
                  alert.getLocalizedWarningMessage(),
                  style: GoogleFonts.poppins(
                    fontSize: 14,
                    color: Colors.grey[700],
                    height: 1.4,
                  ),
                  maxLines: 4,
                  overflow: TextOverflow.ellipsis,
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
                    Expanded(
                      child: Text(
                        'Valid till ${alert.timeRange}',
                        style: GoogleFonts.poppins(
                          fontSize: 12,
                          color: Colors.grey[600],
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
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
