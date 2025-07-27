import 'dart:io';
import 'package:firebase_storage/firebase_storage.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:path/path.dart' as path;

class StorageService {
  static final FirebaseStorage _storage = FirebaseStorage.instance;
  
  /// Uploads an image file to Firebase Storage and returns the download URL
  static Future<String> uploadReportImage(File imageFile) async {
    try {
      final user = FirebaseAuth.instance.currentUser;
      if (user == null) {
        throw Exception('User not authenticated');
      }

      // Generate unique filename
      final String fileName = '${DateTime.now().millisecondsSinceEpoch}_${path.basename(imageFile.path)}';
      final String filePath = 'reports/${user.uid}/$fileName';

      print('🔄 Uploading image to Firebase Storage: $filePath');

      // Create reference to Firebase Storage
      final Reference storageRef = _storage.ref().child(filePath);

      // Upload file
      final UploadTask uploadTask = storageRef.putFile(imageFile);
      
      // Wait for upload to complete
      final TaskSnapshot snapshot = await uploadTask;
      
      // Get download URL
      final String downloadURL = await snapshot.ref.getDownloadURL();
      
      print('✅ Image uploaded successfully: $downloadURL');
      return downloadURL;
      
    } catch (error) {
      print('❌ Error uploading image: $error');
      rethrow;
    }
  }

  /// Deletes an image from Firebase Storage using its URL
  static Future<void> deleteImage(String imageUrl) async {
    try {
      final Reference imageRef = _storage.refFromURL(imageUrl);
      await imageRef.delete();
      print('✅ Image deleted successfully: $imageUrl');
    } catch (error) {
      print('❌ Error deleting image: $error');
      // Don't rethrow as this is not critical for app functionality
    }
  }

  /// Gets the file size in bytes for an image URL
  static Future<int?> getImageSize(String imageUrl) async {
    try {
      final Reference imageRef = _storage.refFromURL(imageUrl);
      final FullMetadata metadata = await imageRef.getMetadata();
      return metadata.size;
    } catch (error) {
      print('❌ Error getting image size: $error');
      return null;
    }
  }
} 