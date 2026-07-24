package com.goldsonhwy.yellowplayer.ui.screens.directory

import android.app.Application
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.goldsonhwy.yellowplayer.data.model.VideoFolder
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

private const val TAG = "DirectoryViewModel"

data class DirectoryUiState(
    val folders: List<VideoFolder> = emptyList(),
    val videos: List<VideoInfo> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSingleFolder: Boolean = false,
    val currentFolderPath: String = ""
)

class DirectoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VideoRepository(application)

    private val _uiState = MutableStateFlow(DirectoryUiState())
    val uiState: StateFlow<DirectoryUiState> = _uiState.asStateFlow()

    fun loadFolders(source: VideoSource, serverId: Long = 0L) {
        viewModelScope.launch {
            _uiState.value = DirectoryUiState(isLoading = true)
            try {
                when (source) {
                    VideoSource.LOCAL -> loadLocalFoldersSafely()
                    VideoSource.EXTERNAL -> {
                        _uiState.value = DirectoryUiState(
                            isLoading = false,
                            error = "外置存储下一版接入系统文件夹选择器；当前请先使用本地存储或 Samba。"
                        )
                    }
                    VideoSource.SAMBA -> {
                        val result = withContext(Dispatchers.IO) {
                            repository.listSambaFoldersById(serverId)
                        }
                        result.fold(
                            onSuccess = { folders ->
                                _uiState.value = DirectoryUiState(
                                    folders = folders,
                                    isLoading = false,
                                    isSingleFolder = false
                                )
                            },
                            onFailure = { err ->
                                _uiState.value = DirectoryUiState(
                                    isLoading = false,
                                    error = "Samba 目录读取失败：${err.message.orEmpty()}"
                                )
                            }
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.e(TAG, "loadFolders crashed", t)
                _uiState.value = DirectoryUiState(
                    isLoading = false,
                    error = "扫描失败，但应用已保护不闪退：${t.javaClass.simpleName}\n${t.message.orEmpty()}"
                )
            }
        }
    }

    private suspend fun loadLocalFoldersSafely() {
        val hasAllFilesAccess = android.os.Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()
        if (!hasAllFilesAccess) {
            _uiState.value = DirectoryUiState(
                isLoading = false,
                error = "还没有「所有文件访问权限」。请返回首页点击本地存储并完成授权。"
            )
            return
        }

        val folders = withTimeoutOrNull(30_000L) {
            withContext(Dispatchers.IO) {
                repository.scanLocalFolders(VideoSource.LOCAL)
            }
        }

        if (folders == null) {
            _uiState.value = DirectoryUiState(
                isLoading = false,
                error = "扫描超时：手机文件太多或部分目录访问很慢。下一步我会继续优化为分批扫描。"
            )
            return
        }

        if (folders.isEmpty()) {
            _uiState.value = DirectoryUiState(
                isLoading = false,
                error = "没有找到含视频的子文件夹。请返回首页重新添加包含视频的文件夹。"
            )
            return
        }

        _uiState.value = DirectoryUiState(
            folders = folders,
            isLoading = false,
            isSingleFolder = false
        )
    }

    fun loadVideosInFolder(source: VideoSource, folderPath: String, serverId: Long = 0) {
        viewModelScope.launch {
            _uiState.value = DirectoryUiState(isLoading = true, currentFolderPath = folderPath)
            try {
                when {
                    source == VideoSource.LOCAL && folderPath == "__favorites__" -> {
                        _uiState.value = DirectoryUiState(
                            videos = repository.getFavoriteVideos(),
                            isLoading = false,
                            isSingleFolder = true,
                            currentFolderPath = folderPath
                        )
                    }
                    source == VideoSource.LOCAL -> {
                        val videos = withTimeoutOrNull(20_000L) {
                            withContext(Dispatchers.IO) {
                                repository.getVideosInLocalFolder(folderPath)
                            }
                        }
                        if (videos == null) {
                            _uiState.value = DirectoryUiState(
                                isLoading = false,
                                isSingleFolder = true,
                                currentFolderPath = folderPath,
                                error = "读取文件夹超时：${folderPath}"
                            )
                        } else {
                            _uiState.value = DirectoryUiState(
                                videos = videos,
                                isLoading = false,
                                isSingleFolder = true,
                                currentFolderPath = folderPath
                            )
                        }
                    }
                    source == VideoSource.SAMBA -> {
                        val result = withContext(Dispatchers.IO) {
                            repository.listSambaVideosById(serverId, folderPath)
                        }
                        result.fold(
                            onSuccess = { videos ->
                                _uiState.value = DirectoryUiState(
                                    videos = videos,
                                    isLoading = false,
                                    isSingleFolder = true,
                                    currentFolderPath = folderPath
                                )
                            },
                            onFailure = { err ->
                                _uiState.value = DirectoryUiState(
                                    isLoading = false,
                                    currentFolderPath = folderPath,
                                    error = "Samba 视频读取失败：${err.message.orEmpty()}"
                                )
                            }
                        )
                    }
                    else -> Unit
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.e(TAG, "loadVideosInFolder crashed", t)
                _uiState.value = DirectoryUiState(
                    isLoading = false,
                    isSingleFolder = true,
                    currentFolderPath = folderPath,
                    error = "读取失败，但应用已保护不闪退：${t.javaClass.simpleName}\n${t.message.orEmpty()}"
                )
            }
        }
    }

    fun getThumbnailUri(videoPath: String): Uri? {
        return try {
            repository.getThumbnailUri(videoPath)
        } catch (t: Throwable) {
            Log.w(TAG, "getThumbnailUri failed: $videoPath", t)
            null
        }
    }
}
