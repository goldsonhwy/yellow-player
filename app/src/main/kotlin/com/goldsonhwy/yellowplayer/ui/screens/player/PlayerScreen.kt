package com.goldsonhwy.yellowplayer.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import com.goldsonhwy.yellowplayer.data.model.VideoSource
import com.goldsonhwy.yellowplayer.ui.theme.*
import kotlinx.coroutines.delay

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

    var currentIndex by remember { mutableIntStateOf(startIndex.coerceAtLeast(0)) }
    var isPlaying by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(true) }
    var isFavorite by remember { mutableStateOf(false) }
    var currentSpeed by remember { mutableFloatStateOf(1f) }
    var longPressSpeed by remember { mutableFloatStateOf(2f) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekProgress by remember { mutableFloatStateOf(0f) }
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(source, folderPath) {
        viewModel.loadVideos(source, folderPath)
    }

    val videos = uiState.videos
    LaunchedEffect(videos) {
        if (videos.isNotEmpty()) {
            currentIndex = startIndex.coerceIn(0, videos.lastIndex)
        }
    }

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    LaunchedEffect(player) {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        })
    }

    LaunchedEffect(videos, currentIndex) {
        val video = videos.getOrNull(currentIndex)
        if (video != null) {
            player.stop()
            player.clearMediaItems()
            player.setMediaItem(MediaItem.fromUri(video.fileUri))
            player.prepare()
            player.playWhenReady = true
        }
    }

    LaunchedEffect(player) {
        while (true) {
            delay(500)
            val dur = player.duration
            progress = if (dur > 0) player.currentPosition.toFloat() / dur else 0f
        }
    }

    LaunchedEffect(showControls) {
        if (showControls) {
            delay(3000)
            showControls = false
        }
    }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black)
    ) {
        when {
            uiState.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("正在加载视频…", color = Yellow500, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
            uiState.error != null -> {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ErrorOutline, null, tint = LikeRed, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(uiState.error ?: "未知错误", color = White, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { navController.popBackStack() }, colors = ButtonDefaults.buttonColors(containerColor = Yellow500)) {
                            Text("返回", color = Black)
                        }
                    }
                }
            }
            videos.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.VideoLibrary, null, tint = TextHint, modifier = Modifier.size(56.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("这个文件夹里没有可播放视频", color = White, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { navController.popBackStack() }, colors = ButtonDefaults.buttonColors(containerColor = Yellow500)) {
                            Text("返回", color = Black)
                        }
                    }
                }
            }
            else -> {
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
        }

        if (isSeeking) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        if (seekProgress < 0) Icons.Default.FastRewind else Icons.Default.FastForward,
                        null,
                        tint = Yellow500,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("${(seekProgress * 100).toInt()}%", color = White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (showControls && videos.isNotEmpty()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))) {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "返回", tint = White)
                    }
                    Text(
                        text = videos.getOrNull(currentIndex)?.name ?: "",
                        color = White,
                        fontSize = 14.sp,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    Text("${currentIndex + 1}/${videos.size}", color = TextSecondary, fontSize = 12.sp)
                    IconButton(onClick = { isFavorite = !isFavorite }) {
                        Icon(
                            if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            "收藏",
                            tint = if (isFavorite) LikeRed else White
                        )
                    }
                }

                IconButton(
                    onClick = {
                        if (player.isPlaying) player.pause() else player.play()
                    },
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                        null,
                        tint = White.copy(alpha = 0.85f),
                        modifier = Modifier.size(64.dp)
                    )
                }

                Column(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(16.dp)
                ) {
                    if (currentSpeed != 1f) {
                        Surface(shape = MaterialTheme.shapes.small, color = Yellow500.copy(alpha = 0.9f)) {
                            Text("${currentSpeed}x", color = Black, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                    Box(Modifier.fillMaxWidth().height(3.dp).background(White.copy(alpha = 0.2f))) {
                        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(3.dp).background(Yellow500))
                    }
                }
            }
        }

        // Gesture layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(videos, currentIndex) {
                    detectTapGestures(
                        onTap = { showControls = !showControls },
                        onDoubleTap = { isFavorite = !isFavorite },
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
                            if (isSeeking) {
                                val duration = player.duration
                                if (duration > 0) {
                                    val seekTo = (player.currentPosition + duration * seekProgress).toLong()
                                    player.seekTo(seekTo.coerceIn(0, duration))
                                }
                                isSeeking = false
                                seekProgress = 0f
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            isSeeking = true
                            seekProgress = (seekProgress + dragAmount / 1200f).coerceIn(-0.5f, 0.5f)
                        }
                    )
                }
        )
    }
}
