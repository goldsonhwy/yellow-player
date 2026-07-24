package com.goldsonhwy.yellowplayer.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.goldsonhwy.yellowplayer.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    var longPressSpeed by remember { mutableFloatStateOf(2f) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var currentSort by remember { mutableStateOf("name") }

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
            // Section: Playback
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
                subtitle = "控制网格中缩略图的大小",
                onClick = { /* TODO: size picker */ }
            )

            Divider(color = DarkSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))

            SectionHeader("存储")

            SettingsItem(
                icon = Icons.Default.DeleteSweep,
                title = "清除缓存",
                subtitle = "清除 Samba 缩略图缓存",
                onClick = { /* TODO: clear cache */ }
            )

            Divider(color = DarkSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))

            SectionHeader("关于")

            SettingsItem(
                icon = Icons.Default.Info,
                title = "版本",
                subtitle = "1.0.0",
                onClick = {}
            )

            Spacer(Modifier.height(32.dp))
        }
    }

    // Speed dialog
    if (showSpeedDialog) {
        SpeedPickerDialog(
            currentSpeed = longPressSpeed,
            onDismiss = { showSpeedDialog = false },
            onSelect = { speed ->
                longPressSpeed = speed
                showSpeedDialog = false
            }
        )
    }

    // Sort dialog
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
    val speeds = listOf(1.0f, 1.5f, 2.0f, 3.0f, 4.0f)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = { Text("选择长按倍速", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                speeds.forEach { speed ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentSpeed == speed,
                            onClick = { onSelect(speed) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Yellow500,
                                unselectedColor = TextSecondary
                            )
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${speed}x",
                            color = if (currentSpeed == speed) Yellow500 else TextPrimary,
                            fontSize = 16.sp
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = when (speed) {
                                1.0f -> "正常"
                                1.5f -> "稍快"
                                2.0f -> "快速"
                                3.0f -> "极速"
                                4.0f -> "疯狂"
                                else -> ""
                            },
                            color = TextHint,
                            fontSize = 13.sp
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
