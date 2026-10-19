package com.yuhinhsiung.clothesmate.core.weather

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * 天气计费请求的回归测试：缓存 LocationID、历史天气缓存、失效 ID 回退。
 *
 * 这些测试用 [WeatherDataSource] 假实现，不访问网络，也不消耗真实和风额度。
 */
class WeatherRepositoryCacheTest {

    private val auth = WeatherAuthConfig(
        apiHost = "abc1234xyz.re.qweatherapi.com",
        developerId = "Q1234567890",
        projectId = "project",
        keyId = "kid",
        privateKeyPem = "-----BEGIN PRIVATE KEY-----\nnot-a-real-key\n-----END PRIVATE KEY-----",
    )

    @Test
    fun `second fetch reuses cached location id and skips geo lookup`() = runTest {
        val source = FakeWeatherSource()
        val cache = FakeWeatherCache()
        val repository = DefaultWeatherRepository(source, cache)

        repository.fetch("杭州", auth)
        assertNull(source.lastKnownLocation)
        assertEquals("101210101", cache.location.value?.locationId)

        repository.fetch("杭州", auth)
        assertEquals("101210101", source.lastKnownLocation?.locationId)
        assertEquals(2, source.fetchCalls)
    }

    @Test
    fun `historical weather for same city and date is served from cache`() = runTest {
        val source = FakeWeatherSource()
        val cache = FakeWeatherCache()
        val repository = DefaultWeatherRepository(source, cache)
        val date = LocalDate.of(2026, 10, 17)

        val first = repository.fetchHistorical("杭州", auth, date)
        val second = repository.fetchHistorical("杭州", auth, date)

        assertEquals(first, second)
        assertEquals(1, source.historicalCalls)
        assertEquals("101210101", cache.location.value?.locationId)
    }

    @Test
    fun `stale location id falls back to one new lookup and refreshes cache`() = runTest {
        val source = FakeWeatherSource(failKnownLocationOnce = true)
        val cache = FakeWeatherCache().apply {
            location.value = CachedWeatherLocation("杭州", "old-id", "旧杭州")
        }
        val repository = DefaultWeatherRepository(source, cache)

        val snapshot = repository.fetch("杭州", auth)

        assertEquals(2, source.fetchCalls)
        assertNull(source.lastKnownLocation)
        assertEquals("101210101", snapshot.locationId)
        assertEquals("101210101", cache.location.value?.locationId)
    }

    @Test
    fun `different city does not reuse another city's location id`() = runTest {
        val source = FakeWeatherSource()
        val cache = FakeWeatherCache().apply {
            location.value = CachedWeatherLocation("杭州", "101210101", "杭州")
        }
        val repository = DefaultWeatherRepository(source, cache)

        repository.fetch("上海", auth)

        assertNull(source.lastKnownLocation)
    }

    private class FakeWeatherSource(
        private var failKnownLocationOnce: Boolean = false,
    ) : WeatherDataSource {
        var fetchCalls = 0
        var historicalCalls = 0
        var lastKnownLocation: KnownWeatherLocation? = null

        override suspend fun fetch(
            locationQuery: String,
            auth: WeatherAuthConfig,
            knownLocation: KnownWeatherLocation?,
        ): WeatherSnapshot {
            fetchCalls++
            lastKnownLocation = knownLocation
            if (failKnownLocationOnce && knownLocation != null) {
                failKnownLocationOnce = false
                throw WeatherException(WeatherFailure.CITY_NOT_FOUND)
            }
            return snapshot(locationQuery, locationId = "101210101")
        }

        override suspend fun fetchHistorical(
            locationQuery: String,
            auth: WeatherAuthConfig,
            date: LocalDate,
            knownLocation: KnownWeatherLocation?,
        ): WeatherSnapshot {
            historicalCalls++
            lastKnownLocation = knownLocation
            return snapshot(locationQuery, locationId = "101210101", date = date.toString())
        }

        private fun snapshot(
            city: String,
            locationId: String,
            date: String? = null,
        ): WeatherSnapshot = WeatherSnapshot(
            city = city,
            temp = 20,
            feelsLike = 20,
            minTemp = 16,
            maxTemp = 24,
            text = "多云",
            fetchedAt = 1_700_000_000_000,
            date = date,
            locationId = locationId,
        )
    }

    private class FakeWeatherCache : WeatherCacheStore {
        override val snapshot = MutableStateFlow<WeatherSnapshot?>(null)
        override val location = MutableStateFlow<CachedWeatherLocation?>(null)
        private val history = mutableMapOf<String, WeatherSnapshot>()

        override suspend fun save(snapshot: WeatherSnapshot) {
            this.snapshot.value = snapshot
        }

        override suspend fun saveLocation(location: CachedWeatherLocation) {
            this.location.value = location
        }

        override suspend fun historical(query: String, date: LocalDate): WeatherSnapshot? =
            history["$query|$date"]

        override suspend fun saveHistorical(
            query: String,
            date: LocalDate,
            snapshot: WeatherSnapshot,
        ) {
            history["$query|$date"] = snapshot
        }

        override suspend fun clear() {
            snapshot.value = null
            location.value = null
            history.clear()
        }
    }
}
