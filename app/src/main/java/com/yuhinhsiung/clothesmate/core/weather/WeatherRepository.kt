package com.yuhinhsiung.clothesmate.core.weather

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

interface WeatherRepository {
    /** 拉取并缓存今日天气。 */
    suspend fun fetch(locationQuery: String, auth: WeatherAuthConfig): WeatherSnapshot

    /**
     * 拉取最近 10 天历史天气；只用于「补记」的穿着快照，不写入今日缓存。
     */
    suspend fun fetchHistorical(
        locationQuery: String,
        auth: WeatherAuthConfig,
        date: LocalDate,
    ): WeatherSnapshot
}

@Singleton
class DefaultWeatherRepository @Inject constructor(
    private val client: WeatherDataSource,
    private val cacheStore: WeatherCacheStore,
) : WeatherRepository {

    override suspend fun fetch(locationQuery: String, auth: WeatherAuthConfig): WeatherSnapshot {
        validate(locationQuery, auth)
        val query = locationQuery.trim()
        val known = cacheStore.location.firstOrNull()
            ?.takeIf { it.query == query }
            ?.toKnownLocation()
        val snapshot = callWithLocationFallback(query, known) { location ->
            client.fetch(query, auth, location)
        }
        runCatching {
            cacheStore.save(snapshot)
            snapshot.locationId?.let { id ->
                cacheStore.saveLocation(CachedWeatherLocation(query, id, snapshot.city))
            }
        }
        return snapshot
    }

    override suspend fun fetchHistorical(
        locationQuery: String,
        auth: WeatherAuthConfig,
        date: LocalDate,
    ): WeatherSnapshot {
        validate(locationQuery, auth)
        val query = locationQuery.trim()
        // 历史天气不会变化：同城同日重复补记直接命中本地缓存，不再计费。
        cacheStore.historical(query, date)?.let { return it }

        val known = cacheStore.location.firstOrNull()
            ?.takeIf { it.query == query }
            ?.toKnownLocation()
        val snapshot = callWithLocationFallback(query, known) { location ->
            client.fetchHistorical(query, auth, date, location)
        }
        runCatching {
            cacheStore.saveHistorical(query, date, snapshot)
            snapshot.locationId?.let { id ->
                cacheStore.saveLocation(CachedWeatherLocation(query, id, snapshot.city))
            }
        }
        return snapshot
    }

    private fun CachedWeatherLocation.toKnownLocation(): KnownWeatherLocation =
        KnownWeatherLocation(locationId = locationId, name = name)

    /**
     * 缓存 LocationID 失效（城市合并/迁移）时只回退一次重新检索；成功后再写回缓存，
     * 以后就不再重复 GeoAPI 请求。
     */
    private suspend fun callWithLocationFallback(
        query: String,
        known: KnownWeatherLocation?,
        block: suspend (KnownWeatherLocation?) -> WeatherSnapshot,
    ): WeatherSnapshot {
        if (known == null) return call { block(null) }
        return try {
            call { block(known) }
        } catch (error: WeatherException) {
            if (error.failure != WeatherFailure.CITY_NOT_FOUND) throw error
            call { block(null) }
        }
    }

    private fun validate(locationQuery: String, auth: WeatherAuthConfig) {
        if (!auth.isConfigured) throw WeatherException(WeatherFailure.NO_KEY)
        if (locationQuery.isBlank()) throw WeatherException(WeatherFailure.NO_LOCATION)
    }

    private suspend fun call(block: suspend () -> WeatherSnapshot): WeatherSnapshot = try {
        withTimeout(REQUEST_TIMEOUT_MS) { block() }
    } catch (error: TimeoutCancellationException) {
        throw WeatherException(WeatherFailure.TIMEOUT, "天气请求超时", error)
    } catch (error: kotlinx.coroutines.CancellationException) {
        // 页面离开/切换城市导致的取消不是失败，不能被下面的 Exception 兜底吞掉。
        throw error
    } catch (error: WeatherException) {
        throw error
    } catch (error: QWeatherException) {
        throw WeatherException(error.failure, error.message, error)
    } catch (error: IOException) {
        throw WeatherException(WeatherFailure.NETWORK, error.message, error)
    } catch (error: Exception) {
        throw WeatherException(WeatherFailure.UNKNOWN, error.message, error)
    }

    private companion object {
        const val REQUEST_TIMEOUT_MS = 12_000L
    }
}
