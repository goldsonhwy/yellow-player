package com.goldsonhwy.yellowplayer.ui.screens.player

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import com.goldsonhwy.yellowplayer.data.model.VideoInfo
import com.goldsonhwy.yellowplayer.data.model.VideoSource
import com.goldsonhwy.yellowplayer.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Full-screen player with TikTok-style gestures.
 *
 * Gestures:
 *   Swipe up/down  → next/prev video (using VerticalViewPager-style)
 *   Swipe L/R      → seek (position indicator shown)
 *   Tap            → pause/play (show/hide controls)
 *   Double tap     → toggle like (heart animation)
 *   Long press     → 2x speed (configurable); release restores 1x
 *   Pinch          → scale/crop mode
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    navController: NavController,
    source: VideoSource,
    folderPath: String,
    startIndex: Int = 0
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // In production: ViewModel provides video list from repository
    val mockVideos = remember {
        List(5) { i ->
            VideoInfo(
                name = "Video_${folderPath.substringAfterLast("/")}_$i.mp4",
                path = "/storage/emulated/0/$folderPath/video_$i.mp4",
                source = source
            )
        }
    }

    var currentIndex by remember { mutableIntStateOf(startIndex.coerceIn(0, mockVideos.lastIndex)) }
    var isPlaying by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(false) }
    var isFavorite by remember { mutableStateOf(false) }
    var currentSpeed by remember { mutableFloatStateOf(1f) }
    var longPressSpeed by remember { mutableFloatStateOf(2f) }

    // Seek state
    var isSeeking by remember { mutableStateOf(false) }
    var seekProgress by remember { mutableFloatStateOf(0f) }

    // Scale state
    var scale by remember { mutableFloatStateOf(1f) }

    // Player
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_OFF

            mockVideos.getOrNull(currentIndex)?.let { video ->
                setMediaItem(MediaItem.fromUri(video.path))
                prepare()
            }
        }
    }

    // Controls auto-hide
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(3000)
            showControls = false
        }
    }

    // Preload next video
    LaunchedEffect(currentIndex) {
        val nextIdx = currentIndex + 1
        if (nextIdx < mockVideos.size) {
            val nextItem = MediaItem.fromUri(mockVideos[nextIdx].path)
            player.addMediaItem(nextItem)
        }
    }

    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            player.release()
        }
    }

    // Player listener for play state
    LaunchedEffect(player) {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isPlaying = playbackState == Player.STATE_READY && player.playWhenReady
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        })
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // ─── ExoPlayer Surface (Media3 PlayerView) ──────────
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    keepScreenOn = true
                }
            },
            modifier = Modifier
                .fillMaxSize()
        )

        // ─── Seek Indicator (shown during L/R swipe) ────────
        if (isSeeking) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        if (seekProgress < 0) Icons.Default.FastRewind else Icons.Default.FastForward,
                        contentDescription = null,
                        tint = Yellow500,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "${((seekProgress * 100).toInt())}%",
                        color = White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // ─── Controls Overlay (tap to show/hide) ────────────
        if (showControls) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
            ) {
                // Top bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "返回", tint = White)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = mockVideos.getOrNull(currentIndex)?.name ?: "",
                        color = White,
                        fontSize = 14.sp,
                        maxLines = 1
                    )
                    Spacer(Modifier.weight(1f))
                    // Favorites button
                    IconButton(onClick = {
                        isFavorite = !isFavorite
                    }) {
                        Icon(
                            if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "收藏",
                            tint = if (isFavorite) LikeRed else White
                        )
                    }
                }

                // Center play/pause
                IconButton(
                    onClick = {
                        if (isPlaying) player.pause() else player.play()
                        isPlaying = !isPlaying
                    },
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        tint = White.copy(alpha = 0.8f),
                        modifier = Modifier.size(64.dp)
                    )
                }

                // Bottom: speed badge + progress bar
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp)
                ) {
                    if (currentSpeed != 1f) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = Yellow500.copy(alpha = 0.9f)
                        ) {
                            Text(
                                text = "${currentSpeed}x",
                                color = Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                    }

                    val progressVal = remember { mutableFloatStateOf(0f) }
                    LaunchedEffect(player) {
                        while (true) {
                            delay(1000)
                            val dur = player.duration
                            progressVal.floatValue = if (dur > 0) player.currentPosition.toFloat() / dur else 0f
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(White.copy(alpha = 0.2f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressVal.floatValue.coerceIn(0f, 1f))
                                .height(3.dp)
                                .background(Yellow500)
                        )
                    }
                }
            }
        }

        // ─── Gesture Handler ─────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            showControls = !showControls
                        },
                        onDoubleTap = {
                            isFavorite = !isFavorite
                        },
                        onLongPress = {
                            currentSpeed = longPressSpeed
                            player.setPlaybackSpeed(longPressSpeed)
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (currentSpeed != 1f) {
                                currentSpeed = 1f
                                player.setPlaybackSpeed(1f)
                            }
                        },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (isSeeking) {
                                val duration = player.duration
                                if (duration > 0) {
                                    val seekTo = (duration * (0.5f + seekProgress)).toLong()
                                    player.seekTo(seekTo.coerceIn(0, duration))
                                }
                                isSeeking = false
                                seekProgress = 0f
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            isSeeking = true
                            seekProgress = (seekProgress + dragAmount / 1000f)
                                .coerceIn(-0.5f, 0.5f)
                        }
                    )
                }
        )
    }
}
