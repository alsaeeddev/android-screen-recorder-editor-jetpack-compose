package com.alsaeeddev.recapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alsaeeddev.recapp.data.model.MediaType
import com.alsaeeddev.recapp.data.model.RecordItem
import com.alsaeeddev.recapp.ui.components.BentoCard
import com.alsaeeddev.recapp.ui.theme.BentoPrimary
import com.alsaeeddev.recapp.util.FormatUtils
import com.alsaeeddev.recapp.util.ShareUtils

enum class LibraryTab(val label: String) {
    VIDEOS("Videos"),
    SCREENSHOTS("Screenshots"),
    FAVORITES("Favorites"),
    RECYCLE_BIN("Recycle Bin")
}

enum class SortOption(val label: String) {
    NEWEST("Newest First"),
    OLDEST("Oldest First"),
    NAME("Name (A-Z)"),
    SIZE("Size (Largest)")
}

@Composable
fun LibraryScreen(
    videoItems: List<RecordItem>,
    screenshotItems: List<RecordItem>,
    favoriteItems: List<RecordItem>,
    recycledItems: List<RecordItem>,
    onSelectItem: (RecordItem) -> Unit,
    onToggleFavorite: (RecordItem) -> Unit,
    onMoveToRecycleBin: (RecordItem) -> Unit,
    onRestoreItem: (RecordItem) -> Unit,
    onDeletePermanently: (RecordItem) -> Unit,
    onEmptyRecycleBin: () -> Unit,
    onRenameItem: (RecordItem, String) -> Unit
) {
    var selectedTab by remember { mutableStateOf(LibraryTab.VIDEOS) }
    var searchQuery by remember { mutableStateOf("") }
    var isGridView by remember { mutableStateOf(false) }
    var selectedSort by remember { mutableStateOf(SortOption.NEWEST) }
    var showSortDropdown by remember { mutableStateOf(false) }

    val rawItems = when (selectedTab) {
        LibraryTab.VIDEOS -> videoItems
        LibraryTab.SCREENSHOTS -> screenshotItems
        LibraryTab.FAVORITES -> favoriteItems
        LibraryTab.RECYCLE_BIN -> recycledItems
    }

    val filteredItems = remember(rawItems, searchQuery, selectedSort) {
        var list = if (searchQuery.isBlank()) rawItems else rawItems.filter { it.title.contains(searchQuery, ignoreCase = true) }
        when (selectedSort) {
            SortOption.NEWEST -> list.sortedByDescending { it.timestamp }
            SortOption.OLDEST -> list.sortedBy { it.timestamp }
            SortOption.NAME -> list.sortedBy { it.title.lowercase() }
            SortOption.SIZE -> list.sortedByDescending { it.sizeBytes }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Library Title & View Toggles
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Library",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${filteredItems.size} items stored",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (selectedTab == LibraryTab.RECYCLE_BIN && recycledItems.isNotEmpty()) {
                    IconButton(
                        onClick = onEmptyRecycleBin,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.Red.copy(alpha = 0.1f))
                    ) {
                        Icon(Icons.Default.DeleteForever, "Empty Bin", tint = Color.Red)
                    }
                }

                IconButton(
                    onClick = { showSortDropdown = true },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(Icons.Default.Sort, "Sort", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                DropdownMenu(
                    expanded = showSortDropdown,
                    onDismissRequest = { showSortDropdown = false }
                ) {
                    SortOption.values().forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                selectedSort = option
                                showSortDropdown = false
                            }
                        )
                    }
                }

                IconButton(
                    onClick = { isGridView = !isGridView },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(
                        imageVector = if (isGridView) Icons.Default.List else Icons.Default.GridView,
                        contentDescription = "Grid/List Toggle",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search recordings...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            shape = RoundedCornerShape(16.dp),
            singleLine = true
        )

        // Scrollable Folder Tabs
        ScrollableTabRow(
            selectedTabIndex = selectedTab.ordinal,
            edgePadding = 0.dp,
            divider = {},
            containerColor = Color.Transparent,
            indicator = {}
        ) {
            LibraryTab.values().forEach { tab ->
                val selected = selectedTab == tab
                Tab(
                    selected = selected,
                    onClick = { selectedTab = tab },
                    text = {
                        Text(
                            text = tab.label,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) BentoPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (selected) BentoPrimary.copy(alpha = 0.12f) else Color.Transparent)
                        .testTag("library_tab_${tab.name}")
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Items List or Grid
        if (filteredItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "No files found",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        } else if (isGridView) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filteredItems, key = { it.id }) { item ->
                    LibraryGridCard(
                        item = item,
                        isRecycleBin = selectedTab == LibraryTab.RECYCLE_BIN,
                        onClick = { onSelectItem(item) },
                        onFavoriteToggle = { onToggleFavorite(item) },
                        onRecycle = { onMoveToRecycleBin(item) },
                        onRestore = { onRestoreItem(item) },
                        onDeletePermanent = { onDeletePermanently(item) }
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filteredItems, key = { it.id }) { item ->
                    LibraryListCard(
                        item = item,
                        isRecycleBin = selectedTab == LibraryTab.RECYCLE_BIN,
                        onClick = { onSelectItem(item) },
                        onFavoriteToggle = { onToggleFavorite(item) },
                        onRecycle = { onMoveToRecycleBin(item) },
                        onRestore = { onRestoreItem(item) },
                        onDeletePermanent = { onDeletePermanently(item) }
                    )
                }
            }
        }
    }
}

@Composable
fun LibraryListCard(
    item: RecordItem,
    isRecycleBin: Boolean,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onRecycle: () -> Unit,
    onRestore: () -> Unit,
    onDeletePermanent: () -> Unit
) {
    val context = LocalContext.current

    BentoCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        onClick = onClick,
        testTag = "library_item_${item.id}"
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (item.mediaType == MediaType.VIDEO) BentoPrimary else Color(
                            0xFFE2A000
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (item.mediaType == MediaType.VIDEO) Icons.Default.PlayArrow else Icons.Default.Image,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = "${FormatUtils.formatDate(item.timestamp)} • ${
                        FormatUtils.formatFileSize(
                            item.sizeBytes
                        )
                    }",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                if (!isRecycleBin) {
                    IconButton(onClick = onFavoriteToggle) {
                        Icon(
                            imageVector = if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (item.isFavorite) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = {
                        ShareUtils.shareRecordItem(context, item)
                    }) {
                        Icon(
                            Icons.Default.Share,
                            "Share",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = onRecycle) {
                        Icon(
                            Icons.Default.Delete,
                            "Recycle",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    IconButton(onClick = onRestore) {
                        Icon(Icons.Default.Restore, "Restore", tint = BentoPrimary)
                    }

                    IconButton(onClick = onDeletePermanent) {
                        Icon(Icons.Default.DeleteForever, "Delete Permanent", tint = Color.Red)
                    }
                }
            }
        }
    }
}

@Composable
fun LibraryGridCard(
    item: RecordItem,
    isRecycleBin: Boolean,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onRecycle: () -> Unit,
    onRestore: () -> Unit,
    onDeletePermanent: () -> Unit
) {
    BentoCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        onClick = onClick,
        testTag = "grid_item_${item.id}"
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (item.mediaType == MediaType.VIDEO) BentoPrimary else Color(
                            0xFFE2A000
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (item.mediaType == MediaType.VIDEO) Icons.Default.PlayArrow else Icons.Default.Image,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            Text(
                text = item.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = FormatUtils.formatFileSize(item.sizeBytes),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
