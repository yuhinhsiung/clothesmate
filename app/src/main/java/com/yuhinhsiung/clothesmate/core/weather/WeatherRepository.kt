package com.yuhinhsiung.clothesmate.core.weather

import kotlinx.coroutines.TimeoutCancellationException
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
    private val client: QWeatherClient,
    private val cacheStore: WeatherCacheStore,
) : WeatherRepository {

    override suspend fun fetch(locationQuery: String, auth: WeatherAuthConfig): WeatherSnapshot {
        validate(locationQuery, auth)
        val snapshot = call { client.fetch(locationQuery.trim(), auth) }
        runCatching { cacheStore.save(snapshot) }
        return snapshot
    }

    override suspend fun fetchHistorical(
        locationQuery: String,
        auth: WeatherAuthConfig,
        date: LocalDate,
    ): WeatherSnapshot {
        validate(locationQuery, auth)
        return call { client.fetchHistorical(locationQuery.trim(), auth, date) }
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
