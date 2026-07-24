package com.goldsonhwy.yellowplayer.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsDataStore(private val context: Context) {

    companion object {
        private val LONG_PRESS_SPEED = floatPreferencesKey("long_press_speed")
        private val VIDEO_SORT = stringPreferencesKey("video_sort")
        private val THUMBNAIL_SIZE = intPreferencesKey("thumbnail_size")

        const val SORT_NAME = "name"
        const val SORT_DATE = "date"
        const val SORT_SIZE = "size"
        const val SORT_RANDOM = "random"
    }

    val longPressSpeed: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[LONG_PRESS_SPEED] ?: 2.0f
    }

    val videoSort: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[VIDEO_SORT] ?: SORT_NAME
    }

    val thumbnailSize: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[THUMBNAIL_SIZE] ?: 180
    }

    suspend fun setLongPressSpeed(speed: Float) {
        context.dataStore.edit { prefs ->
            prefs[LONG_PRESS_SPEED] = speed
        }
    }

    suspend fun setVideoSort(sort: String) {
        context.dataStore.edit { prefs ->
            prefs[VIDEO_SORT] = sort
        }
    }

    suspend fun setThumbnailSize(size: Int) {
        context.dataStore.edit { prefs ->
            prefs[THUMBNAIL_SIZE] = size
        }
    }
}
