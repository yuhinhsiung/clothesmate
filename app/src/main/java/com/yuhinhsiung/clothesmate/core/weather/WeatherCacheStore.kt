package com.yuhinhsiung.clothesmate.core.weather

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 城市检索结果缓存（M3 成本控制）。
 *
 * 和风天气按请求计费，而同一城市的 `locationId` 长期不变。缓存后，实况/预报/历史
 * 请求都直接带 LocationID，省掉每次 `/geo/v2/city/lookup` 的一次计费请求。
 */
@Serializable
data class CachedWeatherLocation(
    /** 用户输入的原始查询（城市名或 `lon,lat`） */
    val query: String,
    val locationId: String,
    val name: String,
)

/** 最后一次成功天气快照（M3-01 的「缓存兜底」）。 */
interface WeatherCacheStore {
    val snapshot: Flow<WeatherSnapshot?>

    /** 最近一次成功使用的城市 LocationID；用于减少 GeoAPI 请求。 */
    val location: Flow<CachedWeatherLocation?>

    suspend fun save(snapshot: WeatherSnapshot)
    suspend fun saveLocation(location: CachedWeatherLocation)

    /** 历史天气按「城市+日期」永久缓存：历史数据不会变化，重复补记不应再计费。 */
    suspend fun historical(query: String, date: LocalDate): WeatherSnapshot?
    suspend fun saveHistorical(query: String, date: LocalDate, snapshot: WeatherSnapshot)

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

    override val location: Flow<CachedWeatherLocation?> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
            else throw error
        }
        .map { preferences ->
            preferences[LOCATION_KEY]?.let { raw ->
                runCatching { json.decodeFromString<CachedWeatherLocation>(raw) }.getOrNull()
            }
        }

    override suspend fun save(snapshot: WeatherSnapshot) {
        dataStore.edit { preferences ->
            preferences[KEY] = json.encodeToString(WeatherSnapshot.serializer(), snapshot)
        }
    }

    override suspend fun saveLocation(location: CachedWeatherLocation) {
        dataStore.edit { preferences ->
            preferences[LOCATION_KEY] =
                json.encodeToString(CachedWeatherLocation.serializer(), location)
        }
    }

    override suspend fun historical(query: String, date: LocalDate): WeatherSnapshot? {
        val preferences = dataStore.data.catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
            else throw error
        }.firstOrNull() ?: return null
        val history = preferences[HISTORY_KEY]?.let(::decodeHistory).orEmpty()
        return history[historyKey(query, date)]
    }

    override suspend fun saveHistorical(
        query: String,
        date: LocalDate,
        snapshot: WeatherSnapshot,
    ) {
        dataStore.edit { preferences ->
            val history = preferences[HISTORY_KEY]?.let(::decodeHistory).orEmpty().toMutableMap()
            history[historyKey(query, date)] = snapshot
            // 历史天气只在最近 10 天补记时使用；保留 30 条足够覆盖一个月，
            // 防止偏好文件无限增长。
            val trimmed = history.entries
                .sortedByDescending { it.key.substringAfterLast('|') }
                .take(MAX_HISTORICAL_ENTRIES)
                .associate { it.key to it.value }
            preferences[HISTORY_KEY] = json.encodeToString(HISTORICAL_SERIALIZER, trimmed)
        }
    }

    override suspend fun clear() {
        dataStore.edit { preferences ->
            preferences.remove(KEY)
            preferences.remove(LOCATION_KEY)
            preferences.remove(HISTORY_KEY)
        }
    }

    private fun decodeHistory(raw: String): Map<String, WeatherSnapshot> =
        runCatching { json.decodeFromString(HISTORICAL_SERIALIZER, raw) }.getOrDefault(emptyMap())

    private fun historyKey(query: String, date: LocalDate): String = "$query|$date"

    private companion object {
        val KEY = stringPreferencesKey("weather_cache_json")
        val LOCATION_KEY = stringPreferencesKey("weather_location_json")
        val HISTORY_KEY = stringPreferencesKey("weather_history_json")
        const val MAX_HISTORICAL_ENTRIES = 30
        val HISTORICAL_SERIALIZER = MapSerializer(String.serializer(), WeatherSnapshot.serializer())
    }
}
