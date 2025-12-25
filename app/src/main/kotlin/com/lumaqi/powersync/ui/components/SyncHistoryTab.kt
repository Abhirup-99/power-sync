package com.lumaqi.powersync.ui.components

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lumaqi.powersync.NativeSyncDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SyncHistoryTabContent(database: NativeSyncDatabase) {
    val context = LocalContext.current
    val syncHistory = remember { mutableStateListOf<Map<String, Any>>() }
    var isLoading by remember { mutableStateOf(true) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var offset by remember { mutableIntStateOf(0) }
    var hasMore by remember { mutableStateOf(true) }
    val limit = 50

    // Function to load history
    suspend fun loadHistoryItems(isInitial: Boolean) {
        if (!hasMore && !isInitial) return

        if (isInitial) {
            isLoading = true
            offset = 0
            syncHistory.clear()
        } else {
            isLoadingMore = true
        }

        withContext(Dispatchers.IO) {
            val newItems = database.getSyncHistory(limit, offset)
            withContext(Dispatchers.Main) {
                if (newItems.size < limit) {
                    hasMore = false
                }
                syncHistory.addAll(newItems)
                offset += newItems.size
                isLoading = false
                isLoadingMore = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadHistoryItems(true)
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (syncHistory.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                    "No sync history yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(syncHistory.size) { index ->
                val item = syncHistory[index]
                val fileName = item["file_name"] as? String ?: "Unknown"
                val syncedAtRaw = item["synced_at"] as? String ?: ""
                val fileSize = item["file_size"] as? Long ?: 0L

                val syncedAtFormatted =
                        remember(syncedAtRaw) {
                            try {
                                val sdf =
                                        java.text.SimpleDateFormat(
                                                "yyyy-MM-dd'T'HH:mm:ss.SSS",
                                                java.util.Locale.US
                                        )
                                val date = sdf.parse(syncedAtRaw)
                                if (date != null) {
                                    DateUtils.getRelativeTimeSpanString(date.time)
                                } else {
                                    syncedAtRaw
                                }
                            } catch (e: Exception) {
                                syncedAtRaw
                            }
                        }

                Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                                CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                ) {
                    Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                    fileName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                    "${Formatter.formatShortFileSize(context, fileSize)} • $syncedAtFormatted",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Load more when reaching the end
                if (index == syncHistory.lastIndex && hasMore && !isLoadingMore) {
                    LaunchedEffect(Unit) {
                        loadHistoryItems(false)
                    }
                }
            }

            if (isLoadingMore) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}
