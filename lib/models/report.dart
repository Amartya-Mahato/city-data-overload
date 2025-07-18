import 'package:cloud_firestore/cloud_firestore.dart';

class Report {
  final String id;
  final String imageUrl;
  final GeoPoint position;
  final String description;
  final Timestamp timestamp;

  Report({
    required this.id,
    required this.imageUrl,
    required this.position,
    required this.description,
    required this.timestamp,
  });

  factory Report.fromFirestore(DocumentSnapshot doc) {
    final data = doc.data() as Map<String, dynamic>;
    return Report(
      id: doc.id,
      imageUrl: data['imageUrl'] ?? '',
      position: data['position'] ?? const GeoPoint(0, 0),
      description: data['description'] ?? '',
      timestamp: data['timestamp'] ?? Timestamp.now(),
    );
  }
}
