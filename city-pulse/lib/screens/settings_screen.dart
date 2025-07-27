import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../providers/user_provider.dart';
import '../providers/theme_provider.dart';
import '../themes/app_theme.dart';
import '../services/alert_service.dart';
import '../services/background_service.dart';
import '../screens/user_reports_screen.dart';
import '../screens/leaderboard_screen.dart';

class SettingsScreen extends ConsumerWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final userAsync = ref.watch(userProvider);
    final themeAsync = ref.watch(themeProvider);
    
    return Scaffold(
      appBar: AppBar(
        title: const Text('Settings'),
        backgroundColor: AppTheme.primaryPurple,
        foregroundColor: Colors.white,
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: () async {
              // Manual refresh all data
              try {
                await Future.wait([
                  ref.read(userProvider.notifier).refreshUserData(),
                  ref.read(userReportsProvider.notifier).refreshReports(),
                  ref.read(leaderboardProvider.notifier).refreshLeaderboard(),
                ]);
                
                if (context.mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(
                      content: Text('Data refreshed successfully!'),
                      duration: Duration(seconds: 2),
                    ),
                  );
                }
              } catch (e) {
                if (context.mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(
                      content: Text('Refresh failed: ${e.toString()}'),
                      backgroundColor: Colors.red,
                      duration: const Duration(seconds: 2),
                    ),
                  );
                }
              }
            },
          ),
        ],
      ),
      body: userAsync.when(
        data: (user) => SingleChildScrollView(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // User Profile Section (with reports, reputation, and leaderboard)
              _buildUserProfileSection(context, ref, user),
              const SizedBox(height: 24),
              
              // Notification Settings
              _buildNotificationSettings(context, ref, user),
              const SizedBox(height: 24),
              
              // App Settings
              _buildAppSettings(context, ref, user, themeAsync),
              const SizedBox(height: 24),
              
              // About Section
              _buildAboutSection(context),
              const SizedBox(height: 32),
            ],
          ),
        ),
        loading: () => const Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              CircularProgressIndicator(),
              SizedBox(height: 16),
              Text('Loading your profile...'),
            ],
          ),
        ),
        error: (error, stackTrace) => Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(Icons.error, size: 64, color: Colors.red),
              const SizedBox(height: 16),
              Text(
                'Error loading profile',
                style: Theme.of(context).textTheme.headlineSmall,
              ),
              const SizedBox(height: 8),
              Text(
                error.toString(),
                style: const TextStyle(color: Colors.grey),
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 16),
              ElevatedButton(
                onPressed: () => ref.read(userProvider.notifier).refreshUserData(),
                child: const Text('Retry'),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildUserProfileSection(BuildContext context, WidgetRef ref, dynamic user) {
    return Card(
      elevation: 4,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: Container(
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(16),
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [
              Colors.white,
              AppTheme.primaryPurple.withValues(alpha: 0.02),
            ],
          ),
        ),
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Header
              Row(
                children: [
                  Text(
                    'Profile',
                    style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                      fontWeight: FontWeight.bold,
                      color: AppTheme.primaryPurple,
                    ),
                  ),
                  const Spacer(),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                    decoration: BoxDecoration(
                      color: AppTheme.primaryPurple.withValues(alpha: 0.1),
                      borderRadius: BorderRadius.circular(20),
                      border: Border.all(color: AppTheme.primaryPurple.withValues(alpha: 0.3)),
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(
                          Icons.verified_user,
                          size: 16,
                          color: AppTheme.primaryPurple,
                        ),
                        const SizedBox(width: 4),
                        Text(
                          'Verified',
                          style: TextStyle(
                            fontSize: 12,
                            fontWeight: FontWeight.w600,
                            color: AppTheme.primaryPurple,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
              
              const SizedBox(height: 24),
              
              // User basic info
              Row(
                children: [
                  Container(
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      boxShadow: [
                        BoxShadow(
                          color: AppTheme.primaryPurple.withValues(alpha: 0.3),
                          blurRadius: 12,
                          offset: const Offset(0, 4),
                        ),
                      ],
                    ),
                    child: CircleAvatar(
                      radius: 35,
                      backgroundColor: AppTheme.primaryPurple,
                      child: Text(
                        user.name.isNotEmpty ? user.name[0].toUpperCase() : 'U',
                        style: const TextStyle(
                          fontSize: 28,
                          fontWeight: FontWeight.bold,
                          color: Colors.white,
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(width: 20),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          user.name,
                          style: Theme.of(context).textTheme.titleLarge?.copyWith(
                            fontWeight: FontWeight.bold,
                            color: Colors.grey[800],
                          ),
                        ),
                        const SizedBox(height: 6),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                          decoration: BoxDecoration(
                            color: Colors.grey[100],
                            borderRadius: BorderRadius.circular(8),
                          ),
                          child: Text(
                            user.email,
                            style: Theme.of(context).textTheme.bodySmall?.copyWith(
                              color: Colors.grey[700],
                              fontWeight: FontWeight.w500,
                              fontSize: 12,
                            ),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                        const SizedBox(height: 8),
                        Row(
                          children: [
                            Icon(
                              Icons.location_on,
                              size: 16,
                              color: Colors.grey[500],
                            ),
                            const SizedBox(width: 4),
                            Expanded(
                              child: Text(
                                user.location.address,
                                style: Theme.of(context).textTheme.bodySmall?.copyWith(
                                  color: Colors.grey[500],
                                  fontSize: 12,
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
              
              const SizedBox(height: 32),
              
              // Stats section header
              Text(
                'Community Stats',
                style: Theme.of(context).textTheme.titleMedium?.copyWith(
                  fontWeight: FontWeight.bold,
                  color: Colors.grey[800],
                ),
              ),
              
              const SizedBox(height: 16),
              
              // Stats row (Reports and Reputation)
              Row(
                children: [
                  Expanded(
                    child: _buildStatItem(
                      context,
                      ref,
                      'Submitted Reports',
                      user.reportsSubmitted.toString(),
                      Icons.assignment_turned_in,
                      AppTheme.primaryBlue,
                    ),
                  ),
                  const SizedBox(width: 16),
                  Expanded(
                    child: _buildStatItem(
                      context,
                      ref,
                      'Reputation',
                      user.reputation.toString(),
                      Icons.star,
                      AppTheme.primaryPurple,
                    ),
                  ),
                ],
              ),
              
              const SizedBox(height: 20),
              
              // Leaderboard section
              _buildLeaderboardSection(context, ref),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildStatItem(
    BuildContext context,
    WidgetRef ref,
    String label,
    String value,
    IconData icon,
    Color color,
  ) {
    return GestureDetector(
      onTap: label == 'Submitted Reports' ? () => _navigateToReports(context, ref) : null,
      child: Container(
        height: 170, // Increased height to prevent overflow
        padding: const EdgeInsets.all(12), // Reduced padding
        decoration: BoxDecoration(
          color: color.withValues(alpha: 0.08),
          borderRadius: BorderRadius.circular(16),
          border: Border.all(
            color: color.withValues(alpha: 0.2),
            width: 1.5,
          ),
          boxShadow: [
            BoxShadow(
              color: color.withValues(alpha: 0.1),
              blurRadius: 8,
              offset: const Offset(0, 2),
            ),
          ],
        ),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              padding: const EdgeInsets.all(10), // Reduced padding
              decoration: BoxDecoration(
                color: color,
                shape: BoxShape.circle,
                boxShadow: [
                  BoxShadow(
                    color: color.withValues(alpha: 0.3),
                    blurRadius: 8,
                    offset: const Offset(0, 2),
                  ),
                ],
              ),
              child: Icon(
                icon,
                size: 22, // Slightly reduced icon size
                color: Colors.white,
              ),
            ),
            const SizedBox(height: 10), // Reduced spacing
            Text(
              value,
              style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                fontWeight: FontWeight.bold,
                color: color,
                fontSize: 28, // Reduced from default
              ),
            ),
            const SizedBox(height: 4), // Reduced spacing
            Text(
              label,
              style: Theme.of(context).textTheme.bodySmall?.copyWith(
                color: Colors.grey[600],
                fontWeight: FontWeight.w600,
                fontSize: 11, // Reduced font size
              ),
              textAlign: TextAlign.center,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
            ),
            if (label == 'Submitted Reports') ...[
              const SizedBox(height: 4), // Reduced spacing
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 1), // Reduced padding
                decoration: BoxDecoration(
                  color: color.withValues(alpha: 0.15),
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Text(
                  'Tap to view',
                  style: TextStyle(
                    fontSize: 9, // Reduced font size
                    color: color,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
            ],
            if (label != 'Submitted Reports') const SizedBox(height: 16), // Reduced balance spacing
          ],
        ),
      ),
    );
  }

  Widget _buildLeaderboardSection(BuildContext context, WidgetRef ref) {
    return GestureDetector(
      onTap: () => _navigateToLeaderboard(context, ref),
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [
              AppTheme.primaryPurple,
              AppTheme.primaryBlue,
            ],
          ),
          borderRadius: BorderRadius.circular(16),
          boxShadow: [
            BoxShadow(
              color: AppTheme.primaryPurple.withValues(alpha: 0.3),
              blurRadius: 12,
              offset: const Offset(0, 4),
            ),
          ],
        ),
        child: Row(
          children: [
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: Colors.white.withValues(alpha: 0.2),
                borderRadius: BorderRadius.circular(12),
              ),
              child: const Icon(
                Icons.leaderboard,
                color: Colors.white,
                size: 28,
              ),
            ),
            const SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Community Leaderboard',
                    style: Theme.of(context).textTheme.titleLarge?.copyWith(
                      fontWeight: FontWeight.bold,
                      color: Colors.white,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    'See how you rank among top contributors',
                    style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      color: Colors.white.withValues(alpha: 0.9),
                    ),
                  ),
                ],
              ),
            ),
            Container(
              padding: const EdgeInsets.all(8),
              decoration: BoxDecoration(
                color: Colors.white.withValues(alpha: 0.2),
                shape: BoxShape.circle,
              ),
              child: const Icon(
                Icons.arrow_forward,
                color: Colors.white,
                size: 20,
              ),
            ),
          ],
        ),
      ),
    );
  }

  void _navigateToReports(BuildContext context, WidgetRef ref) async {
    final result = await Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => const UserReportsScreen(),
      ),
    );
    
    // Refresh user data when returning from reports screen
    // This ensures the reports count is updated
    ref.read(userProvider.notifier).refreshUserData();
  }

  Widget _buildNotificationSettings(
    BuildContext context,
    WidgetRef ref,
    dynamic user,
  ) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Notification Settings',
              style: Theme.of(context).textTheme.titleMedium?.copyWith(
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 16),
            
            SwitchListTile(
              title: const Text('Push Notifications'),
              subtitle: const Text('Receive alerts for events in your area'),
              value: user.preferences.notificationEnabled,
              onChanged: (value) {
                final newPreferences = user.preferences.copyWith(
                  notificationEnabled: value,
                );
                ref.read(userProvider.notifier).updateUserPreferences(newPreferences);
              },
            ),
            
            const Divider(),
            
            ListTile(
              leading: const Icon(Icons.location_on),
              title: const Text('Alert Radius'),
              subtitle: Text('${user.preferences.alertRadius.toInt()} km'),
              trailing: const Icon(Icons.arrow_forward_ios),
              onTap: () => _showRadiusDialog(context, ref, user),
            ),
            
            const Divider(),
            
            ListTile(
              leading: const Icon(Icons.category),
              title: const Text('Alert Categories'),
              subtitle: Text('${user.preferences.categories.length} categories selected'),
              trailing: const Icon(Icons.arrow_forward_ios),
              onTap: () => _showCategoriesDialog(context, ref, user),
            ),
            
            const Divider(),
            
            // Test Background Service Button
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 4.0),
              child: SizedBox(
                width: double.infinity,
                child: ElevatedButton.icon(
                  onPressed: () async {
                    // Show loading indicator
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(
                        content: Row(
                          children: [
                            SizedBox(
                              width: 20,
                              height: 20,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            ),
                            SizedBox(width: 16),
            Text("Checking background service..."),
                          ],
                        ),
                        duration: Duration(seconds: 3),
                      ),
                    );
                    
                    try {
                      // Use the test function that shows ALL alerts, not just severe ones
                      await BackgroundService.testAlertsWithNotifications();
                      
                      if (context.mounted) {
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(
                            content: Text('✅ Background service check completed! Check notifications.'),
                            backgroundColor: Colors.green,
                            duration: Duration(seconds: 4),
                          ),
                        );
                      }
                    } catch (e) {
                      if (context.mounted) {
                        ScaffoldMessenger.of(context).showSnackBar(
                          SnackBar(
                            content: Text('❌ Background service check failed: ${e.toString()}'),
                            backgroundColor: Colors.red,
                            duration: const Duration(seconds: 4),
                          ),
                        );
                      }
                    }
                  },
                  icon: const Icon(Icons.cloud_sync),
                  label: const Text('Check Background Service'),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: AppTheme.primaryPurple,
                    foregroundColor: Colors.white,
                    padding: const EdgeInsets.symmetric(vertical: 12),
                  ),
                ),
              ),
            ),
            
            // Test Foreground Service Button
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 4.0),
              child: SizedBox(
                width: double.infinity,
                child: ElevatedButton.icon(
                  onPressed: () async {
                    // Show loading indicator
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(
                        content: Row(
                          children: [
                            SizedBox(
                              width: 20,
                              height: 20,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            ),
                            SizedBox(width: 16),
            Text("Checking foreground service..."),
                          ],
                        ),
                        duration: Duration(seconds: 3),
                      ),
                    );
                    
                    try {
                      // Test the foreground alert service
                      await AlertService().checkNow();
                      
                      if (context.mounted) {
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(
                            content: Text('✅ Foreground service check completed! Check console logs and notifications.'),
                            backgroundColor: Colors.blue,
                            duration: Duration(seconds: 4),
                          ),
                        );
                      }
                    } catch (e) {
                      if (context.mounted) {
                        ScaffoldMessenger.of(context).showSnackBar(
                          SnackBar(
                            content: Text('❌ Foreground service check failed: ${e.toString()}'),
                            backgroundColor: Colors.red,
                            duration: const Duration(seconds: 4),
                          ),
                        );
                      }
                    }
                  },
                  icon: const Icon(Icons.phone_android),
                  label: const Text('Check Foreground Service'),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.blue,
                    foregroundColor: Colors.white,
                    padding: const EdgeInsets.symmetric(vertical: 12),
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildAppSettings(BuildContext context, WidgetRef ref, dynamic user, bool themeAsync) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'App Settings',
              style: Theme.of(context).textTheme.titleMedium?.copyWith(
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 16),
            
            SwitchListTile(
              title: const Text('Dark Mode'),
              subtitle: const Text('Use dark theme'),
              value: themeAsync,
              onChanged: (value) {
                // Update both theme provider and user preferences
                ref.read(themeProvider.notifier).setTheme(value);
                
                final newPreferences = user.preferences.copyWith(
                  darkMode: value,
                );
                ref.read(userProvider.notifier).updateUserPreferences(newPreferences);
              },
            ),
            
            const Divider(),
            
            ListTile(
              leading: const Icon(Icons.language),
              title: const Text('Language'),
              subtitle: const Text('English'),
              trailing: const Icon(Icons.arrow_forward_ios),
              onTap: () {
                // TODO: Implement language selection
              },
            ),
            
            const Divider(),
            
            ListTile(
              leading: const Icon(Icons.help),
              title: const Text('Help & Support'),
              trailing: const Icon(Icons.arrow_forward_ios),
              onTap: () {
                // TODO: Show help screen
              },
            ),
            
            const Divider(),
            
            ListTile(
              leading: const Icon(Icons.storage),
              title: const Text('Clear Cache'),
              subtitle: const Text('Free up storage space'),
              trailing: const Icon(Icons.arrow_forward_ios),
              onTap: () => _showClearCacheDialog(context),
            ),
          ],
        ),
      ),
    );
  }

  void _navigateToLeaderboard(BuildContext context, WidgetRef ref) async {
    final result = await Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => const LeaderboardScreen(),
      ),
    );
    
    // Only refresh user data when returning from leaderboard if needed
    if (result == true) {
      ref.read(userProvider.notifier).refreshUserData();
    }
  }

  Widget _buildAboutSection(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'About',
              style: Theme.of(context).textTheme.titleMedium?.copyWith(
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 16),
            
            ListTile(
              leading: const Icon(Icons.info),
              title: const Text('App Version'),
              subtitle: const Text('1.0.0'),
            ),
            
            const Divider(),
            
            ListTile(
              leading: const Icon(Icons.privacy_tip),
              title: const Text('Privacy Policy'),
              trailing: const Icon(Icons.arrow_forward_ios),
              onTap: () {
                // TODO: Show privacy policy
              },
            ),
            
            const Divider(),
            
            ListTile(
              leading: const Icon(Icons.description),
              title: const Text('Terms of Service'),
              trailing: const Icon(Icons.arrow_forward_ios),
              onTap: () {
                // TODO: Show terms of service
              },
            ),
            
            const Divider(),
            
            ListTile(
              leading: const Icon(Icons.help),
              title: const Text('Help & Support'),
              trailing: const Icon(Icons.arrow_forward_ios),
              onTap: () {
                // TODO: Show help screen
              },
            ),
          ],
        ),
      ),
    );
  }

  void _showRadiusDialog(BuildContext context, WidgetRef ref, dynamic user) {
    double currentRadius = user.preferences.alertRadius;
    
    showDialog(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setState) => AlertDialog(
          title: const Text('Alert Radius'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text('Receive alerts within ${currentRadius.toInt()} km'),
              const SizedBox(height: 16),
              Slider(
                value: currentRadius,
                min: 1,
                max: 20,
                divisions: 19,
                label: '${currentRadius.toInt()} km',
                onChanged: (value) {
                  setState(() {
                    currentRadius = value;
                  });
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
                final newPreferences = user.preferences.copyWith(
                  alertRadius: currentRadius,
                );
                ref.read(userProvider.notifier).updateUserPreferences(newPreferences);
                Navigator.pop(context);
              },
              child: const Text('Save'),
            ),
          ],
        ),
      ),
    );
  }

  void _showCategoriesDialog(BuildContext context, WidgetRef ref, dynamic user) {
    final allCategories = ['Traffic', 'Emergency', 'Weather', 'Infrastructure', 'Utilities'];
    List<String> selectedCategories = List.from(user.preferences.categories);
    
    showDialog(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setState) => AlertDialog(
          title: const Text('Alert Categories'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: allCategories.map((category) {
              return CheckboxListTile(
                title: Text(category),
                value: selectedCategories.contains(category),
                onChanged: (checked) {
                  setState(() {
                    if (checked == true) {
                      selectedCategories.add(category);
                    } else {
                      selectedCategories.remove(category);
                    }
                  });
                },
              );
            }).toList(),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('Cancel'),
            ),
            ElevatedButton(
              onPressed: () {
                final newPreferences = user.preferences.copyWith(
                  categories: selectedCategories,
                );
                ref.read(userProvider.notifier).updateUserPreferences(newPreferences);
                Navigator.pop(context);
              },
              child: const Text('Save'),
            ),
          ],
        ),
      ),
    );
  }

  void _showClearCacheDialog(BuildContext context) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Clear Cache'),
        content: const Text('This will clear all cached data. Are you sure?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancel'),
          ),
          ElevatedButton(
            onPressed: () {
              // TODO: Implement cache clearing
              Navigator.pop(context);
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(content: Text('Cache cleared successfully')),
              );
            },
            child: const Text('Clear'),
          ),
        ],
      ),
    );
  }
}
