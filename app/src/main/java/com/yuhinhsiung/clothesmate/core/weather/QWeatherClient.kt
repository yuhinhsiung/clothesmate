package com.yuhinhsiung.clothesmate.core.weather

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import retrofit2.HttpException
import retrofit2.http.GET
import retrofit2.http.Url
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 和风天气 QWeather v7 端点 —— M3-01。
 *
 * 与 AI 一样使用 @Url 绝对地址调用，因此 baseUrl 不参与请求拼接；这样用户可配置
 * 商业版/开发版/自建代理域名。Key 只放 query（QWeather 规范），不写日志。
 */
interface QWeatherApi {

    @GET
    suspend fun cityLookup(@Url url: String): CityLookupResponse

    @GET
    suspend fun now(@Url url: String): NowResponse

    @GET
    suspend fun daily(@Url url: String): DailyResponse
}

object WeatherEndpoints {
    const val DEFAULT_BASE = "https://devapi.qweather.com/v7"
    const val DEFAULT_GEO_BASE = "https://geoapi.qweather.com/v2"

    fun normalizeBase(raw: String?): String {
        val trimmed = raw?.trim().orEmpty().trimEnd('/')
        val candidate = trimmed.ifEmpty { DEFAULT_BASE }
        require(candidate.startsWith("http://") || candidate.startsWith("https://")) {
            "天气 Base URL 必须以 http:// 或 https:// 开头"
        }
        require(candidate.toHttpUrlOrNull() != null) { "天气 Base URL 格式不正确" }
        return candidate
    }
}

@Serializable
data class CityLookupResponse(
    val code: String,
    val location: List<CityLocation> = emptyList(),
)

@Serializable
data class CityLocation(
    val name: String,
    val id: String,
    val lat: String? = null,
    val lon: String? = null,
    val adm2: String? = null,
    val adm1: String? = null,
    val country: String? = null,
)

@Serializable
data class NowResponse(
    val code: String,
    val now: NowDto? = null,
)

@Serializable
data class NowDto(
    val obsTime: String? = null,
    val temp: String? = null,
    val feelsLike: String? = null,
    val icon: String? = null,
    val text: String? = null,
    val wind360: String? = null,
    val windDir: String? = null,
    val windScale: String? = null,
    val windSpeed: String? = null,
    val humidity: String? = null,
    val precip: String? = null,
    val pressure: String? = null,
    val vis: String? = null,
    val cloud: String? = null,
    val dew: String? = null,
)

@Serializable
data class DailyResponse(
    val code: String,
    val daily: List<DailyDto> = emptyList(),
)

@Serializable
data class DailyDto(
    val fxDate: String? = null,
    val sunrise: String? = null,
    val sunset: String? = null,
    val tempMax: String? = null,
    val tempMin: String? = null,
    val iconDay: String? = null,
    val textDay: String? = null,
    val wind360Day: String? = null,
    val windDirDay: String? = null,
    val windScaleDay: String? = null,
    val windSpeedDay: String? = null,
    val humidity: String? = null,
    val precip: String? = null,
    val pressure: String? = null,
    val vis: String? = null,
    val cloud: String? = null,
    val uvIndex: String? = null,
)

