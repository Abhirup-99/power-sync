import 'package:firebase_auth/firebase_auth.dart';
import 'package:google_sign_in/google_sign_in.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:http/http.dart' as http;
import '../config/environment.dart';

class AuthService {
  final FirebaseAuth _auth = FirebaseAuth.instance;
  final GoogleSignIn _googleSignIn = GoogleSignIn(
    scopes: ['email', 'https://www.googleapis.com/auth/drive.file'],
  );

  User? get currentUser => _auth.currentUser;

  Stream<User?> get authStateChanges => _auth.authStateChanges();

  Future<UserCredential?> signInWithGoogle() async {
    try {
      await _googleSignIn.signOut();

      final GoogleSignInAccount? googleUser = await _googleSignIn.signIn();

      if (googleUser == null) {
        return null;
      }

      if (!_isValidEmailDomain(googleUser.email)) {
        await _googleSignIn.signOut();
        throw Exception('Only @even.in email addresses are allowed');
      }

      final GoogleSignInAuthentication googleAuth =
          await googleUser.authentication;

      final prefs = await SharedPreferences.getInstance();
      await prefs.setString('google_user_email', googleUser.email);

      final credential = GoogleAuthProvider.credential(
        accessToken: googleAuth.accessToken,
        idToken: googleAuth.idToken,
      );

      final userCredential = await _auth.signInWithCredential(credential);

      if (userCredential.user != null) {
        await _upsertUser(userCredential.user!);
      }

      return userCredential;
    } catch (e) {
      rethrow;
    }
  }

  bool _isValidEmailDomain(String email) {
    return email.toLowerCase().endsWith('@even.in');
  }

  Future<void> _upsertUser(User user) async {
    try {
      final apiUrl = AppEnvironment.config.apiBaseUrl;
      final idToken = await user.getIdToken();

      await http.post(
        Uri.parse('$apiUrl/app-internal/upsert-user'),
        headers: {'Authorization': 'Bearer $idToken'},
      );
    } catch (e) {
      // Silent failure - upsert is best-effort
    }
  }

  Future<void> signOut() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove('google_user_email');

    await Future.wait([_auth.signOut(), _googleSignIn.signOut()]);
  }
}
