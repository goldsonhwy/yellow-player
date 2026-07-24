package com.goldsonhwy.yellowplayer.data.model

import android.net.Uri

/**
 * Represents a single video file discovered by the scanner.
 */
data class VideoInfo(
    val id: Long = 0,
    val name: String,
    val path: String,
    val uri: String = "",
    val size: Long = 0,
    val duration: Long = 0,
    val dateModified: Long = 0,
    val folderPath: String = "",
    val source: VideoSource = VideoSource.LOCAL,
    val serverId: Long = 0,
    val mimeType: String = "video/mp4"
) {
    val isLocal: Boolean get() = source == VideoSource.LOCAL || source == VideoSource.EXTERNAL
    val fileUri: Uri get() = if (uri.isNotEmpty()) Uri.parse(uri) else Uri.parse(path)
}

enum class VideoSource {
    LOCAL,
    EXTERNAL,
    SAMBA
}

/**
 * Represents a folder containing videos.
 */
data class VideoFolder(
    val path: String,
    val name: String,
    val videoCount: Int = 0,
    val thumbnailPath: String = "",
    val source: VideoSource = VideoSource.LOCAL,
    val serverId: Long = 0
)
