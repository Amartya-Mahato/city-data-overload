import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../models/chat_message.dart';

final chatMessageProvider = StateNotifierProvider<ChatMessageController, List<ChatMessage>>((ref) {
  return ChatMessageController([]);
});

class ChatMessageController extends StateNotifier<List<ChatMessage>> {
  ChatMessageController(List<ChatMessage> state) : super(state);

  void addMessage(ChatMessage message) {
    state = [...state, message];
  }

  void removeMessage(String id) {
    state = state.where((message) => message.id != id).toList();
  }
}

final isMapExpandedProvider = StateProvider<bool>((ref) => false);

final userGreetingProvider = StateProvider<String>((ref) => 'Hello! How can I assist you today?');
