import 'dart:convert';
import 'dart:io';
import 'package:path_provider/path_provider.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:call_log/call_log.dart';
import 'package:shared_preferences/shared_preferences.dart';

class CallLogService {
  static const String _lastSyncTimestampKey = 'last_call_log_sync_timestamp';
  Future<File?> exportIncrementalCallLogs() async {
    try {
      // Permissions should already be granted via PermissionsScreen
      // Just check if we have phone permission
      final status = await Permission.phone.status;
      if (!status.isGranted) {
        print('Phone permission not granted. User should grant permissions first.');
        return null;
      }

      final prefs = await SharedPreferences.getInstance();
      final lastSyncTimestamp = prefs.getInt(_lastSyncTimestampKey) ?? 0;

      final Iterable<CallLogEntry> allEntries = await CallLog.get();

      final newEntries = allEntries.where((entry) {
        final timestamp = entry.timestamp ?? 0;
        return timestamp > lastSyncTimestamp;
      }).toList();

      if (newEntries.isEmpty) {
        return null;
      }

      final latestTimestamp = newEntries
          .map((e) => e.timestamp ?? 0)
          .fold(
            lastSyncTimestamp,
            (max, timestamp) => timestamp > max ? timestamp : max,
          );

      final List<Map<String, dynamic>> callLogsData = newEntries.map((entry) {
        return {
          'number': entry.number ?? 'Unknown',
          'name': entry.name ?? 'Unknown',
          'formattedNumber': entry.formattedNumber ?? '',
          'callType': _getCallTypeName(entry.callType),
          'date': DateTime.fromMillisecondsSinceEpoch(
            entry.timestamp ?? 0,
          ).toIso8601String(),
          'duration': entry.duration ?? 0,
          'cachedNumberType': entry.cachedNumberType ?? 0,
          'cachedNumberLabel': entry.cachedNumberLabel ?? '',
        };
      }).toList();

      final directory = await getTemporaryDirectory();
      final fileName =
          'call_logs_${DateTime.now().millisecondsSinceEpoch}.json';
      final file = File('${directory.path}/$fileName');

      final jsonString = const JsonEncoder.withIndent('  ').convert({
        'exportDate': DateTime.now().toIso8601String(),
        'lastSyncTimestamp': lastSyncTimestamp,
        'newSyncTimestamp': latestTimestamp,
        'totalNewCalls': callLogsData.length,
        'callLogs': callLogsData,
      });

      await file.writeAsString(jsonString);

      await prefs.setInt(_lastSyncTimestampKey, latestTimestamp);

      return file;
    } catch (e) {
      return null;
    }
  }

  String _getCallTypeName(CallType? callType) {
    switch (callType) {
      case CallType.incoming:
        return 'Incoming';
      case CallType.outgoing:
        return 'Outgoing';
      case CallType.missed:
        return 'Missed';
      case CallType.voiceMail:
        return 'Voicemail';
      case CallType.rejected:
        return 'Rejected';
      case CallType.blocked:
        return 'Blocked';
      case CallType.answeredExternally:
        return 'Answered Externally';
      case CallType.wifiIncoming:
        return 'WiFi Incoming';
      case CallType.wifiOutgoing:
        return 'WiFi Outgoing';
      default:
        return 'Unknown';
    }
  }
}
