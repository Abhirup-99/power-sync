import 'package:flutter/material.dart';
import 'package:file_picker/file_picker.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'dart:async';
import '../../services/auth_service.dart';
import '../../services/foreground_sync_service.dart';
import '../../services/drive_upload_service.dart';
import '../../widgets/onboarding/user_profile_card.dart';
import '../../widgets/onboarding/folder_selection_card.dart';

class OnboardingScreen extends StatefulWidget {
  const OnboardingScreen({super.key});

  static const routeName = '/onboarding';

  @override
  State<OnboardingScreen> createState() => _OnboardingScreenState();
}

class _OnboardingScreenState extends State<OnboardingScreen> {
  final AuthService _authService = AuthService();
  final ForegroundSyncService _syncService = ForegroundSyncService();
  final DriveUploadService _driveService = DriveUploadService();

  String? _folderPath;
  String? _userName;
  bool _isLoading = true;
  bool _isSyncing = false;
  int _uploadedCount = 0;
  int _totalCount = 0;
  bool _autoSyncActive = false;
  DateTime? _lastSyncTime;
  int _pendingFilesCount = 0;

  Timer? _statusUpdateTimer;

  @override
  void initState() {
    super.initState();
    _checkInitialStatus();
    _startStatusUpdateTimer();
  }

  @override
  void dispose() {
    _statusUpdateTimer?.cancel();
    super.dispose();
  }

  void _startStatusUpdateTimer() {
    _statusUpdateTimer = Timer.periodic(const Duration(seconds: 10), (timer) {
      if (mounted && _autoSyncActive) {
        _updatePendingCount();
      }
    });
  }

  Future<void> _updatePendingCount() async {
    if (_folderPath == null) return;

    try {
      final pendingCount = await _driveService.getUnsyncedFilesCount(
        _folderPath!,
      );

      if (mounted && pendingCount != _pendingFilesCount) {
        setState(() {
          _pendingFilesCount = pendingCount;
        });
      }
    } catch (e) {
      // Silent failure for background updates
    }
  }

  Future<void> _checkInitialStatus() async {
    if (!mounted) return;

    setState(() {
      _isLoading = true;
    });

    try {
      final user = _authService.currentUser;
      if (user != null) {
        _userName = user.displayName ?? user.email;

        final prefs = await SharedPreferences.getInstance();
        final storedPath = prefs.getString('sync_folder_path');
        if (storedPath != null) {
          _folderPath = storedPath;
        }

        final syncActive = await _syncService.isRunning();
        _autoSyncActive = syncActive;

        _lastSyncTime = await _syncService.getLastSyncTime();

        if (_folderPath != null) {
          _pendingFilesCount = await _driveService.getUnsyncedFilesCount(
            _folderPath!,
          );
        }
      }
    } catch (e) {
    } finally {
      if (mounted) {
        setState(() {
          _isLoading = false;
        });
      }
    }
  }

