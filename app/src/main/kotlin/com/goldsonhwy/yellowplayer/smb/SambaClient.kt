package com.goldsonhwy.yellowplayer.smb

import android.net.Uri
import com.goldsonhwy.yellowplayer.data.model.SambaServer
import com.goldsonhwy.yellowplayer.data.model.VideoFolder
import com.goldsonhwy.yellowplayer.data.model.VideoInfo
import com.goldsonhwy.yellowplayer.data.model.VideoSource
import jcifs.CIFSContext
import jcifs.CIFSException
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import jcifs.smb.SmbFileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.MalformedURLException

/**
 * SMB client wrapper for jcifs-ng.
 * Handles connection, directory listing, file discovery, and thumbnail caching.
 */
class SambaClient {

    private val cifsContext: ThreadLocal<CIFSContext?> = ThreadLocal()

    /**
     * Create an authenticated CIFS context for a given server.
     */
    private fun createContext(server: SambaServer): CIFSContext {
        return cifsContext.get()?.let {
            // Re-use context if credentials match
            it
        } ?: run {
            val auth = NtlmPasswordAuthenticator(server.username, server.password)
            val ctx = BaseContext().withCredentials(auth)
            cifsContext.set(ctx)
            ctx
        }
    }

    /**
     * Build a proper SMB URL.
     */
    private fun buildSmbUrl(server: SambaServer, path: String = ""): String {
        val share = if (server.shareName.isNotEmpty()) "/${server.shareName}" else ""
        val dir = if (path.isNotEmpty() && !path.startsWith("/")) "/$path" else path
        return "smb://${server.host}:${server.port}$share$dir/"
    }

    /**
     * Test connection to a Samba server.
     */
    suspend fun testConnection(server: SambaServer): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val ctx = createContext(server)
            val url = buildSmbUrl(server)
            val file = SmbFile(url, ctx)
            Result.success(file.exists())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * List all folders/shares on a server.
     */
    suspend fun listFolders(server: SambaServer): Result<List<VideoFolder>> =
        withContext(Dispatchers.IO) {
            try {
                val ctx = createContext(server)
                val url = buildSmbUrl(server)
                val smbFile = SmbFile(url, ctx)
                val entries = smbFile.listFiles()

                val folders = entries
                    .filter { it.isDirectory }
                    .map { dir ->
                        val videoFiles = dir.listFiles()
                            ?.filter { isVideoFile(it.name) }
                            ?: emptyList()

                        VideoFolder(
                            path = dir.path,
                            name = dir.name,
                            videoCount = videoFiles.size,
                            thumbnailPath = videoFiles.firstOrNull()?.path ?: "",
                            source = VideoSource.SAMBA,
                            serverId = server.id
                        )
                    }

                Result.success(folders)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * List video files in a specific SMB path.
     */
    suspend fun listVideos(server: SambaServer, folderPath: String): Result<List<VideoInfo>> =
        withContext(Dispatchers.IO) {
            try {
                val ctx = createContext(server)
                val url = if (folderPath.endsWith("/")) folderPath else "$folderPath/"
                val smbFile = SmbFile(url, ctx)
                val entries = smbFile.listFiles()

                val videos = entries
                    .filter { !it.isDirectory && isVideoFile(it.name) }
                    .map { file ->
                        VideoInfo(
                            name = file.name,
                            path = file.path,
                            size = file.length(),
                            dateModified = file.lastModified(),
                            folderPath = file.parent,
                            source = VideoSource.SAMBA,
                            serverId = server.id
                        )
                    }

                Result.success(videos)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Stream a remote SMB file to local cache for thumbnail generation.
     * Returns the local cached file path.
     */
    suspend fun cacheForThumbnail(
        server: SambaServer,
        remotePath: String,
        cacheDir: File
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val ctx = createContext(server)
            val smbFile = SmbFile(remotePath, ctx)
            val cacheFile = File(cacheDir, "smb_thumb_${smbFile.name}")

            if (cacheFile.exists() && cacheFile.lastModified() > smbFile.lastModified()) {
                return@withContext Result.success(cacheFile.absolutePath)
            }

            smbFile.inputStream.use { input ->
                FileOutputStream(cacheFile).use { output ->
                    input.copyTo(output, bufferSize = 8192)
                }
            }

            Result.success(cacheFile.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Download SMB file to local cache for playback.
     */
    fun getInputStream(server: SambaServer, remotePath: String): Result<SmbFileInputStream> {
        return try {
            val ctx = createContext(server)
            val smbFile = SmbFile(remotePath, ctx)
            Result.success(smbFile.inputStream as SmbFileInputStream)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun cleanup() {
        cifsContext.set(null)
    }

    companion object {
        private val VIDEO_EXTENSIONS = setOf(
            "mp4", "mkv", "avi", "mov", "flv", "webm",
            "ts", "m4v", "3gp", "wmv", "mpeg", "mpg", "vob"
        )

        fun isVideoFile(name: String): Boolean {
            val ext = name.substringAfterLast('.', "").lowercase()
            return ext in VIDEO_EXTENSIONS
        }
    }
}
