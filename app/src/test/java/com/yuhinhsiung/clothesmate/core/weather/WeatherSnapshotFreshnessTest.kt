package com.yuhinhsiung.clothesmate.core.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class WeatherSnapshotFreshnessTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `cache is fresh within thirty minutes`() {
        assertTrue(snapshot(now - 29 * 60_000).isFresh(now))
    }

    @Test
    fun `cache is stale after thirty minutes`() {
        assertFalse(snapshot(now - 31 * 60_000).isFresh(now))
    }

    @Test
    fun `future timestamp or zero is never considered fresh`() {
        assertFalse(snapshot(now + 60_000).isFresh(now))
        assertFalse(snapshot(0).isFresh(now))
    }


    @Test
    fun `snapshot date check rejects yesterday cache`() {
        val today = LocalDate.of(2026, 10, 19)
        val todayMillis = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val todaySnapshot = snapshot(todayMillis)
        assertTrue(todaySnapshot.isForDate(today))
        assertFalse(todaySnapshot.isForDate(today.plusDays(1)))

        val historical = snapshot(todayMillis).copy(date = "2026-10-17")
        assertTrue(historical.isForDate(LocalDate.of(2026, 10, 17)))
        assertFalse(historical.isForDate(today))
    }

    @Test
    fun `city matching accepts cached query mapping and display name`() {
        val cached = CachedWeatherLocation(
            query = "beijing",
            locationId = "101010100",
            name = "北京",
        )
        val snapshot = snapshot(now).copy(city = "北京")
        assertTrue(snapshot.matchesQuery("beijing", cached))
        assertTrue(snapshot.matchesQuery("北京", null))
        assertFalse(snapshot.matchesQuery("上海", cached))
        assertFalse(snapshot.matchesQuery("上海", null))
    }

    private fun snapshot(fetchedAt: Long) = WeatherSnapshot(
        city = "杭州",
        temp = 20,
        feelsLike = 20,
        minTemp = 16,
        maxTemp = 24,
        text = "多云",
        fetchedAt = fetchedAt,
    )
}
