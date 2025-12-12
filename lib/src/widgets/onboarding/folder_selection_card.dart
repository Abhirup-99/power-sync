import 'package:flutter/material.dart';
import 'folder_status_display.dart';

class FolderSelectionCard extends StatelessWidget {
  final String? folderPath;
  final bool isSyncing;
  final bool autoSyncActive;
  final DateTime? lastSyncTime;
  final int pendingFilesCount;
  final VoidCallback onPickFolder;
  final VoidCallback onSyncNow;
  final VoidCallback onForceSync;
  final VoidCallback onRefreshStatus;
  final int uploadedCount;
  final int totalCount;

  const FolderSelectionCard({
    super.key,
    required this.folderPath,
    required this.isSyncing,
    required this.autoSyncActive,
    required this.lastSyncTime,
    required this.pendingFilesCount,
    required this.onPickFolder,
    required this.onSyncNow,
    required this.onForceSync,
    required this.onRefreshStatus,
    this.uploadedCount = 0,
    this.totalCount = 0,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              'Folder Selection',
              style: theme.textTheme.titleMedium?.copyWith(
                fontWeight: FontWeight.bold,
                color: Colors.grey[900],
              ),
            ),
            const SizedBox(height: 16),
            ElevatedButton.icon(
              onPressed: onPickFolder,
              icon: const Icon(Icons.folder_open_rounded),
              label: const Text('Select Folder'),
              style: ElevatedButton.styleFrom(
                padding: const EdgeInsets.symmetric(vertical: 16),
                backgroundColor: const Color(0xFF4285F4),
                foregroundColor: Colors.white,
                elevation: 0,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
              ),
            ),
            if (folderPath != null) ...[
              const SizedBox(height: 20),
              FolderStatusDisplay(
                folderPath: folderPath!,
                autoSyncActive: autoSyncActive,
                lastSyncTime: lastSyncTime,
                onRefresh: onRefreshStatus,
              ),
            ],
            const SizedBox(height: 20),
            ElevatedButton.icon(
              onPressed: (folderPath != null && !isSyncing) ? onSyncNow : null,
              style: ElevatedButton.styleFrom(
                padding: const EdgeInsets.symmetric(vertical: 16),
                backgroundColor: Colors.green[600],
                foregroundColor: Colors.white,
                disabledBackgroundColor: Colors.grey[200],
                disabledForegroundColor: Colors.grey[400],
                elevation: 0,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
              ),
              icon: isSyncing
                  ? const SizedBox(
                      width: 20,
                      height: 20,
                      child: CircularProgressIndicator(
                        strokeWidth: 2,
                        valueColor: AlwaysStoppedAnimation<Color>(Colors.white),
                      ),
                    )
                  : const Icon(Icons.cloud_upload_rounded),
              label: Text(
                isSyncing
                    ? (totalCount > 0
                          ? 'Syncing $uploadedCount/$totalCount...'
                          : 'Syncing...')
                    : 'Sync Now',
                style: const TextStyle(fontWeight: FontWeight.w600),
              ),
            ),
            const SizedBox(height: 12),
            OutlinedButton.icon(
              onPressed: (folderPath != null && !isSyncing)
                  ? onForceSync
                  : null,
              style: OutlinedButton.styleFrom(
                padding: const EdgeInsets.symmetric(vertical: 16),
                foregroundColor: Colors.orange[800],
                side: BorderSide(color: Colors.orange[800]!),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
              ),
              icon: const Icon(Icons.sync_problem_rounded),
              label: const Text('Force Sync (Reset & Sync All)'),
            ),
          ],
        ),
      ),
    );
  }
}
