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
            val savedRoots = context.getSharedPreferences("local_video_folders", Context.MODE_PRIVATE)
                .getStringSet("paths", emptySet())
                .orEmpty()

            val rootDirs = savedRoots
                .map { File(it) }
                .filter { it.exists() && it.isDirectory }

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

    suspend fun getFavoriteVideos(): List<VideoInfo> = withContext(Dispatchers.IO) {
        favoriteDao.getAllFavoritesOnce().map { fav ->
            VideoInfo(
                name = fav.name,
                path = fav.currentPath,
                uri = Uri.fromFile(File(fav.currentPath)).toString(),
                folderPath = fav.currentFolderPath,
                source = fav.source,
                serverId = fav.serverId
            )
        }.filter { File(it.path).exists() }
    }

    suspend fun toggleFavorite(video: VideoInfo): VideoInfo? = withContext(Dispatchers.IO) {
        val existing = favoriteDao.getFavoriteByAnyPath(video.path)
        if (existing != null) {
            // Unfavorite: move back if this app moved it into the favorite directory.
            if (existing.movedToFavoriteDir) {
                runCatching {
                    val current = File(existing.currentPath)
                    val original = File(existing.originalPath)
                    original.parentFile?.mkdirs()
                    if (current.exists()) current.renameTo(original)
                }
            }
            favoriteDao.removeFavoriteByPath(video.path)
            null
        } else {
            val original = File(video.path)
            val favoriteRoot = context.getSharedPreferences("favorite_move", Context.MODE_PRIVATE)
                .getString("dir", "")
                .orEmpty()
            val savedRoots = context.getSharedPreferences("local_video_folders", Context.MODE_PRIVATE)
                .getStringSet("paths", emptySet())
                .orEmpty()
            var currentPath = video.path
            var currentFolder = video.folderPath
            var moved = false

            if (favoriteRoot.isNotBlank() && original.exists() && video.source == VideoSource.LOCAL) {
                val base = savedRoots.firstOrNull { video.path.startsWith(it.trimEnd('/') + "/") }
                    ?: original.parentFile?.absolutePath.orEmpty()
                val rel = if (base.isNotBlank()) video.path.removePrefix(base).trimStart('/') else original.name
                val target = File(favoriteRoot, rel)
                target.parentFile?.mkdirs()
                if (original.renameTo(target)) {
                    currentPath = target.absolutePath
                    currentFolder = target.parentFile?.absolutePath.orEmpty()
                    moved = true
                }
            }

            val savedVideo = video.copy(
                path = currentPath,
                uri = Uri.fromFile(File(currentPath)).toString(),
                folderPath = currentFolder
            )
            favoriteDao.addFavorite(
                FavoriteEntity(
                    originalPath = video.path,
                    currentPath = currentPath,
                    name = video.name,
                    originalFolderPath = video.folderPath,
                    currentFolderPath = currentFolder,
                    source = video.source,
                    serverId = video.serverId,
                    movedToFavoriteDir = moved
                )
            )
            savedVideo
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