  Future<void> _pickFolder() async {
    try {
      String? selectedDirectory = await FilePicker.platform.getDirectoryPath();

      if (selectedDirectory != null && mounted) {
        setState(() {
          _folderPath = selectedDirectory;
        });

        final prefs = await SharedPreferences.getInstance();
        await prefs.setString('sync_folder_path', selectedDirectory);

        _refreshStatus();
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Error selecting folder: ${e.toString()}'),
            backgroundColor: Colors.red,
          ),
        );
      }
    }
  }

  Future<void> _performSync({
    bool startBackgroundService = false,
    bool isForceSync = false,
  }) async {
    if (_folderPath == null) return;

    if (mounted) {
      setState(() {
        _isSyncing = true;
      });
    }

    try {
      if (isForceSync) {
        await _driveService.clearSyncCache();
      }

      // Ensure folder path is saved
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString('sync_folder_path', _folderPath!);

      await _driveService.uploadFilesFromDirectory(
        _folderPath!,
        'Recordings',
        onProgress: (uploaded, total) {
          if (mounted) {
            setState(() {
              _uploadedCount = uploaded;
              _totalCount = total;
            });
          }
        },
      );

      final unsyncedRecordings = await _driveService.getUnsyncedRecordings();
      if (unsyncedRecordings.isNotEmpty) {
        await _syncService.syncToApi();
      }

      await prefs.setString('last_sync_time', DateTime.now().toIso8601String());

      bool syncActive = await _syncService.isRunning();

      if (startBackgroundService) {
        print('Attempting to start background sync service...');
        await _syncService.startSync();
        syncActive = await _syncService.isRunning();
        print('Background sync service running status: $syncActive');
      }

      // Wait a bit for cache to be fully updated
      await Future.delayed(const Duration(milliseconds: 500));

      final lastSync = await _syncService.getLastSyncTime();
      final pendingCount = await _driveService.getUnsyncedFilesCount(
        _folderPath!,
      );

      if (mounted) {
        setState(() {
          _lastSyncTime = lastSync;
          _autoSyncActive = syncActive;
          _pendingFilesCount = pendingCount;
        });

        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              isForceSync
                  ? 'Force sync completed!'
                  : (startBackgroundService
                        ? (syncActive
                              ? 'Sync completed! Background sync activated.'
                              : 'Sync completed! Note: Background sync could not be activated.')
                        : 'Sync completed!'),
            ),
            backgroundColor: (!startBackgroundService || syncActive)
                ? Colors.green
                : Colors.orange,
            duration: const Duration(seconds: 2),
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Sync error: ${e.toString()}'),
            backgroundColor: Colors.red,
          ),
        );
      }
    } finally {
      if (mounted) {
        setState(() {
          _isSyncing = false;
        });
      }
    }
  }

  Future<void> _refreshStatus() => _performSync(startBackgroundService: false);

  Future<void> _signOut() async {
    try {
      await _authService.signOut();

      await _syncService.stopSync();

      final prefs = await SharedPreferences.getInstance();
      await prefs.remove('sync_folder_path');

      if (mounted) {
        setState(() {
          _userName = null;
          _folderPath = null;
          _autoSyncActive = false;
        });

        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Signed out successfully'),
            backgroundColor: Colors.green,
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Error signing out: ${e.toString()}'),
            backgroundColor: Colors.red,
          ),
        );
      }
    }
  }

  Future<void> _syncFiles() async {
    if (_folderPath == null) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Please select a folder first'),
            backgroundColor: Colors.orange,
          ),
        );
      }
      return;
    }

    await _performSync(startBackgroundService: true);
  }

  Future<void> _forceSync() async {
    if (_folderPath == null) return;

    final confirm = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Force Sync?'),
        content: const Text(
          'This will clear the sync history and re-upload all files. This might take a while. Are you sure?',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Force Sync'),
          ),
        ],
      ),
    );

    if (confirm != true) return;

    await _performSync(isForceSync: true);
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final user = _authService.currentUser;

    return Scaffold(
      backgroundColor: Colors.white,
      body: SafeArea(
        child: _isLoading
            ? Center(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    const CircularProgressIndicator(),
                    const SizedBox(height: 16),
                    Text(
                      'Checking signin status...',
                      style: theme.textTheme.bodyLarge?.copyWith(
                        color: Colors.grey[600],
                      ),
                    ),
                  ],
                ),
              )
            : SingleChildScrollView(
                padding: const EdgeInsets.all(24.0),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    const SizedBox(height: 48),
                    if (user != null) ...[
                      UserProfileCard(userName: _userName, onSignOut: _signOut),
                      const SizedBox(height: 24),
                      FolderSelectionCard(
                        folderPath: _folderPath,
                        isSyncing: _isSyncing,
                        autoSyncActive: _autoSyncActive,
                        lastSyncTime: _lastSyncTime,
                        pendingFilesCount: _pendingFilesCount,
                        onPickFolder: _pickFolder,
                        onSyncNow: _syncFiles,
                        onForceSync: _forceSync,
                        onRefreshStatus: _refreshStatus,
                        uploadedCount: _uploadedCount,
                        totalCount: _totalCount,
                      ),
                    ],
                  ],
                ),
              ),
      ),
    );
  }
}
