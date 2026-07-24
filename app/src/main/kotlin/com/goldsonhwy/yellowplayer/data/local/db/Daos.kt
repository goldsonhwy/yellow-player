package com.goldsonhwy.yellowplayer.data.local.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY timestamp DESC")
    fun getAllFavorites(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites ORDER BY timestamp DESC")
    suspend fun getAllFavoritesOnce(): List<FavoriteEntity>

    @Query("SELECT * FROM favorites WHERE originalPath = :path OR currentPath = :path LIMIT 1")
    suspend fun getFavoriteByAnyPath(path: String): FavoriteEntity?

    @Query("SELECT COUNT(*) FROM favorites WHERE originalPath = :path OR currentPath = :path")
    suspend fun isFavorite(path: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteEntity)

    @Update
    suspend fun updateFavorite(favorite: FavoriteEntity)

    @Delete
    suspend fun removeFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE originalPath = :path OR currentPath = :path")
    suspend fun removeFavoriteByPath(path: String)
}

@Dao
interface PlaybackProgressDao {
    @Query("SELECT * FROM playback_progress WHERE path = :path")
    suspend fun getProgress(path: String): PlaybackProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: PlaybackProgressEntity)

    @Query("DELETE FROM playback_progress")
    suspend fun clearAll()
}

@Dao
interface SambaServerDao {
    @Query("SELECT * FROM samba_servers ORDER BY name ASC")
    fun getAllServers(): Flow<List<SambaServerEntity>>

    @Query("SELECT * FROM samba_servers ORDER BY name ASC")
    fun getAllServersBlocking(): List<SambaServerEntity>

    @Query("SELECT * FROM samba_servers WHERE id = :id")
    suspend fun getServer(id: Long): SambaServerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServer(server: SambaServerEntity): Long

    @Update
    suspend fun updateServer(server: SambaServerEntity)

    @Delete
    suspend fun deleteServer(server: SambaServerEntity)

    @Query("DELETE FROM samba_servers WHERE id = :id")
    suspend fun deleteServerById(id: Long)
}
