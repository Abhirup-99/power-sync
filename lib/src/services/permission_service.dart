import 'package:permission_handler/permission_handler.dart';

/// Service to handle all app permissions in one place
class PermissionService {
  /// Request all necessary permissions for the app
  /// Returns true if all critical permissions are granted
  Future<bool> requestAllPermissions() async {
    // Request all permissions in parallel
    final Map<Permission, PermissionStatus> statuses = await [
      Permission.phone, // For call logs
      Permission.notification, // For notifications
      Permission.storage, // For file access
      Permission.manageExternalStorage, // For full file access on Android 11+
      Permission.ignoreBatteryOptimizations, // For background sync
    ].request();

    // Check if critical permissions are granted
    final phoneGranted = statuses[Permission.phone]?.isGranted ?? false;
    final storageGranted = statuses[Permission.storage]?.isGranted ?? false;
    final manageStorageGranted = statuses[Permission.manageExternalStorage]?.isGranted ?? false;

    // We need either storage or manageExternalStorage
    final hasStoragePermission = storageGranted || manageStorageGranted;

    return phoneGranted && hasStoragePermission;
  }

  /// Check if all critical permissions are already granted
  Future<bool> hasAllPermissions() async {
    final phoneGranted = await Permission.phone.isGranted;
    final storageGranted = await Permission.storage.isGranted;
    final manageStorageGranted = await Permission.manageExternalStorage.isGranted;

    final hasStoragePermission = storageGranted || manageStorageGranted;

    return phoneGranted && hasStoragePermission;
  }

  /// Get list of denied permissions
  Future<List<String>> getDeniedPermissions() async {
    final List<String> denied = [];

    if (!await Permission.phone.isGranted) {
      denied.add('Phone & Call Logs');
    }

    final storageGranted = await Permission.storage.isGranted;
    final manageStorageGranted = await Permission.manageExternalStorage.isGranted;
    
    if (!storageGranted && !manageStorageGranted) {
      denied.add('Storage & Files');
    }

    if (!await Permission.notification.isGranted) {
      denied.add('Notifications');
    }

    if (!await Permission.ignoreBatteryOptimizations.isGranted) {
      denied.add('Battery Optimization');
    }

    return denied;
  }
}
