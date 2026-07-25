package com.goldsonhwy.yellowplayer.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.goldsonhwy.yellowplayer.data.model.VideoSource
import com.goldsonhwy.yellowplayer.ui.screens.directory.DirectoryScreen
import com.goldsonhwy.yellowplayer.ui.screens.player.PlayerScreen
import com.goldsonhwy.yellowplayer.ui.screens.settings.SettingsScreen
import com.goldsonhwy.yellowplayer.ui.screens.directory.SourceSelectScreen
import com.goldsonhwy.yellowplayer.ui.screens.directory.SambaConfigScreen
import java.net.URLDecoder
import java.net.URLEncoder

object Routes {
    const val SOURCE_SELECT = "source_select"
    const val DIRECTORY = "directory/{source}/{folderPath}"
    const val PLAYER = "player/{source}/{folderPath}/{startIndex}"
    const val SETTINGS = "settings"
    const val SAMBA_CONFIG = "samba_config"
    const val SMB_COMMON = "smb_common"
    const val SAMBA_BROWSE = "samba_browse/{serverId}/{folderPath}"

    private const val ROOT_PLACEHOLDER = "_root"

    /** Build directory route. Encodes path to avoid slashes breaking Navigation Compose. */
    fun directory(source: VideoSource, folderPath: String = ""): String {
        val path = if (folderPath.isEmpty()) ROOT_PLACEHOLDER
                   else URLEncoder.encode(folderPath, "UTF-8")
        return "directory/${source.name}/$path"
    }

    /** Build player route. */
    fun player(source: VideoSource, folderPath: String, startIndex: Int): String {
        val path = if (folderPath.isEmpty()) ROOT_PLACEHOLDER
                   else URLEncoder.encode(folderPath, "UTF-8")
        return "player/${source.name}/$path/$startIndex"
    }

    /** Build Samba browse route. */
    fun sambaBrowse(serverId: Long, folderPath: String = ""): String {
        val path = if (folderPath.isEmpty()) ROOT_PLACEHOLDER
                   else URLEncoder.encode(folderPath, "UTF-8")
        return "samba_browse/$serverId/$path"
    }

    /** Decode a raw route path back to the actual folder path. */
    fun resolveFolderPath(raw: String): String {
        if (raw == ROOT_PLACEHOLDER || raw.isEmpty()) return ""
        return try {
            URLDecoder.decode(raw, "UTF-8")
        } catch (_: Exception) {
            raw
        }
    }
}

@Composable
fun AppNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Routes.SOURCE_SELECT
    ) {
        composable(Routes.SOURCE_SELECT) {
            SourceSelectScreen(navController)
        }

        composable(
            route = Routes.DIRECTORY,
            arguments = listOf(
                navArgument("source") { type = NavType.StringType },
                navArgument("folderPath") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val source = try {
                VideoSource.valueOf(backStackEntry.arguments?.getString("source") ?: "LOCAL")
            } catch (_: Exception) { VideoSource.LOCAL }

            val folderPath = Routes.resolveFolderPath(
                backStackEntry.arguments?.getString("folderPath") ?: ""
            )

            DirectoryScreen(
                navController = navController,
                source = source,
                folderPath = folderPath
            )
        }

        composable(
            route = Routes.PLAYER,
            arguments = listOf(
                navArgument("source") { type = NavType.StringType },
                navArgument("folderPath") { type = NavType.StringType },
                navArgument("startIndex") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val source = try {
                VideoSource.valueOf(backStackEntry.arguments?.getString("source") ?: "LOCAL")
            } catch (_: Exception) { VideoSource.LOCAL }

            val folderPath = Routes.resolveFolderPath(
                backStackEntry.arguments?.getString("folderPath") ?: ""
            )
            val startIndex = backStackEntry.arguments?.getInt("startIndex") ?: 0

            PlayerScreen(
                navController = navController,
                source = source,
                folderPath = folderPath,
                startIndex = startIndex
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(navController)
        }

        composable(Routes.SAMBA_CONFIG) {
            SambaConfigScreen(navController)
        }

        composable(Routes.SMB_COMMON) {
            DirectoryScreen(
                navController = navController,
                source = VideoSource.SAMBA,
                serverId = 0L,
                folderPath = "__common_smb__"
            )
        }

        composable(
            route = Routes.SAMBA_BROWSE,
            arguments = listOf(
                navArgument("serverId") { type = NavType.LongType },
                navArgument("folderPath") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val serverId = backStackEntry.arguments?.getLong("serverId") ?: 0L
            val folderPath = Routes.resolveFolderPath(
                backStackEntry.arguments?.getString("folderPath") ?: ""
            )

            DirectoryScreen(
                navController = navController,
                source = VideoSource.SAMBA,
                serverId = serverId,
                folderPath = folderPath
            )
        }
    }
}
