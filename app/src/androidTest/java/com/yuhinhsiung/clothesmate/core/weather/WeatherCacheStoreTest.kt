package com.yuhinhsiung.clothesmate.core.weather

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class WeatherCacheStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun locationAndHistoricalWeatherRoundTrip() = runBlocking {
        val store = newStore()
        val location = CachedWeatherLocation("杭州", "101210101", "杭州")
        val snapshot = snapshot("2026-10-17")

        store.saveLocation(location)
        store.saveHistorical("杭州", LocalDate.of(2026, 10, 17), snapshot)

        assertEquals(location, store.location.first())
        assertEquals(snapshot, store.historical("杭州", LocalDate.of(2026, 10, 17)))
        assertNull(store.historical("杭州", LocalDate.of(2026, 10, 16)))
    }

    @Test
    fun historicalCacheKeepsOnlyThirtyMostRecentEntries() = runBlocking {
        val store = newStore()
        val start = LocalDate.of(2026, 9, 1)
        repeat(32) { offset ->
            val date = start.plusDays(offset.toLong())
            store.saveHistorical("杭州", date, snapshot(date.toString()))
        }

        assertNull(store.historical("杭州", start))
        assertNull(store.historical("杭州", start.plusDays(1)))
        assertEquals(
            snapshot(start.plusDays(2).toString()),
            store.historical("杭州", start.plusDays(2)),
        )
        assertEquals(
            snapshot(start.plusDays(31).toString()),
            store.historical("杭州", start.plusDays(31)),
        )
    }

    private fun newStore(): DataStoreWeatherCacheStore {
        val file = File(tempFolder.root, "weather-${UUID.randomUUID()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        )
        return DataStoreWeatherCacheStore(dataStore, json)
    }

    private fun snapshot(date: String) = WeatherSnapshot(
        city = "杭州",
        temp = 20,
        feelsLike = 20,
        minTemp = 16,
        maxTemp = 24,
        text = "多云",
        fetchedAt = 1_700_000_000_000,
        date = date,
        locationId = "101210101",
    )
}
