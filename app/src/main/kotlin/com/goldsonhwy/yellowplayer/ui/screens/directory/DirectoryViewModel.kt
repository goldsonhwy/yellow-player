package com.goldsonhwy.yellowplayer.ui.screens.directory

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.goldsonhwy.yellowplayer.data.model.VideoFolder
import com.goldsonhwy.yellowplayer.data.model.VideoInfo
import com.goldsonhwy.yellowplayer.data.model.VideoSource
import com.goldsonhwy.yellowplayer.data.repository.VideoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DirectoryUiState(
    val folders: List<VideoFolder> = emptyList(),
    val videos: List<VideoInfo> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSingleFolder: Boolean = false, // true = show grid of videos; false = show folders
    val currentFolderPath: String = ""
)

class DirectoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VideoRepository(application)

    private val _uiState = MutableStateFlow(DirectoryUiState())
    val uiState: StateFlow<DirectoryUiState> = _uiState.asStateFlow()

    /**
     * Load folders for a given source (root level for LOCAL / EXTERNAL).
     */
    fun loadFolders(source: VideoSource) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            when (source) {
                VideoSource.LOCAL -> {
                    val folders = repository.scanLocalFolders(VideoSource.LOCAL)
                    _uiState.value = _uiState.value.copy(
                        folders = folders,
                        isLoading = false,
                        isSingleFolder = false
                    )
                }
                VideoSource.EXTERNAL -> {
                    // For external storage, we'd use SAF to pick a directory
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "外置存储请通过系统文件选择器选择目录"
                    )
                }
                VideoSource.SAMBA -> {
                    // Samba is handled separately via SambaConfigScreen
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        folders = emptyList()
                    )
                }
            }
        }
    }

    /**
     * Load videos inside a specific folder.
     */
    fun loadVideosInFolder(source: VideoSource, folderPath: String, serverId: Long = 0) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, currentFolderPath = folderPath)

            when (source) {
                VideoSource.LOCAL -> {
                    val videos = repository.getVideosInLocalFolder(folderPath)
                    _uiState.value = _uiState.value.copy(
                        videos = videos,
                        isLoading = false,
                        isSingleFolder = true
                    )
                }
                else -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "暂不支持此来源的文件夹浏览"
                    )
                }
            }
        }
    }

    fun getThumbnailUri(videoPath: String): Uri? {
        return repository.getThumbnailUri(videoPath)
    }

    override fun onCleared() {
        super.onCleared()
    }
}
