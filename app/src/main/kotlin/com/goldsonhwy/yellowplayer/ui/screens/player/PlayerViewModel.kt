package com.goldsonhwy.yellowplayer.ui.screens.player

import android.app.Application
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
                                if (source == VideoSource.SAMBA) {
                                    val raw = repository.listSambaVideosById(folderPath.substringBefore('|').toLongOrNull() ?: 0L, folderPath.substringAfter('|', folderPath)).getOrElse { emptyList() }
                                    repository.cacheSambaVideosForLocalUse(folderPath.substringBefore('|').toLongOrNull() ?: 0L, raw)
                                } else {
                                    repository.getVideosInLocalFolder(folderPath)
                                }
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
}
