package com.goldsonhwy.yellowplayer.data.scanner

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.goldsonhwy.yellowplayer.data.model.VideoFolder
import com.goldsonhwy.yellowplayer.data.model.VideoInfo
import com.goldsonhwy.yellowplayer.data.model.VideoSource
import java.io.File
import java.util.LinkedList

/**
 * Scans local storage for video files, including .nomedia folders.
 *
 * Two-pass strategy:
 * 1. MediaStore query — fast, provides thumbnails + metadata
 * 2. File API walk (iterative, not recursive) — catches folders with .nomedia
 *
 * Both passes wrapped in try-catch at every level to prevent crashes.
 */
class VideoScanner(private val context: Context) {

    companion object {
        private val VIDEO_EXTENSIONS = setOf(
            "mp4", "mkv", "avi", "mov", "flv", "webm",
            "ts", "m4v", "3gp", "wmv", "mpeg", "mpg", "vob"
        )

        private val EXCLUDED_DIRS = setOf(
            "android", "obb", "data", "cache", "tmp", "temp",
            "log", "logs", "thumbnails", ".thumbnails",
            "alarms", "notifications", "ringtones"
        )

        // Max scan depth to prevent stack/memory issues
        private const val MAX_DEPTH = 12
        // Max folders to scan
        private const val MAX_FOLDERS = 5000
    }

    /**
     * Scan ALL local video files (iterative, no recursion).
     */
    fun scanAllVideos(onProgress: (Int) -> Unit = {}): List<VideoInfo> {
        val videos = mutableListOf<VideoInfo>()

        // Pass 1: MediaStore (non-.nomedia files)
        try {
            videos.addAll(scanMediaStore(onProgress))
        } catch (_: Exception) { }

        // Pass 2: Iterative file walk for .nomedia folders
        try {
            val nomediaFolders = findNomediaFoldersIterative()
            for (folder in nomediaFolders) {
                try {
                    videos.addAll(scanDirectoryIterative(folder, VideoSource.LOCAL))
                } catch (_: Exception) { }
                onProgress(videos.size)
            }
        } catch (_: Exception) { }

        return videos.distinctBy { it.path }
    }

    /**
     * Iterative directory scan using a Queue (no recursion / stack overflow risk).
     */
    fun scanDirectoryIterative(
        rootDir: File,
        source: VideoSource = VideoSource.LOCAL
    ): List<VideoInfo> {
        val result = mutableListOf<VideoInfo>()
        if (!rootDir.exists() || !rootDir.isDirectory) return result

        val queue = LinkedList<File>()
        queue.add(rootDir)
        var depth = 0

        while (queue.isNotEmpty() && depth < MAX_DEPTH && result.size < MAX_FOLDERS) {
            val batch = mutableListOf<File>()
            while (queue.isNotEmpty() && batch.size < 100) {
                batch.add(queue.poll())
            }

            for (dir in batch) {
                try {
                    val files = dir.listFiles() ?: continue
                    for (file in files) {
                        try {
                            when {
                                file.isDirectory -> {
                                    val name = file.name.lowercase()
                                    if (!name.startsWith(".") && name !in EXCLUDED_DIRS) {
                                        queue.add(file)
                                    }
                                }
                                file.isFile && isVideoFile(file.name) -> {
                                    result.add(
                                        VideoInfo(
                                            name = file.name,
                                            path = file.absolutePath,
                                            uri = Uri.fromFile(file).toString(),
                                            size = file.length(),
                                            dateModified = file.lastModified(),
                                            folderPath = dir.absolutePath,
                                            source = source
                                        )
                                    )
                                }
                            }
                        } catch (_: Exception) { }
                    }
                } catch (_: Exception) { }
            }
            depth++
        }

        return result
    }

    /**
     * Scan folders that contain videos (for grid view). Iterative.
     */
    fun scanVideoFolders(
        rootDirs: List<File> = defaultScanDirs(),
        source: VideoSource = VideoSource.LOCAL
    ): Map<File, List<VideoInfo>> {
        val folderMap = LinkedHashMap<File, MutableList<VideoInfo>>()
        for (root in rootDirs) {
            try {
                scanFoldersIterative(root, folderMap, source)
            } catch (_: Exception) { }
        }
        return folderMap
    }

