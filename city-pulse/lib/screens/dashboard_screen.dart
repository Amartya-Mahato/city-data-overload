import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:latlong2/latlong.dart';
import '../providers/user_provider.dart';
import '../providers/chat_providers.dart';
import '../models/chat_message.dart';
import '../themes/app_theme.dart';

class DashboardScreen extends ConsumerStatefulWidget {
  const DashboardScreen({super.key});

  @override
  ConsumerState<DashboardScreen> createState() => _DashboardScreenState();
}

class _DashboardScreenState extends ConsumerState<DashboardScreen> with TickerProviderStateMixin {
  late AnimationController _fadeController;
  late Animation<double> _fadeAnimation;
  late TextEditingController _chatController;
  late ScrollController _chatScrollController;

  @override
  void initState() {
    super.initState();
    _fadeController = AnimationController(
      duration: const Duration(milliseconds: 600),
      vsync: this,
    );
    _fadeAnimation = Tween<double>(
      begin: 0.0,
      end: 1.0,
    ).animate(CurvedAnimation(
      parent: _fadeController,
      curve: Curves.easeInOut,
    ));
    _chatController = TextEditingController();
    _chatScrollController = ScrollController();

    // Start the fade animation
    _fadeController.forward();
  }

  @override
  void dispose() {
    _fadeController.dispose();
    _chatController.dispose();
    _chatScrollController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final userAsync = ref.watch(userProvider);
    final chatMessages = ref.watch(chatMessageProvider);
    final theme = Theme.of(context);
    return Scaffold(
      body: Stack(
        children: [
          // Main content with DraggableScrollableSheet
          DraggableScrollableSheet(
            initialChildSize: 0.5,
            minChildSize: 0.2,
            maxChildSize: 0.8,
            builder: (context, scrollController) {
              return Container(
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    begin: Alignment.topCenter,
                    end: Alignment.bottomCenter,
                    colors: [
                      AppTheme.primaryPurple.withOpacity(0.8),
                      AppTheme.primaryPurple,
                    ],
                  ),
                ),
                child: FlutterMap(
                  options: MapOptions(
                    center: const LatLng(12.9716, 77.5946),
                    zoom: 11.0,
                    maxZoom: 18.0,
                    minZoom: 3.0,
                    interactiveFlags: InteractiveFlag.pinchZoom | InteractiveFlag.drag,
                  ),
                  children: [
                    TileLayer(
                      urlTemplate: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
                      userAgentPackageName: 'com.bengaluru.citypulse.bengaluru_city_pulse',
                      maxZoom: 19,
                    ),
                  ],
                ),
              );
            },
          ),

          // Bottom Half: Chat Interface
          DraggableScrollableSheet(
            initialChildSize: 0.5,
            minChildSize: 0.2,
            maxChildSize: 0.8,
            builder: (context, scrollController) {
              return Container(
                decoration: BoxDecoration(
                  color: theme.colorScheme.surface,
                  borderRadius: const BorderRadius.vertical(
                    top: Radius.circular(24),
                  ),
                  boxShadow: [
                    BoxShadow(
                      color: Colors.black.withOpacity(0.1),
                      blurRadius: 10,
                      spreadRadius: 2,
                      offset: const Offset(0, -2),
                    ),
                  ],
                ),
                child: Column(
                  children: [
                    // Handle bar
                    Container(
                      width: 40,
                      height: 4,
                      margin: const EdgeInsets.only(top: 12),
                      decoration: BoxDecoration(
                        color: Colors.grey[300],
                        borderRadius: BorderRadius.circular(2),
                      ),
                    ),

                    // Chat header
                    Padding(
                      padding: const EdgeInsets.all(16),
                      child: Text(
                        'Ask about events and news in your area',
                        style: GoogleFonts.poppins(
                          fontSize: 16,
                          fontWeight: FontWeight.w600,
                          color: theme.colorScheme.onSurface,
                        ),
                      ),
                    ),

                    // Chat messages
                    Expanded(
                      child: ListView.builder(
                        controller: _chatScrollController,
                        padding: const EdgeInsets.symmetric(horizontal: 16),
                        itemCount: chatMessages.length,
                        itemBuilder: (context, index) {
                          final message = chatMessages[index];
                          return ChatBubble(message: message);
                        },
                      ),
                    ),

                    // Chat input
                    Container(
                      padding: const EdgeInsets.all(16),
                      decoration: BoxDecoration(
                        color: theme.colorScheme.surface,
                        border: Border(
                          top: BorderSide(
                            color: Colors.grey[200]!,
                            width: 1,
                          ),
                        ),
                      ),
                      child: Row(
                        children: [
                          Expanded(
                            child: TextField(
                              controller: _chatController,
                              decoration: InputDecoration(
                                hintText: 'Type your message...',
                                border: OutlineInputBorder(
                                  borderRadius: BorderRadius.circular(25),
                                  borderSide: BorderSide.none,
                                ),
                                filled: true,
                                fillColor: Colors.grey[100],
                                contentPadding: const EdgeInsets.symmetric(
                                  horizontal: 16,
                                  vertical: 12,
                                ),
                              ),
                              onSubmitted: _sendMessage,
                            ),
                          ),
                          const SizedBox(width: 8),
                          AnimatedScale(
                            scale: 1.0,
                            duration: const Duration(milliseconds: 100),
                            child: IconButton(
                              onPressed: () => _sendMessage(_chatController.text),
                              icon: const Icon(Icons.send),
                              style: IconButton.styleFrom(
                                backgroundColor: AppTheme.primaryPurple,
                                foregroundColor: Colors.white,
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              );
            },
          ),

          // Middle Section: Floating User Greeting Card
          Positioned(
            top: MediaQuery.of(context).size.height * 0.3,
            left: 16,
            right: 16,
            child: AnimatedOpacity(
              opacity: _fadeAnimation.value,
              duration: const Duration(milliseconds: 300),
              child: userAsync.when(
                data: (user) => UserGreetingCard(
                  userName: user.name,
                  userAvatar: 'assets/images/avatar_placeholder.png',
                ),
                loading: () => const UserGreetingCard(
                  userName: 'Loading...',
                  userAvatar: 'assets/images/avatar_placeholder.png',
                ),
                error: (error, _) => const UserGreetingCard(
                  userName: 'Guest',
                  userAvatar: 'assets/images/avatar_placeholder.png',
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  void _sendMessage(String text) {
    if (text.trim().isEmpty) return;

    final message = ChatMessage(
      id: DateTime.now().millisecondsSinceEpoch.toString(),
      content: text,
      isUser: true,
      timestamp: DateTime.now(),
    );

    ref.read(chatMessageProvider.notifier).addMessage(message);
    _chatController.clear();

    // Simulate AI response
    Future.delayed(const Duration(milliseconds: 1000), () {
      final aiResponse = ChatMessage(
        id: DateTime.now().millisecondsSinceEpoch.toString(),
        content: _generateAIResponse(text),
        isUser: false,
        timestamp: DateTime.now(),
        type: MessageType.suggestion,
      );
      ref.read(chatMessageProvider.notifier).addMessage(aiResponse);

      // Auto-scroll to bottom
      _chatScrollController.animateTo(
        _chatScrollController.position.maxScrollExtent,
        duration: const Duration(milliseconds: 300),
        curve: Curves.easeInOut,
      );
    });
  }

  String _generateAIResponse(String userMessage) {
    // Simple placeholder AI responses
    if (userMessage.toLowerCase().contains('traffic')) {
      return 'I found some traffic updates near your location. There\'s moderate congestion on MG Road and heavy traffic on Outer Ring Road.';
    } else if (userMessage.toLowerCase().contains('weather')) {
      return 'The weather in Bengaluru today is partly cloudy with a high of 26°C. There\'s a 30% chance of rain in the evening.';
    } else if (userMessage.toLowerCase().contains('events')) {
      return 'Here are some upcoming events in your area: Tech meetup at UB City Mall, Cultural festival at Lalbagh, and a food festival at Brigade Road.';
    } else {
      return 'I\'m here to help you with information about Bengaluru! You can ask me about traffic, weather, events, or any city-related queries.';
    }
  }

  Widget _buildDetailRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 100,
            child: Text(
              '$label:',
              style: const TextStyle(fontWeight: FontWeight.w600),
            ),
          ),
          Expanded(
            child: Text(value),
          ),
        ],
      ),
    );
  }
}

// User greeting card widget
class UserGreetingCard extends StatelessWidget {
  final String userName;
  final String userAvatar;

  const UserGreetingCard({
    super.key,
    required this.userName,
    required this.userAvatar,
  });

  @override
  Widget build(BuildContext context) {
    return Card(
      elevation: 8,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
      ),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          children: [
            CircleAvatar(
              radius: 24,
              backgroundColor: AppTheme.successGreen,
              child: Text(
                userName.isNotEmpty ? userName[0].toUpperCase() : 'G',
                style: GoogleFonts.poppins(
                  fontSize: 18,
                  fontWeight: FontWeight.bold,
                  color: Colors.white,
                ),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Hi, $userName!',
                    style: GoogleFonts.poppins(
                      fontSize: 16,
                      fontWeight: FontWeight.bold,
                      color: AppTheme.primaryPurple,
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  Text(
                    'What\'s happening in your area?',
                    style: GoogleFonts.poppins(
                      fontSize: 12,
                      color: Colors.grey[600],
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

// Chat bubble widget
class ChatBubble extends StatelessWidget {
  final ChatMessage message;

  const ChatBubble({super.key, required this.message});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      child: Row(
        mainAxisAlignment: message.isUser
            ? MainAxisAlignment.end
            : MainAxisAlignment.start,
        children: [
          if (!message.isUser)
            Container(
              width: 32,
              height: 32,
              margin: const EdgeInsets.only(right: 8),
              decoration: const BoxDecoration(
                color: AppTheme.primaryPurple,
                shape: BoxShape.circle,
              ),
              child: const Icon(
                Icons.smart_toy,
                color: Colors.white,
                size: 16,
              ),
            ),
          Flexible(
            child: Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: message.isUser
                    ? AppTheme.primaryPurple
                    : Colors.grey[100],
                borderRadius: BorderRadius.circular(16),
              ),
              child: Text(
                message.content,
                style: GoogleFonts.poppins(
                  fontSize: 14,
                  color: message.isUser
                      ? Colors.white
                      : theme.colorScheme.onSurface,
                ),
              ),
            ),
          ),
          if (message.isUser)
            Container(
              width: 32,
              height: 32,
              margin: const EdgeInsets.only(left: 8),
              decoration: const BoxDecoration(
                color: AppTheme.accentCyan,
                shape: BoxShape.circle,
              ),
              child: const Icon(
                Icons.person,
                color: Colors.white,
                size: 16,
              ),
            ),
        ],
      ),
    );
  }
}
