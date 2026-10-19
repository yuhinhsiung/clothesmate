package com.yuhinhsiung.clothesmate.core.weather

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import retrofit2.HttpException
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Url
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 和风天气 API（2026 JWT 版）。
 *
 * 所有请求都使用用户自己的 API Host（控制台分配），路径：
 * - `/geo/v2/city/lookup` 城市检索
 * - `/v7/weather/now` 实况
 * - `/v7/weather/3d` 今日预报
 * - `/v7/historical/weather` 最近 10 天历史（不含今天）
 *
 * 认证统一用 `Authorization: Bearer <JWT>`，不再使用 `key` query；JWT 由
 * [QWeatherJwtProvider] 生成，私钥不进入请求。
 */
interface QWeatherApi {

    @GET
    suspend fun cityLookup(
        @Url url: String,
        @Header("Authorization") authorization: String,
    ): CityLookupResponse

    @GET
    suspend fun now(
        @Url url: String,
        @Header("Authorization") authorization: String,
    ): NowResponse

    @GET
    suspend fun daily(
        @Url url: String,
        @Header("Authorization") authorization: String,
    ): DailyResponse

    @GET
    suspend fun historical(
        @Url url: String,
        @Header("Authorization") authorization: String,
    ): HistoricalResponse
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

@Serializable
data class HistoricalResponse(
    val code: String,
    val weatherDaily: HistoricalDailyDto? = null,
    val weatherHourly: List<HistoricalHourlyDto> = emptyList(),
)

@Serializable
data class HistoricalDailyDto(
    val date: String? = null,
    val tempMax: String? = null,
    val tempMin: String? = null,
)

@Serializable
data class HistoricalHourlyDto(
    val time: String? = null,
    val temp: String? = null,
    val text: String? = null,
    val windScale: String? = null,
)

class QWeatherException(
    val failure: WeatherFailure,
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause)

/** 已缓存的 LocationID；避免每次刷新都调用一次 GeoAPI。 */
data class KnownWeatherLocation(val locationId: String, val name: String)

/**
 * QWeather 客户端：先按城市/经纬度查到 locationId，再取实况、今日预报或历史。
 *
 * JWT 生成失败 / 401 / 403 → [WeatherFailure.AUTH]；城市无数据 → CITY_NOT_FOUND；
 * 其余业务码 → UNKNOWN；超时/网络异常由 Repository 统一映射。
 */
/** 天气数据源接口：便于对缓存/计费策略做无网络的单元测试。 */
interface WeatherDataSource {
    suspend fun fetch(
        locationQuery: String,
        auth: WeatherAuthConfig,
        knownLocation: KnownWeatherLocation? = null,
    ): WeatherSnapshot

