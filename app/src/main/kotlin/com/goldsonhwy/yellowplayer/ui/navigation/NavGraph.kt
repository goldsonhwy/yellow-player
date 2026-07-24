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
    const val SAMBA_BROWSE = "samba_browse/{serverId}/{folderPath}"

    fun directory(source: VideoSource, folderPath: String = ""): String {
        val encoded = URLEncoder.encode(folderPath, "UTF-8")
        return "directory/${source.name}/$encoded"
    }

    fun player(source: VideoSource, folderPath: String, startIndex: Int): String {
        val encoded = URLEncoder.encode(folderPath, "UTF-8")
        return "player/${source.name}/$encoded/$startIndex"
    }

    fun sambaBrowse(serverId: Long, folderPath: String = ""): String {
        return "samba_browse/$serverId/$folderPath"
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
            val source = VideoSource.valueOf(backStackEntry.arguments?.getString("source") ?: "LOCAL")
            val folderPath = URLDecoder.decode(
                backStackEntry.arguments?.getString("folderPath") ?: "",
                "UTF-8"
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
            val source = VideoSource.valueOf(backStackEntry.arguments?.getString("source") ?: "LOCAL")
            val folderPath = URLDecoder.decode(
                backStackEntry.arguments?.getString("folderPath") ?: "",
                "UTF-8"
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

        composable(
            route = Routes.SAMBA_BROWSE,
            arguments = listOf(
                navArgument("serverId") { type = NavType.LongType },
                navArgument("folderPath") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val serverId = backStackEntry.arguments?.getLong("serverId") ?: 0L
            val folderPath = URLDecoder.decode(
                backStackEntry.arguments?.getString("folderPath") ?: "",
                "UTF-8"
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
