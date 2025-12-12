import 'dart:io';
import 'package:crypto/crypto.dart';
import 'package:sqflite/sqflite.dart';
import '../utils/mutex.dart';
import 'database_helper.dart';

class SyncCacheService {
  final _mutex = Mutex();
  final _dbHelper = DatabaseHelper();

  Future<Map<String, SyncedFileMetadata>> getSyncedFiles() async {
    try {
      final db = await _dbHelper.database;
      final List<Map<String, dynamic>> maps = await db.query('synced_files');

      final result = <String, SyncedFileMetadata>{};
      for (final map in maps) {
        final metadata = SyncedFileMetadata(
          filePath: map['file_path'],
          fileName: map['file_name'],
          targetFolder: map['target_folder'],
          driveFileId: map['drive_file_id'],
          fileSize: map['file_size'],
          fileHash: map['file_hash'],
          syncedAt: DateTime.parse(map['synced_at']),
          lastModified: DateTime.parse(map['last_modified']),
        );
        result[metadata.filePath] = metadata;
      }

      return result;
    } catch (e) {
      return {};
    }
  }

  Future<void> markAsSynced(
    String filePath,
    String targetFolder,
    String driveFileId,
  ) async {
    await _mutex.protect(() async {
      try {
        final file = File(filePath);
        final db = await _dbHelper.database;

        await db.insert('synced_files', {
          'file_path': filePath,
          'file_name': file.path.split('/').last,
          'target_folder': targetFolder,
          'drive_file_id': driveFileId,
          'file_size': await file.length(),
          'file_hash': await _calculateFileHash(file),
          'synced_at': DateTime.now().toIso8601String(),
          'last_modified': (await file.lastModified()).toIso8601String(),
        }, conflictAlgorithm: ConflictAlgorithm.replace);
      } catch (e) {}
    });
  }

  Future<bool> isFileSynced(String filePath) async {
    try {
      final db = await _dbHelper.database;
      final List<Map<String, dynamic>> maps = await db.query(
        'synced_files',
        where: 'file_path = ?',
        whereArgs: [filePath],
        limit: 1,
      );

      if (maps.isEmpty) {
        return false;
      }

      final file = File(filePath);
      if (!await file.exists()) {
        await removeFromCache(filePath);
        return false;
      }

      final cachedModified = DateTime.parse(maps.first['last_modified']);
      final cachedSize = maps.first['file_size'] as int;
      final currentModified = await file.lastModified();
      final currentSize = await file.length();

      if (currentModified.isAfter(cachedModified) ||
          currentSize != cachedSize) {
        return false;
      }

      return true;
    } catch (e) {
      return false;
    }
  }

  Future<List<File>> getUnsyncedFiles(String directoryPath) async {
    try {
      final directory = Directory(directoryPath);
      if (!await directory.exists()) {
        return [];
      }

      final unsyncedFiles = <File>[];

      await for (final entity in directory.list()) {
        if (entity is File) {
          if (!await isFileSynced(entity.path)) {
            unsyncedFiles.add(entity);
          }
        }
      }

      return unsyncedFiles;
    } catch (e) {
      return [];
    }
  }

  Future<void> removeFromCache(String filePath) async {
    try {
      final db = await _dbHelper.database;
      await db.delete(
        'synced_files',
        where: 'file_path = ?',
        whereArgs: [filePath],
      );
    } catch (e) {}
  }

  Future<void> clearCache() async {
    try {
      await _dbHelper.clearDatabase();
    } catch (e) {}
  }

  Future<DateTime?> getLastSyncTime() async {
    try {
      final db = await _dbHelper.database;
      final List<Map<String, dynamic>> maps = await db.query(
        'sync_metadata',
        where: 'key = ?',
        whereArgs: ['last_sync_timestamp'],
      );

      if (maps.isEmpty) return null;

      final timestamp = int.tryParse(maps.first['value']);
      if (timestamp == null) return null;

      return DateTime.fromMillisecondsSinceEpoch(timestamp);
    } catch (e) {
      return null;
    }
  }

