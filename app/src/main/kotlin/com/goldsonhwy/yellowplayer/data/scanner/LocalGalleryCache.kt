package com.goldsonhwy.yellowplayer.data.scanner

import android.content.Context
import com.goldsonhwy.yellowplayer.data.model.VideoFolder
import com.goldsonhwy.yellowplayer.data.model.VideoSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

object LocalGalleryCache {
    @Volatile var folders: List<VideoFolder>? = null
        private set
    @Volatile var isScanning: Boolean = false
        private set
    @Volatile private var hasStarted: Boolean = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start(context: Context) {
        if (hasStarted) return
        hasStarted = true
        scope.launch {
            isScanning = true
            try {
                val prefs = context.applicationContext.getSharedPreferences("local_video_folders", Context.MODE_PRIVATE)
                val roots = prefs.getStringSet("paths", emptySet()).orEmpty()
                    .map { File(it) }
                    .filter { it.exists() && it.isDirectory }
                val scanner = VideoScanner(context.applicationContext)
                val map = scanner.scanVideoFolders(roots, VideoSource.LOCAL)
                folders = map.map { (dir, videos) ->
                    VideoFolder(
                        path = dir.absolutePath,
                        name = dir.name,
                        videoCount = videos.size,
                        thumbnailPath = videos.firstOrNull()?.path ?: "",
                        source = VideoSource.LOCAL
                    )
                }.sortedBy { it.name }
            } finally {
                isScanning = false
            }
        }
    }

    fun clearAndRestart(context: Context) {
        folders = null
        hasStarted = false
        start(context)
    }
}
