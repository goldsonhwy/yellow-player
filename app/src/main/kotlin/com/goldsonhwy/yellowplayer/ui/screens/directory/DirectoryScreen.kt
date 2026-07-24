package com.goldsonhwy.yellowplayer.ui.screens.directory

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.goldsonhwy.yellowplayer.data.model.VideoFolder
import com.goldsonhwy.yellowplayer.data.model.VideoInfo
import com.goldsonhwy.yellowplayer.data.model.VideoSource
import com.goldsonhwy.yellowplayer.ui.navigation.Routes
import com.goldsonhwy.yellowplayer.ui.theme.*

/**
 * Directory screen — shows a 3-column grid of video folders or files.
 * Uses ViewModel with real VideoScanner for local storage.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectoryScreen(
    navController: NavController,
    source: VideoSource,
    serverId: Long = 0,
    folderPath: String = "",
    viewModel: DirectoryViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val gridState = rememberLazyGridState()
    var showSortDialog by remember { mutableStateOf(false) }
    var sortMode by remember { mutableStateOf("name") }
    var moveProgressText by remember { mutableStateOf("") }

    val isFavorites = folderPath == "__favorites__"
    val isRootLevel = folderPath.isEmpty()

    // Load data on first composition
    LaunchedEffect(source, folderPath) {
        when {
            isFavorites -> { /* Load favorites from Room */ }
            isRootLevel -> viewModel.loadFolders(source, serverId)
            else -> viewModel.loadVideosInFolder(source, folderPath, serverId)
        }
    }

    val title = when {
        isFavorites -> "收藏"
        isRootLevel -> when (source) {
            VideoSource.LOCAL -> "本地存储"
            VideoSource.EXTERNAL -> "外置存储"
            VideoSource.SAMBA -> "Samba"
        }
        else -> folderPath.substringAfterLast("/").ifEmpty { "视频" }
    }

    val sortedFolders = remember(uiState.folders, sortMode) {
        when (sortMode) {
            "count" -> uiState.folders.sortedByDescending { it.videoCount }
            "name_desc" -> uiState.folders.sortedByDescending { it.name }
            else -> uiState.folders.sortedBy { it.name }
        }
    }
    val sortedVideos = remember(uiState.videos, sortMode) {
        when (sortMode) {
            "date" -> uiState.videos.sortedByDescending { it.dateModified }
            "size" -> uiState.videos.sortedByDescending { it.size }
            "name_desc" -> uiState.videos.sortedByDescending { it.name }
            else -> uiState.videos.sortedBy { it.name }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            val all = context.getSharedPreferences("favorite_move_progress", android.content.Context.MODE_PRIVATE).all
            moveProgressText = all.entries.firstOrNull()?.let { entry ->
                val parts = entry.value.toString().split('/')
                val copied = parts.getOrNull(0)?.toLongOrNull() ?: 0L
                val total = parts.getOrNull(1)?.toLongOrNull() ?: 1L
                "正在移动收藏文件：${((copied * 100) / total.coerceAtLeast(1L))}%"
            }.orEmpty()
            kotlinx.coroutines.delay(1000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        title,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "返回", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground),
                actions = {
                    IconButton(onClick = { showSortDialog = true }) {
                        Icon(Icons.Default.Sort, "排序", tint = TextSecondary)
                    }
                }
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        when {
            // Loading state
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        SafeLoadingIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("正在扫描视频…", color = TextSecondary, fontSize = 14.sp)
                    }
                }
            }

            // Error state
            uiState.error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = LikeRed,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = uiState.error ?: "未知错误",
                            color = TextSecondary,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Favorites — empty state
            isFavorites -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.FavoriteBorder,
                            contentDescription = null,
                            tint = LikeRed,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("还没有收藏视频", color = TextSecondary, fontSize = 16.sp)
                        Text("在全屏播放时双击 ❤️ 即可收藏", color = TextHint, fontSize = 14.sp)
                    }
                }
            }

            // Empty state
            uiState.folders.isEmpty() && uiState.videos.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.VideoLibrary,
                            contentDescription = null,
                            tint = TextHint,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("没有找到视频文件", color = TextSecondary, fontSize = 16.sp)
                        Text("支持 mp4, mkv, avi, mov, flv, webm 等格式", color = TextHint, fontSize = 13.sp)
                    }
                }
            }

            // Folder grid (root level)
            !uiState.isSingleFolder && uiState.folders.isNotEmpty() -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 4.dp),
                    state = gridState,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(sortedFolders, key = { it.path }) { folder ->
                        VideoFolderCard(
                            folder = folder,
                            onClick = {
                                if (source == VideoSource.SAMBA) {
                                    navController.navigate(
                                        Routes.sambaBrowse(serverId, folder.path)
                                    )
                                } else {
                                    // Open the folder grid first. Older builds jumped directly
                                    // to PlayerScreen with a folder path, which could crash or
                                    // show mock videos instead of the real file list.
                                    navController.navigate(
                                        Routes.directory(source, folder.path)
                                    )
                                }
                            }
                        )
                    }
                }
            }

            // Video grid (inside a folder)
            uiState.isSingleFolder && uiState.videos.isNotEmpty() -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 4.dp),
                    state = gridState,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    if (moveProgressText.isNotEmpty()) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            Text(moveProgressText, color = Yellow500, modifier = Modifier.padding(8.dp), fontSize = 13.sp)
                        }
                    }
                    items(sortedVideos, key = { it.path }) { video ->
                        VideoThumbnailCard(
                            video = video,
                            onClick = {
                                val idx = uiState.videos.indexOf(video)
                                navController.navigate(
                                    Routes.player(source, uiState.currentFolderPath, idx)
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    if (showSortDialog) {
        AlertDialog(
            onDismissRequest = { showSortDialog = false },
            containerColor = DarkSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("排序方式", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    listOf(
                        "name" to "名称 A-Z",
                        "name_desc" to "名称 Z-A",
                        "date" to "最新优先",
                        "size" to "大文件优先",
                        "count" to "文件夹视频数"
                    ).forEach { (key, label) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = sortMode == key,
                                onClick = { sortMode = key; showSortDialog = false },
                                colors = RadioButtonDefaults.colors(selectedColor = Yellow500)
                            )
                            Text(label, color = TextPrimary)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSortDialog = false }) {
                    Text("关闭", color = Yellow500)
                }
            }
        )
    }
}

@Composable
private fun VideoFolderCard(
    folder: VideoFolder,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .aspectRatio(0.75f)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .background(DarkSurfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            if (folder.thumbnailPath.isNotEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(Uri.fromFile(java.io.File(folder.thumbnailPath)))
                        .crossfade(true)
                        .size(180)
                        .build(),
                    contentDescription = folder.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = Yellow500.copy(alpha = 0.6f),
                    modifier = Modifier.size(36.dp)
                )
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp),
                shape = MaterialTheme.shapes.extraSmall,
                color = Color.Black.copy(alpha = 0.7f)
            ) {
                Text(
                    text = "${folder.videoCount}",
                    color = Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }

        Text(
            text = folder.name,
            color = TextPrimary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun VideoThumbnailCard(
    video: VideoInfo,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .aspectRatio(0.75f)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .background(DarkSurfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            // Load thumbnail from real video file
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(video.fileUri)
                    .crossfade(true)
                    .size(180)
                    .build(),
                contentDescription = video.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Duration badge
            if (video.duration > 0) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp),
                    shape = MaterialTheme.shapes.extraSmall,
                    color = Color.Black.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = formatDuration(video.duration),
                        color = Color.White,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }

        Text(
            text = video.name,
            color = TextPrimary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
        )
    }
}

private fun formatDuration(millis: Long): String {
    val totalSec = millis / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}

@Composable
private fun SafeLoadingIndicator() {
    // Avoid Material3 CircularProgressIndicator here: v0.0.7 crash logs showed
    // a NoSuchMethodError inside its keyframes animation on Android SDK 36.
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = DarkSurfaceVariant
    ) {
        Text(
            text = "扫描中",
            color = Yellow500,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}
