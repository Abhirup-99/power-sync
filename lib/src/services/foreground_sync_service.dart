import 'dart:io';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:permission_handler/permission_handler.dart';
import 'drive_upload_service.dart';
import 'api_service.dart';
import 'debug_log_service.dart';

/// ForegroundSyncService - controls WorkManager-based background sync
///
/// The background sync logic runs via Android WorkManager (SyncWorker.kt), which:
/// - Runs periodically every 15 minutes (Android minimum)
/// - Survives app kills and device reboots
/// - Is battery-friendly and respects Doze mode
/// - Uses native Firebase SDK directly
/// - Shares the same SQLite database with Flutter
///
/// This Flutter service provides:
/// - Start/stop controls for the WorkManager sync
/// - Manual sync functionality for UI triggers
/// - Status reporting for the UI
class ForegroundSyncService {
  static ForegroundSyncService? _instance;
  final DebugLogService _log = DebugLogService();

  // Sync control channel - WorkManager based
  static const _syncChannel = MethodChannel('com.even.chord/sync');

  factory ForegroundSyncService() {
    _instance ??= ForegroundSyncService._internal();
    return _instance!;
  }

  ForegroundSyncService._internal();

  /// Start the WorkManager sync
  Future<bool> startSync() async {
    try {
      // Check notification permission (required for Android 13+)
      final notificationStatus = await Permission.notification.status;
      if (!notificationStatus.isGranted) {
        _log.log(
          'ForegroundSyncService',
          'Requesting notification permission...',
          level: 'WARN',
        );
        final result = await Permission.notification.request();
        if (!result.isGranted) {
          _log.log(
            'ForegroundSyncService',
            'Notification permission denied',
            level: 'ERROR',
          );
          return false;
        }
      }

      _log.log('ForegroundSyncService', 'Starting WorkManager sync...');

      // Start WorkManager sync via MethodChannel
      if (Platform.isAndroid) {
        try {
          final result = await _syncChannel.invokeMethod('startSync');
          _log.log(
            'ForegroundSyncService',
            'WorkManager sync started: $result',
          );
          return result == true;
        } catch (e) {
          _log.log(
            'ForegroundSyncService',
            'Failed to start sync: $e',
            level: 'ERROR',
          );
          return false;
        }
      }

      return false;
    } catch (e, stackTrace) {
      _log.log(
        'ForegroundSyncService',
        'Failed to start sync: $e\n$stackTrace',
        level: 'ERROR',
      );
      return false;
    }
  }

  /// Stop the WorkManager sync
  Future<void> stopSync() async {
    try {
      if (Platform.isAndroid) {
        await _syncChannel.invokeMethod('stopSync');
        _log.log('ForegroundSyncService', 'WorkManager sync stopped');
      }

      final prefs = await SharedPreferences.getInstance();
      await prefs.setBool('sync_active', false);
    } catch (e) {
      _log.log(
        'ForegroundSyncService',
        'Failed to stop sync: $e',
        level: 'ERROR',
      );
    }
  }

  /// Check if sync is active
  Future<bool> isRunning() async {
    try {
      if (Platform.isAndroid) {
        final result = await _syncChannel.invokeMethod('isSyncActive');
        return result == true;
      }
      return false;
    } catch (e) {
      _log.log(
        'ForegroundSyncService',
        'Failed to check running status: $e',
        level: 'ERROR',
      );
      return false;
    }
  }

  /// Get sync status
  Future<Map<String, dynamic>> getSyncStatus() async {
    try {
      if (Platform.isAndroid) {
        final result = await _syncChannel.invokeMethod('getSyncStatus');
        if (result != null) {
          return Map<String, dynamic>.from(result);
        }
      }
      return {'syncActive': false, 'lastSyncTime': null};
    } catch (e) {
      _log.log(
        'ForegroundSyncService',
        'Failed to get sync status: $e',
        level: 'ERROR',
      );
      return {'syncActive': false, 'lastSyncTime': null};
    }
  }

