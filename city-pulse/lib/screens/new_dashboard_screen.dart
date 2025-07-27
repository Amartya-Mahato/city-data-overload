import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:latlong2/latlong.dart';
import '../providers/user_provider.dart';
import '../providers/chat_providers.dart';
import '../models/chat_message.dart';
import '../themes/app_theme.dart';
import '../components/sos_button.dart';

class NewDashboardScreen extends ConsumerStatefulWidget {
  const NewDashboardScreen({super.key});

  @override
  ConsumerState<NewDashboardScreen> createState() => _NewDashboardScreenState();
}

class _NewDashboardScreenState extends ConsumerState<NewDashboardScreen>
    with TickerProviderStateMixin {
  late AnimationController _chatController;
  late Animation<double> _chatHeightAnimation;
  late Animation<double> _chatOpacityAnimation;
  late TextEditingController _messageController;
  late ScrollController _chatScrollController;
  
  bool _isChatExpanded = false;
  final double _minChatHeight = 120;
  final double _maxChatHeight = 400;

  @override
  void initState() {
    super.initState();
    _chatController = AnimationController(
      duration: const Duration(milliseconds: 300),
      vsync: this,
    );
    
    _chatHeightAnimation = Tween<double>(
      begin: _minChatHeight,
      end: _maxChatHeight,
    ).animate(CurvedAnimation(
      parent: _chatController,
      curve: Curves.easeInOut,
    ));
    
    _chatOpacityAnimation = Tween<double>(
      begin: 0.95,
      end: 1.0,
    ).animate(CurvedAnimation(
      parent: _chatController,
      curve: Curves.easeInOut,
    ));
    
    _messageController = TextEditingController();
    _chatScrollController = ScrollController();
  }

  @override
  void dispose() {
    _chatController.dispose();
    _messageController.dispose();
    _chatScrollController.dispose();
    super.dispose();
  }

  void _toggleChat() {
    setState(() {
      _isChatExpanded = !_isChatExpanded;
    });
    
    if (_isChatExpanded) {
      _chatController.forward();
    } else {
      _chatController.reverse();
    }
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
    _messageController.clear();

    // Auto-scroll to bottom
    Future.delayed(const Duration(milliseconds: 100), () {
      if (_chatScrollController.hasClients) {
        _chatScrollController.animateTo(
          _chatScrollController.position.maxScrollExtent,
          duration: const Duration(milliseconds: 300),
          curve: Curves.easeInOut,
        );
      }
    });

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

      // Auto-scroll to bottom again
      Future.delayed(const Duration(milliseconds: 100), () {
        if (_chatScrollController.hasClients) {
          _chatScrollController.animateTo(
            _chatScrollController.position.maxScrollExtent,
            duration: const Duration(milliseconds: 300),
            curve: Curves.easeInOut,
          );
        }
      });
    });
  }

  String _generateAIResponse(String userMessage) {
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

  @override
  Widget build(BuildContext context) {
    final userAsync = ref.watch(userProvider);
    final chatMessages = ref.watch(chatMessageProvider);
    final screenHeight = MediaQuery.of(context).size.height;
    final statusBarHeight = MediaQuery.of(context).padding.top;
    
    return Scaffold(
      body: Stack(
        children: [
          // Full screen map
          Positioned.fill(
            child: Container(
              decoration: const BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                  colors: [
                    Color(0xFF667EEA),
                    Color(0xFF764BA2),
                  ],
                ),
              ),
              child: FlutterMap(
                options: MapOptions(
                  center: const LatLng(12.9716, 77.5946),
                  zoom: 12.0,
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
                  // Add markers here for events
                ],
              ),
            ),
          ),
          
          // Welcome message overlay
          Positioned(
            top: statusBarHeight + 20,
            left: 16,
            right: 16,
            child: userAsync.when(
              data: (user) => _buildWelcomeCard(user.name),
              loading: () => _buildWelcomeCard('Loading...'),
              error: (error, _) => _buildWelcomeCard('Guest'),
            ),
          ),
          
          // Collapsible chat interface at bottom
          Positioned(
            bottom: 0,
            left: 0,
            right: 0,
            child: AnimatedBuilder(
              animation: _chatHeightAnimation,
              builder: (context, child) {
                final bottomPadding = MediaQuery.of(context).padding.bottom;
                final safeHeight = _chatHeightAnimation.value;
                
                return Container(
                  height: safeHeight + bottomPadding,
                  constraints: BoxConstraints(
                    maxHeight: screenHeight * 0.7, // Prevent overflow
                    minHeight: _minChatHeight + bottomPadding,
                  ),
                  decoration: BoxDecoration(
                    color: Colors.white.withOpacity(_chatOpacityAnimation.value),
                    borderRadius: const BorderRadius.vertical(
                      top: Radius.circular(24),
                    ),
                    boxShadow: [
                      BoxShadow(
                        color: Colors.black.withOpacity(0.15),
                        blurRadius: 20,
                        spreadRadius: 5,
                        offset: const Offset(0, -5),
                      ),
                    ],
                  ),
                  child: SafeArea(
                    top: false,
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        // Drag handle and header
                        GestureDetector(
                          onTap: _toggleChat,
                          onPanUpdate: (details) {
                            // Handle swipe gestures
                            final dy = details.delta.dy;
                            if (dy.abs() > 2) { // Threshold to prevent accidental swipes
                              if (dy < -5 && !_isChatExpanded) {
                                // Swipe up to expand
                                _toggleChat();
                              } else if (dy > 5 && _isChatExpanded) {
                                // Swipe down to collapse
                                _toggleChat();
                              }
                            }
                          },
                          child: Container(
                            width: double.infinity,
                            padding: const EdgeInsets.symmetric(vertical: 12),
                            decoration: const BoxDecoration(
                              borderRadius: BorderRadius.vertical(
                                top: Radius.circular(24),
                              ),
                            ),
                            child: Column(
                              children: [
                                // Drag handle
                                Container(
                                  width: 50,
                                  height: 4,
                                  decoration: BoxDecoration(
                                    color: Colors.grey[400],
                                    borderRadius: BorderRadius.circular(2),
                                  ),
                                ),
                                const SizedBox(height: 12),
                                // Chat header
                                Row(
                                  mainAxisAlignment: MainAxisAlignment.center,
                                  children: [
                                    Icon(
                                      Icons.chat_bubble_outline,
                                      color: AppTheme.primaryPurple,
                                      size: 20,
                                    ),
                                    const SizedBox(width: 8),
                                    Text(
                                      'Ask about your area',
                                      style: GoogleFonts.poppins(
                                        fontSize: 16,
                                        fontWeight: FontWeight.w600,
                                        color: AppTheme.primaryPurple,
                                      ),
                                    ),
                                    const SizedBox(width: 8),
                                    AnimatedRotation(
                                      turns: _isChatExpanded ? 0.5 : 0,
                                      duration: const Duration(milliseconds: 300),
                                      child: Icon(
                                        Icons.keyboard_arrow_up,
                                        color: AppTheme.primaryPurple,
                                      ),
                                    ),
                                  ],
                                ),
                              ],
                            ),
                          ),
                        ),
                        
                        // Chat messages (only visible when expanded)
                        if (_isChatExpanded)
                          Expanded(
                            child: Container(
                              padding: const EdgeInsets.symmetric(horizontal: 16),
                              constraints: BoxConstraints(
                                maxHeight: _maxChatHeight - 120, // Reserve space for input
                              ),
                              child: ListView.builder(
                                controller: _chatScrollController,
                                shrinkWrap: true,
                                itemCount: chatMessages.length,
                                itemBuilder: (context, index) {
                                  final message = chatMessages[index];
                                  return _buildChatBubble(message);
                                },
                              ),
                            ),
                          ),
                        
                        // Chat input
                        Container(
                          padding: const EdgeInsets.all(16),
                          decoration: BoxDecoration(
                            color: Colors.grey[50],
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
                                child: Container(
                                  decoration: BoxDecoration(
                                    color: Colors.white,
                                    borderRadius: BorderRadius.circular(25),
                                    boxShadow: [
                                      BoxShadow(
                                        color: Colors.grey.withOpacity(0.1),
                                        blurRadius: 5,
                                        offset: const Offset(0, 2),
                                      ),
                                    ],
                                  ),
                                  child: TextField(
                                    controller: _messageController,
                                    decoration: InputDecoration(
                                      hintText: 'Ask about traffic, events, weather...',
                                      hintStyle: GoogleFonts.poppins(
                                        color: Colors.grey[500],
                                        fontSize: 14,
                                      ),
                                      border: OutlineInputBorder(
                                        borderRadius: BorderRadius.circular(25),
                                        borderSide: BorderSide.none,
                                      ),
                                      contentPadding: const EdgeInsets.symmetric(
                                        horizontal: 20,
                                        vertical: 12,
                                      ),
                                    ),
                                    onSubmitted: _sendMessage,
                                  ),
                                ),
                              ),
                              const SizedBox(width: 12),
                              GestureDetector(
                                onTap: () => _sendMessage(_messageController.text),
                                child: Container(
                                  width: 48,
                                  height: 48,
                                  decoration: BoxDecoration(
                                    gradient: const LinearGradient(
                                      colors: [AppTheme.primaryPurple, AppTheme.primaryBlue],
                                    ),
                                    shape: BoxShape.circle,
                                    boxShadow: [
                                      BoxShadow(
                                        color: AppTheme.primaryPurple.withOpacity(0.3),
                                        blurRadius: 8,
                                        offset: const Offset(0, 4),
                                      ),
                                    ],
                                  ),
                                  child: const Icon(
                                    Icons.send,
                                    color: Colors.white,
                                    size: 20,
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                );
              },
            ),
          ),
          
          // SOS button overlay - positioned above chat pane with state preservation
          AnimatedBuilder(
            animation: _chatHeightAnimation,
            builder: (context, child) {
              final bottomPadding = MediaQuery.of(context).padding.bottom;
              final chatHeight = _chatHeightAnimation.value + bottomPadding;
              
              return Positioned(
                bottom: chatHeight + 30, // Optimal space above chat pane (30px)
                right: 16,
                child: child!,
              );
            },
            child: const SosButton(),
          ),
        ],
      ),
    );
  }

  Widget _buildWelcomeCard(String userName) {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          colors: [Colors.white, Colors.white70],
        ),
        borderRadius: BorderRadius.circular(20),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.1),
            blurRadius: 15,
            offset: const Offset(0, 5),
          ),
        ],
      ),
      child: Row(
        children: [
          Container(
            width: 50,
            height: 50,
            decoration: BoxDecoration(
              gradient: const LinearGradient(
                colors: [AppTheme.primaryPurple, AppTheme.primaryBlue],
              ),
              shape: BoxShape.circle,
            ),
            child: Center(
              child: Text(
                userName.isNotEmpty ? userName[0].toUpperCase() : 'G',
                style: GoogleFonts.poppins(
                  fontSize: 20,
                  fontWeight: FontWeight.bold,
                  color: Colors.white,
                ),
              ),
            ),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Welcome back, $userName!',
                  style: GoogleFonts.poppins(
                    fontSize: 16,
                    fontWeight: FontWeight.bold,
                    color: AppTheme.primaryPurple,
                  ),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
                const SizedBox(height: 4),
                Text(
                  'Stay updated with real-time city insights',
                  style: GoogleFonts.poppins(
                    fontSize: 13,
                    color: Colors.grey[600],
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildChatBubble(ChatMessage message) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
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
                gradient: LinearGradient(
                  colors: [AppTheme.primaryPurple, AppTheme.primaryBlue],
                ),
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
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              decoration: BoxDecoration(
                gradient: message.isUser
                    ? const LinearGradient(
                        colors: [AppTheme.primaryPurple, AppTheme.primaryBlue],
                      )
                    : null,
                color: message.isUser ? null : Colors.grey[100],
                borderRadius: BorderRadius.circular(20),
                boxShadow: [
                  BoxShadow(
                    color: Colors.grey.withOpacity(0.1),
                    blurRadius: 5,
                    offset: const Offset(0, 2),
                  ),
                ],
              ),
              child: Text(
                message.content,
                style: GoogleFonts.poppins(
                  fontSize: 14,
                  color: message.isUser ? Colors.white : Colors.grey[800],
                  height: 1.3,
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
