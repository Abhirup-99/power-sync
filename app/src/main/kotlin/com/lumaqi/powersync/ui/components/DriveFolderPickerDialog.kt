package com.lumaqi.powersync.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.lumaqi.powersync.services.DriveCategory
import com.lumaqi.powersync.services.DriveFolder
import com.lumaqi.powersync.services.GoogleDriveService
import kotlinx.coroutines.launch

private const val ID_MY_DRIVE = "category_my_drive"
private const val ID_SHARED = "category_shared"
private const val ID_STARRED = "category_starred"

// Flattened item representation for LazyColumn
sealed class TreeItem {
    data class FolderNode(
            val folder: DriveFolder,
            val level: Int,
            val isExpanded: Boolean,
            val isSelected: Boolean,
            val isLoading: Boolean,
            val isEmpty: Boolean
    ) : TreeItem()

    data class Loading(val level: Int) : TreeItem()
    data class Empty(val level: Int) : TreeItem()
}

@Composable
fun DriveFolderPickerDialog(
        driveService: GoogleDriveService,
        onDismissRequest: () -> Unit,
        onFolderSelected: (String, String) -> Unit // id, name
) {
    var selectedFolderId by remember { mutableStateOf<String?>(null) }
    var selectedFolderName by remember { mutableStateOf<String?>(null) }

    // Tree State
    val expandedFolderIds = remember { mutableStateListOf<String>() }
    // Key null = root of the whole tree (which contains the 3 categories)
    val folderChildren = remember { mutableStateMapOf<String?, List<DriveFolder>>() }
    val loadingFolders = remember { mutableStateListOf<String?>() }

    val scope = rememberCoroutineScope()

    // Function to load children
    fun loadChildren(parentId: String?) {
        if (loadingFolders.contains(parentId)) return
        scope.launch {
            loadingFolders.add(parentId)
            val children =
                    when (parentId) {
                        ID_MY_DRIVE -> driveService.listFolders(null, DriveCategory.MY_DRIVE)
                        ID_SHARED -> driveService.listFolders(null, DriveCategory.SHARED_WITH_ME)
                        ID_STARRED -> driveService.listFolders(null, DriveCategory.STARRED)
                        else ->
                                driveService.listFolders(
                                        parentId
                                ) // Category ignored for subfolders
                    }
            folderChildren[parentId] = children
            loadingFolders.remove(parentId)
        }
    }

    // Initial Setup: Populate root with categories
    LaunchedEffect(Unit) {
        folderChildren[null] =
                listOf(
                        DriveFolder(ID_MY_DRIVE, "My Drive"),
                        DriveFolder(ID_SHARED, "Shared with me"),
                        DriveFolder(ID_STARRED, "Starred")
                )
    }

    // Function to recursively build flattened list
    fun buildFlattenedList(parentId: String? = null, level: Int = 0): List<TreeItem> {
        val children = folderChildren[parentId] ?: return emptyList()
        val result = mutableListOf<TreeItem>()

        children.forEach { folder ->
            val isExpanded = expandedFolderIds.contains(folder.id)
            val isLoading = loadingFolders.contains(folder.id)
            val childList = folderChildren[folder.id]
            val isEmpty = childList?.isEmpty() == true

            result.add(
                    TreeItem.FolderNode(
                            folder = folder,
                            level = level,
                            isExpanded = isExpanded,
                            isSelected = selectedFolderId == folder.id,
                            isLoading = isLoading,
                            isEmpty = isEmpty
                    )
            )

            if (isExpanded) {
                if (isLoading) {
                    // Loading indicator is inside arrow or we could add item
                } else {
                    result.addAll(buildFlattenedList(folder.id, level + 1))
                }
            }
        }
        return result
    }

    val flattenedItems = buildFlattenedList(null, 0)

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
                modifier = Modifier.fillMaxWidth().height(600.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Select Drive Folder", style = MaterialTheme.typography.titleMedium)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Box(modifier = Modifier.weight(1f)) {
                    // Root is pre-populated
                    if (flattenedItems.isEmpty()) {
                        Text("No folders found", modifier = Modifier.align(Alignment.Center))
                    } else {
                        LazyColumn {
                            items(flattenedItems) { item ->
                                when (item) {
                                    is TreeItem.FolderNode -> {
                                        DriveTreeItemRow(
                                                item = item,
                                                onExpand = {
                                                    if (expandedFolderIds.contains(item.folder.id)
                                                    ) {
                                                        expandedFolderIds.remove(item.folder.id)
                                                    } else {
                                                        expandedFolderIds.add(item.folder.id)
                                                        loadChildren(item.folder.id)
                                                    }
                                                },
                                                onSelect = {
                                                    selectedFolderId = item.folder.id
                                                    selectedFolderName = item.folder.name
                                                }
                                        )
                                    }
                                    else -> {}
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismissRequest) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                            enabled =
                                    selectedFolderId != null &&
                                            selectedFolderId != ID_SHARED &&
                                            selectedFolderId != ID_STARRED,
                            onClick = {
                                val finalId =
                                        if (selectedFolderId == ID_MY_DRIVE) "root"
                                        else selectedFolderId!!
                                val finalName =
                                        if (selectedFolderId == ID_MY_DRIVE) "My Drive"
                                        else selectedFolderName!!
                                onFolderSelected(finalId, finalName)
                            }
                    ) { Text("Select") }
                }
            }
        }
    }
}

@Composable
fun DriveTreeItemRow(item: TreeItem.FolderNode, onExpand: () -> Unit, onSelect: () -> Unit) {
    Row(
            modifier =
                    Modifier.fillMaxWidth()
                            .clickable { onSelect() }
                            .background(
                                    if (item.isSelected) MaterialTheme.colorScheme.primaryContainer
                                    else Color.Transparent
                            )
                            .padding(vertical = 8.dp)
                            .padding(start = (item.level * 24).dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        // Expand Button
        IconButton(onClick = onExpand, modifier = Modifier.size(24.dp)) {
            if (item.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                        imageVector =
                                if (item.isExpanded) Icons.Default.KeyboardArrowDown
                                else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Folder Icon based on ID
        val icon =
                when (item.folder.id) {
                    ID_MY_DRIVE -> Icons.Default.Folder
                    ID_SHARED -> Icons.Default.Group
                    ID_STARRED -> Icons.Default.Star
                    else -> Icons.Default.Folder
                }

        val folderColor =
                when {
                    item.isSelected -> MaterialTheme.colorScheme.primary
                    item.folder.id == ID_STARRED -> Color(0xFFFFC107)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }

        Icon(
                imageVector = icon,
                contentDescription = null,
                tint = folderColor,
                modifier = Modifier.padding(horizontal = 8.dp).size(20.dp)
        )

        Text(
                text = item.folder.name,
                style =
                        if (item.level == 0) MaterialTheme.typography.titleSmall
                        else MaterialTheme.typography.bodyMedium, // Bold for root categories
                color =
                        if (item.isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface
        )
    }
}
