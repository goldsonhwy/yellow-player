package com.goldsonhwy.yellowplayer.ui.screens.directory

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceSelectScreen(navController: NavController) {
    val context = LocalContext.current
    var showPermissionDialog by remember { mutableStateOf(false) }
    var permissionDialogMessage by remember { mutableStateOf("") }
    var pendingSource by remember { mutableStateOf<VideoSource?>(null) }

    // ─── Launchers declared FIRST (their lambdas inline everything) ──

    val launcherReadMediaVideo = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val src = pendingSource ?: return@rememberLauncherForActivityResult
        if (granted) {
            navController.navigate(Routes.directory(src))
        } else {
            permissionDialogMessage = "需要「视频和照片」权限才能扫描本地视频。\n\n请前往系统设置中授予权限。"
            showPermissionDialog = true
        }
    }

    val launcherReadStorage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val src = pendingSource ?: return@rememberLauncherForActivityResult
        if (granted) {
            navController.navigate(Routes.directory(src))
        } else {
            permissionDialogMessage = "需要「存储」权限才能扫描本地视频。"
            showPermissionDialog = true
        }
    }

    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        val src = pendingSource ?: return@rememberLauncherForActivityResult
        if (Environment.isExternalStorageManager()) {
            navController.navigate(Routes.directory(src))
        } else {
            permissionDialogMessage = "未获得文件访问权限，无法读取本地视频。" +
                    "\n\n请在系统设置中手动授予权限。"
            showPermissionDialog = true
        }
    }

    // ─── Click handler (inlined, no forward references) ─────

    fun onLocalStorageClick() {
        val source = VideoSource.LOCAL
        when {
            Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager() -> {
                pendingSource = source
                permissionDialogMessage = "Yellow Player 需要「所有文件访问权限」才能读取本地视频文件，" +
                        "包括 .nomedia 文件夹中的内容。\n\n" +
                        "请点击「去授权」→ 找到 Yellow Player → 开启「允许管理所有文件」。"
                showPermissionDialog = true
            }
            Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_MEDIA_VIDEO
            ) != PackageManager.PERMISSION_GRANTED -> {
                pendingSource = source
                launcherReadMediaVideo.launch(Manifest.permission.READ_MEDIA_VIDEO)
            }
            Build.VERSION.SDK_INT < 30 && ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED -> {
                pendingSource = source
                launcherReadStorage.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            else -> navController.navigate(Routes.directory(source))
        }
    }

    // ─── UI ──────────────────────────────────────────────────

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

            SourceCard(
                title = "本地存储",
                subtitle = Environment.getExternalStorageDirectory().path,
                icon = Icons.Default.Storage,
                onClick = { onLocalStorageClick() }
            )

            SourceCard(
                title = "外置存储",
                subtitle = "USB / OTG / SD 卡（需手动选择目录）",
                icon = Icons.Default.SdCard,
                onClick = {
                    navController.navigate(Routes.directory(VideoSource.EXTERNAL))
                }
            )

            SourceCard(
                title = "Samba 共享",
                subtitle = "NAS / 路由器 / 电脑共享（无需存储权限）",
                icon = Icons.Default.Lan,
                onClick = { navController.navigate(Routes.SAMBA_CONFIG) }
            )

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

            val permStatus = when {
                Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager() ->
                    "✅ 已获得完整文件访问权限（含 .nomedia）"
                Build.VERSION.SDK_INT >= 30 ->
                    "⚠️ 需要「所有文件访问权限」才能读取 .nomedia 视频"
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED ->
                    "✅ 已获得存储权限"
                else -> "⚠️ 需要存储权限"
            }
            Text(
                text = permStatus,
                color = if (permStatus.startsWith("✅")) TextSecondary else Yellow500,
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            )

            Text(
                text = "v0.0.8",
                color = TextHint,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }

    // ─── Permission Dialog ───────────────────────────────────

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            containerColor = DarkSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("需要权限", fontWeight = FontWeight.Bold) },
            text = { Text(permissionDialogMessage) },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionDialog = false
                        val src = pendingSource ?: return@Button
                        if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            manageStorageLauncher.launch(intent)
                        } else {
                            // Re-trigger permission check after dialog
                            onLocalStorageClick()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Yellow500)
                ) {
                    Text(if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) "去授权" else "重试", color = Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("取消", color = TextSecondary)
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
