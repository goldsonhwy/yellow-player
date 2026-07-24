package com.goldsonhwy.yellowplayer.ui.screens.player

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import com.goldsonhwy.yellowplayer.data.model.VideoSource
import com.goldsonhwy.yellowplayer.smb.SmbStreamDataSource
import com.goldsonhwy.yellowplayer.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun PlayerScreen(
    navController: NavController,
    source: VideoSource,
    folderPath: String,
    startIndex: Int = 0,
    viewModel: PlayerViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()

    var deletedPaths by remember { mutableStateOf(setOf<String>()) }
    val videos = uiState.videos.filterNot { it.path in deletedPaths }

    var currentIndex by remember { mutableIntStateOf(startIndex.coerceAtLeast(0)) }
    var isFavorite by remember { mutableStateOf(false) }
    var showLikeAnimation by remember { mutableStateOf(false) }
    var currentSpeed by remember { mutableFloatStateOf(1f) }
    val longPressSpeed = 2f
    var seekProgress by remember { mutableFloatStateOf(0f) }
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(source, folderPath) {
        viewModel.loadVideos(source, folderPath)
    }

    LaunchedEffect(videos) {
        if (videos.isNotEmpty()) currentIndex = currentIndex.coerceIn(0, videos.lastIndex)
    }

    val player = remember {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(SmbStreamDataSource.Factory(context)))
            .build().apply {
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_ONE
        }
    }

    LaunchedEffect(videos, currentIndex) {
        val video = videos.getOrNull(currentIndex)
        if (video != null) {
            // Do not clear/stop first; this reduces black flicker when switching videos.
            player.setMediaItem(MediaItem.fromUri(video.fileUri), 0L)
            player.prepare()
            player.setPlaybackSpeed(currentSpeed)
            player.playWhenReady = true
        }
    }

    LaunchedEffect(player) {
        while (true) {
            delay(400)
            val dur = player.duration
            progress = if (dur > 0) player.currentPosition.toFloat() / dur else 0f
        }
    }

    LaunchedEffect(showLikeAnimation) {
        if (showLikeAnimation) {
            delay(700)
            showLikeAnimation = false
        }
    }

    val currentPathForDispose by rememberUpdatedState(videos.getOrNull(currentIndex)?.path)

    DisposableEffect(Unit) {
        onDispose {
            val path = currentPathForDispose
            player.release()
            if (path != null) {
                viewModel.moveFavoriteAfterReleaseAsync(path)
            }
        }
    }

    LaunchedEffect(videos.getOrNull(currentIndex)?.path) {
        val video = videos.getOrNull(currentIndex)
        isFavorite = if (video != null) viewModel.isFavorite(video.path) else false
    }

    fun toggleCurrentFavorite(showAnimation: Boolean = true) {
        val video = videos.getOrNull(currentIndex) ?: return
        scope.launch {
            val result = viewModel.toggleFavorite(video)
            isFavorite = result != null
            if (showAnimation && isFavorite) showLikeAnimation = true
        }
    }

    fun shareCurrentVideo() {
        val video = videos.getOrNull(currentIndex) ?: return
        if (source != VideoSource.LOCAL) {
            Toast.makeText(context, "当前仅支持分享本地文件", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val file = File(video.path)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = video.mimeType.ifEmpty { "video/*" }
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "分享视频"))
        } catch (t: Throwable) {
            Toast.makeText(context, "分享失败：${t.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun deleteCurrentVideo() {
        val video = videos.getOrNull(currentIndex) ?: return
        if (source != VideoSource.LOCAL) {
            Toast.makeText(context, "当前仅支持删除本地文件", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val file = File(video.path)
            file.delete()
            deletedPaths = deletedPaths + video.path
            val nextSize = videos.size - 1
            currentIndex = when {
                nextSize <= 0 -> 0
                currentIndex >= nextSize -> nextSize - 1
                else -> currentIndex
            }
        } catch (t: Throwable) {
            Toast.makeText(context, "删除失败：${t.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun stepFrame(deltaMs: Long) {
        player.playWhenReady = false
        player.pause()
        val duration = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        val target = (player.currentPosition + deltaMs).coerceIn(0L, duration)
        player.seekTo(target)
        player.playWhenReady = false
        player.pause()
    }

    fun switchToIndex(newIndex: Int) {
        val oldPath = videos.getOrNull(currentIndex)?.path
        currentIndex = newIndex.coerceIn(0, videos.lastIndex)
        if (oldPath != null) {
            scope.launch {
                delay(350)
                viewModel.moveFavoriteAfterRelease(oldPath)
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (videos.isNotEmpty()) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = false
                        keepScreenOn = true
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Top progress bar.
        if (videos.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(Yellow500.copy(alpha = 0.22f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0.02f, 1f))
                            .height(8.dp)
                            .background(Yellow500)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(0.5f, 1f, 1.5f, 2f, 3f, 4f, 5f).forEach { speed ->
                        androidx.compose.material3.Surface(
                            modifier = Modifier.clickable {
                                currentSpeed = speed
                                player.setPlaybackSpeed(speed)
                            },
                            shape = androidx.compose.material3.MaterialTheme.shapes.extraLarge,
                            color = if (currentSpeed == speed) Yellow500 else DarkSurfaceVariant
                        ) {
                            androidx.compose.material3.Text(
                                text = if (speed == 1f) "正常" else "${speed}x",
                                color = if (currentSpeed == speed) Black else White,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }

        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                androidx.compose.material3.Text("正在加载视频…", color = Yellow500)
            }
        } else if (videos.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                androidx.compose.material3.Text("没有可播放的视频", color = White)
            }
        }

        if (showLikeAnimation) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = null,
                    tint = LikeRed,
                    modifier = Modifier.size(132.dp)
                )
            }
        }

        // Bottom: five equally spaced buttons.
        if (videos.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CapsuleIconButton(
                    onClick = { stepFrame(-33L) },
                    modifier = Modifier.weight(1f),
                    contentDescription = "逐帧后退"
                ) {
                    Icon(Icons.Default.SkipPrevious, "逐帧后退", tint = White, modifier = Modifier.size(24.dp))
                }

                CapsuleIconButton(
                    onClick = { shareCurrentVideo() },
                    modifier = Modifier.weight(1f),
                    contentDescription = "分享"
                ) {
                    Icon(Icons.Default.Share, "分享", tint = White, modifier = Modifier.size(24.dp))
                }

                CapsuleIconButton(
                    onClick = { toggleCurrentFavorite(showAnimation = true) },
                    modifier = Modifier.weight(1f),
                    contentDescription = "收藏"
                ) {
                    Icon(
                        if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        "收藏",
                        tint = if (isFavorite) LikeRed else White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                CapsuleIconButton(
                    onClick = { deleteCurrentVideo() },
                    modifier = Modifier.weight(1f),
                    contentDescription = "删除"
                ) {
                    Icon(Icons.Default.Delete, "删除", tint = White, modifier = Modifier.size(24.dp))
                }

                CapsuleIconButton(
                    onClick = { stepFrame(33L) },
                    modifier = Modifier.weight(1f),
                    contentDescription = "逐帧前进"
                ) {
                    Icon(Icons.Default.SkipNext, "逐帧前进", tint = White, modifier = Modifier.size(24.dp))
                }
            }
        }

        // Gesture layer: tap toggles pause/play directly; no dark overlay, no icons.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 44.dp)
                .navigationBarsPadding()
                .padding(bottom = 92.dp)
                .pointerInput(videos, currentIndex) {
                    detectTapGestures(
                        onTap = {
                            if (player.isPlaying) player.pause() else player.play()
                        },
                        onDoubleTap = {
                            toggleCurrentFavorite(showAnimation = true)
                        },
                        onLongPress = {
                            currentSpeed = longPressSpeed
                            player.setPlaybackSpeed(longPressSpeed)
                        }
                    )
                }
                .pointerInput(videos, currentIndex) {
                    var totalDrag = 0f
                    detectVerticalDragGestures(
                        onDragStart = { totalDrag = 0f },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            totalDrag += dragAmount
                        },
                        onDragEnd = {
                            if (videos.isNotEmpty()) {
                                when {
                                    totalDrag < -120f && currentIndex < videos.lastIndex -> switchToIndex(currentIndex + 1)
                                    totalDrag > 120f && currentIndex > 0 -> switchToIndex(currentIndex - 1)
                                }
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val duration = player.duration
                            if (duration > 0 && seekProgress != 0f) {
                                val seekTo = (player.currentPosition + duration * seekProgress).toLong()
                                player.seekTo(seekTo.coerceIn(0, duration))
                            }
                            seekProgress = 0f
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            seekProgress = (seekProgress + dragAmount / 1200f).coerceIn(-0.5f, 0.5f)
                        }
                    )
                }
        )
    }
}

@Composable
private fun CapsuleIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String,
    content: @Composable () -> Unit
) {
    androidx.compose.material3.Surface(
        modifier = modifier
            .padding(horizontal = 3.dp)
            .height(38.dp)
            .clickable(onClick = onClick),
        shape = androidx.compose.material3.MaterialTheme.shapes.extraLarge,
        color = DarkSurfaceVariant
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}
