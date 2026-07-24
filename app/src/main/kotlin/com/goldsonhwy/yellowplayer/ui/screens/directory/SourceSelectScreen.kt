package com.goldsonhwy.yellowplayer.ui.screens.directory

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.goldsonhwy.yellowplayer.data.model.VideoSource
import com.goldsonhwy.yellowplayer.ui.navigation.Routes
import com.goldsonhwy.yellowplayer.ui.theme.*

/**
 * First screen — choose video source with runtime permission handling.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceSelectScreen(navController: NavController) {
    val context = LocalContext.current
    var showPermissionDenied by remember { mutableStateOf(false) }

    // Runtime permission launcher for READ_MEDIA_VIDEO (Android 13+)
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            navController.navigate(
                Routes.directory(VideoSource.LOCAL)
            )
        } else {
            showPermissionDenied = true
        }
    }

    fun checkAndNavigate() {
        if (Build.VERSION.SDK_INT >= 33) {
            when {
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_MEDIA_VIDEO
                ) == PackageManager.PERMISSION_GRANTED -> {
                    navController.navigate(Routes.directory(VideoSource.LOCAL))
                }
                else -> {
                    permissionLauncher.launch(Manifest.permission.READ_MEDIA_VIDEO)
                }
            }
        } else {
            // Android 8-12: READ_EXTERNAL_STORAGE is requested at install time
            navController.navigate(Routes.directory(VideoSource.LOCAL))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Yellow Player",
                        fontWeight = FontWeight.Bold,
                        color = Yellow500
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                ),
                actions = {
                    IconButton(onClick = { navController.navigate(Routes.SETTINGS) }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "设置",
                            tint = TextSecondary
                        )
                    }
                }
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "选择视频源",
                color = TextPrimary,
                fontSize = 18.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Local Storage
            SourceCard(
                title = "本地存储",
                subtitle = Environment.getExternalStorageDirectory().path,
                icon = Icons.Default.Storage,
                onClick = { checkAndNavigate() }
            )

            // External / OTG
            SourceCard(
                title = "外置存储",
                subtitle = "USB / OTG / SD 卡",
                icon = Icons.Default.SdCard,
                onClick = {
                    navController.navigate(
                        Routes.directory(VideoSource.EXTERNAL)
                    )
                }
            )

            // Samba
            SourceCard(
                title = "Samba 共享",
                subtitle = "NAS / 路由器 / 电脑共享",
                icon = Icons.Default.Lan,
                onClick = { navController.navigate(Routes.SAMBA_CONFIG) }
            )

            // Favorites
            SourceCard(
                title = "收藏",
                subtitle = "你点赞过的视频",
                icon = Icons.Default.FavoriteBorder,
                tint = LikeRed,
                onClick = {
                    navController.navigate(
                        Routes.directory(VideoSource.LOCAL, "__favorites__")
                    )
                }
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "v1.0.0",
                color = TextHint,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }

    // Permission denied dialog
    if (showPermissionDenied) {
        AlertDialog(
            onDismissRequest = { showPermissionDenied = false },
            containerColor = DarkSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("需要权限", fontWeight = FontWeight.Bold) },
            text = {
                Text("Yellow Player 需要访问视频文件的权限才能扫描本地视频。请在系统设置中授予「视频和照片」权限。")
            },
            confirmButton = {
                TextButton(onClick = { showPermissionDenied = false }) {
                    Text("知道了", color = Yellow500)
                }
            }
        )
    }
}

@Composable
private fun SourceCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color = Yellow500,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(32.dp)
            )
            Column {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
        }
    }
}
