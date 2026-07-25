package com.goldsonhwy.yellowplayer.ui.screens.directory

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.goldsonhwy.yellowplayer.data.model.VideoSource
import com.goldsonhwy.yellowplayer.data.scanner.LocalGalleryCache
import com.goldsonhwy.yellowplayer.ui.navigation.Routes
import com.goldsonhwy.yellowplayer.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceSelectScreen(navController: NavController) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("local_video_folders", Context.MODE_PRIVATE) }
    val favoritePrefs = remember { context.getSharedPreferences("favorite_move", Context.MODE_PRIVATE) }
    var savedFolders by remember { mutableStateOf(prefs.getStringSet("paths", emptySet()).orEmpty().toList().sorted()) }
    var favoriteDir by remember { mutableStateOf(favoritePrefs.getString("dir", "").orEmpty()) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var permissionDialogMessage by remember { mutableStateOf("") }
    var pendingSource by remember { mutableStateOf<VideoSource?>(null) }
    var showFolderManager by remember { mutableStateOf(false) }

    fun refreshSavedFolders() {
        savedFolders = prefs.getStringSet("paths", emptySet()).orEmpty().toList().sorted()
    }

    fun saveFolder(path: String) {
        val set = prefs.getStringSet("paths", emptySet()).orEmpty().toMutableSet()
        set.add(path)
        prefs.edit().putStringSet("paths", set).apply()
        refreshSavedFolders()
        LocalGalleryCache.clearAndRestart(context)
    }

    fun removeFolder(path: String) {
        val set = prefs.getStringSet("paths", emptySet()).orEmpty().toMutableSet()
        set.remove(path)
        prefs.edit().putStringSet("paths", set).apply()
        refreshSavedFolders()
        LocalGalleryCache.clearAndRestart(context)
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) { }
            val path = treeUriToPrimaryPath(uri)
            if (path != null) {
                saveFolder(path)
                showFolderManager = true
            } else {
                permissionDialogMessage = "当前只支持手机主存储目录。请选择内部存储中的视频文件夹。"
                showPermissionDialog = true
            }
        }
    }

    val favoriteDirPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val path = treeUriToPrimaryPath(uri)
            if (path != null) {
                favoritePrefs.edit().putString("dir", path).apply()
                favoriteDir = path
            }
        }
    }

    val launcherReadMediaVideo = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) folderPickerLauncher.launch(null) else {
            permissionDialogMessage = "需要「视频和照片」权限才能扫描本地视频。"
            showPermissionDialog = true
        }
    }

    val launcherReadStorage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) folderPickerLauncher.launch(null) else {
            permissionDialogMessage = "需要「存储」权限才能扫描本地视频。"
            showPermissionDialog = true
        }
    }

    val manageStorageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()) {
            folderPickerLauncher.launch(null)
        } else {
            permissionDialogMessage = "未获得文件访问权限，无法读取本地视频。请在系统设置中手动授予权限。"
            showPermissionDialog = true
        }
    }

    fun ensurePermissionThenPickFolder() {
        val source = VideoSource.LOCAL
        when {
            Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager() -> {
                pendingSource = source
                permissionDialogMessage = "Yellow Player 需要「所有文件访问权限」才能读取本地视频文件，包括 .nomedia 文件夹中的内容。\n\n请点击「去授权」→ 找到 Yellow Player → 开启「允许管理所有文件」。"
                showPermissionDialog = true
            }
            Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED -> {
                pendingSource = source
                launcherReadMediaVideo.launch(Manifest.permission.READ_MEDIA_VIDEO)
            }
            Build.VERSION.SDK_INT < 30 && ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED -> {
                pendingSource = source
                launcherReadStorage.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            else -> folderPickerLauncher.launch(null)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Yellow Player", fontWeight = FontWeight.Bold, color = Yellow500) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground),
                actions = {
                    IconButton(onClick = { navController.navigate(Routes.SETTINGS) }) {
                        Icon(Icons.Default.Settings, "设置", tint = TextSecondary)
                    }
                }
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("选择视频源", color = TextPrimary, fontSize = 18.sp, modifier = Modifier.padding(bottom = 8.dp))

            SourceCard(
                title = "本地视频画廊",
                subtitle = if (savedFolders.isEmpty()) "还没有添加文件夹，请先添加" else "已添加 ${savedFolders.size} 个视频文件夹，点击进入画廊",
                icon = Icons.Default.VideoLibrary,
                onClick = {
                    if (savedFolders.isEmpty()) showFolderManager = true
                    else navController.navigate(Routes.directory(VideoSource.LOCAL))
                }
            )

            SourceCard(
                title = "添加 / 管理本地文件夹",
                subtitle = "添加视频文件夹，也可以删除已添加的文件夹",
                icon = Icons.Default.CreateNewFolder,
                onClick = { showFolderManager = true }
            )

            SourceCard(
                title = "选择收藏目录",
                subtitle = favoriteDir.ifEmpty { "未设置：请先选择收藏视频移动到哪个目录" },
                icon = Icons.Default.FolderSpecial,
                tint = LikeRed,
                onClick = { favoriteDirPickerLauncher.launch(null) }
            )

            SourceCard(
                title = "外置存储",
                subtitle = "USB / OTG / SD 卡（需手动选择目录）",
                icon = Icons.Default.SdCard,
                onClick = { navController.navigate(Routes.directory(VideoSource.EXTERNAL)) }
            )

            SourceCard(
                title = "Samba 共享",
                subtitle = "NAS / 路由器 / 电脑共享（无需存储权限）",
                icon = Icons.Default.Lan,
                onClick = { navController.navigate(Routes.SAMBA_CONFIG) }
            )

            SourceCard(
                title = "收藏目录",
                subtitle = if (favoriteDir.isEmpty()) "请先选择收藏目录" else "查看收藏目录下的视频",
                icon = Icons.Default.FavoriteBorder,
                tint = LikeRed,
                onClick = {
                    if (favoriteDir.isNotEmpty()) navController.navigate(Routes.directory(VideoSource.LOCAL, favoriteDir))
                    else favoriteDirPickerLauncher.launch(null)
                }
            )

            Spacer(Modifier.weight(1f))
            Text(
                text = if (Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()) "✅ 已获得完整文件访问权限（含 .nomedia）" else "⚠️ 需要权限才能读取 .nomedia 视频",
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
            )
            Text("v1.0.1", color = TextHint, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }

    if (showFolderManager) {
        AlertDialog(
            onDismissRequest = { showFolderManager = false },
            containerColor = DarkSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("本地视频文件夹", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (savedFolders.isEmpty()) {
                        Text("还没有添加文件夹。点击下方「添加文件夹」。", color = TextSecondary)
                    } else {
                        savedFolders.forEach { path ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(path.substringAfterLast('/').ifEmpty { path }, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(path, color = TextHint, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                IconButton(onClick = { removeFolder(path) }) {
                                    Icon(Icons.Default.Delete, "删除", tint = LikeRed)
                                }
                            }
                            Divider(color = DarkSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { ensurePermissionThenPickFolder() }) {
                    Text("添加文件夹", color = Yellow500)
                }
            },
            dismissButton = {
                Row {
                    if (savedFolders.isNotEmpty()) {
                        TextButton(onClick = {
                            showFolderManager = false
                            navController.navigate(Routes.directory(VideoSource.LOCAL))
                        }) { Text("进入画廊", color = Yellow500) }
                    }
                    TextButton(onClick = { showFolderManager = false }) { Text("关闭", color = TextSecondary) }
                }
            }
        )
    }

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
                        if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
                            manageStorageLauncher.launch(intent)
                        } else ensurePermissionThenPickFolder()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Yellow500)
                ) { Text(if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) "去授权" else "重试", color = Black) }
            },
            dismissButton = { TextButton(onClick = { showPermissionDialog = false }) { Text("取消", color = TextSecondary) } }
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
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(32.dp))
            Column {
                Text(title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(subtitle, color = TextSecondary, fontSize = 13.sp)
            }
        }
    }
}

private fun treeUriToPrimaryPath(uri: Uri): String? {
    return try {
        val treeId = DocumentsContract.getTreeDocumentId(uri)
        if (treeId == "primary:") return Environment.getExternalStorageDirectory().absolutePath
        if (treeId.startsWith("primary:")) {
            val rel = treeId.removePrefix("primary:")
            Environment.getExternalStorageDirectory().absolutePath + "/" + rel
        } else null
    } catch (_: Exception) { null }
}
