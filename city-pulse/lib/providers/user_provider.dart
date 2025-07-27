import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:firebase_auth/firebase_auth.dart' as firebase_auth;
import 'package:cloud_firestore/cloud_firestore.dart';
import '../models/user.dart';
import '../models/event.dart';
import 'dart:async'; // Added for Timer

// User state provider
final userProvider = StateNotifierProvider<UserNotifier, AsyncValue<User>>((ref) {
  return UserNotifier();
});

// Bottom navigation provider
final bottomNavProvider = StateNotifierProvider<BottomNavNotifier, int>((ref) {
  return BottomNavNotifier();
});

// Provider for user submitted reports
final userReportsProvider = StateNotifierProvider<UserReportsNotifier, AsyncValue<List<Report>>>((ref) {
  return UserReportsNotifier();
});

// Provider for leaderboard (top users by reputation)
final leaderboardProvider = StateNotifierProvider<LeaderboardNotifier, AsyncValue<List<LeaderboardUser>>>((ref) {
  return LeaderboardNotifier();
});

class LeaderboardUser {
  final String userId;
  final String name;
  final int reputation;
  final int reportsSubmitted;

  LeaderboardUser({
    required this.userId,
    required this.name,
    required this.reputation,
    required this.reportsSubmitted,
  });

  factory LeaderboardUser.fromJson(Map<String, dynamic> json) {
    return LeaderboardUser(
      userId: json['userId'] ?? '',
      name: json['name'] ?? 'Anonymous User',
      reputation: json['reputation'] ?? 100,
      reportsSubmitted: json['reportsSubmitted'] ?? 0,
    );
  }
}

class LeaderboardNotifier extends StateNotifier<AsyncValue<List<LeaderboardUser>>> {
  LeaderboardNotifier() : super(const AsyncValue.loading()) {
    loadLeaderboard();
  }

  Future<void> loadLeaderboard() async {
    try {
      print('🏆 Loading leaderboard...');
      
      // Fetch top 5 users by reputation from Firestore
      final querySnapshot = await FirebaseFirestore.instance
          .collection('users')
          .orderBy('reputation', descending: true)
          .limit(5)
          .get();

      final topUsers = querySnapshot.docs.map((doc) {
        final data = doc.data();
        return LeaderboardUser.fromJson({
          'userId': doc.id,
          ...data,
        });
      }).toList();

      print('🏆 Loaded ${topUsers.length} top users for leaderboard');
      state = AsyncValue.data(topUsers);
    } catch (error, stackTrace) {
      print('❌ Error loading leaderboard: $error');
      state = AsyncValue.error(error, stackTrace);
    }
  }

  Future<void> refreshLeaderboard() async {
    print('🔄 Refreshing leaderboard...');
    await loadLeaderboard();
  }
}

class UserNotifier extends StateNotifier<AsyncValue<User>> {
  UserNotifier() : super(const AsyncValue.loading()) {
    loadUser();
  }

