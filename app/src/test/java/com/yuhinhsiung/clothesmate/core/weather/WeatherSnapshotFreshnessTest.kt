package com.yuhinhsiung.clothesmate.core.weather

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
