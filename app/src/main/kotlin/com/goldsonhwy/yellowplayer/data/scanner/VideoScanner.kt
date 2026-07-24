package com.goldsonhwy.yellowplayer.data.scanner

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.DocumentsContract
import com.goldsonhwy.yellowplayer.data.model.VideoFolder
import com.goldsonhwy.yellowplayer.data.model.VideoInfo
import com.goldsonhwy.yellowplayer.data.model.VideoSource
import java.io.File

/**
 * Scans local storage for video files, including .nomedia folders.
 *
 * Two-pass strategy:
 * 1. MediaStore query — fast, provides thumbnails + metadata
 * 2. File API walk — catches folders with .nomedia that MediaStore skips
 */
class VideoScanner(private val context: Context) {

    companion object {
        private val VIDEO_EXTENSIONS = setOf(
            "mp4", "mkv", "avi", "mov", "flv", "webm",
            "ts", "m4v", "3gp", "wmv", "mpeg", "mpg", "vob"
        )

        private val VIDEO_MIME_TYPES = arrayOf(
            "video/mp4",
            "video/x-matroska",
            "video/avi",
            "video/quicktime",
            "video/x-flv",
            "video/webm",
            "video/mp2t",
            "video/x-msvideo",
            "video/3gpp",
            "video/x-ms-wmv",
            "video/mpeg"
        )
    }

    /**
     * Scan local storage — returns all video files including those behind .nomedia.
     */
    fun scanAllVideos(onProgress: (Int) -> Unit = {}): List<VideoInfo> {
        val videos = mutableListOf<VideoInfo>()

        // Pass 1: MediaStore (normal videos, fast)
        videos.addAll(scanMediaStore(onProgress))

        // Pass 2: File API walk — finds .nomedia-hidden videos
        val nomediaFolders = findNomediaFolders()
        onProgress(videos.size)
        for (folder in nomediaFolders) {
            videos.addAll(scanDirectory(folder, VideoSource.LOCAL))
            onProgress(videos.size)
        }

        return videos.distinctBy { it.path }
    }

    /**
     * Scans a specific directory and all subdirectories for video files.
     */
    fun scanDirectory(
        dir: File,
        source: VideoSource = VideoSource.LOCAL
    ): List<VideoInfo> {
        val result = mutableListOf<VideoInfo>()
        if (!dir.exists() || !dir.isDirectory) return result

        dir.listFiles()?.forEach { file ->
            when {
                file.isDirectory && !file.name.startsWith(".") -> {
                    result.addAll(scanDirectory(file, source))
                }
                file.isFile && isVideoFile(file.name) -> {
                    result.add(
                        VideoInfo(
                            name = file.name,
                            path = file.absolutePath,
                            uri = Uri.fromFile(file).toString(),
                            size = file.length(),
                            dateModified = file.lastModified(),
                            folderPath = file.parent ?: "",
                            source = source
                        )
                    )
                }
            }
        }
        return result
    }

    /**
     * Scan a directory and return folders that contain videos (for grid view).
     */
    fun scanVideoFolders(
        rootDirs: List<File> = defaultScanDirs(),
        source: VideoSource = VideoSource.LOCAL
    ): Map<File, List<VideoInfo>> {
        val folderMap = LinkedHashMap<File, MutableList<VideoInfo>>()

        for (root in rootDirs) {
            scanFoldersRecursive(root, folderMap, source)
        }

        return folderMap
    }

    private fun scanFoldersRecursive(
        dir: File,
        folderMap: LinkedHashMap<File, MutableList<VideoInfo>>,
        source: VideoSource
    ) {
        if (!dir.exists() || !dir.isDirectory || dir.name.startsWith(".")) return

        val videos = mutableListOf<VideoInfo>()
        val subDirs = mutableListOf<File>()

        dir.listFiles()?.forEach { file ->
            when {
                file.isDirectory && !file.name.startsWith(".") -> {
                    subDirs.add(file)
                }
                file.isFile && isVideoFile(file.name) -> {
                    videos.add(
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
        }

        if (videos.isNotEmpty()) {
            folderMap.getOrPut(dir) { mutableListOf() }.addAll(videos)
        }

        for (sub in subDirs) {
            scanFoldersRecursive(sub, folderMap, source)
        }
    }

    /**
     * Get a single thumbnail URI for a video file (used by grid view).
     */
    fun getThumbnailUri(videoPath: String): Uri? {
        try {
            val file = File(videoPath)
            if (!file.exists()) return null

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

        // Fallback: return the video URI itself (Glide can handle it)
        return Uri.fromFile(File(videoPath))
    }

    // ─── Private helpers ─────────────────────────────────────────

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
                collection, projection, null, null, "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
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
                    val data = cursor.getString(dataCol)
                    if (data == null || !File(data).exists()) continue

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
                            folderPath = File(data).parent ?: "",
                            source = VideoSource.LOCAL,
                            mimeType = cursor.getString(mimeCol) ?: "video/mp4"
                        )
                    )
                    count++
                    if (count % 50 == 0) onProgress(count)
                }
            }
        } catch (_: Exception) { }

        return videos
    }

    /**
     * Find all folders that contain a .nomedia file, recursively.
     */
    private fun findNomediaFolders(): List<File> {
        val result = mutableListOf<File>()
        val roots = defaultScanDirs()

        for (root in roots) {
            findNomediaRecursive(root, result)
        }
        return result
    }

    private fun findNomediaRecursive(dir: File, result: MutableList<File>) {
        if (!dir.exists() || !dir.isDirectory) return

        val hasNomedia = File(dir, ".nomedia").exists()
        if (hasNomedia) {
            // Check if this folder actually has video files
            val hasVideos = dir.listFiles()?.any { isVideoFile(it.name) } == true
            if (hasVideos) {
                result.add(dir)
                return // Don't recurse deeper — .nomedia applies to this dir only
            }
        }

        dir.listFiles()?.forEach {
            if (it.isDirectory && !it.name.startsWith(".")) {
                findNomediaRecursive(it, result)
            }
        }
    }

    private fun isVideoFile(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in VIDEO_EXTENSIONS
    }

    private fun defaultScanDirs(): List<File> {
        val dirs = mutableListOf<File>()

        // Primary external storage
        Environment.getExternalStorageDirectory()?.let { dirs.add(it) }

        // Common SD card paths
        File("/storage").listFiles()?.forEach { dirs.add(it) }

        return dirs.distinct().filter { it.exists() }
    }
}
