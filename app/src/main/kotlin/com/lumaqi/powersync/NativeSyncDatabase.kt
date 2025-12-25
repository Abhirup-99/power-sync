package com.lumaqi.powersync

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.lumaqi.powersync.models.SyncFolder
import com.lumaqi.powersync.models.SyncStatus
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/**
 * Native SQLite database helper that accesses the same database as Flutter's sqflite. This allows
 * Kotlin to read/write sync status independently of Flutter engine.
 */
class NativeSyncDatabase private constructor(context: Context) :
        SQLiteOpenHelper(
                context,
                context.getDatabasePath(NativeSyncConfig.DATABASE_NAME).absolutePath,
                null,
                NativeSyncConfig.DATABASE_VERSION
        ) {

    companion object {
        @Volatile
        private var INSTANCE: NativeSyncDatabase? = null

        fun getInstance(context: Context): NativeSyncDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = NativeSyncDatabase(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }

        private const val TABLE_SYNCED_FILES = "synced_files"
        private const val TABLE_SYNC_METADATA = "sync_metadata"
        private const val TABLE_SYNC_FOLDERS = "sync_folders"

        // synced_files columns
        private const val COL_FILE_PATH = "file_path"
        private const val COL_FILE_NAME = "file_name"
        private const val COL_TARGET_FOLDER = "target_folder"
        private const val COL_DRIVE_FILE_ID = "drive_file_id"
        private const val COL_FILE_SIZE = "file_size"
        private const val COL_FILE_HASH = "file_hash"
        private const val COL_SYNCED_AT = "synced_at"
        private const val COL_LAST_MODIFIED = "last_modified"

        // sync_metadata columns
        private const val COL_KEY = "key"
        private const val COL_VALUE = "value"

        // sync_folders columns
        private const val COL_FOLDER_ID = "id"
        private const val COL_FOLDER_LOCAL_PATH = "local_path"
        private const val COL_FOLDER_NAME = "name"
        private const val COL_FOLDER_IS_ENABLED = "is_enabled"
        private const val COL_FOLDER_LAST_SYNC_TIME = "last_sync_time"
        private const val COL_FOLDER_STATUS = "status"
        private const val COL_FOLDER_DRIVE_ID = "drive_folder_id"
        private const val COL_FOLDER_DRIVE_NAME = "drive_folder_name"
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Database should already exist from Flutter
        // But create tables if they don't exist (fallback)
        db.execSQL(
                """
            CREATE TABLE IF NOT EXISTS $TABLE_SYNCED_FILES (
                $COL_FILE_PATH TEXT PRIMARY KEY,
                $COL_FILE_NAME TEXT NOT NULL,
                $COL_TARGET_FOLDER TEXT NOT NULL,
                $COL_DRIVE_FILE_ID TEXT NOT NULL,
                $COL_FILE_SIZE INTEGER NOT NULL,
                $COL_FILE_HASH TEXT NOT NULL,
                $COL_SYNCED_AT TEXT NOT NULL,
                $COL_LAST_MODIFIED TEXT NOT NULL
            )
        """
        )

        db.execSQL(
                """
            CREATE TABLE IF NOT EXISTS $TABLE_SYNC_METADATA (
                $COL_KEY TEXT PRIMARY KEY,
                $COL_VALUE TEXT NOT NULL
            )
        """
        )

        db.execSQL(
                """
            CREATE TABLE IF NOT EXISTS $TABLE_SYNC_FOLDERS (
                $COL_FOLDER_ID TEXT PRIMARY KEY,
                $COL_FOLDER_LOCAL_PATH TEXT NOT NULL,
                $COL_FOLDER_NAME TEXT NOT NULL,
                $COL_FOLDER_IS_ENABLED INTEGER NOT NULL DEFAULT 1,
                $COL_FOLDER_LAST_SYNC_TIME INTEGER NOT NULL DEFAULT 0,
                $COL_FOLDER_STATUS TEXT NOT NULL DEFAULT 'Idle',
                $COL_FOLDER_DRIVE_ID TEXT,
                $COL_FOLDER_DRIVE_NAME TEXT
            )
        """
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL(
                    """
                CREATE TABLE IF NOT EXISTS $TABLE_SYNC_FOLDERS (
                    $COL_FOLDER_ID TEXT PRIMARY KEY,
                    $COL_FOLDER_LOCAL_PATH TEXT NOT NULL,
                    $COL_FOLDER_NAME TEXT NOT NULL,
                    $COL_FOLDER_IS_ENABLED INTEGER NOT NULL DEFAULT 1,
                    $COL_FOLDER_LAST_SYNC_TIME INTEGER NOT NULL DEFAULT 0,
                    $COL_FOLDER_STATUS TEXT NOT NULL DEFAULT 'Idle',
                    $COL_FOLDER_DRIVE_ID TEXT,
                    $COL_FOLDER_DRIVE_NAME TEXT
                )
            """
            )
        } else if (oldVersion < 3) {
            db.execSQL("ALTER TABLE $TABLE_SYNC_FOLDERS ADD COLUMN $COL_FOLDER_DRIVE_ID TEXT")
            db.execSQL("ALTER TABLE $TABLE_SYNC_FOLDERS ADD COLUMN $COL_FOLDER_DRIVE_NAME TEXT")
        }
    }

    /** Check if a file is already synced and unchanged */
    fun isFileSynced(filePath: String): Boolean {
        val file = File(filePath)
        if (!file.exists()) {
            removeFromCache(filePath)
            return false
        }

        val db = readableDatabase
        val cursor =
                db.query(
                        TABLE_SYNCED_FILES,
                        arrayOf(COL_LAST_MODIFIED, COL_FILE_SIZE),
                        "$COL_FILE_PATH = ?",
                        arrayOf(filePath),
                        null,
                        null,
                        null,
                        "1"
                )

        return cursor.use {
            if (!it.moveToFirst()) {
                false
            } else {
                val cachedModified = it.getString(0)
                val cachedSize = it.getLong(1)
                val currentModified =
                        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", java.util.Locale.US)
                                .format(java.util.Date(file.lastModified()))
                val currentSize = file.length()

                // File is synced if size matches and not modified after cache time
                cachedSize == currentSize && !isAfter(currentModified, cachedModified)
            }
        }
    }

    private fun isAfter(current: String, cached: String): Boolean {
        return try {
            current > cached
        } catch (e: Exception) {
            false
        }
    }

    /** Get list of files in directory that need to be synced */
    fun getUnsyncedFiles(directoryPath: String): List<File> {
        val directory = File(directoryPath)
        if (!directory.exists() || !directory.isDirectory) {
            return emptyList()
        }

        val unsyncedFiles = mutableListOf<File>()

        directory.listFiles()?.forEach { file ->
            if (file.isFile &&
                            !file.name.startsWith(".trashed-") &&
                            !isFileSynced(file.absolutePath)
            ) {
                unsyncedFiles.add(file)
            }
        }

        return unsyncedFiles
    }

    /** Mark a file as synced in the database */
    fun markAsSynced(filePath: String, targetFolder: String, driveFileId: String) {
        val file = File(filePath)
        if (!file.exists()) return

        val db = writableDatabase
        val values =
                ContentValues().apply {
                    put(COL_FILE_PATH, filePath)
                    put(COL_FILE_NAME, file.name)
                    put(COL_TARGET_FOLDER, targetFolder)
                    put(COL_DRIVE_FILE_ID, driveFileId)
                    put(COL_FILE_SIZE, file.length())
                    put(COL_FILE_HASH, calculateFileHash(file))
                    put(COL_SYNCED_AT, getCurrentTimestamp())
                    put(COL_LAST_MODIFIED, getFileModifiedTimestamp(file))
                }

        db.insertWithOnConflict(TABLE_SYNCED_FILES, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** Remove a file from the sync cache */
    fun removeFromCache(filePath: String) {
        val db = writableDatabase
        db.delete(TABLE_SYNCED_FILES, "$COL_FILE_PATH = ?", arrayOf(filePath))
    }

    /** Update sync metadata */
    fun updateMetadata(key: String, value: String) {
        val db = writableDatabase
        val values =
                ContentValues().apply {
                    put(COL_KEY, key)
                    put(COL_VALUE, value)
                }
        db.insertWithOnConflict(TABLE_SYNC_METADATA, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** Get sync metadata value */
    fun getMetadata(key: String): String? {
        val db = readableDatabase
        val cursor =
                db.query(
                        TABLE_SYNC_METADATA,
                        arrayOf(COL_VALUE),
                        "$COL_KEY = ?",
                        arrayOf(key),
                        null,
                        null,
                        null,
                        "1"
                )

        return cursor.use { if (it.moveToFirst()) it.getString(0) else null }
    }

    /** Get all sync folders */
    fun getAllFolders(): List<SyncFolder> {
        val db = readableDatabase
        val folders = mutableListOf<SyncFolder>()
        val cursor = db.query(TABLE_SYNC_FOLDERS, null, null, null, null, null, null)
        cursor.use {
            while (it.moveToNext()) {
                folders.add(
                        SyncFolder(
                                id = it.getString(it.getColumnIndexOrThrow(COL_FOLDER_ID)),
                                localPath =
                                        it.getString(it.getColumnIndexOrThrow(COL_FOLDER_LOCAL_PATH)),
                                name = it.getString(it.getColumnIndexOrThrow(COL_FOLDER_NAME)),
                                isEnabled =
                                        it.getInt(it.getColumnIndexOrThrow(COL_FOLDER_IS_ENABLED)) ==
                                                1,
                                lastSyncTime =
                                        it.getLong(
                                                it.getColumnIndexOrThrow(COL_FOLDER_LAST_SYNC_TIME)
                                        ),
                                status =
                                        SyncStatus.valueOf(
                                                it.getString(
                                                        it.getColumnIndexOrThrow(COL_FOLDER_STATUS)
                                                )
                                        ),
                                driveFolderId =
                                        it.getString(it.getColumnIndexOrThrow(COL_FOLDER_DRIVE_ID)),
                                driveFolderName =
                                        it.getString(it.getColumnIndexOrThrow(COL_FOLDER_DRIVE_NAME))
                        )
                )
            }
        }
        return folders
    }

    /** Add or update a sync folder */
    fun saveFolder(folder: SyncFolder) {
        val db = writableDatabase
        val values =
                ContentValues().apply {
                    put(COL_FOLDER_ID, folder.id)
                    put(COL_FOLDER_LOCAL_PATH, folder.localPath)
                    put(COL_FOLDER_NAME, folder.name)
                    put(COL_FOLDER_IS_ENABLED, if (folder.isEnabled) 1 else 0)
                    put(COL_FOLDER_LAST_SYNC_TIME, folder.lastSyncTime)
                    put(COL_FOLDER_STATUS, folder.status.name)
                    put(COL_FOLDER_DRIVE_ID, folder.driveFolderId)
                    put(COL_FOLDER_DRIVE_NAME, folder.driveFolderName)
                }
        db.insertWithOnConflict(TABLE_SYNC_FOLDERS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** Remove a sync folder */
    fun deleteFolder(folderId: String) {
        val db = writableDatabase
        db.delete(TABLE_SYNC_FOLDERS, "$COL_FOLDER_ID = ?", arrayOf(folderId))
    }

    /** Get list of recently synced files */
    fun getSyncHistory(limit: Int = 50, offset: Int = 0): List<Map<String, Any>> {
        val db = readableDatabase
        val history = mutableListOf<Map<String, Any>>()

        val cursor =
                db.query(
                        TABLE_SYNCED_FILES,
                        null, // all columns
                        null,
                        null,
                        null,
                        null,
                        "$COL_SYNCED_AT DESC",
                        "$offset, $limit"
                )

        cursor.use {
            while (it.moveToNext()) {
                val item = mutableMapOf<String, Any>()
                for (i in 0 until it.columnCount) {
                    val columnName = it.getColumnName(i)
                    when (it.getType(i)) {
                        android.database.Cursor.FIELD_TYPE_INTEGER ->
                                item[columnName] = it.getLong(i)
                        android.database.Cursor.FIELD_TYPE_STRING ->
                                item[columnName] = it.getString(i)
                        android.database.Cursor.FIELD_TYPE_FLOAT ->
                                item[columnName] = it.getDouble(i)
                        android.database.Cursor.FIELD_TYPE_BLOB -> item[columnName] = it.getBlob(i)
                        else -> item[columnName] = "NULL"
                    }
                }
                history.add(item)
            }
        }

        return history
    }

    /** Get total count and size of synced files */
    fun getSyncedStats(): Pair<Int, Long> {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*), COALESCE(SUM(file_size), 0) FROM $TABLE_SYNCED_FILES", null)
        return cursor.use {
            if (it.moveToFirst()) {
                Pair(it.getInt(0), it.getLong(1))
            } else {
                Pair(0, 0L)
            }
        }
    }

    private fun getCurrentTimestamp(): String {
        return java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", java.util.Locale.US)
                .format(java.util.Date())
    }

    private fun getFileModifiedTimestamp(file: File): String {
        return java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", java.util.Locale.US)
                .format(java.util.Date(file.lastModified()))
    }

    private fun calculateFileHash(file: File): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            file.inputStream().use { fis ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    md.update(buffer, 0, bytesRead)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }
}
