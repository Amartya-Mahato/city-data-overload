import 'dart:convert';
import 'package:http/http.dart' as http;

void main() async {
  await testNdmaApi();
}

Future<void> testNdmaApi() async {
  const String baseUrl = 'https://sachet.ndma.gov.in/cap_public_website/FetchLocationWiseAlerts';
  
  // Test coordinates for Bangalore
  final testCases = [
    {'lat': '12.888615', 'long': '77.610481', 'radius': '5'},
    {'lat': '12.888615', 'long': '77.610481', 'radius': '10'},
    {'lat': '12.888615', 'long': '77.610481', 'radius': '50'},
    {'lat': '12.888615', 'lng': '77.610481', 'radius': '10'}, // Try 'lng' instead of 'long'
  ];
  
  for (int i = 0; i < testCases.length; i++) {
    final testData = testCases[i];
    print('\n--- Test Case ${i + 1}: $testData ---');
    
    try {
      // Test POST request (current implementation)
      final postResponse = await http.post(
        Uri.parse(baseUrl),
        headers: {
          'Accept': 'application/json',
          'Content-Type': 'application/x-www-form-urlencoded',
          'User-Agent': 'CityPulse/1.0',
        },
        body: testData,
      ).timeout(const Duration(seconds: 10));
      
      print('POST Response Status: ${postResponse.statusCode}');
      print('POST Response Headers: ${postResponse.headers}');
      print('POST Response Body: ${postResponse.body.length > 500 ? postResponse.body.substring(0, 500) + "..." : postResponse.body}');
      
      // Test GET request with query parameters
      final queryParams = testData.entries.map((e) => '${e.key}=${e.value}').join('&');
      final getUrl = '$baseUrl?$queryParams';
      
      final getResponse = await http.get(
        Uri.parse(getUrl),
        headers: {
          'Accept': 'application/json',
          'User-Agent': 'CityPulse/1.0',
        },
      ).timeout(const Duration(seconds: 10));
      
      print('GET Response Status: ${getResponse.statusCode}');
      print('GET Response Body: ${getResponse.body.length > 500 ? getResponse.body.substring(0, 500) + "..." : getResponse.body}');
      
    } catch (e) {
      print('Error in test case ${i + 1}: $e');
    }
    
    // Wait between requests to be respectful to the API
    await Future.delayed(const Duration(seconds: 2));
  }
  
  // Test if the service is reachable at all
  print('\n--- Testing Service Availability ---');
  try {
    final headResponse = await http.head(
      Uri.parse('https://sachet.ndma.gov.in'),
      headers: {'User-Agent': 'CityPulse/1.0'},
    ).timeout(const Duration(seconds: 10));
    
    print('NDMA Main Site Status: ${headResponse.statusCode}');
    print('NDMA Main Site Headers: ${headResponse.headers}');
  } catch (e) {
    print('Error accessing NDMA main site: $e');
  }
}
