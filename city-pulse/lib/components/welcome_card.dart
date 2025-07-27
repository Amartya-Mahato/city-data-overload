import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import '../themes/app_theme.dart';

class WelcomeCard extends StatelessWidget {
  final String userName;
  final String? userAvatar;

  const WelcomeCard({
    super.key,
    required this.userName,
    this.userAvatar,
  });

  @override
  Widget build(BuildContext context) {
    return Card(
      elevation: 12,
      shadowColor: Colors.black26,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(20),
      ),
      child: Container(
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(20),
          gradient: const LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [
              Colors.white,
              Color(0xFFF8F9FF),
            ],
          ),
        ),
        child: Row(
          children: [
            // User Avatar
            Container(
              width: 60,
              height: 60,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                gradient: const LinearGradient(
                  colors: [
                    AppTheme.primaryPurple,
                    AppTheme.accentCyan,
                  ],
                ),
                boxShadow: [
                  BoxShadow(
                    color: AppTheme.primaryPurple.withOpacity(0.3),
                    blurRadius: 10,
                    offset: const Offset(0, 5),
                  ),
                ],
              ),
              child: userAvatar != null
                  ? ClipOval(
                      child: Image.asset(
                        userAvatar!,
                        fit: BoxFit.cover,
                        errorBuilder: (context, error, stackTrace) {
                          return _buildInitials(userName);
                        },
                      ),
                    )
                  : _buildInitials(userName),
            ),
            
            const SizedBox(width: 16),
            
            // Welcome Text
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Welcome back,',
                    style: GoogleFonts.poppins(
                      fontSize: 14,
                      color: Colors.grey[600],
                      fontWeight: FontWeight.w400,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    userName.isEmpty ? 'Guest' : userName,
                    style: GoogleFonts.poppins(
                      fontSize: 20,
                      fontWeight: FontWeight.bold,
                      color: AppTheme.primaryPurple,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 8,
                      vertical: 4,
                    ),
                    decoration: BoxDecoration(
                      color: AppTheme.successGreen.withOpacity(0.1),
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(
                        color: AppTheme.successGreen.withOpacity(0.3),
                        width: 1,
                      ),
                    ),
                    child: Text(
                      '✨ Bengaluru Active',
                      style: GoogleFonts.poppins(
                        fontSize: 11,
                        color: AppTheme.successGreen,
                        fontWeight: FontWeight.w500,
                      ),
                    ),
                  ),
                ],
              ),
            ),
            
            // Notification Indicator
            Container(
              width: 8,
              height: 8,
              decoration: const BoxDecoration(
                color: AppTheme.alertRed,
                shape: BoxShape.circle,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildInitials(String name) {
    final initials = name.isNotEmpty 
        ? (name.split(' ').length > 1 
            ? '${name.split(' ')[0][0]}${name.split(' ')[1][0]}' 
            : name[0])
        : 'G';
    
    return Center(
      child: Text(
        initials.toUpperCase(),
        style: GoogleFonts.poppins(
          fontSize: 20,
          fontWeight: FontWeight.bold,
          color: Colors.white,
        ),
      ),
    );
  }
}
