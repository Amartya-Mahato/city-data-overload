import 'dart:io';
import 'dart:convert';
import 'package:google_generative_ai/google_generative_ai.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class GeminiService {
  static const String _apiKey = 'AIzaSyCL75p9wAFmbyrDPwJZLoKER76K9xWi8kM'; // Replace with your actual API key
  late final GenerativeModel _model;

  GeminiService() {
    _model = GenerativeModel(
      model: 'gemini-1.5-flash',
      apiKey: _apiKey,
    );
  }

  Future<Map<String, dynamic>> analyzeImageWithGemini(String imagePath) async {
    try {
      final imageFile = File(imagePath);
      final imageBytes = await imageFile.readAsBytes();

      // Create a more specific and detailed prompt for city event detection
      final prompt = '''
Analyze this image and determine what type of city event or incident it shows. You are helping a citizen reporting app classify events accurately.

Please identify the most likely category from these options:
- Traffic (cars, vehicles, traffic jams, accidents, road conditions)
- Emergency (fires, accidents, medical emergencies, police activity)
- Weather (flooding, storms, heavy rain, snow, extreme weather conditions)
- Infrastructure (road damage, construction, broken utilities, building issues)
- Utilities (power outages, water issues, gas leaks, telecommunication problems)
- Others

Look for these specific indicators:
- Traffic: Multiple vehicles, traffic congestion, road blockages, vehicle accidents
- Emergency: Fire, smoke, emergency vehicles, accident scenes, police/ambulance activity
- Weather: Flooded areas, storm damage, heavy precipitation, weather-related damage
- Infrastructure: Damaged roads, construction sites, broken infrastructure, maintenance issues
- Utilities: Downed power lines, water main breaks, utility repair work

Provide your response in this exact JSON format:
{
  "category": "one of the 5 categories above",
  "confidence": confidence_score_between_0_and_1,
  "description": "detailed description of what you see and why you classified it this way",
  "title": "short descriptive title for this event (3-8 words)"
}

Be specific about what you observe in the image. If you see water on roads or flooding, categorize as "Weather". If you see multiple cars or traffic, categorize as "Traffic". If you see emergency vehicles or incidents, categorize as "Emergency".
''';

      final content = [
        Content.multi([
          TextPart(prompt),
          DataPart('image/jpeg', imageBytes),
        ])
      ];

      final response = await _model.generateContent(content);
      final responseText = response.text;

      if (responseText == null || responseText.isEmpty) {
        throw Exception('Empty response from Gemini API');
      }

      print('🤖 Gemini Response: $responseText');

      // Try to extract JSON from the response
      Map<String, dynamic> analysisResult = _parseGeminiResponse(responseText);
      
      // Validate and ensure required fields exist
      analysisResult = _validateAndCleanResponse(analysisResult);
      
      return analysisResult;

    } catch (e) {
      print('❌ Error in Gemini analysis: $e');
      
      // Return a fallback response
      return {
        'category': 'Infrastructure',
        'confidence': 0.5,
        'description': 'Unable to analyze image automatically. Please select the appropriate category manually.',
        'title': 'Event Detected'
      };
    }
  }

  Map<String, dynamic> _parseGeminiResponse(String responseText) {
    try {
      // Try to find JSON in the response
      final jsonStart = responseText.indexOf('{');
      final jsonEnd = responseText.lastIndexOf('}') + 1;
      
      if (jsonStart != -1 && jsonEnd > jsonStart) {
        final jsonString = responseText.substring(jsonStart, jsonEnd);
        return json.decode(jsonString);
      }
      
      // If no JSON found, try to parse the response manually
      return _manualParseResponse(responseText);
      
    } catch (e) {
      print('❌ Error parsing Gemini response: $e');
      return _manualParseResponse(responseText);
    }
  }

  Map<String, dynamic> _manualParseResponse(String responseText) {
    // Fallback manual parsing if JSON parsing fails
    String category = 'Infrastructure';
    double confidence = 0.5;
    String description = responseText.length > 200 
        ? responseText.substring(0, 200) + '...' 
        : responseText;
    String title = 'Event Detected';

    // Simple keyword-based category detection as fallback
    final lowerResponse = responseText.toLowerCase();
    
    if (lowerResponse.contains('traffic') || 
        lowerResponse.contains('car') || 
        lowerResponse.contains('vehicle') ||
        lowerResponse.contains('road') ||
        lowerResponse.contains('congestion')) {
      category = 'Traffic';
      title = 'Traffic Issue Detected';
    } else if (lowerResponse.contains('flood') || 
               lowerResponse.contains('water') || 
               lowerResponse.contains('rain') ||
               lowerResponse.contains('storm') ||
               lowerResponse.contains('weather')) {
      category = 'Weather';
      title = 'Weather Event Detected';
    } else if (lowerResponse.contains('fire') || 
               lowerResponse.contains('emergency') || 
               lowerResponse.contains('accident') ||
               lowerResponse.contains('police') ||
               lowerResponse.contains('ambulance')) {
      category = 'Emergency';
      title = 'Emergency Detected';
    } else if (lowerResponse.contains('power') || 
               lowerResponse.contains('utility') || 
               lowerResponse.contains('electric') ||
               lowerResponse.contains('water') ||
               lowerResponse.contains('gas')) {
      category = 'Utilities';
      title = 'Utility Issue Detected';
    }

    return {
      'category': category,
      'confidence': confidence,
      'description': description,
      'title': title
    };
  }

  Map<String, dynamic> _validateAndCleanResponse(Map<String, dynamic> response) {
    final validCategories = ['Traffic', 'Emergency', 'Weather', 'Infrastructure', 'Utilities'];
    
    // Ensure category is valid
    if (!validCategories.contains(response['category'])) {
      response['category'] = 'Infrastructure';
    }
    
    // Ensure confidence is a valid number between 0 and 1
    if (response['confidence'] is! num || 
        response['confidence'] < 0 || 
        response['confidence'] > 1) {
      response['confidence'] = 0.5;
    }
    
    // Ensure description exists and is reasonable length
    if (response['description'] == null || response['description'].toString().isEmpty) {
      response['description'] = 'Event detected in the uploaded image.';
    } else if (response['description'].toString().length > 300) {
      response['description'] = response['description'].toString().substring(0, 300) + '...';
    }
    
    // Ensure title exists and is reasonable length
    if (response['title'] == null || response['title'].toString().isEmpty) {
      response['title'] = '${response['category']} Event';
    } else if (response['title'].toString().length > 50) {
      response['title'] = response['title'].toString().substring(0, 50) + '...';
    }
    
    return response;
  }

  // Additional method for getting severity suggestions based on image analysis
  Future<String> suggestSeverity(String imagePath, String category) async {
    try {
      final imageFile = File(imagePath);
      final imageBytes = await imageFile.readAsBytes();

      final severityPrompt = '''
Based on this image showing a $category event, suggest the severity level:
- Low: Minor issues, minimal impact
- Medium: Moderate issues, some impact on daily life  
- High: Serious issues, significant impact or danger

Respond with just one word: Low, Medium, or High
''';

      final content = [
        Content.multi([
          TextPart(severityPrompt),
          DataPart('image/jpeg', imageBytes),
        ])
      ];

      final response = await _model.generateContent(content);
      final severityText = response.text?.trim() ?? 'Medium';
      
      // Validate severity response
      if (['Low', 'Medium', 'High'].contains(severityText)) {
        return severityText;
      }
      
      return 'Medium'; // Default fallback
      
    } catch (e) {
      print('❌ Error getting severity suggestion: $e');
      return 'Medium';
    }
  }
}

// Provider for Gemini Service
final geminiServiceProvider = Provider<GeminiService>((ref) {
  return GeminiService();
});