import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:path_provider/path_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:flutter/services.dart';

/// Debug logging service that captures logs from both Dart and Kotlin
/// and saves them to a file for debugging purposes.
class DebugLogService {
  static DebugLogService? _instance;
  static const String _debugModeKey = 'debug_mode_enabled';
  static const String _logFileName = 'chord_debug_log.txt';
  static const int _maxLogSizeBytes = 5 * 1024 * 1024; // 5MB max
  static const _channel = MethodChannel('com.even.chord/debug_log');

  File? _logFile;
  bool _isEnabled = false;

  factory DebugLogService() {
    _instance ??= DebugLogService._internal();
    return _instance!;
  }

  DebugLogService._internal();

  /// Initialize the debug log service
  Future<void> initialize() async {
    final prefs = await SharedPreferences.getInstance();
    _isEnabled = prefs.getBool(_debugModeKey) ?? false;

    if (_isEnabled) {
      await _initLogFile();
    }
  }

  /// Check if debug mode is enabled
  bool get isEnabled => _isEnabled;

  /// Enable or disable debug mode
  Future<void> setEnabled(bool enabled) async {
    _isEnabled = enabled;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_debugModeKey, enabled);

    if (enabled) {
      await _initLogFile();
      log('DebugLogService', 'Debug logging enabled');

      // Tell Kotlin to enable logging
      try {
        await _channel.invokeMethod('enableLogging', {'enabled': true});
      } catch (e) {
        log(
          'DebugLogService',
          'Failed to enable Kotlin logging: $e',
          level: 'ERROR',
        );
      }
    } else {
      log('DebugLogService', 'Debug logging disabled');

      // Tell Kotlin to disable logging
      try {
        await _channel.invokeMethod('enableLogging', {'enabled': false});
      } catch (e) {
        // Ignore
      }
    }
  }

  /// Initialize the log file path and rotate if needed
  Future<void> _initLogFile() async {
    try {
      final directory = await getApplicationDocumentsDirectory();
      _logFile = File('${directory.path}/$_logFileName');

      // Check file size and rotate if too large
      if (await _logFile!.exists()) {
        final size = await _logFile!.length();
        if (size > _maxLogSizeBytes) {
          // Keep last half of the file
          final content = await _logFile!.readAsString();
          final halfLength = content.length ~/ 2;
          final newContent = content.substring(halfLength);
          await _logFile!.writeAsString('--- Log rotated ---\n$newContent');
        }
      }

      // Write session start marker
      await _writeToFile(
        '\n\n=== Debug session started at ${DateTime.now().toIso8601String()} ===',
      );
    } catch (e) {
      debugPrint('Failed to init log file: $e');
    }
  }

  /// Write a message to the log file (thread-safe using append mode)
  Future<void> _writeToFile(String message) async {
    if (_logFile == null) return;

    try {
      final timestamp = DateTime.now().toIso8601String();
      final line = '$timestamp $message\n';
      await _logFile!.writeAsString(line, mode: FileMode.append, flush: true);
    } catch (e) {
      debugPrint('Failed to write to log file: $e');
    }
  }

  /// Log a message (only when debug mode is enabled)
  void log(String tag, String message, {String level = 'INFO'}) {
    // Only log if debug mode is enabled
    if (!_isEnabled) return;

    final logMessage = '[$level] [$tag] $message';

    // Print to console
    debugPrint(logMessage);

    // Write to file (fire and forget, don't await)
    _writeToFile(logMessage);
  }

  /// Log info level
  void info(String tag, String message) => log(tag, message, level: 'INFO');

  /// Log warning level
  void warn(String tag, String message) => log(tag, message, level: 'WARN');

  /// Log error level
  void error(String tag, String message) => log(tag, message, level: 'ERROR');

  /// Get the log file path
  Future<String?> getLogFilePath() async {
    if (_logFile == null) {
      final directory = await getApplicationDocumentsDirectory();
      _logFile = File('${directory.path}/$_logFileName');
    }
    return _logFile?.path;
  }

  /// Get the log file contents
  Future<String> getLogContents() async {
    try {
      if (_logFile == null) {
        final directory = await getApplicationDocumentsDirectory();
        _logFile = File('${directory.path}/$_logFileName');
      }

      if (await _logFile!.exists()) {
        return await _logFile!.readAsString();
      }
      return 'No log file found';
    } catch (e) {
      return 'Error reading log file: $e';
    }
  }

  /// Get the log file for sharing
  Future<File?> getLogFile() async {
    try {
      if (_logFile == null) {
        final directory = await getApplicationDocumentsDirectory();
        _logFile = File('${directory.path}/$_logFileName');
      }

      if (await _logFile!.exists()) {
        return _logFile;
      }
      return null;
    } catch (e) {
      return null;
    }
  }

  /// Clear the log file
  Future<void> clearLogs() async {
    try {
      if (_logFile != null && await _logFile!.exists()) {
        await _logFile!.delete();
      }

      if (_isEnabled) {
        await _initLogFile();
        log('DebugLogService', 'Logs cleared');
      }
    } catch (e) {
      debugPrint('Failed to clear logs: $e');
    }
  }

  /// Get device and app info for debugging
  Future<Map<String, dynamic>> getDebugInfo() async {
    final prefs = await SharedPreferences.getInstance();

    return {
      'timestamp': DateTime.now().toIso8601String(),
      'debug_mode': _isEnabled,
      'sync_active': prefs.getBool('sync_active') ?? false,
      'last_sync_time': prefs.getString('last_sync_time'),
      'service_death_count': prefs.getInt('service_death_count') ?? 0,
      'service_death_time': prefs.getString('service_death_time'),
      'heartbeat_last_check': prefs.getInt('heartbeat_last_check'),
      'worker_last_check': prefs.getInt('worker_last_check'),
      'service_restart_attempt': prefs.getInt('service_restart_attempt'),
      'platform': Platform.operatingSystem,
      'platform_version': Platform.operatingSystemVersion,
    };
  }

  /// Log debug info to file
  Future<void> logDebugInfo() async {
    final info = await getDebugInfo();
    log('DEBUG_INFO', info.toString());
  }
}

/// Global debug log instance
final debugLog = DebugLogService();

/// Convenience function for logging
void dlog(String tag, String message, {String level = 'INFO'}) {
  debugLog.log(tag, message, level: level);
}
