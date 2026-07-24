package com.goldsonhwy.yellowplayer.ui.screens.directory

import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.goldsonhwy.yellowplayer.ui.navigation.Routes
import com.goldsonhwy.yellowplayer.ui.theme.*

/**
 * First screen — choose video source (Local, External, Samba, Settings).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceSelectScreen(navController: NavController) {
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
                onClick = {
                    navController.navigate(
                        Routes.directory(
                            com.goldsonhwy.yellowplayer.data.model.VideoSource.LOCAL
                        )
                    )
                }
            )

            // External / OTG
            SourceCard(
                title = "外置存储",
                subtitle = "USB / OTG / SD 卡",
                icon = Icons.Default.SdCard,
                onClick = {
                    navController.navigate(
                        Routes.directory(
                            com.goldsonhwy.yellowplayer.data.model.VideoSource.EXTERNAL
                        )
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
                        Routes.directory(
                            com.goldsonhwy.yellowplayer.data.model.VideoSource.LOCAL,
                            "__favorites__"
                        )
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
