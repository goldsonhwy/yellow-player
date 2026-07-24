package com.goldsonhwy.yellowplayer.data.repository

import android.content.Context
import android.net.Uri
import com.goldsonhwy.yellowplayer.data.local.db.*
import com.goldsonhwy.yellowplayer.data.local.preferences.SettingsDataStore
import com.goldsonhwy.yellowplayer.data.model.*
import com.goldsonhwy.yellowplayer.data.scanner.VideoScanner
import com.goldsonhwy.yellowplayer.smb.SambaClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Single source of truth for all video data.
 */
class VideoRepository(private val context: Context) {

    private val scanner = VideoScanner(context)
    private val db = AppDatabase.getInstance(context)
    private val settings = SettingsDataStore(context)
    private val sambaClient = SambaClient()

    val favoriteDao = db.favoriteDao()
    val progressDao = db.playbackProgressDao()
    val sambaServerDao = db.sambaServerDao()

    // ─── Local Video Scanning ──────────────────────────────────

    suspend fun scanLocalVideos(onProgress: (Int) -> Unit = {}): List<VideoInfo> =
        withContext(Dispatchers.IO) {
            scanner.scanAllVideos(onProgress)
        }

    suspend fun scanLocalFolders(source: VideoSource = VideoSource.LOCAL): List<VideoFolder> =
        withContext(Dispatchers.IO) {
            val rootDirs = listOfNotNull(
                android.os.Environment.getExternalStorageDirectory()
            ).filter { it.exists() }

            val folderMap = scanner.scanVideoFolders(rootDirs, source)
            folderMap.map { (dir, videos) ->
                VideoFolder(
                    path = dir.absolutePath,
                    name = dir.name,
                    videoCount = videos.size,
                    thumbnailPath = videos.firstOrNull()?.path ?: "",
                    source = source
                )
            }.sortedBy { it.name }
        }

    suspend fun getVideosInLocalFolder(folderPath: String): List<VideoInfo> =
        withContext(Dispatchers.IO) {
            scanner.scanDirectoryIterative(File(folderPath), VideoSource.LOCAL)
        }

    fun getThumbnailUri(videoPath: String): Uri? {
        return scanner.getThumbnailUri(videoPath)
    }

    // ─── SMB ───────────────────────────────────────────────────

    suspend fun testSambaConnection(server: SambaServer): Result<Boolean> {
        return sambaClient.testConnection(server)
    }

    suspend fun listSambaFolders(server: SambaServer): Result<List<VideoFolder>> {
        return sambaClient.listFolders(server)
    }

    fun getSambaServers(): Flow<List<SambaServer>> {
        return sambaServerDao.getAllServers().map { entities ->
            entities.map { it.toModel() }
        }
    }

    suspend fun saveSambaServer(server: SambaServer): Long {
        val entity = SambaServerEntity(
            id = server.id,
            name = server.displayName.ifEmpty { server.name },
            host = server.host,
            port = server.port,
            shareName = server.shareName,
            username = server.username,
            password = server.password
        )
        return if (server.id == 0L) {
            sambaServerDao.insertServer(entity)
        } else {
            sambaServerDao.updateServer(entity)
            server.id
        }
    }

    suspend fun deleteSambaServer(server: SambaServer) {
        if (server.id != 0L) sambaServerDao.deleteServerById(server.id)
    }

    suspend fun getSambaServer(id: Long): SambaServer? = sambaServerDao.getServer(id)?.toModel()

    suspend fun listSambaFoldersById(serverId: Long): Result<List<VideoFolder>> {
        val server = getSambaServer(serverId) ?: return Result.failure(IllegalArgumentException("Samba server not found: $serverId"))
        return sambaClient.listFolders(server)
    }

    suspend fun listSambaVideosById(serverId: Long, folderPath: String): Result<List<VideoInfo>> {
        val server = getSambaServer(serverId) ?: return Result.failure(IllegalArgumentException("Samba server not found: $serverId"))
        return sambaClient.listVideos(server, folderPath)
    }

    suspend fun listSambaVideos(
        server: SambaServer,
        folderPath: String
    ): Result<List<VideoInfo>> {
        return sambaClient.listVideos(server, folderPath)
    }

    suspend fun cacheSambaThumbnail(
        server: SambaServer,
        remotePath: String,
        cacheDir: File
    ): Result<String> {
        return sambaClient.cacheForThumbnail(server, remotePath, cacheDir)
    }

    // ─── Playback Progress ────────────────────────────────────

    suspend fun getPlaybackProgress(path: String): Long {
        return progressDao.getProgress(path)?.position ?: 0L
    }

    suspend fun savePlaybackProgress(path: String, position: Long, duration: Long) {
        progressDao.saveProgress(
            PlaybackProgressEntity(
                path = path,
                position = position,
                duration = duration
            )
        )
    }

    // ─── Favorites ────────────────────────────────────────────

    suspend fun isFavorite(path: String): Boolean {
        return favoriteDao.isFavorite(path) > 0
    }

    suspend fun toggleFavorite(video: VideoInfo) {
        if (favoriteDao.isFavorite(video.path) > 0) {
            favoriteDao.removeFavoriteByPath(video.path)
        } else {
            favoriteDao.addFavorite(
                FavoriteEntity(
                    path = video.path,
                    name = video.name,
                    folderPath = video.folderPath,
                    source = video.source,
                    serverId = video.serverId
                )
            )
        }
    }

    // ─── Settings ─────────────────────────────────────────────

    fun getLongPressSpeed(): Flow<Float> = settings.longPressSpeed
    fun getVideoSort(): Flow<String> = settings.videoSort
    fun getThumbnailSize(): Flow<Int> = settings.thumbnailSize

    suspend fun setLongPressSpeed(speed: Float) = settings.setLongPressSpeed(speed)
    suspend fun setVideoSort(sort: String) = settings.setVideoSort(sort)
    suspend fun setThumbnailSize(size: Int) = settings.setThumbnailSize(size)

    private fun SambaServerEntity.toModel(): SambaServer = SambaServer(
        id = id,
        name = name,
        host = host,
        port = port,
        shareName = shareName,
        username = username,
        password = password,
        displayName = name
    )
}
