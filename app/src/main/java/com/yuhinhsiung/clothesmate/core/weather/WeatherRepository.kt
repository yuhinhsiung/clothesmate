package com.yuhinhsiung.clothesmate.core.weather

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

interface WeatherRepository {
    /**
     * 拉取并缓存天气。
     *
     * @param locationQuery 城市名（如「杭州」）或「经度,纬度」
     * @param apiKey 用户配置的 QWeather Key
     * @param baseUrl 可选自定义 Base URL；为空用 [WeatherEndpoints.DEFAULT_BASE]
     */
    suspend fun fetch(
        locationQuery: String,
        apiKey: String,
        baseUrl: String? = null,
    ): WeatherSnapshot
}

@Singleton
class DefaultWeatherRepository @Inject constructor(
    private val client: QWeatherClient,
    private val cacheStore: WeatherCacheStore,
) : WeatherRepository {

    override suspend fun fetch(
        locationQuery: String,
        apiKey: String,
        baseUrl: String?,
    ): WeatherSnapshot {
        if (apiKey.isBlank()) throw WeatherException(WeatherFailure.NO_KEY)
        if (locationQuery.isBlank()) throw WeatherException(WeatherFailure.NO_LOCATION)

        val snapshot = try {
            withTimeout(REQUEST_TIMEOUT_MS) {
                client.fetch(
                    locationQuery = locationQuery,
                    apiKey = apiKey.trim(),
                    baseUrl = baseUrl,
                )
            }
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

        // 缓存失败不能反噬已经成功的网络结果；下次打开会重新拉。
        runCatching { cacheStore.save(snapshot) }
        return snapshot
    }

    private companion object {
        const val REQUEST_TIMEOUT_MS = 12_000L
    }
}