  Future<void> loadUser() async {
    try {
      final currentUser = firebase_auth.FirebaseAuth.instance.currentUser;
      if (currentUser == null) {
        throw Exception('No user logged in');
      }

      print('🔄 Loading user data for: ${currentUser.uid}');

      // Fetch user document from Firestore
      final userDoc = await FirebaseFirestore.instance
          .collection('users')
          .doc(currentUser.uid)
          .get();

      // Get actual reports count by querying user_reported_events collection
      final reportsQuery = await FirebaseFirestore.instance
          .collection('user_reported_events')
          .where('submittedByUid', isEqualTo: currentUser.uid)
          .get();
      
      final actualReportsCount = reportsQuery.docs.length;
      
      // Get reputation and preferences from user document, default values if not found
      int reputation = 100;
      UserPreferences preferences = UserPreferences(
        alertRadius: 5.0,
        categories: ['Traffic', 'Emergency', 'Weather', 'Infrastructure'],
        notificationEnabled: true,
        darkMode: false,
      );

      if (userDoc.exists) {
        final userData = userDoc.data()!;
        reputation = userData['reputation'] ?? 100;
        
        // Load user preferences from Firestore
        if (userData['preferences'] != null) {
          final prefsData = userData['preferences'] as Map<String, dynamic>;
          preferences = UserPreferences(
            alertRadius: (prefsData['alertRadius'] ?? 5.0).toDouble(),
            categories: List<String>.from(prefsData['categories'] ?? ['Traffic', 'Emergency', 'Weather', 'Infrastructure']),
            notificationEnabled: prefsData['notificationEnabled'] ?? true,
            darkMode: prefsData['darkMode'] ?? false,
          );
        }
        
        print('📊 User document exists - Reputation: $reputation');
      } else {
        print('📊 User document not found - Using default reputation: $reputation');
        // Create user document if it doesn't exist
        await _createInitialUserDocument(currentUser.uid, currentUser.displayName ?? 'User', currentUser.email ?? '', preferences);
      }

      print('📊 Final User stats: Reports: $actualReportsCount, Reputation: $reputation');

      final user = User(
        userId: currentUser.uid,
        name: currentUser.displayName ?? 'User',
        email: currentUser.email ?? '',
        location: Location(
          lat: 12.9716,
          lng: 77.5946,
          address: 'Bengaluru, Karnataka, India',
        ),
        preferences: preferences,
        reputation: reputation,
        reportsSubmitted: actualReportsCount,
      );

      state = AsyncValue.data(user);
      print('✅ User data loaded successfully');
    } catch (error, stackTrace) {
      print('❌ Error loading user: $error');
      state = AsyncValue.error(error, stackTrace);
    }
  }

  Future<void> _createInitialUserDocument(String userId, String name, String email, UserPreferences preferences) async {
    try {
      await FirebaseFirestore.instance.collection('users').doc(userId).set({
        'userId': userId,
        'name': name,
        'email': email,
        'reputation': 100,
        'reportsSubmitted': 0,
        'preferences': {
          'alertRadius': preferences.alertRadius,
          'categories': preferences.categories,
          'notificationEnabled': preferences.notificationEnabled,
          'darkMode': preferences.darkMode,
        },
        'createdAt': DateTime.now().toIso8601String(),
      });
      print('✅ Created initial user document for: $userId');
    } catch (error) {
      print('⚠️ Warning: Could not create initial user document: $error');
    }
  }

  Future<void> refreshUserData() async {
    print('🔄 Refreshing user data...');
    await loadUser();
  }

  Future<void> updateUserPreferences(UserPreferences preferences) async {
    try {
      final currentUser = firebase_auth.FirebaseAuth.instance.currentUser;
      if (currentUser == null) {
        throw Exception('No user logged in');
      }

      // Update preferences in Firestore
      await FirebaseFirestore.instance
          .collection('users')
          .doc(currentUser.uid)
          .update({
        'preferences': {
          'alertRadius': preferences.alertRadius,
          'categories': preferences.categories,
          'notificationEnabled': preferences.notificationEnabled,
          'darkMode': preferences.darkMode,
        },
        'updatedAt': DateTime.now().toIso8601String(),
      });

      // Update local state
      final currentState = state.asData?.value;
      if (currentState != null) {
        final updatedUser = currentState.copyWith(preferences: preferences);
        state = AsyncValue.data(updatedUser);
        print('✅ User preferences updated successfully');
      }
    } catch (error, stackTrace) {
      print('❌ Error updating user preferences: $error');
      state = AsyncValue.error(error, stackTrace);
    }
  }
}

class BottomNavNotifier extends StateNotifier<int> {
  BottomNavNotifier() : super(0); // Default to Dashboard

  void setIndex(int index) {
    state = index;
  }
}

class UserReportsNotifier extends StateNotifier<AsyncValue<List<Report>>> {
  UserReportsNotifier() : super(const AsyncValue.loading()) {
    _loadReports();
  }

