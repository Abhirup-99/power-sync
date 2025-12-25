package com.lumaqi.powersync.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lumaqi.powersync.NativeSyncConfig
import com.lumaqi.powersync.models.SyncFolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SyncSettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(NativeSyncConfig.PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val _folders = MutableStateFlow<List<SyncFolder>>(emptyList())
    val folders: StateFlow<List<SyncFolder>> = _folders.asStateFlow()

    companion object {
        private const val KEY_SYNC_FOLDERS = "sync_folders_list"
    }

    init {
        loadFolders()
    }

    private fun loadFolders() {
        val json = prefs.getString(KEY_SYNC_FOLDERS, null)
        if (json != null) {
            try {
                val type = object : TypeToken<List<SyncFolder>>() {}.type
                val loadedFolders: List<SyncFolder> = gson.fromJson(json, type)
                _folders.value = loadedFolders
            } catch (e: Exception) {
                e.printStackTrace()
                _folders.value = emptyList()
            }
        } else {
            // Migration: Check for legacy single folder
            val legacyPath = prefs.getString(NativeSyncConfig.KEY_SYNC_FOLDER_PATH, null)
            if (legacyPath != null) {
                val legacyFolder = SyncFolder(
                    localPath = legacyPath,
                    name = "Default Sync"
                )
                addFolder(legacyFolder)
            }
        }
    }

    fun addFolder(folder: SyncFolder) {
        val current = _folders.value.toMutableList()
        current.add(folder)
        saveFolders(current)
    }

    fun removeFolder(folderId: String) {
        val current = _folders.value.toMutableList()
        current.removeAll { it.id == folderId }
        saveFolders(current)
    }

    fun updateFolder(folder: SyncFolder) {
        val current = _folders.value.toMutableList()
        val index = current.indexOfFirst { it.id == folder.id }
        if (index != -1) {
            current[index] = folder
            saveFolders(current)
        }
    }

    private fun saveFolders(list: List<SyncFolder>) {
        _folders.value = list
        val json = gson.toJson(list)
        prefs.edit().putString(KEY_SYNC_FOLDERS, json).apply()
        
        // Update legacy key for backward compatibility if needed, 
        // or just use the first enabled folder as the "primary" one for now
        val primary = list.firstOrNull { it.isEnabled }
        if (primary != null) {
            prefs.edit().putString(NativeSyncConfig.KEY_SYNC_FOLDER_PATH, primary.localPath).apply()
        }
    }
    
    fun getFolders(): List<SyncFolder> {
        return _folders.value
    }
}
