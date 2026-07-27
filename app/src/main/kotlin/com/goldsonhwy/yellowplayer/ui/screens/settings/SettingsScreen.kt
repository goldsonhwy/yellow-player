package com.goldsonhwy.yellowplayer.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.goldsonhwy.yellowplayer.ui.theme.*
import com.goldsonhwy.yellowplayer.util.CrashReporter
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val playerPrefs = remember { context.getSharedPreferences("player_prefs", android.content.Context.MODE_PRIVATE) }
    val uiPrefs = remember { context.getSharedPreferences("ui_prefs", android.content.Context.MODE_PRIVATE) }
    var longPressSpeed by remember { mutableFloatStateOf(playerPrefs.getFloat("long_press_speed", 2f)) }
    var thumbnailSize by remember { mutableIntStateOf(uiPrefs.getInt("thumbnail_size", 180)) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showThumbDialog by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var currentSort by remember { mutableStateOf("name") }
    var favoriteDir by remember {
        mutableStateOf(context.getSharedPreferences("favorite_move", android.content.Context.MODE_PRIVATE).getString("dir", "").orEmpty())
    }

    val favoriteDirPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val path = treeUriToPrimaryPath(uri)
            if (path != null) {
                favoriteDir = path
                context.getSharedPreferences("favorite_move", android.content.Context.MODE_PRIVATE)
                    .edit().putString("dir", path).apply()
            } else {
                Toast.makeText(context, "当前只支持内部存储目录", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun exportDebugLog() {
        try {
            val zip = CrashReporter.createDebugZip(context)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                zip
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Yellow Shorts 调试日志")
                putExtra(Intent.EXTRA_TEXT, "Yellow Shorts v2.1.0 调试日志/崩溃日志")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "导出调试日志"))
        } catch (t: Throwable) {
            Toast.makeText(context, "导出失败：${t.message}", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置", fontWeight = FontWeight.Bold, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "返回", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SectionHeader("播放")

            SettingsItem(
                icon = Icons.Default.Speed,
                title = "长按倍速",
                subtitle = "长按时使用 ${longPressSpeed}x 速度播放",
                onClick = { showSpeedDialog = true }
            )

            Divider(color = DarkSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))

            SectionHeader("显示")

            SettingsItem(
                icon = Icons.Default.Sort,
                title = "视频排序",
                subtitle = when (currentSort) {
                    "name" -> "按名称"
                    "date" -> "按日期"
                    "size" -> "按大小"
                    "random" -> "随机"
                    else -> "按名称"
                },
                onClick = { showSortDialog = true }
            )

            Divider(color = DarkSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))

            SettingsItem(
                icon = Icons.Default.PhotoSizeSelectLarge,
                title = "缩略图尺寸",
                subtitle = "当前 ${thumbnailSize}px，影响画廊列数与缩略图请求尺寸",
                onClick = { showThumbDialog = true }
            )

            Divider(color = DarkSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))

            SectionHeader("存储")

            SettingsItem(
                icon = Icons.Default.DeleteSweep,
                title = "清除缓存",
                subtitle = "清除 Samba 缩略图缓存",
                onClick = { }
            )

            Divider(color = DarkSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))

            SettingsItem(
                icon = Icons.Default.Folder,
                title = "收藏移动目录",
                subtitle = favoriteDir.ifEmpty { "未设置：收藏只记录，不移动文件" },
                onClick = { favoriteDirPicker.launch(null) }
            )

            Divider(color = DarkSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))

            SectionHeader("调试")

            SettingsItem(
                icon = Icons.Default.BugReport,
                title = "导出调试/崩溃日志",
                subtitle = "闪退后点这里，把 zip 发给我定位真实原因",
                onClick = { exportDebugLog() }
            )

            Divider(color = DarkSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))

            SectionHeader("关于")

            SettingsItem(
                icon = Icons.Default.Info,
                title = "版本",
                subtitle = "2.1.0",
                onClick = {}
            )

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showSpeedDialog) {
        SpeedPickerDialog(
            currentSpeed = longPressSpeed,
            onDismiss = { showSpeedDialog = false },
            onSelect = { speed ->
                longPressSpeed = speed
                playerPrefs.edit().putFloat("long_press_speed", speed).apply()
            }
        )
    }

    if (showSortDialog) {
        SortDialog(
            currentSort = currentSort,
            onDismiss = { showSortDialog = false },
            onSelect = { sort ->
                currentSort = sort
                showSortDialog = false
            }
        )
    }

    if (showThumbDialog) {
        ThumbnailSizeDialog(
            currentSize = thumbnailSize,
            onDismiss = { showThumbDialog = false },
            onSelect = { size ->
                thumbnailSize = size
                uiPrefs.edit().putInt("thumbnail_size", size).apply()
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = Yellow500,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
    )
}

@Composable
private fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = DarkSurface,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = Yellow500,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = TextPrimary, fontSize = 15.sp)
                Text(subtitle, color = TextSecondary, fontSize = 13.sp)
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextHint,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SpeedPickerDialog(
    currentSpeed: Float,
    onDismiss: () -> Unit,
    onSelect: (Float) -> Unit
) {
    var value by remember(currentSpeed) { mutableFloatStateOf(currentSpeed.coerceIn(1f, 5f)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = { Text("长按倍速", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${"%.1f".format(value)}x", color = Yellow500, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Slider(
                    value = value,
                    onValueChange = {
                        value = ((it.coerceIn(1f, 5f) * 10).roundToInt() / 10f)
                        onSelect(value)
                    },
                    valueRange = 1f..5f,
                    steps = 0,
                    colors = SliderDefaults.colors(
                        thumbColor = Yellow500,
                        activeTrackColor = Yellow500,
                        inactiveTrackColor = DarkSurfaceVariant
                    )
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("1x", color = TextHint, fontSize = 12.sp)
                    Text("5x", color = TextHint, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("完成", color = Yellow500)
            }
        }
    )
}

@Composable
private fun ThumbnailSizeDialog(
    currentSize: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    var value by remember(currentSize) { mutableFloatStateOf(currentSize.toFloat().coerceIn(120f, 260f)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = { Text("缩略图尺寸", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${value.roundToInt()}px", color = Yellow500, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Slider(
                    value = value,
                    onValueChange = {
                        value = ((it.coerceIn(120f, 260f) / 20f).roundToInt() * 20).toFloat()
                        onSelect(value.roundToInt())
                    },
                    valueRange = 120f..260f,
                    steps = 6,
                    colors = SliderDefaults.colors(
                        thumbColor = Yellow500,
                        activeTrackColor = Yellow500,
                        inactiveTrackColor = DarkSurfaceVariant
                    )
                )
                Text("小尺寸显示更多列，大尺寸缩略图更清晰", color = TextHint, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成", color = Yellow500) } }
    )
}

@Composable
private fun SortDialog(
    currentSort: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val sorts = listOf(
        "name" to "按名称",
        "date" to "按日期",
        "size" to "按大小",
        "random" to "随机"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = { Text("视频排序方式", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                sorts.forEach { (key, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentSort == key,
                            onClick = { onSelect(key) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Yellow500,
                                unselectedColor = TextSecondary
                            )
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = label,
                            color = if (currentSort == key) Yellow500 else TextPrimary,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("完成", color = Yellow500)
            }
        }
    )
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
