package com.goldsonhwy.yellowplayer.ui.screens.player

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import com.goldsonhwy.yellowplayer.data.model.VideoSource
import com.goldsonhwy.yellowplayer.ui.theme.*
import kotlinx.coroutines.delay
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
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    LaunchedEffect(videos, currentIndex) {
        val video = videos.getOrNull(currentIndex)
        if (video != null) {
            // Do not clear/stop first; this reduces black flicker when switching videos.
            player.setMediaItem(MediaItem.fromUri(video.fileUri), 0L)
            player.prepare()
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

    DisposableEffect(Unit) {
        onDispose { player.release() }
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
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(White.copy(alpha = 0.25f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(3.dp)
                        .background(Yellow500)
                )
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

        // Bottom: only three buttons. Center=share, between center/right=like, right=delete.
        if (videos.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 22.dp)
            ) {
                IconButton(
                    onClick = { shareCurrentVideo() },
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Icon(Icons.Default.Share, "分享", tint = White, modifier = Modifier.size(34.dp))
                }

                IconButton(
                    onClick = {
                        isFavorite = !isFavorite
                        showLikeAnimation = true
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 76.dp)
                ) {
                    Icon(
                        if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        "点赞",
                        tint = if (isFavorite) LikeRed else White,
                        modifier = Modifier.size(34.dp)
                    )
                }

                IconButton(
                    onClick = { deleteCurrentVideo() },
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 18.dp)
                ) {
                    Icon(Icons.Default.Delete, "删除", tint = White, modifier = Modifier.size(34.dp))
                }
            }
        }

        // Gesture layer: tap toggles pause/play directly; no dark overlay, no icons.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(videos, currentIndex) {
                    detectTapGestures(
                        onTap = {
                            if (player.isPlaying) player.pause() else player.play()
                        },
                        onDoubleTap = {
                            isFavorite = !isFavorite
                            showLikeAnimation = true
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
                            if (currentSpeed != 1f) {
                                currentSpeed = 1f
                                player.setPlaybackSpeed(1f)
                            }
                            if (videos.isNotEmpty()) {
                                when {
                                    totalDrag < -120f && currentIndex < videos.lastIndex -> currentIndex++
                                    totalDrag > 120f && currentIndex > 0 -> currentIndex--
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
