package com.yuhinhsiung.clothesmate.core.weather

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** 最后一次成功天气快照（M3-01 的「缓存兜底」）。 */
interface WeatherCacheStore {
    val snapshot: Flow<WeatherSnapshot?>
    suspend fun save(snapshot: WeatherSnapshot)
    suspend fun clear()
}

@Singleton
class DataStoreWeatherCacheStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
) : WeatherCacheStore {

    override val snapshot: Flow<WeatherSnapshot?> = dataStore.data
        .catch { error ->
            // DataStore 读失败不能把今日页整个打崩：当作无缓存，等待网络刷新。
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
            else throw error
        }
        .map { preferences ->
            preferences[KEY]?.let { raw ->
                runCatching { json.decodeFromString<WeatherSnapshot>(raw) }.getOrNull()
            }
        }

    override suspend fun save(snapshot: WeatherSnapshot) {
        dataStore.edit { preferences ->
            preferences[KEY] = json.encodeToString(WeatherSnapshot.serializer(), snapshot)
        }
    }

    override suspend fun clear() {
        dataStore.edit { preferences -> preferences.remove(KEY) }
    }

    private companion object {
        val KEY = stringPreferencesKey("weather_cache_json")
    }
}
