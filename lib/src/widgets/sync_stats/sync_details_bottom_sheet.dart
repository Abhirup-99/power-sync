import 'package:flutter/material.dart';
import '../../services/foreground_sync_service.dart';
import 'sync_detail_card.dart';

Future<void> showSyncDetailsBottomSheet(
  BuildContext context,
  String folderPath,
) async {
  final syncService = ForegroundSyncService();

  showModalBottomSheet(
    context: context,
    isScrollControlled: true,
    backgroundColor: Colors.transparent,
    builder: (context) => DraggableScrollableSheet(
      initialChildSize: 0.7,
      minChildSize: 0.5,
      maxChildSize: 0.9,
      builder: (context, scrollController) => Container(
        decoration: const BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
        ),
        child: FutureBuilder<Map<String, dynamic>>(
          future: syncService.getApiSyncStats(),
          builder: (context, snapshot) {
            if (snapshot.connectionState == ConnectionState.waiting) {
              return const Center(child: CircularProgressIndicator());
            }

            if (!snapshot.hasData) {
              return const Center(child: Text('No sync data available'));
            }

            final stats = snapshot.data!;

            return _SyncDetailsContent(
              stats: stats,
              folderPath: folderPath,
              scrollController: scrollController,
              syncService: syncService,
            );
          },
        ),
      ),
    ),
  );
}

class _SyncDetailsContent extends StatelessWidget {
  final Map<String, dynamic> stats;
  final String folderPath;
  final ScrollController scrollController;
  final ForegroundSyncService syncService;

  const _SyncDetailsContent({
    required this.stats,
    required this.folderPath,
    required this.scrollController,
    required this.syncService,
  });

  @override
  Widget build(BuildContext context) {
    final totalRecordings = stats['totalRecordings'] as int? ?? 0;
    final syncedRecordings = stats['syncedRecordings'] as int? ?? 0;
    final unsyncedRecordings = stats['unsyncedRecordings'] as int? ?? 0;

    return Column(
      children: [
        Container(
          margin: const EdgeInsets.symmetric(vertical: 12),
          width: 40,
          height: 4,
          decoration: BoxDecoration(
            color: Colors.grey[300],
            borderRadius: BorderRadius.circular(2),
          ),
        ),
        Expanded(
          child: ListView(
            controller: scrollController,
            padding: const EdgeInsets.all(20),
            children: [
              _buildHeader(),
              const SizedBox(height: 24),
              SyncDetailCard(
                icon: Icons.cloud_done,
                title: 'Total Synced Files',
                value: '$syncedRecordings',
                color: Colors.green,
              ),
              const SizedBox(height: 12),
              SyncDetailCard(
                icon: Icons.storage,
                title: 'Total Files',
                value: '$totalRecordings',
                color: Colors.blue,
              ),
              const SizedBox(height: 12),
              SyncDetailCard(
                icon: Icons.cloud_upload,
                title: 'Pending Uploads',
                value:
                    '$unsyncedRecordings file${unsyncedRecordings != 1 ? 's' : ''}',
                color: unsyncedRecordings > 0 ? Colors.orange : Colors.green,
              ),
              const SizedBox(height: 12),
              FutureBuilder<DateTime?>(
                future: syncService.getLastSyncTime(),
                builder: (context, snapshot) {
                  final lastSync = snapshot.data;
                  return SyncDetailCard(
                    icon: Icons.access_time,
                    title: 'Last Sync',
                    value: lastSync?.toString() ?? 'Never',
                    color: Colors.purple,
                  );
                },
              ),
              const SizedBox(height: 24),
              _buildClearCacheButton(context),
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildHeader() {
    return Row(
      children: [
        Icon(Icons.analytics, color: Colors.blue[700], size: 28),
        const SizedBox(width: 12),
        const Text(
          'Sync Details',
          style: TextStyle(fontSize: 24, fontWeight: FontWeight.bold),
        ),
      ],
    );
  }

  Widget _buildClearCacheButton(BuildContext context) {
    return ElevatedButton.icon(
      onPressed: () => _handleClearCache(context),
      icon: const Icon(Icons.delete_outline),
      label: const Text('Clear Sync Cache'),
      style: ElevatedButton.styleFrom(
        backgroundColor: Colors.red[50],
        foregroundColor: Colors.red[700],
        padding: const EdgeInsets.symmetric(vertical: 16),
      ),
    );
  }

  Future<void> _handleClearCache(BuildContext context) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Clear Sync Cache'),
        content: const Text(
          'This will clear all sync history. All files will be re-uploaded on the next sync. Continue?',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Clear'),
          ),
        ],
      ),
    );

    if (confirmed == true) {
      await syncService.clearSyncCache();
      if (context.mounted) {
        Navigator.pop(context);
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('Sync cache cleared')));
      }
    }
  }
}