    suspend fun fetchHistorical(
        locationQuery: String,
        auth: WeatherAuthConfig,
        date: LocalDate,
        knownLocation: KnownWeatherLocation? = null,
    ): WeatherSnapshot
}

@Singleton
class QWeatherClient @Inject constructor(
    private val api: QWeatherApi,
    private val jwtProvider: QWeatherJwtProvider,
) : WeatherDataSource {

    override suspend fun fetch(
        locationQuery: String,
        auth: WeatherAuthConfig,
        knownLocation: KnownWeatherLocation?,
    ): WeatherSnapshot {
        requireAuth(auth)
        val base = apiBase(auth)
        val authorization = authorization(auth)
        val city = knownLocation?.asCity() ?: lookupCity(base, authorization, locationQuery)

        val now = runCatching {
            api.now(url(base, "/v7/weather/now", "location" to city.id), authorization)
        }.getOrElse { throw it.toWeatherException() }
        if (now.code != "200") throw codeException(now.code)
        val nowDto = now.now ?: throw QWeatherException(WeatherFailure.UNKNOWN, "实况为空")

        val daily = runCatching {
            api.daily(url(base, "/v7/weather/3d", "location" to city.id), authorization)
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
            date = LocalDate.now().toString(),
            locationId = city.id,
        )
    }

    suspend fun fetch(locationQuery: String, auth: WeatherAuthConfig): WeatherSnapshot =
        fetch(locationQuery, auth, null)

    /**
     * 最近 10 天历史（不含今天）。QWeather 只保留 10 天，超出范围直接返回
     * `CITY_NOT_FOUND` 之外的业务错误由 Repository 降级处理。
     */
    suspend fun fetchHistorical(
        locationQuery: String,
        auth: WeatherAuthConfig,
        date: LocalDate,
    ): WeatherSnapshot = fetchHistorical(locationQuery, auth, date, null)

    override suspend fun fetchHistorical(
        locationQuery: String,
        auth: WeatherAuthConfig,
        date: LocalDate,
        knownLocation: KnownWeatherLocation?,
    ): WeatherSnapshot {
        requireAuth(auth)
        val base = apiBase(auth)
        val authorization = authorization(auth)
        val city = knownLocation?.asCity() ?: lookupCity(base, authorization, locationQuery)
        val dateParam = date.format(DateTimeFormatter.BASIC_ISO_DATE)
        val response = runCatching {
            api.historical(
                url(base, "/v7/historical/weather", "location" to city.id, "date" to dateParam),
                authorization,
            )
        }.getOrElse { throw it.toWeatherException() }
        if (response.code != "200") throw codeException(response.code)

        val daily = response.weatherDaily
            ?: throw QWeatherException(WeatherFailure.UNKNOWN, "历史天气为空")
        val tempMin = daily.tempMin?.toIntOrNull()
        val tempMax = daily.tempMax?.toIntOrNull()
        if (tempMin == null && tempMax == null) {
            throw QWeatherException(WeatherFailure.UNKNOWN, "历史天气温度缺失")
        }
        val hourly = response.weatherHourly
        val temp = hourly.mapNotNull { it.temp?.toIntOrNull() }
            .takeIf { it.isNotEmpty() }
            ?.average()
            ?.toInt()
            ?: (((tempMin ?: 0) + (tempMax ?: 0)) / 2)
        val text = hourly.mapNotNull { it.text?.takeIf(String::isNotBlank) }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            ?: "历史天气"
        val windScale = hourly.mapNotNull { it.windScale?.takeIf(String::isNotBlank) }
            .maxByOrNull { it.toIntOrNull() ?: 0 }
        return WeatherSnapshot(
            city = city.name,
            temp = temp,
            feelsLike = temp,
            minTemp = minOf(tempMin ?: temp, tempMax ?: temp),
            maxTemp = maxOf(tempMin ?: temp, tempMax ?: temp),
            text = text,
            windScale = windScale,
            fetchedAt = System.currentTimeMillis(),
            date = date.toString(),
            locationId = city.id,
        )
    }

    private fun KnownWeatherLocation.asCity(): CityLocation =
        CityLocation(name = name, id = locationId)

    private suspend fun lookupCity(
        base: String,
        authorization: String,
        query: String,
    ): CityLocation {
        val response = runCatching {
            api.cityLookup(url(base, "/geo/v2/city/lookup", "location" to query), authorization)
        }.getOrElse { throw it.toWeatherException() }

        if (response.code == "404") {
            throw QWeatherException(WeatherFailure.CITY_NOT_FOUND, "未找到城市")
        }
        if (response.code != "200") throw codeException(response.code)
        return response.location.firstOrNull()
            ?: throw QWeatherException(WeatherFailure.CITY_NOT_FOUND, "未找到城市")
    }

    private fun apiBase(auth: WeatherAuthConfig): String {
        val host = normalizeWeatherApiHost(auth.apiHost)
            ?: throw QWeatherException(WeatherFailure.NO_KEY, "API Host 未配置")
        return "https://$host"
    }

    private fun authorization(auth: WeatherAuthConfig): String = try {
        "Bearer ${jwtProvider.token(auth)}"
    } catch (error: QWeatherJwtException) {
        throw QWeatherException(WeatherFailure.AUTH, "和风天气认证失败", error)
    }

    private fun requireAuth(auth: WeatherAuthConfig) {
        if (!auth.isConfigured) throw QWeatherException(WeatherFailure.NO_KEY, "和风天气认证未配置")
    }

    private fun url(base: String, path: String, vararg params: Pair<String, String>): String {
        val root = (base + path).toHttpUrlOrNull()
            ?: throw QWeatherException(WeatherFailure.UNKNOWN, "天气地址格式错误")
        return root.newBuilder()
            .apply { params.forEach { (name, value) -> addQueryParameter(name, value) } }
            .addQueryParameter("lang", "zh")
            .build()
            .toString()
    }

    private fun codeException(code: String): QWeatherException = when (code) {
        "401", "403" -> QWeatherException(WeatherFailure.AUTH, "和风天气认证无效")
        "404" -> QWeatherException(WeatherFailure.CITY_NOT_FOUND, "城市无数据")
        "429" -> QWeatherException(WeatherFailure.UNKNOWN, "请求过于频繁")
        else -> QWeatherException(WeatherFailure.UNKNOWN, "QWeather 返回 $code")
    }

    private fun Throwable.toWeatherException(): QWeatherException {
        if (this is kotlinx.coroutines.CancellationException) throw this
        if (this is QWeatherException) return this
        return when (this) {
            is HttpException -> when (code()) {
                401, 403 -> QWeatherException(WeatherFailure.AUTH, "和风天气认证无效", this)
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
