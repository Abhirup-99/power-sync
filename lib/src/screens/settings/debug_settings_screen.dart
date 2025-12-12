import 'package:flutter/material.dart';
import 'package:share_plus/share_plus.dart';
import '../../services/debug_log_service.dart';

class DebugSettingsScreen extends StatefulWidget {
  const DebugSettingsScreen({super.key});

  @override
  State<DebugSettingsScreen> createState() => _DebugSettingsScreenState();
}

class _DebugSettingsScreenState extends State<DebugSettingsScreen> {
  bool _isDebugEnabled = false;
  bool _isLoading = true;
  String _logPreview = '';
  Map<String, dynamic> _debugInfo = {};

  @override
  void initState() {
    super.initState();
    _loadState();
  }

  Future<void> _loadState() async {
    await debugLog.initialize();
    final logs = await debugLog.getLogContents();
    final info = await debugLog.getDebugInfo();

    setState(() {
      _isDebugEnabled = debugLog.isEnabled;
      _logPreview = logs.length > 2000
          ? '...${logs.substring(logs.length - 2000)}'
          : logs;
      _debugInfo = info;
      _isLoading = false;
    });
  }

  Future<void> _toggleDebugMode(bool enabled) async {
    setState(() => _isLoading = true);
    await debugLog.setEnabled(enabled);
    await _loadState();
  }

  Future<void> _shareLogs() async {
    final file = await debugLog.getLogFile();
    if (file != null) {
      await Share.shareXFiles(
        [XFile(file.path)],
        subject: 'Chord Debug Logs',
        text: 'Debug logs from Chord app',
      );
    } else {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('No log file found')));
      }
    }
  }

  Future<void> _clearLogs() async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Clear Logs'),
        content: const Text('Are you sure you want to clear all debug logs?'),
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

    if (confirm == true) {
      await debugLog.clearLogs();
      await _loadState();
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('Logs cleared')));
      }
    }
  }

  Future<void> _refreshLogs() async {
    setState(() => _isLoading = true);
    await _loadState();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Debug Settings'),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: _refreshLogs,
            tooltip: 'Refresh',
          ),
        ],
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : SingleChildScrollView(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // Debug Mode Toggle
                  Card(
                    child: SwitchListTile(
                      title: const Text('Debug Mode'),
                      subtitle: Text(
                        _isDebugEnabled
                            ? 'Logging all events to file'
                            : 'Enable to capture detailed logs',
                      ),
                      value: _isDebugEnabled,
                      onChanged: _toggleDebugMode,
                      secondary: Icon(
                        _isDebugEnabled
                            ? Icons.bug_report
                            : Icons.bug_report_outlined,
                        color: _isDebugEnabled ? Colors.green : null,
                      ),
                    ),
                  ),
                  const SizedBox(height: 16),

                  // Debug Info Card
                  Card(
                    child: Padding(
                      padding: const EdgeInsets.all(16),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const Text(
                            'Service Status',
                            style: TextStyle(
                              fontSize: 18,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                          const SizedBox(height: 12),
                          _buildInfoRow(
                            'Sync Active',
                            _debugInfo['sync_active']?.toString() ?? 'Unknown',
                          ),
                          _buildInfoRow(
                            'Last Sync',
                            _formatTime(_debugInfo['last_sync_time']),
                          ),
                          _buildInfoRow(
                            'Service Deaths',
                            _debugInfo['service_death_count']?.toString() ??
                                '0',
                          ),
                          _buildInfoRow(
                            'Last Death',
                            _formatTime(_debugInfo['service_death_time']),
                          ),
                          _buildInfoRow(
                            'Platform',
                            _debugInfo['platform'] ?? 'Unknown',
                          ),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(height: 16),

                  // Action Buttons
                  Row(
                    children: [
                      Expanded(
                        child: ElevatedButton.icon(
                          onPressed: _shareLogs,
                          icon: const Icon(Icons.share),
                          label: const Text('Share Logs'),
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: OutlinedButton.icon(
                          onPressed: _clearLogs,
                          icon: const Icon(Icons.delete_outline),
                          label: const Text('Clear Logs'),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 24),

                  // Log Preview
                  const Text(
                    'Recent Logs',
                    style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                  ),
                  const SizedBox(height: 8),
                  Container(
                    width: double.infinity,
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: Colors.grey[900],
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: SelectableText(
                      _logPreview.isEmpty
                          ? 'No logs yet. Enable debug mode to start capturing logs.'
                          : _logPreview,
                      style: const TextStyle(
                        fontFamily: 'monospace',
                        fontSize: 11,
                        color: Colors.greenAccent,
                      ),
                    ),
                  ),
                  const SizedBox(height: 24),

                  // Instructions
                  Card(
                    color: Colors.blue.withOpacity(0.1),
                    child: const Padding(
                      padding: EdgeInsets.all(16),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            children: [
                              Icon(Icons.info_outline, color: Colors.blue),
                              SizedBox(width: 8),
                              Text(
                                'How to use',
                                style: TextStyle(
                                  fontWeight: FontWeight.bold,
                                  color: Colors.blue,
                                ),
                              ),
                            ],
                          ),
                          SizedBox(height: 8),
                          Text(
                            '1. Enable Debug Mode above\n'
                            '2. Use the app normally overnight\n'
                            '3. If the app gets killed, check logs here\n'
                            '4. Share logs for troubleshooting',
                            style: TextStyle(fontSize: 14),
                          ),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            ),
    );
  }

  Widget _buildInfoRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: TextStyle(color: Colors.grey[600])),
          Text(value, style: const TextStyle(fontWeight: FontWeight.w500)),
        ],
      ),
    );
  }

  String _formatTime(String? isoTime) {
    if (isoTime == null) return 'Never';
    try {
      final dt = DateTime.parse(isoTime);
      final now = DateTime.now();
      final diff = now.difference(dt);

      if (diff.inMinutes < 1) return 'Just now';
      if (diff.inMinutes < 60) return '${diff.inMinutes}m ago';
      if (diff.inHours < 24) return '${diff.inHours}h ago';
      return '${diff.inDays}d ago';
    } catch (e) {
      return isoTime;
    }
  }
}
