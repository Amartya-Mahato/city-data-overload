import 'dart:io';

import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_storage/firebase_storage.dart';
import 'package:geolocator/geolocator.dart';
import 'package:google_generative_ai/google_generative_ai.dart';

class FirebaseService {
  final FirebaseStorage _storage = FirebaseStorage.instance;
  final FirebaseFirestore _firestore = FirebaseFirestore.instance;
  final GenerativeModel _gemini =
      GenerativeModel(model: 'gemini-pro-vision', apiKey: 'YOUR_API_KEY');

  Future<void> uploadReport(File image, Position position) async {
    final String imagePath =
        'reports/${DateTime.now().millisecondsSinceEpoch}.jpg';
    final Reference storageRef = _storage.ref().child(imagePath);

    // Upload the image to Firebase Storage
    await storageRef.putFile(image);
    final String downloadUrl = await storageRef.getDownloadURL();

    // Get a description of the image from Gemini
    final response = await _gemini.generateContent([
      Content.multi([
        TextPart('Describe this image'),
        DataPart('image/jpeg', image.readAsBytesSync())
      ])
    ]);

    // Save the report to Firestore
    await _firestore.collection('reports').add({
      'imageUrl': downloadUrl,
      'position': GeoPoint(position.latitude, position.longitude),
      'description': response.text,
      'timestamp': FieldValue.serverTimestamp(),
    });
  }
}
