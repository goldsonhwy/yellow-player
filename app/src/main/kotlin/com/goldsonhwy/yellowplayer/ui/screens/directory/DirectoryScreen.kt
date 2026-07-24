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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.goldsonhwy.yellowplayer.data.model.VideoFolder
import com.goldsonhwy.yellowplayer.data.model.VideoInfo
import com.goldsonhwy.yellowplayer.data.model.VideoSource
import com.goldsonhwy.yellowplayer.ui.navigation.Routes
import com.goldsonhwy.yellowplayer.ui.theme.*

/**
 * Directory grid screen — 3-column thumbnail grid showing video folders or files.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectoryScreen(
    navController: NavController,
    source: VideoSource,
    serverId: Long = 0,
    folderPath: String = ""
) {
    val context = LocalContext.current
    val gridState = rememberLazyGridState()

    // Mock data for structure demonstration
    val isFavorites = folderPath == "__favorites__"
    val title = when {
        isFavorites -> "收藏"
        folderPath.isNotEmpty() -> folderPath.substringAfterLast("/")
            .ifEmpty { "视频" }
        else -> "视频"
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
                    // Sort button
                    IconButton(onClick = { /* TODO: sort dialog */ }) {
                        Icon(Icons.Default.Sort, "排序", tint = TextSecondary)
                    }
                }
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        if (isFavorites) {
            // Favorites empty state
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
        } else {
            // 3-column grid of video folders/files
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
                // Mock folders for structure demo
                val mockFolders = listOf(
                    VideoFolder("DCIM/Camera", "Camera", 12, "", source, serverId),
                    VideoFolder("Movies", "Movies", 5, "", source, serverId),
                    VideoFolder("Download", "Download", 8, "", source, serverId),
                    VideoFolder("WhatsApp/Video", "WhatsApp Video", 23, "", source, serverId),
                    VideoFolder("Telegram", "Telegram", 15, "", source, serverId),
                    VideoFolder("screen_record", "录屏", 3, "", source, serverId),
                    VideoFolder("weixin", "微信", 9, "", source, serverId),
                    VideoFolder("douyin", "抖音", 7, "", source, serverId),
                    VideoFolder("bilibili", "Bilibili", 4, "", source, serverId),
                )

                items(mockFolders, key = { it.path }) { folder ->
                    VideoFolderCard(
                        folder = folder,
                        onClick = {
                            // Navigate to player with this folder's videos
                            navController.navigate(
                                Routes.player(source, folder.path, 0)
                            )
                        }
                    )
                }
            }
        }
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
        // Thumbnail area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            if (folder.thumbnailPath.isNotEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(Uri.parse(folder.thumbnailPath))
                        .crossfade(true)
                        .build(),
                    contentDescription = folder.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Placeholder icon
                Icon(
                    Icons.Default.PlayCircleOutline,
                    contentDescription = null,
                    tint = TextHint,
                    modifier = Modifier.size(36.dp)
                )
            }

            // Video count badge
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp),
                shape = MaterialTheme.shapes.extraSmall,
                color = Black.copy(alpha = 0.7f)
            ) {
                Text(
                    text = "${folder.videoCount}",
                    color = White,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }

        // Folder name
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
