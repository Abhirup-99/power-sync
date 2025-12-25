package com.lumaqi.powersync.utils

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract

object FileUtils {
    fun getPathFromUri(context: Context, uri: Uri): String? {
        // Handle Tree Uri
        if (DocumentsContract.isTreeUri(uri)) {
            val documentId = DocumentsContract.getTreeDocumentId(uri)
            val split = documentId.split(":")
            val type = split[0]

            return if ("primary".equals(type, ignoreCase = true)) {
                if (split.size > 1) {
                    "${Environment.getExternalStorageDirectory()}/${split[1]}"
                } else {
                    "${Environment.getExternalStorageDirectory()}/"
                }
            } else {
                // Handle SD Card
                val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as android.os.storage.StorageManager
                val storageVolumes = storageManager.storageVolumes
                for (volume in storageVolumes) {
                    val uuid = volume.uuid
                    if (uuid != null && uuid.equals(type, ignoreCase = true)) {
                        val path = if (split.size > 1) {
                            "${volume.directory?.absolutePath}/${split[1]}"
                        } else {
                            volume.directory?.absolutePath
                        }
                        return path
                    }
                }
                null
            }
        }
        
        // Fallback for non-tree URIs if needed, but OpenDocumentTree returns tree URIs
        return uri.path?.let { path ->
            if (path.contains("primary:")) {
                "${Environment.getExternalStorageDirectory()}/${path.substringAfter("primary:")}"
            } else {
                path
            }
        }
    }
}
