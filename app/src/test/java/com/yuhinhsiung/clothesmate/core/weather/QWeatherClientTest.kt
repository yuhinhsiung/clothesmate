package com.yuhinhsiung.clothesmate.core.weather

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QWeatherClientTest {

    @Test
    fun `looks up city then fetches now and daily with key and location`() = runTest {
        val api = FakeQWeatherApi()
        val client = QWeatherClient(api)

        val snapshot = client.fetch(
            locationQuery = "杭州",
            apiKey = "secret-key",
            baseUrl = "https://weather.example/v7/",
            geoBaseUrl = "https://geo.example/v2",
        )

        assertEquals("杭州", snapshot.city)
        assertEquals(27, snapshot.temp)
        assertEquals(29, snapshot.feelsLike)
        assertEquals(21, snapshot.minTemp)
        assertEquals(31, snapshot.maxTemp)
        assertEquals("多云", snapshot.text)
        assertEquals("3-4", snapshot.windScale)

        val lookup = api.lookupUrls.single()
        assertTrue(lookup.startsWith("https://geo.example/v2/city/lookup?"))
        assertTrue(lookup.contains("location=%E6%9D%AD%E5%B7%9E"))
        assertTrue(lookup.contains("key=secret-key"))

        val now = api.nowUrls.single()
        assertTrue(now.startsWith("https://weather.example/v7/weather/now?"))
        assertTrue(now.contains("location=101210101"))
        val daily = api.dailyUrls.single()
        assertTrue(daily.startsWith("https://weather.example/v7/weather/3d?"))
    }

    @Test
    fun `default base uses devapi and geoapi`() = runTest {
        val api = FakeQWeatherApi()
        QWeatherClient(api).fetch("杭州", "k")
        assertTrue(api.lookupUrls.single().startsWith("https://geoapi.qweather.com/v2/city/lookup?"))
        assertTrue(api.nowUrls.single().startsWith("https://devapi.qweather.com/v7/weather/now?"))
    }

    @Test
    fun `city not found maps to city not found`() = runTest {
        val api = FakeQWeatherApi().apply { lookupResponse = CityLookupResponse("404") }
        val error = runCatching { QWeatherClient(api).fetch("不存在", "k") }.exceptionOrNull()
        assertTrue(error is QWeatherException)
        assertEquals(WeatherFailure.CITY_NOT_FOUND, (error as QWeatherException).failure)
    }

    @Test
    fun `auth code maps to auth`() = runTest {
        val api = FakeQWeatherApi().apply { lookupResponse = CityLookupResponse("401") }
        val error = runCatching { QWeatherClient(api).fetch("杭州", "bad") }.exceptionOrNull()
        assertEquals(WeatherFailure.AUTH, (error as QWeatherException).failure)
    }

    @Test
    fun `service code maps to unknown and keeps code message`() = runTest {
        val api = FakeQWeatherApi().apply { nowResponse = NowResponse("500") }
        val error = runCatching { QWeatherClient(api).fetch("杭州", "k") }.exceptionOrNull()
        assertEquals(WeatherFailure.UNKNOWN, (error as QWeatherException).failure)
        assertTrue(error.message.orEmpty().contains("500"))
    }

    @Test
    fun `missing temperature maps to unknown`() = runTest {
        val api = FakeQWeatherApi().apply { nowResponse = NowResponse("200", NowDto(temp = null)) }
        val error = runCatching { QWeatherClient(api).fetch("杭州", "k") }.exceptionOrNull()
        assertEquals(WeatherFailure.UNKNOWN, (error as QWeatherException).failure)
    }

    @Test
    fun `empty daily maps to unknown`() = runTest {
        val api = FakeQWeatherApi().apply { dailyResponse = DailyResponse("200", emptyList()) }
        val error = runCatching { QWeatherClient(api).fetch("杭州", "k") }.exceptionOrNull()
        assertEquals(WeatherFailure.UNKNOWN, (error as QWeatherException).failure)
    }

    @Test
    fun `normalize base rejects ftp and defaults empty`() {
        assertEquals(WeatherEndpoints.DEFAULT_BASE, WeatherEndpoints.normalizeBase(""))
        assertEquals(WeatherEndpoints.DEFAULT_BASE, WeatherEndpoints.normalizeBase(null))
        assertEquals(
            "https://example.com/v7",
            WeatherEndpoints.normalizeBase("https://example.com/v7/"),
        )
        assertTrue(
            runCatching { WeatherEndpoints.normalizeBase("ftp://example.com") }.isFailure,
        )
    }

    @Test
    fun `coordinates use longitude latitude with two decimals`() {
        assertEquals(
            "120.16,30.29",
            WeatherCoordinates(latitude = 30.2874, longitude = 120.1551).toQueryValue(),
        )
    }

    private class FakeQWeatherApi : QWeatherApi {
        val lookupUrls = mutableListOf<String>()
        val nowUrls = mutableListOf<String>()
        val dailyUrls = mutableListOf<String>()

        var lookupResponse = CityLookupResponse(
            code = "200",
            location = listOf(
                CityLocation(
                    name = "杭州",
                    id = "101210101",
                    lat = "30.29",
                    lon = "120.16",
                    adm2 = "杭州",
                    adm1 = "浙江省",
                ),
            ),
        )
        var nowResponse = NowResponse(
            code = "200",
            now = NowDto(
                temp = "27",
                feelsLike = "29",
                text = "晴",
                windScale = "2",
            ),
        )
        var dailyResponse = DailyResponse(
            code = "200",
            daily = listOf(
                DailyDto(
                    tempMin = "21",
                    tempMax = "31",
                    textDay = "多云",
                    windScaleDay = "3-4",
                ),
            ),
        )

        override suspend fun cityLookup(url: String): CityLookupResponse {
            lookupUrls += url
            return lookupResponse
        }

        override suspend fun now(url: String): NowResponse {
            nowUrls += url
            return nowResponse
        }

        override suspend fun daily(url: String): DailyResponse {
            dailyUrls += url
            return dailyResponse
        }
    }
}
