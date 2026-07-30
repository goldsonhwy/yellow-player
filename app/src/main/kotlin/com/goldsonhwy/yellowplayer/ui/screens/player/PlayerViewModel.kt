package com.goldsonhwy.yellowplayer.ui.screens.player

import android.app.Application
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.goldsonhwy.yellowplayer.data.model.VideoInfo
import com.goldsonhwy.yellowplayer.data.model.VideoSource
import com.goldsonhwy.yellowplayer.data.repository.VideoRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.nio.ByteBuffer

private const val TAG = "PlayerViewModel"

data class PlayerUiState(
    val videos: List<VideoInfo> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = VideoRepository(application)
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    fun loadVideos(source: VideoSource, folderPath: String) {
        viewModelScope.launch {
            _uiState.value = PlayerUiState(isLoading = true)
            try {
                when {
                    folderPath == "__favorites__" -> {
                        _uiState.value = PlayerUiState(videos = repository.getFavoriteVideos())
                    }
                    source == VideoSource.LOCAL || source == VideoSource.SAMBA -> {
                        val videos = withTimeoutOrNull(60_000L) {
                            withContext(Dispatchers.IO) {
                                val rawVideos = if (source == VideoSource.SAMBA) {
                                    val serverId = folderPath.substringBefore('|').toLongOrNull() ?: 0L
                                    val realFolderPath = folderPath.substringAfter('|', folderPath)
                                    repository.listSambaVideosById(serverId, realFolderPath).getOrElse { emptyList() }
                                } else {
                                    repository.getVideosInLocalFolder(folderPath)
                                }
                                sortVideosForDirectory(source, folderPath, rawVideos)
                            }
                        }
                        _uiState.value = if (videos == null) {
                            PlayerUiState(error = "读取视频列表超时：$folderPath")
                        } else {
                            PlayerUiState(videos = videos)
                        }
                    }
                    else -> _uiState.value = PlayerUiState(error = "播放器暂只接入本地视频，Samba 播放下一版继续接入。")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.e(TAG, "loadVideos failed", t)
                _uiState.value = PlayerUiState(error = "加载视频失败：${t.javaClass.simpleName}\n${t.message.orEmpty()}")
            }
        }
    }

    private fun sortVideosForDirectory(source: VideoSource, folderPath: String, videos: List<VideoInfo>): List<VideoInfo> {
        val app = getApplication<Application>()
        val (serverId, realFolderPath) = if (source == VideoSource.SAMBA) {
            (folderPath.substringBefore('|').toLongOrNull() ?: 0L) to folderPath.substringAfter('|', folderPath)
        } else {
            0L to folderPath
        }
        val sortKey = "${source.name}_${serverId}_${realFolderPath.hashCode()}"
        val sortMode = app.getSharedPreferences("directory_sort", Context.MODE_PRIVATE)
            .getString(sortKey, "name") ?: "name"
        return when (sortMode) {
            "date" -> videos.sortedByDescending { it.dateModified }
            "size" -> videos.sortedByDescending { it.size }
            "name_desc" -> videos.sortedByDescending { it.name }
            else -> videos.sortedBy { it.name }
        }
    }

    suspend fun isFavorite(path: String): Boolean = withContext(Dispatchers.IO) {
        repository.isFavorite(path)
    }

    suspend fun toggleFavorite(video: VideoInfo): VideoInfo? = withContext(Dispatchers.IO) {
        repository.toggleFavorite(video)
    }

    suspend fun moveFavoriteAfterRelease(path: String): VideoInfo? = withContext(Dispatchers.IO) {
        repository.moveFavoriteAfterRelease(path)
    }

    fun moveFavoriteAfterReleaseAsync(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.moveFavoriteAfterRelease(path)
        }
    }

    fun prefetchSmbHeaderAsync(serverId: Long, remotePath: String) {
        if (serverId <= 0 || remotePath.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            repository.prefetchSmbHeader(serverId, remotePath)
        }
    }

    suspend fun getVideoRotation(path: String): Int = withContext(Dispatchers.IO) {
        val prefs = getApplication<Application>().getSharedPreferences("video_rotation", Context.MODE_PRIVATE)
        prefs.getInt(path, 0).floorMod360()
    }

    fun saveVideoRotation(path: String, rotation: Int) {
        if (path.isBlank()) return
        val normalized = rotation.floorMod360()
        getApplication<Application>().getSharedPreferences("video_rotation", Context.MODE_PRIVATE)
            .edit().putInt(path, normalized).apply()
    }

    fun finalizeVideoAfterSwitchAsync(path: String, pendingRotation: Int, source: VideoSource) {
        viewModelScope.launch(Dispatchers.IO) {
            val normalizedPending = pendingRotation.floorMod360()
            if ((source == VideoSource.LOCAL || source == VideoSource.EXTERNAL) &&
                normalizedPending != 0 && canTryMetadataRotation(path)
            ) {
                runCatching {
                    val existingRotation = readFileRotation(path)
                    val targetRotation = (existingRotation + normalizedPending).floorMod360()
                    if (remuxVideoWithRotationMetadata(path, targetRotation)) {
                        getApplication<Application>().getSharedPreferences("video_rotation", Context.MODE_PRIVATE)
                            .edit().remove(path).commit()
                        Log.i(TAG, "Rotation metadata written without transcoding: $path -> $targetRotation")
                    } else {
                        Log.i(TAG, "Rotation metadata unsupported; keeping playback rotation: $path -> $normalizedPending")
                    }
                }.onFailure { t ->
                    Log.w(TAG, "Rotation metadata write failed; keeping playback rotation: $path", t)
                }
            }
            repository.moveFavoriteAfterRelease(path)
        }
    }

    private fun canTryMetadataRotation(path: String): Boolean {
        val file = File(path)
        if (!file.exists() || !file.isFile || !file.canRead() || !file.canWrite()) return false
        val ext = file.extension.lowercase()
        return ext == "mp4" || ext == "m4v" || ext == "3gp" || ext == "3gpp"
    }

    private fun readFileRotation(path: String): Int {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull()?.floorMod360() ?: 0
        } finally {
            retriever.release()
        }
    }

    private fun remuxVideoWithRotationMetadata(path: String, rotation: Int): Boolean {
        val input = File(path)
        val parent = input.parentFile ?: return false
        val tmp = File(parent, ".${input.name}.rotate.tmp.mp4")
        val backup = File(parent, ".${input.name}.rotate.bak")
        tmp.delete()
        backup.delete()

        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        return try {
            extractor.setDataSource(input.absolutePath)
            muxer = MediaMuxer(tmp.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4).apply {
                setOrientationHint(rotation)
            }

            val trackMap = mutableMapOf<Int, Int>()
            var maxInputSize = 1 * 1024 * 1024
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    val outputTrack = muxer.addTrack(format)
                    trackMap[i] = outputTrack
                    extractor.selectTrack(i)
                    if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                        maxInputSize = maxOf(maxInputSize, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
                    }
                }
            }
            if (trackMap.isEmpty()) return false

            val buffer = ByteBuffer.allocate(maxInputSize)
            val info = MediaCodec.BufferInfo()
            muxer.start()

            while (true) {
                val inputTrack = extractor.sampleTrackIndex
                if (inputTrack < 0) break
                val outputTrack = trackMap[inputTrack]
                if (outputTrack == null) {
                    extractor.advance()
                    continue
                }
                buffer.clear()
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                info.set(0, sampleSize, extractor.sampleTime.coerceAtLeast(0L), extractor.sampleFlags)
                muxer.writeSampleData(outputTrack, buffer, info)
                extractor.advance()
            }

            muxer.stop()
            muxer.release()
            muxer = null
            extractor.release()

            if (tmp.length() <= 0L) return false
            if (!input.renameTo(backup)) return false
            if (!tmp.renameTo(input)) {
                backup.renameTo(input)
                return false
            }
            backup.delete()
            true
        } catch (t: Throwable) {
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
            tmp.delete()
            if (backup.exists() && !input.exists()) backup.renameTo(input)
            Log.w(TAG, "remuxVideoWithRotationMetadata failed: $path", t)
            false
        }
    }
}

private fun Int.floorMod360(): Int = ((this % 360) + 360) % 360
