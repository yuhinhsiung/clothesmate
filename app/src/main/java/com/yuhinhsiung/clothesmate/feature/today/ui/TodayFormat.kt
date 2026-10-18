package com.yuhinhsiung.clothesmate.feature.today.ui

import com.yuhinhsiung.clothesmate.R
import com.yuhinhsiung.clothesmate.core.model.WearFailure
import com.yuhinhsiung.clothesmate.core.model.WearRecord
import com.yuhinhsiung.clothesmate.core.weather.WeatherCondition
import com.yuhinhsiung.clothesmate.core.weather.WeatherFailure
import com.yuhinhsiung.clothesmate.core.weather.WeatherSnapshot
import com.yuhinhsiung.clothesmate.ui.icon.AppIcons
import kotlinx.serialization.json.Json

internal fun WeatherCondition.icon() = when (this) {
    WeatherCondition.SUNNY -> AppIcons.WeatherSunny
    WeatherCondition.CLOUDY -> AppIcons.WeatherCloudy
    WeatherCondition.RAIN -> AppIcons.WeatherRainy
    WeatherCondition.SNOW -> AppIcons.WeatherSnowy
}

internal fun WeatherFailure.messageRes(): Int = when (this) {
    WeatherFailure.NO_KEY -> R.string.weather_no_key_title
    WeatherFailure.NO_LOCATION -> R.string.weather_need_location_desc
    WeatherFailure.TIMEOUT -> R.string.weather_timeout
    WeatherFailure.AUTH -> R.string.weather_auth
    WeatherFailure.CITY_NOT_FOUND -> R.string.weather_city_not_found
    WeatherFailure.NETWORK -> R.string.weather_network
    WeatherFailure.UNKNOWN -> R.string.weather_service
}

internal fun WearFailure?.messageRes(): Int = when (this) {
    WearFailure.OUTFIT_NOT_FOUND -> R.string.completion_outfit_deleted
    WearFailure.EMPTY_OUTFIT -> R.string.completion_wear_error
    WearFailure.ITEM_UNAVAILABLE -> R.string.completion_wear_error
    WearFailure.ITEM_STORED -> R.string.completion_wear_error
    WearFailure.INVALID_DATE -> R.string.completion_wear_error
    null -> R.string.completion_wear_error
}

internal fun WearRecord.metaLine(): String {
    val schedule = scheduleTags.joinToString(" · ")
    val weather = weatherJson?.let { raw ->
        runCatching { Json.decodeFromString<WeatherSnapshot>(raw) }.getOrNull()
    }?.let { "${it.temp}° ${it.text}" }
    return listOfNotNull(schedule.ifBlank { null }, weather).joinToString(" · ")
}
