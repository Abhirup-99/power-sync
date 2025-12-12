import 'package:firebase_storage/firebase_storage.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'dart:io';
import 'dart:convert';
import 'sync_cache_service.dart';
import '../utils/mutex.dart';

class DriveUploadService {
  final FirebaseStorage _storage = FirebaseStorage.instance;
  final FirebaseAuth _auth = FirebaseAuth.instance;
  final SyncCacheService _cacheService = SyncCacheService();
  final _mutex = Mutex();

  Future<bool> _checkStoragePermissions() async {
    try {
      // Permissions should already be granted via PermissionsScreen
      // Just check if we have any storage permission
      if (await Permission.manageExternalStorage.isGranted) {
        return true;
      }

      if (await Permission.audio.isGranted) {
        return true;
      }

      if (await Permission.storage.isGranted) {
        return true;
      }

      // Don't request here - permissions should be handled upfront
      print(
        'Storage permissions not granted. User should grant permissions first.',
      );
      return false;
    } catch (e) {
      print('Error checking permissions: $e');
      return false;
    }
  }

  String? _getUserEmail() {
    return _auth.currentUser?.email;
  }

  Future<bool> uploadFile(
    File file,
    String targetFolder, {
    bool checkCache = true,
  }) async {
    try {
      if (checkCache && await _cacheService.isFileSynced(file.path)) {
        return true;
      }

      final user = _auth.currentUser;
      if (user == null) {
        return false;
      }

      final userEmail = _getUserEmail();
      if (userEmail == null) {
        return false;
      }

      try {
        await user.getIdToken(false);
      } catch (e) {
        print('Error getting ID token: $e');
        return false;
      }

      final fileName = file.path.split('/').last;
      final filePath = 'chord/$userEmail/$targetFolder/$fileName';

      final ref = _storage.ref().child(filePath);
      try {
        final metadata = await ref.getMetadata();
        final fileSize = await file.length();

        if (metadata.size == fileSize) {
          await storeRecordingMetadata(
            fileName: fileName,
            storagePath: filePath,
            fileSize: fileSize,
          );

          await _cacheService.markAsSynced(file.path, targetFolder, filePath);
          return true;
        }
      } on FirebaseException catch (e) {
        const permissionErrors = {'unauthorized', 'permission-denied'};

        if (e.code == 'object-not-found') {
        } else if (permissionErrors.contains(e.code)) {
        } else {
          return false;
        }
      }

      final fileSize = await file.length();

      final uploadTask = ref.putFile(file);
      await uploadTask;

      await storeRecordingMetadata(
        fileName: fileName,
        storagePath: filePath,
        fileSize: fileSize,
      );

      await _cacheService.markAsSynced(file.path, targetFolder, filePath);

      return true;
    } catch (e) {
      print('Error uploading file: $e');
      return false;
    }
  }

  Future<void> uploadFilesFromDirectory(
    String directoryPath,
    String targetFolder, {
    Function(int uploaded, int total)? onProgress,
  }) async {
    try {
      if (!await _checkStoragePermissions()) {
        return;
      }

      final directory = Directory(directoryPath);
      if (!await directory.exists()) {
        return;
      }

      final unsyncedFiles = await _cacheService.getUnsyncedFiles(directoryPath);

      if (unsyncedFiles.isEmpty) {
        await _cacheService.updateLastSyncTime();
        return;
      }

      int successCount = 0;

      // Initial progress update
      onProgress?.call(0, unsyncedFiles.length);

      const int batchSize = 3;
      for (var i = 0; i < unsyncedFiles.length; i += batchSize) {
        final end = (i + batchSize < unsyncedFiles.length)
            ? i + batchSize
            : unsyncedFiles.length;
        final batch = unsyncedFiles.sublist(i, end);

        final results = await Future.wait(
          batch.map(
            (file) => uploadFile(file, targetFolder, checkCache: false),
          ),
        );

        for (final success in results) {
          if (success) {
            successCount++;
          }
        }

        onProgress?.call(successCount, unsyncedFiles.length);
      }

      await _cacheService.updateLastSyncTime();
    } catch (e) {
      print('Error uploading files from directory: $e');
    }
  }

  Future<SyncStatistics> getSyncStatistics() async {
    return await _cacheService.getSyncStatistics();
  }

  Future<void> clearSyncCache() async {
    await _cacheService.clearCache();
    await clearRecordingMetadata();
  }

  Future<void> clearRecordingMetadata() async {
    await _mutex.protect(() async {
      try {
        final prefs = await SharedPreferences.getInstance();
        await prefs.remove('recent_recordings');
      } catch (e) {
        print('Error clearing recording metadata: $e');
      }
    });
  }

  Future<int> getUnsyncedFilesCount(String directoryPath) async {
    try {
      final unsyncedFiles = await _cacheService.getUnsyncedFiles(directoryPath);
      return unsyncedFiles.length;
    } catch (e) {
      return 0;
    }
  }

  Future<List<Map<String, dynamic>>> getRecentRecordingLinks() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final recordingsJson = prefs.getString('recent_recordings') ?? '[]';
      final recordings = jsonDecode(recordingsJson) as List;

      return recordings.cast<Map<String, dynamic>>();
    } catch (e) {
      print('Error getting recent recording links: $e');
      return [];
    }
  }

  Future<void> storeRecordingMetadata({
    required String fileName,
    required String storagePath,
    required int fileSize,
  }) async {
    await _mutex.protect(() async {
      try {
        final prefs = await SharedPreferences.getInstance();
        final recordingsJson = prefs.getString('recent_recordings') ?? '[]';
        final recordings = jsonDecode(recordingsJson) as List;

        recordings.add({
          'fileName': fileName,
          'storagePath': storagePath,
          'fileSize': fileSize,
          'uploadedAt': DateTime.now().toIso8601String(),
          'syncedToApi': false,
        });

        if (recordings.length > 500) {
          recordings.removeRange(0, recordings.length - 500);
        }

        await prefs.setString('recent_recordings', jsonEncode(recordings));
      } catch (e) {
        print('Error storing recording metadata: $e');
      }
    });
  }

  Future<void> markRecordingsAsSyncedToApi(
    List<String> syncedStoragePaths,
  ) async {
    await _mutex.protect(() async {
      try {
        final prefs = await SharedPreferences.getInstance();
        final recordingsJson = prefs.getString('recent_recordings') ?? '[]';
        final recordings = jsonDecode(recordingsJson) as List;

        final syncedSet = syncedStoragePaths.toSet();

        for (var recording in recordings) {
          if (syncedSet.contains(recording['storagePath'])) {
            recording['syncedToApi'] = true;
          }
        }

        await prefs.setString('recent_recordings', jsonEncode(recordings));
      } catch (e) {
        print('Error marking recordings as synced: $e');
      }
    });
  }

  Future<List<Map<String, dynamic>>> getUnsyncedRecordings() async {
    try {
      final allRecordings = await getRecentRecordingLinks();
      final unsynced = allRecordings
          .where((recording) => recording['syncedToApi'] == false)
          .toList();

      return unsynced;
    } catch (e) {
      print('Error getting unsynced recordings: $e');
      return [];
    }
  }
}