  Future<void> updateLastSyncTime() async {
    try {
      final db = await _dbHelper.database;
      await db.insert('sync_metadata', {
        'key': 'last_sync_timestamp',
        'value': DateTime.now().millisecondsSinceEpoch.toString(),
      }, conflictAlgorithm: ConflictAlgorithm.replace);
    } catch (e) {}
  }

  Future<SyncStatistics> getSyncStatistics() async {
    try {
      final db = await _dbHelper.database;
      final lastSync = await getLastSyncTime();

      // Get total count and size using SQL aggregation
      final countResult = await db.rawQuery(
        'SELECT COUNT(*) as count, COALESCE(SUM(file_size), 0) as total_size FROM synced_files',
      );

      final totalCount = countResult.first['count'] as int;
      final totalSize = countResult.first['total_size'] as int;

      // Get distinct folders
      final foldersResult = await db.rawQuery(
        'SELECT DISTINCT target_folder FROM synced_files',
      );
      final folders = foldersResult
          .map((row) => row['target_folder'] as String)
          .toList();

      return SyncStatistics(
        totalSyncedFiles: totalCount,
        lastSyncTime: lastSync,
        syncedFolders: folders,
        totalSyncedSize: totalSize,
      );
    } catch (e) {
      return SyncStatistics(
        totalSyncedFiles: 0,
        lastSyncTime: null,
        syncedFolders: [],
        totalSyncedSize: 0,
      );
    }
  }

  Future<String> _calculateFileHash(File file) async {
    try {
      final stream = file.openRead();
      final digest = await md5.bind(stream).first;
      return digest.toString();
    } catch (e) {
      return '';
    }
  }
}

class SyncedFileMetadata {
  final String filePath;
  final String fileName;
  final String targetFolder;
  final String driveFileId;
  final int fileSize;
  final String fileHash;
  final DateTime syncedAt;
  final DateTime lastModified;

  SyncedFileMetadata({
    required this.filePath,
    required this.fileName,
    required this.targetFolder,
    required this.driveFileId,
    required this.fileSize,
    required this.fileHash,
    required this.syncedAt,
    required this.lastModified,
  });

  Map<String, dynamic> toJson() {
    return {
      'filePath': filePath,
      'fileName': fileName,
      'targetFolder': targetFolder,
      'driveFileId': driveFileId,
      'fileSize': fileSize,
      'fileHash': fileHash,
      'syncedAt': syncedAt.toIso8601String(),
      'lastModified': lastModified.toIso8601String(),
    };
  }

  factory SyncedFileMetadata.fromJson(Map<String, dynamic> json) {
    return SyncedFileMetadata(
      filePath: json['filePath'],
      fileName: json['fileName'],
      targetFolder: json['targetFolder'],
      driveFileId: json['driveFileId'],
      fileSize: json['fileSize'],
      fileHash: json['fileHash'] ?? '',
      syncedAt: DateTime.parse(json['syncedAt']),
      lastModified: DateTime.parse(json['lastModified']),
    );
  }
}

class SyncStatistics {
  final int totalSyncedFiles;
  final DateTime? lastSyncTime;
  final List<String> syncedFolders;
  final int totalSyncedSize;

  SyncStatistics({
    required this.totalSyncedFiles,
    required this.lastSyncTime,
    required this.syncedFolders,
    required this.totalSyncedSize,
  });

  String get formattedSize {
    if (totalSyncedSize < 1024) {
      return '$totalSyncedSize B';
    } else if (totalSyncedSize < 1024 * 1024) {
      return '${(totalSyncedSize / 1024).toStringAsFixed(2)} KB';
    } else if (totalSyncedSize < 1024 * 1024 * 1024) {
      return '${(totalSyncedSize / (1024 * 1024)).toStringAsFixed(2)} MB';
    } else {
      return '${(totalSyncedSize / (1024 * 1024 * 1024)).toStringAsFixed(2)} GB';
    }
  }
}