  /// Trigger an immediate sync via WorkManager
  Future<bool> triggerImmediateSync() async {
    try {
      if (Platform.isAndroid) {
        final result = await _syncChannel.invokeMethod('triggerImmediateSync');
        return result == true;
      }
      return false;
    } catch (e) {
      _log.log(
        'ForegroundSyncService',
        'Failed to trigger immediate sync: $e',
        level: 'ERROR',
      );
      return false;
    }
  }

  // Kept for backward compatibility with getNativeSyncStatus calls
  Future<Map<String, dynamic>> getNativeSyncStatus() async {
    return getSyncStatus();
  }

  Future<DateTime?> getLastSyncTime() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final timeString = prefs.getString('last_sync_time');
      if (timeString != null) {
        return DateTime.parse(timeString);
      }
      return null;
    } catch (e) {
      return null;
    }
  }

  /// Manual sync to API (runs in Flutter)
  Future<bool> syncToApi() async {
    try {
      final driveService = DriveUploadService();
      final apiService = ApiService();

      final unsyncedRecordings = await driveService.getUnsyncedRecordings();

      if (unsyncedRecordings.isEmpty) {
        return true;
      }

      final storagePaths = unsyncedRecordings
          .map((r) => r['storagePath'] as String)
          .toList();

      final success = await apiService.uploadRecordingsInternal(storagePaths);

      if (success) {
        await driveService.markRecordingsAsSyncedToApi(storagePaths);
      }

      return success;
    } catch (e) {
      return false;
    }
  }

  Future<Map<String, dynamic>> getApiSyncStats() async {
    try {
      final driveService = DriveUploadService();

      final allRecordings = await driveService.getRecentRecordingLinks();
      final unsyncedRecordings = await driveService.getUnsyncedRecordings();

      return {
        'totalRecordings': allRecordings.length,
        'unsyncedRecordings': unsyncedRecordings.length,
        'syncedRecordings': allRecordings.length - unsyncedRecordings.length,
      };
    } catch (e) {
      return {
        'totalRecordings': 0,
        'unsyncedRecordings': 0,
        'syncedRecordings': 0,
      };
    }
  }

  /// Force sync - runs in Flutter (for manual trigger from UI)
  Future<void> forceSync({
    Function(int uploaded, int total)? onProgress,
  }) async {
    try {
      final driveService = DriveUploadService();
      final apiService = ApiService();

      // 1. Clear cache to force re-upload/re-sync
      await driveService.clearSyncCache();

      // 2. Get folder path
      final prefs = await SharedPreferences.getInstance();
      final folderPath = prefs.getString('sync_folder_path');

      if (folderPath == null) {
        return;
      }

      // 3. Upload files to Storage
      if (await Directory(folderPath).exists()) {
        await driveService.uploadFilesFromDirectory(
          folderPath,
          'Recordings',
          onProgress: onProgress,
        );
      }

      // 4. Sync to API
      final unsyncedRecordings = await driveService.getUnsyncedRecordings();

      if (unsyncedRecordings.isNotEmpty) {
        final storagePaths = unsyncedRecordings
            .map((r) => r['storagePath'] as String)
            .toList();

        final apiSuccess = await apiService.uploadRecordingsInternal(
          storagePaths,
        );

        if (apiSuccess) {
          await driveService.markRecordingsAsSyncedToApi(storagePaths);
        }
      }

      // 5. Update last sync time
      await prefs.setString('last_sync_time', DateTime.now().toIso8601String());
    } catch (e) {
      _log.log(
        'ForegroundSyncService',
        'Force sync failed: $e',
        level: 'ERROR',
      );
      rethrow;
    }
  }

  /// Clear sync cache - delegates to DriveUploadService
  Future<void> clearSyncCache() async {
    final driveService = DriveUploadService();
    await driveService.clearSyncCache();
  }
}
