package com.yuhinhsiung.clothesmate.feature.today.data

import com.yuhinhsiung.clothesmate.core.datastore.UserSettingsStore
import com.yuhinhsiung.clothesmate.core.weather.WeatherCacheStore
import com.yuhinhsiung.clothesmate.core.weather.WeatherRepository
import com.yuhinhsiung.clothesmate.core.weather.WeatherSnapshot
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.json.Json
import java.time.LocalDate
import javax.inject.Inject

/**
 * 「今天穿这套」的应用层入口：读取当天行程与天气快照，再交给 [WearRepository]
 * 在同一事务中写记录、加计数。
 *
 * 快照只在**记录发生的那一刻**取一次（M3-08）：之后天气变化、行程改动都不会
 * 回写历史；日历里看到的就是当天真实的样子。
 */
interface WearService {
    /** 今天穿这套；返回新建的 wear_record id。 */
    suspend fun confirmToday(outfitId: Long, note: String? = null): Long

    /** 补记历史日期（M3-07）：能取到最近 10 天历史天气时写入真实快照。 */
    suspend fun backfill(outfitId: Long, date: LocalDate, note: String? = null): Long
}

class DefaultWearService @Inject constructor(
    private val wearRepository: WearRepository,
    private val scheduleSelectionStore: ScheduleSelectionStore,
    private val weatherCacheStore: WeatherCacheStore,
    private val settingsStore: UserSettingsStore,
    private val weatherRepository: WeatherRepository,
    private val json: Json,
) : WearService {

    override suspend fun confirmToday(outfitId: Long, note: String?): Long {
        val today = LocalDate.now()
        val schedule = scheduleSelectionStore.observe()
            .firstOrNull()
            ?.takeIf { it.date == today }
            ?.tags
            ?.sorted()
            .orEmpty()
        val configuredCity = settingsStore.settings.firstOrNull()?.weatherCity.orEmpty().trim()
        val cachedLocation = weatherCacheStore.location.firstOrNull()
        val weather = weatherCacheStore.snapshot.firstOrNull()
            ?.takeIf { snapshot ->
                // review R04：跨午夜后昨天的缓存不能写进今天的记录；
                // review R07：城市查询用 LocationID 缓存对齐，不能只比中文名。
                snapshot.isForDate(today) &&
                    snapshot.fetchedAt > System.currentTimeMillis() - WEATHER_MAX_AGE_MS &&
                    (configuredCity.isBlank() || snapshot.matchesQuery(configuredCity, cachedLocation))
            }
            ?.toJsonOrNull()
        return wearRepository.confirmWear(
            outfitId = outfitId,
            date = today,
            scheduleTags = schedule,
            weatherJson = weather,
            note = note?.trim()?.take(500)?.takeIf { it.isNotEmpty() },
        ).id
    }

    override suspend fun backfill(outfitId: Long, date: LocalDate, note: String?): Long {
        val today = LocalDate.now()
        require(!date.isAfter(today)) { "不能补记未来日期" }
        val historicalWeather = if (date.isBefore(today) && date >= today.minusDays(10)) {
            val settings = settingsStore.settings.firstOrNull()
            val city = settings?.weatherCity.orEmpty().trim()
            val auth = settings?.weatherAuth
            if (city.isNotEmpty() && auth != null && auth.isConfigured) {
                runCatching { weatherRepository.fetchHistorical(city, auth, date) }
                    .getOrNull()
                    ?.toJsonOrNull()
            } else {
                null
            }
        } else {
            null
        }
        return wearRepository.confirmWear(
            outfitId = outfitId,
            date = date,
            scheduleTags = emptyList(),
            weatherJson = historicalWeather,
            note = note?.trim()?.take(500)?.takeIf { it.isNotEmpty() },
        ).id
    }

    private fun WeatherSnapshot.toJsonOrNull(): String? =
        runCatching { json.encodeToString(WeatherSnapshot.serializer(), this) }.getOrNull()

    private companion object {
        const val WEATHER_MAX_AGE_MS = 6 * 60 * 60 * 1000L
    }
}
