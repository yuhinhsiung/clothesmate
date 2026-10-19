package com.yuhinhsiung.clothesmate.core.weather

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 天气快照 —— 开发文档 §2.4 T-01、§5 推荐输入、M3-01。
 *
 * 这是**展示与写入穿着记录共用的唯一结构**：缓存、今日卡片、推荐理由、`wear_record.weatherJson`
 * 都从这里序列化，避免“卡片显示 18°C、记录里却是 20°C”的口径分裂。
 */
@Serializable
data class WeatherSnapshot(
    val city: String,
    /** 当前温度，摄氏度 */
    val temp: Int,
    val feelsLike: Int,
    val minTemp: Int,
    val maxTemp: Int,
    /** 现象文本，如「多云」「小雨」 */
    val text: String,
    val windScale: String? = null,
    /** 拉取成功的时间戳，用于缓存新鲜度判断 */
    val fetchedAt: Long,
    /** 历史天气对应的日期；今天/实时天气为 null */
    val date: String? = null,
    /** 和风解析后的 LocationID；用于跳过重复的城市检索，降低计费请求数 */
    val locationId: String? = null,
) {
    val averageTemp: Double
        get() = (minTemp + maxTemp) / 2.0

    /** 雨雪天气：推荐引擎的硬排除条件之一（§5.2） */
    val isPrecipitation: Boolean
        get() = text.contains("雨") || text.contains("雪") || text.contains("雷")

    /** 天气卡图标选择用的粗分类，不参与业务判断 */
    val condition: WeatherCondition
        get() = when {
            text.contains("雪") || text.contains("冰") -> WeatherCondition.SNOW
            text.contains("雨") || text.contains("雷") -> WeatherCondition.RAIN
            text.contains("晴") -> WeatherCondition.SUNNY
            else -> WeatherCondition.CLOUDY
        }

    /** 缓存是否仍在 TTL 内；FetchedAt 用系统时间，跨进程重启同样适用。 */
    fun isFresh(now: Long = System.currentTimeMillis(), ttlMillis: Long = 30 * 60 * 1000L): Boolean =
        fetchedAt > 0 && now - fetchedAt in 0 until ttlMillis

    /**
     * 快照是否属于 [date]：历史快照看 `date`，实时快照看抓取时的本地日期。
     * 跨午夜后不能把昨天的天气写进今天的穿着记录（review R04）。
     */
    fun isForDate(date: LocalDate): Boolean {
        val historical = this.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (historical != null) return historical == date
        val fetchedDate = Instant.ofEpochMilli(fetchedAt).atZone(ZoneId.systemDefault()).toLocalDate()
        return fetchedDate == date
    }

    /**
     * 缓存是否属于用户当前查询：优先用 LocationID 缓存里的原始 query 对齐，
     * 避免城市名「北京」与查询「beijing」被误判为不同城市（review R07）。
     */
    fun matchesQuery(query: String, cachedLocation: CachedWeatherLocation?): Boolean {
        val target = query.trim()
        if (target.isEmpty()) return false
        if (cachedLocation?.query == target) return cachedLocation.name == city
        return city == target || city.contains(target) || target.contains(city)
    }
}

enum class WeatherCondition { SUNNY, CLOUDY, RAIN, SNOW }

/** 天气请求失败分型；文案在 UI 层映射，领域层不持有 Android 资源。 */
enum class WeatherFailure {
    /** 没有配置和风天气 JWT 认证（API Host/凭据/私钥） */
    NO_KEY,

    /** 定位权限未授予 / 无可用定位 */
    NO_LOCATION,

    /** 请求超时 */
    TIMEOUT,

    /** Key 无效或额度耗尽（401/403） */
    AUTH,

    /** 城市无法识别 / 城市无数据（404） */
    CITY_NOT_FOUND,

    /** 网络不可达、DNS、5xx */
    NETWORK,

    /** 其他服务端错误 / 响应结构异常 */
    UNKNOWN,
}

class WeatherException(
    val failure: WeatherFailure,
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause)

/** 定位结果（经纬度，WGS84）。 */
data class WeatherCoordinates(
    val latitude: Double,
    val longitude: Double,
) {
    /** QWeather location 参数：经度在前、纬度在后，最多 2 位小数。 */
    fun toQueryValue(): String = "%.2f,%.2f".format(java.util.Locale.US, longitude, latitude)
}

/**
 * 相对湿度/风力等未建模字段不进入快照——推荐与 UI 都不使用，避免 `weatherJson`
 * 把供应商原始响应整包写进历史记录。
 */
