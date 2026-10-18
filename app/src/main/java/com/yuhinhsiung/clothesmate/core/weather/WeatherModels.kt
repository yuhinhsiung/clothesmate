package com.yuhinhsiung.clothesmate.core.weather

import kotlinx.serialization.Serializable

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
}

enum class WeatherCondition { SUNNY, CLOUDY, RAIN, SNOW }

/** 天气请求失败分型；文案在 UI 层映射，领域层不持有 Android 资源。 */
enum class WeatherFailure {
    /** 没有配置 API Key */
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
