package com.yuhinhsiung.clothesmate.core.update

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpdateCheckStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var store: UpdateCheckStore

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        store = UpdateCheckStore(
            PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(folder.root, "update.preferences_pb") }),
        )
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun firstCheckRunsAndSameVersionIsThrottledFor24Hours() = runTest {
        val now = 1_000_000_000L
        assertTrue(store.shouldCheckSilently(now, "1.0.0"))
        store.recordAttempt(now, "1.0.0", successful = true)
        assertFalse(store.shouldCheckSilently(now + 60 * 60 * 1000, "1.0.0"))
        assertTrue(store.shouldCheckSilently(now + 24 * 60 * 60 * 1000, "1.0.0"))
    }

    @Test
    fun versionChangeBypassesCacheAndFailureStillThrottles() = runTest {
        val now = 1_000_000_000L
        store.recordAttempt(now, "1.0.0", successful = false)
        assertTrue(store.shouldCheckSilently(now + 1000, "1.0.1"))
        assertFalse(store.shouldCheckSilently(now + 1000, "1.0.0"))
    }
}
