package com.even.chord

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Debug logging utility that writes logs to a file for debugging.
 * Logs are written to the app's documents directory (same as Dart) for unified logging.
 */
object DebugLogger {
    private const val LOG_FILE_NAME = "chord_debug_log.txt"
    private const val MAX_LOG_SIZE_BYTES = 5 * 1024 * 1024L // 5MB
    
    private var isEnabled = false
    private var logFile: File? = null
    private var context: Context? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US)
    
    /**
     * Initialize the debug logger with context
     */
    fun initialize(context: Context) {
        this.context = context
        val prefs = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        isEnabled = prefs.getBoolean("flutter.debug_mode_enabled", false)
        
        if (isEnabled) {
            // Use the same documents directory as Dart for unified logs
            val documentsDir = File(context.filesDir.parentFile, "app_flutter")
            if (!documentsDir.exists()) {
                documentsDir.mkdirs()
            }
            logFile = File(documentsDir, LOG_FILE_NAME)
            rotateLogIfNeeded()
        }
    }
    
    /**
     * Enable or disable logging
     */
    fun setEnabled(enabled: Boolean, context: Context) {
        isEnabled = enabled
        this.context = context
        
        val prefs = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("flutter.debug_mode_enabled", enabled).apply()
        
        if (enabled) {
            // Use the same documents directory as Dart for unified logs
            val documentsDir = File(context.filesDir.parentFile, "app_flutter")
            if (!documentsDir.exists()) {
                documentsDir.mkdirs()
            }
            logFile = File(documentsDir, LOG_FILE_NAME)
            rotateLogIfNeeded()
            log("DebugLogger", "Kotlin debug logging enabled")
        }
    }
    
    /**
     * Rotate log file if it's too large
     */
    private fun rotateLogIfNeeded() {
        try {
            logFile?.let { file ->
                if (file.exists() && file.length() > MAX_LOG_SIZE_BYTES) {
                    val content = file.readText()
                    val halfLength = content.length / 2
                    val newContent = "--- Log rotated ---\n${content.substring(halfLength)}"
                    file.writeText(newContent)
                }
            }
        } catch (e: Exception) {
            Log.e("DebugLogger", "Failed to rotate log file", e)
        }
    }
    
    /**
     * Log a message at INFO level
     */
    fun i(tag: String, message: String) {
        log(tag, message, "INFO")
    }
    
    /**
     * Log a message at WARNING level
     */
    fun w(tag: String, message: String) {
        log(tag, message, "WARN")
    }
    
    /**
     * Log a message at ERROR level
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val fullMessage = if (throwable != null) {
            "$message: ${throwable.message}\n${throwable.stackTraceToString()}"
        } else {
            message
        }
        log(tag, fullMessage, "ERROR")
    }
    
    /**
     * Core logging function - only logs when debug mode is enabled
     */
    fun log(tag: String, message: String, level: String = "INFO") {
        // Only log if debug mode is enabled
        if (!isEnabled) return
        
        // Log to Android logcat
        when (level) {
            "ERROR" -> Log.e(tag, message)
            "WARN" -> Log.w(tag, message)
            else -> Log.i(tag, message)
        }
        
        // Write to file (same file as Dart uses)
        try {
            logFile?.let { file ->
                val timestamp = dateFormat.format(Date())
                val logLine = "$timestamp [$level] [$tag] $message\n"
                
                synchronized(this) {
                    FileWriter(file, true).use { writer ->
                        writer.append(logLine)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("DebugLogger", "Failed to write log to file", e)
        }
    }
    
    /**
     * Get the log file path
     */
    fun getLogFilePath(): String? {
        return logFile?.absolutePath
    }
    
    /**
     * Get log file contents
     */
    fun getLogContents(): String {
        return try {
            logFile?.readText() ?: "No log file found"
        } catch (e: Exception) {
            "Error reading log file: ${e.message}"
        }
    }
    
    /**
     * Clear the log file
     */
    fun clearLogs() {
        try {
            logFile?.let { file ->
                synchronized(this) {
                    if (file.exists()) {
                        file.delete()
                    }
                }
                if (isEnabled) {
                    log("DebugLogger", "Logs cleared")
                }
            }
        } catch (e: Exception) {
            Log.e("DebugLogger", "Failed to clear logs", e)
        }
    }
}