    private fun scanFoldersIterative(
        rootDir: File,
        folderMap: LinkedHashMap<File, MutableList<VideoInfo>>,
        source: VideoSource
    ) {
        if (!rootDir.exists() || !rootDir.isDirectory) return

        val queue = LinkedList<File>()
        queue.add(rootDir)
        var depth = 0
        var folderCount = 0

        while (queue.isNotEmpty() && depth < MAX_DEPTH && folderCount < MAX_FOLDERS) {
            val batch = mutableListOf<File>()
            while (queue.isNotEmpty() && batch.size < 100) {
                queue.poll()?.let { batch.add(it) }
            }

            for (dir in batch) {
                folderCount++
                val videosInDir = mutableListOf<VideoInfo>()

                try {
                    val files = dir.listFiles() ?: continue
                    for (file in files) {
                        try {
                            when {
                                file.isDirectory -> {
                                    val name = file.name.lowercase()
                                    if (!name.startsWith(".") && name !in EXCLUDED_DIRS) {
                                        queue.add(file)
                                    }
                                }
                                file.isFile && isVideoFile(file.name) -> {
                                    videosInDir.add(
                                        VideoInfo(
                                            name = file.name,
                                            path = file.absolutePath,
                                            uri = Uri.fromFile(file).toString(),
                                            size = file.length(),
                                            dateModified = file.lastModified(),
                                            folderPath = dir.absolutePath,
                                            source = source
                                        )
                                    )
                                }
                            }
                        } catch (_: Exception) { }
                    }
                } catch (_: Exception) { }

                if (videosInDir.isNotEmpty()) {
                    folderMap.getOrPut(dir) { mutableListOf() }.addAll(videosInDir)
                }
            }
            depth++
        }
    }

    fun getThumbnailUri(videoPath: String): Uri? {
        try {
            val file = File(videoPath)
            if (!file.exists()) return Uri.fromFile(file) // Let Coil try anyway

            val projection = arrayOf(MediaStore.Video.Media._ID)
            val selection = "${MediaStore.Video.Media.DATA} = ?"
            val cursor = context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                arrayOf(videoPath),
                null
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val id = it.getLong(0)
                    return ContentUris.withAppendedId(
                        Uri.parse("content://media/external/video/thumbnails"),
                        id
                    )
                }
            }
        } catch (_: Exception) { }

        return Uri.fromFile(File(videoPath))
    }

    // ─── Private ────────────────────────────────────────────

    private fun scanMediaStore(onProgress: (Int) -> Unit): List<VideoInfo> {
        val videos = mutableListOf<VideoInfo>()
        val collection = if (android.os.Build.VERSION.SDK_INT >= 29) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.MIME_TYPE
        )

        try {
            context.contentResolver.query(
                collection, projection, null, null,
                "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)

                var count = 0
                while (cursor.moveToNext()) {
                    try {
                        val data = cursor.getString(dataCol) ?: continue
                        val file = File(data)
                        if (!file.exists()) continue

                        videos.add(
                            VideoInfo(
                                id = cursor.getLong(idCol),
                                name = cursor.getString(nameCol) ?: "Unknown",
                                path = data,
                                uri = ContentUris.withAppendedId(
                                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                                    cursor.getLong(idCol)
                                ).toString(),
                                size = cursor.getLong(sizeCol),
                                duration = cursor.getLong(durCol),
                                dateModified = cursor.getLong(dateCol),
                                folderPath = file.parent ?: "",
                                source = VideoSource.LOCAL,
                                mimeType = cursor.getString(mimeCol) ?: "video/mp4"
                            )
                        )
                        count++
                        if (count % 50 == 0) onProgress(count)
                    } catch (_: Exception) { }
                }
            }
        } catch (_: Exception) { }

        return videos
    }

    /**
     * Find folders containing .nomedia files (iterative).
     */
    private fun findNomediaFoldersIterative(): List<File> {
        val result = mutableListOf<File>()
        for (root in defaultScanDirs()) {
            try {
                findNomediaIterative(root, result)
            } catch (_: Exception) { }
        }
        return result
    }

    private fun findNomediaIterative(root: File, result: MutableList<File>) {
        if (!root.exists() || !root.isDirectory) return

        val queue = LinkedList<File>()
        queue.add(root)
        var depth = 0

        while (queue.isNotEmpty() && depth < MAX_DEPTH) {
            val batch = mutableListOf<File>()
            while (queue.isNotEmpty() && batch.size < 100) {
                queue.poll()?.let { batch.add(it) }
            }

            for (dir in batch) {
                try {
                    val nomediaFile = File(dir, ".nomedia")
                    if (nomediaFile.exists()) {
                        val hasVideos = dir.listFiles()
                            ?.any { it.isFile && isVideoFile(it.name) } == true
                        if (hasVideos) {
                            result.add(dir)
                            continue // Don't go deeper — .nomedia applies to this dir
                        }
                    }

                    val children = dir.listFiles() ?: continue
                    for (child in children) {
                        if (child.isDirectory && !child.name.startsWith(".")) {
                            queue.add(child)
                        }
                    }
                } catch (_: Exception) { }
            }
            depth++
        }
    }

    private fun isVideoFile(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in VIDEO_EXTENSIONS
    }

    private fun defaultScanDirs(): List<File> {
        val dirs = mutableListOf<File>()
        try {
            Environment.getExternalStorageDirectory()?.let { dirs.add(it) }
        } catch (_: Exception) { }
        try {
            val storage = File("/storage")
            if (storage.exists()) {
                storage.listFiles()?.forEach { dirs.add(it) }
            }
        } catch (_: Exception) { }
        return dirs.distinct().filter { it.exists() }
    }
}
