import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import '../models/chat_message.dart';
import '../providers/chat_providers.dart';
import '../services/gemini_service.dart';
import '../themes/app_theme.dart';

class ChatPane extends ConsumerWidget {
  const ChatPane({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final chatMessages = ref.watch(chatMessageProvider);
    final chatController = TextEditingController();
    final screenHeight = MediaQuery.of(context).size.height;
    final screenWidth = MediaQuery.of(context).size.width;
    final bottomPadding = MediaQuery.of(context).padding.bottom;
    final isDarkMode = Theme.of(context).brightness == Brightness.dark;
    
    // Responsive height calculation
    double maxHeight;
    if (screenWidth < 600) { // Mobile
      maxHeight = screenHeight * 0.75; // 75% for mobile
    } else if (screenWidth < 1024) { // Tablet
      maxHeight = screenHeight * 0.6; // 60% for tablet
    } else { // Desktop
      maxHeight = screenHeight * 0.5; // 50% for desktop
    }
    
    // Ensure safe area and prevent overflow - Fix the overflow issue
    maxHeight = (maxHeight - bottomPadding - 100).clamp(250.0, screenHeight * 0.7);

    return SafeArea(
      child: Container(
        constraints: BoxConstraints(
          maxHeight: maxHeight,
          minHeight: 250,
        ),
        decoration: BoxDecoration(
          color: Theme.of(context).colorScheme.surface,
          borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
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
            GestureDetector(
              onTap: () {},
              child: Container(
                width: 40,
                height: 4,
                margin: const EdgeInsets.only(top: 12),
                decoration: BoxDecoration(
                  color: Colors.grey[300],
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ),

            // Chat header
            Padding(
              padding: const EdgeInsets.all(16),
              child: Row(
                children: [
                  const Icon(Icons.chat_bubble_outline),
                  const SizedBox(width: 8),
                  Text(
                    'Ask nearby reports',
                    style: GoogleFonts.poppins(
                      fontSize: 16,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ],
              ),
            ),

            // Chat messages
            Expanded(
              child: chatMessages.isEmpty
                  ? Center(
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Icon(
                            Icons.chat_bubble_outline,
                            size: 48,
                            color: Colors.grey[400],
                          ),
                          const SizedBox(height: 16),
                          Text(
                            'Start a conversation',
                            style: GoogleFonts.poppins(
                              fontSize: 16,
                              color: Colors.grey[600],
                              fontWeight: FontWeight.w500,
                            ),
                          ),
                          const SizedBox(height: 8),
                          Text(
                            'Ask about traffic, events, or weather',
                            style: GoogleFonts.poppins(
                              fontSize: 14,
                              color: Colors.grey[500],
                            ),
                          ),
                        ],
                      ),
                    )
                  : ListView.builder(
                      padding: EdgeInsets.only(
                        left: screenWidth < 600 ? 12 : 16,
                        right: screenWidth < 600 ? 12 : 16,
                        top: 8,
                        bottom: 8,
                      ),
                      shrinkWrap: true,
                      itemCount: chatMessages.length,
                      itemBuilder: (context, index) {
                        final message = chatMessages[index];
                        return Padding(
                          padding: EdgeInsets.symmetric(
                            vertical: screenWidth < 600 ? 3.0 : 4.0,
                          ),
                          child: ChatBubble(message: message),
                        );
                      },
                    ),
            ),

            // Chat input
            Padding(
              padding: EdgeInsets.only(
                left: screenWidth < 600 ? 12 : 16,
                right: screenWidth < 600 ? 12 : 16,
                bottom: 8 ,
                top: 8,
              ),
              child: Container(
                decoration: BoxDecoration(
                  color: Theme.of(context).colorScheme.surface,
                ),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    Expanded(
                      child: ConstrainedBox(
                        constraints: BoxConstraints(
                          maxHeight: screenWidth < 600 ? 100 : 120,
                        ),
                        child: TextField(
                          controller: chatController,
                          maxLines: null,
                          textInputAction: TextInputAction.newline,
                          style: GoogleFonts.poppins(
                            fontSize: screenWidth < 600 ? 14 : 16,
                          ),
                          decoration: InputDecoration(
                            hintText: screenWidth < 600 
                                ? 'Ask about your area...' 
                                : 'Type your message...',
                            hintStyle: GoogleFonts.poppins(
                              fontSize: screenWidth < 600 ? 14 : 16,
                              color: Colors.grey[500],
                            ),
                            border: OutlineInputBorder(
                              borderRadius: BorderRadius.circular(25),
                              borderSide: BorderSide.none,
                            ),
                            filled: true,
                            fillColor: isDarkMode ? Colors.grey[700] : Colors.grey[100],
                            contentPadding: EdgeInsets.symmetric(
                              horizontal: screenWidth < 600 ? 14 : 16,
                              vertical: screenWidth < 600 ? 10 : 12,
                            ),
                          ),
                          onSubmitted: (text) {
                            _sendMessage(text, ref);
                            chatController.clear();
                          },
                        ),
                      ),
                    ),
                    const SizedBox(width: 8),
                    IconButton(
                      onPressed: () {
                        _sendMessage(chatController.text, ref);
                        chatController.clear();
                      },
                      icon: const Icon(Icons.send),
                      color: AppTheme.primaryPurple,
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  void _sendMessage(String text, WidgetRef ref) {
    if (text.trim().isEmpty) return;

    final message = ChatMessage(
      id: DateTime.now().millisecondsSinceEpoch.toString(),
      content: text,
      isUser: true,
      timestamp: DateTime.now(),
    );

    ref.read(chatMessageProvider.notifier).addMessage(message);

    // Simulate AI response via Gemini Service
    ref.read(geminiServiceProvider).getResponse(text).then((response) {
      final aiResponse = ChatMessage(
        id: DateTime.now().millisecondsSinceEpoch.toString(),
        content: response,
        isUser: false,
        timestamp: DateTime.now(),
      );
      ref.read(chatMessageProvider.notifier).addMessage(aiResponse);
    });
  }
}

class ChatBubble extends StatelessWidget {
  final ChatMessage message;

  const ChatBubble({super.key, required this.message});

  @override
  Widget build(BuildContext context) {
    final bool isUserMessage = message.isUser;
    final isDarkMode = Theme.of(context).brightness == Brightness.dark;

    return Align(
      alignment: isUserMessage ? Alignment.centerRight : Alignment.centerLeft,
      child: Column(
        crossAxisAlignment:
            isUserMessage ? CrossAxisAlignment.end : CrossAxisAlignment.start,
        children: [
          Container(
            margin: const EdgeInsets.symmetric(vertical: 4, horizontal: 8),
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: isUserMessage 
                  ? AppTheme.primaryPurple 
                  : isDarkMode 
                      ? Colors.grey[800]
                      : Colors.grey.shade200,
              borderRadius: BorderRadius.circular(15),
            ),
            child: Text(
              message.content,
              style: GoogleFonts.poppins(
                fontSize: 14,
                color: isUserMessage 
                    ? Colors.white 
                    : isDarkMode 
                        ? Colors.white.withValues(alpha: 0.87)
                        : Colors.black87,
              ),
            ),
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 8),
            child: Text(
              _formatTimestamp(message.timestamp),
              style: GoogleFonts.poppins(
                fontSize: 10, 
                color: isDarkMode ? Colors.grey[400] : Colors.grey[600],
              ),
            ),
          ),
        ],
      ),
    );
  }

  String _formatTimestamp(DateTime timestamp) {
    final hours = timestamp.hour.toString().padLeft(2, '0');
    final minutes = timestamp.minute.toString().padLeft(2, '0');
    return '$hours:$minutes';
  }
}