class QWeatherException(
    val failure: WeatherFailure,
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * QWeather v7 客户端：先按城市/经纬度查到 locationId，再并发取实况与今日预报。
 *
 * - 城市检索失败（404）→ [WeatherFailure.CITY_NOT_FOUND]；
 * - 401/403 → [WeatherFailure.AUTH]；
 * - 响应 code 非 200 或 JSON 结构异常 → [WeatherFailure.UNKNOWN]；
 * - 超时/网络异常由调用方（Repository）统一映射。
 */
@Singleton
class QWeatherClient @Inject constructor(
    private val api: QWeatherApi,
) {

    suspend fun fetch(
        locationQuery: String,
        apiKey: String,
        baseUrl: String? = null,
        geoBaseUrl: String = WeatherEndpoints.DEFAULT_GEO_BASE,
    ): WeatherSnapshot {
        val base = WeatherEndpoints.normalizeBase(baseUrl)
        val city = lookupCity(geoBaseUrl, locationQuery, apiKey)

        val now = runCatching {
            api.now(url(base, "/weather/now", location = city.id, apiKey = apiKey))
        }.getOrElse { throw it.toWeatherException() }
        if (now.code != "200") throw codeException(now.code)
        val nowDto = now.now ?: throw QWeatherException(WeatherFailure.UNKNOWN, "实况为空")

        val daily = runCatching {
            api.daily(url(base, "/weather/3d", location = city.id, apiKey = apiKey))
        }.getOrElse { throw it.toWeatherException() }
        if (daily.code != "200") throw codeException(daily.code)
        val today = daily.daily.firstOrNull()
            ?: throw QWeatherException(WeatherFailure.UNKNOWN, "预报为空")

        val temp = nowDto.temp?.toIntOrNull()
            ?: throw QWeatherException(WeatherFailure.UNKNOWN, "实况温度缺失")
        val feelsLike = nowDto.feelsLike?.toIntOrNull() ?: temp
        val min = today.tempMin?.toIntOrNull() ?: temp
        val max = today.tempMax?.toIntOrNull() ?: temp
        val text = today.textDay?.takeIf { it.isNotBlank() }
            ?: nowDto.text?.takeIf { it.isNotBlank() }
            ?: "未知"

        return WeatherSnapshot(
            city = city.name,
            temp = temp,
            feelsLike = feelsLike,
            minTemp = minOf(min, max),
            maxTemp = maxOf(min, max),
            text = text,
            windScale = today.windScaleDay ?: nowDto.windScale,
            fetchedAt = System.currentTimeMillis(),
        )
    }

    private suspend fun lookupCity(
        base: String,
        query: String,
        apiKey: String,
    ): CityLocation {
        val response = runCatching {
            api.cityLookup(url(base, "/city/lookup", location = query, apiKey = apiKey))
        }.getOrElse { throw it.toWeatherException() }

        if (response.code == "404") {
            throw QWeatherException(WeatherFailure.CITY_NOT_FOUND, "未找到城市")
        }
        if (response.code != "200") throw codeException(response.code)
        return response.location.firstOrNull()
            ?: throw QWeatherException(WeatherFailure.CITY_NOT_FOUND, "未找到城市")
    }

    private fun url(base: String, path: String, location: String, apiKey: String): String {
        val root = (base + path).toHttpUrlOrNull()
            ?: throw QWeatherException(WeatherFailure.UNKNOWN, "天气地址格式错误")
        return root.newBuilder()
            .addQueryParameter("location", location)
            .addQueryParameter("key", apiKey)
            .addQueryParameter("lang", "zh")
            .build()
            .toString()
    }

    private fun codeException(code: String): QWeatherException = when (code) {
        "401", "403" -> QWeatherException(WeatherFailure.AUTH, "API Key 无效")
        "404" -> QWeatherException(WeatherFailure.CITY_NOT_FOUND, "城市无数据")
        else -> QWeatherException(WeatherFailure.UNKNOWN, "QWeather 返回 $code")
    }

    private fun Throwable.toWeatherException(): QWeatherException {
        if (this is kotlinx.coroutines.CancellationException) throw this
        if (this is QWeatherException) return this
        return when (this) {
            is HttpException -> when (code()) {
                401, 403 -> QWeatherException(WeatherFailure.AUTH, "API Key 无效", this)
                404 -> QWeatherException(WeatherFailure.CITY_NOT_FOUND, "城市无数据", this)
                else -> QWeatherException(WeatherFailure.NETWORK, "HTTP ${code()}", this)
            }

            is SerializationException ->
                QWeatherException(WeatherFailure.UNKNOWN, "天气响应解析失败", this)

            else -> QWeatherException(WeatherFailure.NETWORK, message, this)
        }
    }
}

/**
 * Repository 层的超时/网络映射。QWeather 客户端自身只把“服务能用但返回业务错误”
 * 翻译成领域失败；传输层统一在这里收口，便于单测。
 */
internal fun Throwable.toWeatherFailure(): WeatherFailure = when (this) {
    is WeatherException -> failure
    is QWeatherException -> failure
    is SocketTimeoutException, is TimeoutException -> WeatherFailure.TIMEOUT
    is IOException -> WeatherFailure.NETWORK
    else -> WeatherFailure.UNKNOWN
}
