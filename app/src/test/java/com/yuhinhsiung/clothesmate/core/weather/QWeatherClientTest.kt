package com.yuhinhsiung.clothesmate.core.weather

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.util.Base64

class QWeatherClientTest {

    private val auth = testAuth("weather.example")

    @Test
    fun `looks up city then fetches now and daily with jwt`() = runTest {
        val api = FakeQWeatherApi()
        val client = QWeatherClient(api, QWeatherJwtProvider())

        val snapshot = client.fetch(locationQuery = "杭州", auth = auth)

        assertEquals("杭州", snapshot.city)
        assertEquals(27, snapshot.temp)
        assertEquals(29, snapshot.feelsLike)
        assertEquals(21, snapshot.minTemp)
        assertEquals(31, snapshot.maxTemp)
        assertEquals("多云", snapshot.text)
        assertEquals("3-4", snapshot.windScale)

        val lookup = api.lookupUrls.single()
        assertTrue(lookup.startsWith("https://weather.example/geo/v2/city/lookup?"))
        assertTrue(lookup.contains("location=%E6%9D%AD%E5%B7%9E"))
        assertFalse(lookup.contains("key="))
        assertTrue(api.authorizations.all { it.startsWith("Bearer ") })

        val now = api.nowUrls.single()
        assertTrue(now.startsWith("https://weather.example/v7/weather/now?"))
        assertTrue(now.contains("location=101210101"))
        val daily = api.dailyUrls.single()
        assertTrue(daily.startsWith("https://weather.example/v7/weather/3d?"))
    }

    @Test
    fun `city not found maps to city not found`() = runTest {
        val api = FakeQWeatherApi().apply { lookupResponse = CityLookupResponse("404") }
        val error = runCatching {
            QWeatherClient(api, QWeatherJwtProvider()).fetch("不存在", auth)
        }.exceptionOrNull()
        assertTrue(error is QWeatherException)
        assertEquals(WeatherFailure.CITY_NOT_FOUND, (error as QWeatherException).failure)
    }

    @Test
    fun `auth code maps to auth`() = runTest {
        val api = FakeQWeatherApi().apply { lookupResponse = CityLookupResponse("401") }
        val error = runCatching {
            QWeatherClient(api, QWeatherJwtProvider()).fetch("杭州", auth)
        }.exceptionOrNull()
        assertEquals(WeatherFailure.AUTH, (error as QWeatherException).failure)
    }

    @Test
    fun `service code maps to unknown and keeps code message`() = runTest {
        val api = FakeQWeatherApi().apply { nowResponse = NowResponse("500") }
        val error = runCatching {
            QWeatherClient(api, QWeatherJwtProvider()).fetch("杭州", auth)
        }.exceptionOrNull()
        assertEquals(WeatherFailure.UNKNOWN, (error as QWeatherException).failure)
        assertTrue(error.message.orEmpty().contains("500"))
    }

    @Test
    fun `missing temperature maps to unknown`() = runTest {
        val api = FakeQWeatherApi().apply { nowResponse = NowResponse("200", NowDto(temp = null)) }
        val error = runCatching {
            QWeatherClient(api, QWeatherJwtProvider()).fetch("杭州", auth)
        }.exceptionOrNull()
        assertEquals(WeatherFailure.UNKNOWN, (error as QWeatherException).failure)
    }

    @Test
    fun `empty daily maps to unknown`() = runTest {
        val api = FakeQWeatherApi().apply { dailyResponse = DailyResponse("200", emptyList()) }
        val error = runCatching {
            QWeatherClient(api, QWeatherJwtProvider()).fetch("杭州", auth)
        }.exceptionOrNull()
        assertEquals(WeatherFailure.UNKNOWN, (error as QWeatherException).failure)
    }

    @Test
    fun `historical weather uses date and hourly average`() = runTest {
        val api = FakeQWeatherApi()
        val snapshot = QWeatherClient(api, QWeatherJwtProvider()).fetchHistorical(
            locationQuery = "杭州",
            auth = auth,
            date = java.time.LocalDate.of(2026, 10, 17),
        )
        assertTrue(api.historicalUrls.single().contains("date=20261017"))
        assertEquals(18, snapshot.temp)
        assertEquals(17, snapshot.minTemp)
        assertEquals(23, snapshot.maxTemp)
        assertEquals("小雨", snapshot.text)
    }

    @Test
    fun `normalize api host strips scheme and validates`() {
        assertEquals("abc.qweatherapi.com", normalizeWeatherApiHost("https://abc.qweatherapi.com/"))
        assertEquals(null, normalizeWeatherApiHost(""))
        assertEquals(null, normalizeWeatherApiHost("abc/def"))
        assertEquals(null, normalizeWeatherApiHost("localhost"))
    }

    @Test
    fun `coordinates use longitude latitude with two decimals`() {
        assertEquals(
            "120.16,30.29",
            WeatherCoordinates(latitude = 30.2874, longitude = 120.1551).toQueryValue(),
        )
    }

    private fun testAuth(host: String): WeatherAuthConfig {
        val generator = KeyPairGenerator.getInstance("Ed25519")
        val pair = generator.generateKeyPair()
        val pem = Base64.getMimeEncoder(64, "\n".toByteArray())
            .encodeToString(pair.private.encoded)
        return WeatherAuthConfig(
            apiHost = host,
            developerId = "Q1234567890",
            projectId = "ABCDEF123456",
            keyId = "KEY123",
            privateKeyPem = "-----BEGIN PRIVATE KEY-----\n$pem\n-----END PRIVATE KEY-----",
        )
    }

    private class FakeQWeatherApi : QWeatherApi {
        val lookupUrls = mutableListOf<String>()
        val nowUrls = mutableListOf<String>()
        val dailyUrls = mutableListOf<String>()
        val historicalUrls = mutableListOf<String>()
        val authorizations = mutableListOf<String>()

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
        var historicalResponse = HistoricalResponse(
            code = "200",
            weatherDaily = HistoricalDailyDto(date = "2026-10-17", tempMax = "23", tempMin = "17"),
            weatherHourly = listOf(
                HistoricalHourlyDto(time = "2026-10-17T08:00", temp = "18", text = "小雨", windScale = "2"),
                HistoricalHourlyDto(time = "2026-10-17T14:00", temp = "18", text = "小雨", windScale = "3"),
            ),
        )

        override suspend fun cityLookup(url: String, authorization: String): CityLookupResponse {
            lookupUrls += url
            authorizations += authorization
            return lookupResponse
        }

        override suspend fun now(url: String, authorization: String): NowResponse {
            nowUrls += url
            authorizations += authorization
            return nowResponse
        }

        override suspend fun daily(url: String, authorization: String): DailyResponse {
            dailyUrls += url
            authorizations += authorization
            return dailyResponse
        }

        override suspend fun historical(url: String, authorization: String): HistoricalResponse {
            historicalUrls += url
            authorizations += authorization
            return historicalResponse
        }
    }
}