  Future<void> _loadReports() async {
    try {
      final currentUser = firebase_auth.FirebaseAuth.instance.currentUser;
      if (currentUser == null) {
        state = const AsyncValue.data([]);
        return;
      }

      print('🔄 Loading reports for user: ${currentUser.uid}');

      final querySnapshot = await FirebaseFirestore.instance
          .collection('user_reported_events')
          .where('submittedByUid', isEqualTo: currentUser.uid)
          .get(); // Removed orderBy to avoid composite index requirement

      final reports = querySnapshot.docs.map((doc) {
        final data = doc.data();
        return Report.fromJson({
          'id': doc.id,
          ...data,
        });
      }).toList();

      // Sort by submission date on client side (newest first)
      reports.sort((a, b) => b.submittedAt.compareTo(a.submittedAt));
      print('✅ Loaded ${reports.length} reports for user ${currentUser.uid}');
      state = AsyncValue.data(reports);
    } catch (error, stackTrace) {
      print('❌ Error loading reports: $error');
      state = AsyncValue.error(error, stackTrace);
    }
  }

  Future<String> submitReport({
    required String title,
    required String description,
    required String category,
    required String severity,
    required double latitude,
    required double longitude,
    String? imageUrl,
  }) async {
    try {
      final currentUser = firebase_auth.FirebaseAuth.instance.currentUser;
      if (currentUser == null) {
        throw Exception('User not authenticated');
      }

      print('📝 Submitting new report...');

      final reportData = {
        'title': title,
        'description': description,
        'category': category,
        'severity': severity,
        'submittedAt': DateTime.now().toIso8601String(),
        'status': 'submitted',
        'submittedByUid': currentUser.uid,
        'latitude': latitude,
        'longitude': longitude,
        'imageUrl': imageUrl,
        'feedback': null,
      };

      final docRef = await FirebaseFirestore.instance
          .collection('user_reported_events')
          .add(reportData);

      print('✅ Report submitted successfully with ID: ${docRef.id}');

      // Update user's reports count and reputation
      await _incrementUserReportsCount(currentUser.uid);
      
      // Refresh the reports list immediately
      await _loadReports();
      
      return docRef.id;
    } catch (error) {
      print('❌ Error submitting report: $error');
      rethrow;
    }
  }

  Future<void> _incrementUserReportsCount(String userId) async {
    try {
      final currentUser = firebase_auth.FirebaseAuth.instance.currentUser;
      final userDocRef = FirebaseFirestore.instance.collection('users').doc(userId);
      
      // Check if user document exists
      final userDoc = await userDocRef.get();
      
      if (userDoc.exists) {
        // Increment existing count and add reputation points for submitting a report
        final currentReports = userDoc.data()?['reportsSubmitted'] ?? 0;
        final currentReputation = userDoc.data()?['reputation'] ?? 100;
        
        await userDocRef.update({
          'reportsSubmitted': currentReports + 1,
          'reputation': currentReputation + 5, // Award 5 points for each report
          'lastReportSubmitted': DateTime.now().toIso8601String(),
          'name': currentUser?.displayName ?? 'Anonymous User', // Update name for leaderboard
        });
        
        print('✅ Updated: Reports: ${currentReports + 1}, Reputation: ${currentReputation + 5}');
      } else {
        // Create user document with initial values
        await userDocRef.set({
          'reportsSubmitted': 1,
          'reputation': 105, // Starting 100 + 5 for first report
          'userId': userId,
          'name': currentUser?.displayName ?? 'Anonymous User',
          'email': currentUser?.email ?? '',
          'createdAt': DateTime.now().toIso8601String(),
          'lastReportSubmitted': DateTime.now().toIso8601String(),
        });
        
        print('✅ Created user document: Reports: 1, Reputation: 105');
      }
    } catch (error) {
      print('⚠️ Warning: Could not update user stats: $error');
      // Don't throw error here as the report was already submitted successfully
    }
  }

  Future<void> refreshReports() async {
    print('🔄 Refreshing reports...');
    await _loadReports();
  }
}
