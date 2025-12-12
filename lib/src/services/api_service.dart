import 'dart:convert';
import 'package:http/http.dart' as http;
import 'package:firebase_auth/firebase_auth.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../config/environment.dart';

class ApiService {
  final FirebaseAuth _auth = FirebaseAuth.instance;
  
  String get _baseUrl => AppEnvironment.config.apiBaseUrl;
  
  String? _getUserEmail() {
    return _auth.currentUser?.email;
  }

  String? _getUserId() {
    return _auth.currentUser?.uid;
  }

  Future<String?> _getIdToken() async {
    try {
      final user = _auth.currentUser;
      if (user != null) {
        return await user.getIdToken();
      }
      return null;
    } catch (e) {
      return null;
    }
  }

  Future<bool> uploadRecordingsInternal(List<String> recordingPaths) async {
    try {
      final userEmail = _getUserEmail();
      final userId = _getUserId();
      
      if (userEmail == null || userId == null) {
        return false;
      }

      final idToken = await _getIdToken();
      if (idToken == null) {
        return false;
      }

      bool allSuccess = true;
      const int batchSize = 50;

      for (var i = 0; i < recordingPaths.length; i += batchSize) {
        final end = (i + batchSize < recordingPaths.length) ? i + batchSize : recordingPaths.length;
        final batch = recordingPaths.sublist(i, end);
        final recordings = batch.map((path) => {'file_path': path}).toList();

        final response = await http.post(
          Uri.parse('$_baseUrl/app-internal/upload-recording'),
          headers: {
            'Content-Type': 'application/json',
            'Authorization': 'Bearer $idToken',
          },
          body: jsonEncode({
            'recordings': recordings,
          }),
        );

        if (response.statusCode != 200 && response.statusCode != 201) {
          allSuccess = false;
        }
      }

      return allSuccess;
    } catch (e) {
      return false;
    }
  }
}
