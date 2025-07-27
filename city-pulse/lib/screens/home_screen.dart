import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../providers/user_provider.dart';
import '../components/bottom_nav_bar.dart';
import 'dashboard_page.dart';
import 'alerts_screen.dart';
import 'recent_events_screen.dart';
import 'report_screen.dart';
import 'settings_screen.dart';

class HomeScreen extends ConsumerWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final currentIndex = ref.watch(bottomNavProvider);
    
    return Scaffold(
      body: IndexedStack(
        index: currentIndex,
        children: const [
          DashboardPage(),
          AlertsScreen(),
          RecentEventsScreen(),
          ReportScreen(),
          SettingsScreen(),
        ],
      ),
      bottomNavigationBar: BottomNavBar(
        currentIndex: currentIndex,
        onTap: (index) {
          ref.read(bottomNavProvider.notifier).setIndex(index);
        },
      ),
    );
  }
}
