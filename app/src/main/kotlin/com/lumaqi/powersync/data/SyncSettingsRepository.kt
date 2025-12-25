package com.lumaqi.powersync.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lumaqi.powersync.NativeSyncConfig
import com.lumaqi.powersync.NativeSyncDatabase
import com.lumaqi.powersync.models.SyncFolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SyncSettingsRepository private constructor(context: Context) {
    private val db = NativeSyncDatabase.getInstance(context)
    private val prefs: SharedPreferences =
            context.getSharedPreferences(NativeSyncConfig.PREFS_NAME, Context.MODE_PRIVATE)
    private val _folders = MutableStateFlow<List<SyncFolder>>(emptyList())
    val folders: StateFlow<List<SyncFolder>> = _folders.asStateFlow()

    companion object {
        @Volatile
        private var INSTANCE: SyncSettingsRepository? = null

        fun getInstance(context: Context): SyncSettingsRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = SyncSettingsRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }

        private const val KEY_SYNC_FOLDERS = "sync_folders_list"
        private const val KEY_MIGRATION_DONE = "migration_to_sqlite_done"
    }

    init {
        migrateIfNeeded()
        loadFolders()
    }

    private fun migrateIfNeeded() {
        if (prefs.getBoolean(KEY_MIGRATION_DONE, false)) return

        // Migrate folders
        val json = prefs.getString(KEY_SYNC_FOLDERS, null)
        if (json != null) {
            try {
                val type = object : TypeToken<List<SyncFolder>>() {}.type
                val loadedFolders: List<SyncFolder> = Gson().fromJson(json, type)
                loadedFolders.forEach { db.saveFolder(it) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            // Legacy single folder
            val legacyPath = prefs.getString(NativeSyncConfig.KEY_SYNC_FOLDER_PATH, null)
            if (legacyPath != null) {
                val driveId = prefs.getString(NativeSyncConfig.KEY_DRIVE_FOLDER_ID, null)
                val driveName = prefs.getString(NativeSyncConfig.KEY_DRIVE_FOLDER_NAME, null)
                val legacyFolder =
                        SyncFolder(
                                localPath = legacyPath,
                                name = "Default Sync",
                                driveFolderId = driveId,
                                driveFolderName = driveName ?: "PowerSync"
                        )
                db.saveFolder(legacyFolder)
            }
        }

        // Migrate other settings
        val allPrefs = prefs.all
        allPrefs.forEach { (key, value) ->
            if (key != KEY_SYNC_FOLDERS && key != KEY_MIGRATION_DONE) {
                db.updateMetadata(key, value.toString())
            }
        }

        prefs.edit().putBoolean(KEY_MIGRATION_DONE, true).apply()
    }

    private fun loadFolders() {
        _folders.value = db.getAllFolders()
    }

    fun addFolder(folder: SyncFolder) {
        db.saveFolder(folder)
        loadFolders()
    }

    fun removeFolder(folderId: String) {
        db.deleteFolder(folderId)
        loadFolders()
    }

    fun updateFolder(folder: SyncFolder) {
        db.saveFolder(folder)
        loadFolders()
    }

    fun getFolders(): List<SyncFolder> {
        return db.getAllFolders()
    }

    // Generic settings methods
    fun getString(key: String, defaultValue: String? = null): String? {
        return db.getMetadata(key) ?: defaultValue
    }

    fun setString(key: String, value: String) {
        db.updateMetadata(key, value)
    }

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        return db.getMetadata(key)?.toBoolean() ?: defaultValue
    }

    fun setBoolean(key: String, value: Boolean) {
        db.updateMetadata(key, value.toString())
    }

    fun getLong(key: String, defaultValue: Long = 0L): Long {
        return db.getMetadata(key)?.toLongOrNull() ?: defaultValue
    }

    fun setLong(key: String, value: Long) {
        db.updateMetadata(key, value.toString())
    }
}
