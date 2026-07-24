package com.goldsonhwy.yellowplayer.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.goldsonhwy.yellowplayer.data.model.VideoSource

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey
    val originalPath: String,
    val currentPath: String,
    val name: String,
    val originalFolderPath: String,
    val currentFolderPath: String,
    val source: VideoSource,
    val serverId: Long = 0,
    val movedToFavoriteDir: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "playback_progress")
data class PlaybackProgressEntity(
    @PrimaryKey
    val path: String,
    val position: Long = 0,
    val duration: Long = 0,
    val lastPlayed: Long = System.currentTimeMillis()
)

@Entity(tableName = "samba_servers")
data class SambaServerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String = "",
    val host: String,
    val port: Int = 445,
    val shareName: String = "",
    val username: String = "",
    val password: String = ""
)
