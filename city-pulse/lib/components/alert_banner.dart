import 'package:flutter/material.dart';
import '../models/event.dart';
import '../themes/app_theme.dart';

class AlertBanner extends StatelessWidget {
  final Event event;
  final VoidCallback? onTap;
  final VoidCallback? onDismiss;

  const AlertBanner({
    super.key,
    required this.event,
    this.onTap,
    this.onDismiss,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isHighSeverity = event.severity.toLowerCase() == 'high';
    final backgroundColor = isHighSeverity 
        ? AppTheme.alertRed
        : AppTheme.getSeverityColor(event.severity);

    return Container(
      margin: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: backgroundColor,
        borderRadius: BorderRadius.circular(12),
        boxShadow: [
          BoxShadow(
            color: backgroundColor.withOpacity(0.3),
            blurRadius: 8,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(12),
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Row(
              children: [
                // Icon
                Container(
                  padding: const EdgeInsets.all(8),
                  decoration: BoxDecoration(
                    color: Colors.white.withOpacity(0.2),
                    shape: BoxShape.circle,
                  ),
                  child: Icon(
                    _getAlertIcon(event.eventType),
                    color: Colors.white,
                    size: 24,
                  ),
                ),
                const SizedBox(width: 12),
                
                // Content
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        '${event.severity.toUpperCase()} ALERT',
                        style: theme.textTheme.labelSmall?.copyWith(
                          color: Colors.white,
                          fontWeight: FontWeight.bold,
                          fontSize: 11,
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                      const SizedBox(height: 4),
                      Text(
                        event.summary,
                        style: theme.textTheme.bodyMedium?.copyWith(
                          color: Colors.white,
                          fontWeight: FontWeight.w600,
                          fontSize: 13,
                        ),
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                      ),
                      const SizedBox(height: 4),
                      Text(
                        event.location.address,
                        style: theme.textTheme.bodySmall?.copyWith(
                          color: Colors.white.withOpacity(0.8),
                          fontSize: 11,
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ],
                  ),
                ),
                
                // Dismiss button
                if (onDismiss != null)
                  IconButton(
                    onPressed: onDismiss,
                    icon: const Icon(
                      Icons.close,
                      color: Colors.white,
                      size: 20,
                    ),
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  IconData _getAlertIcon(String eventType) {
    switch (eventType.toLowerCase()) {
      case 'earthquake':
        return Icons.vibration;
      case 'flood':
        return Icons.water_damage;
      case 'thunderstorm':
        return Icons.thunderstorm;
      case 'fire':
        return Icons.local_fire_department;
      case 'accident':
        return Icons.car_crash;
      case 'road closure':
        return Icons.block;
      default:
        return Icons.warning;
    }
  }
}

// Pulsating Alert Banner for critical events
class PulsatingAlertBanner extends StatefulWidget {
  final Event event;
  final VoidCallback? onTap;
  final VoidCallback? onDismiss;

  const PulsatingAlertBanner({
    super.key,
    required this.event,
    this.onTap,
    this.onDismiss,
  });

  @override
  State<PulsatingAlertBanner> createState() => _PulsatingAlertBannerState();
}

class _PulsatingAlertBannerState extends State<PulsatingAlertBanner>
    with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  late Animation<double> _animation;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      duration: const Duration(milliseconds: 1000),
      vsync: this,
    );
    _animation = Tween<double>(begin: 0.8, end: 1.0).animate(
      CurvedAnimation(parent: _controller, curve: Curves.easeInOut),
    );
    _controller.repeat(reverse: true);
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _animation,
      builder: (context, child) {
        return Transform.scale(
          scale: _animation.value,
          child: AlertBanner(
            event: widget.event,
            onTap: widget.onTap,
            onDismiss: widget.onDismiss,
          ),
        );
      },
    );
  }
}
