package com.yuhinhsiung.clothesmate.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.yuhinhsiung.clothesmate.core.ai.QuickCaptureAiSuggestion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuickCaptureAiSuggestionStoreTest {

    private class FakePreferencesDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: StateFlow<Preferences> = state
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            val next = transform(state.value)
            state.value = next
            return next
        }
    }

    @Test
    fun suggestionIsReadByAnotherStoreInstanceThroughDataStore() = runTest {
        val dataStore = FakePreferencesDataStore()
        val suggestion = QuickCaptureAiSuggestion(
            categoryKey = "shirt",
            styleTags = listOf("简约"),
            seasonTags = listOf("spring"),
            createdAt = 7L,
        )
        QuickCaptureAiSuggestionStore(dataStore).save(1L, suggestion)

        // 新的 Store 实例没有进程内缓存可依赖，只能从 DataStore 读；
        // 这正是“退出连拍后重新打开待完善衣物”的真实读取路径。
        assertEquals(suggestion, QuickCaptureAiSuggestionStore(dataStore).get(1L))
    }

    @Test
    fun pendingIdsSurviveNewStoreInstanceAndClearOneByOne() = runTest {
        val dataStore = FakePreferencesDataStore()
        val first = QuickCaptureAiSuggestionStore(dataStore)
        first.markPending(11L)
        first.markPending(12L)
        first.clearPending(11L)

        assertEquals(setOf(12L), QuickCaptureAiSuggestionStore(dataStore).pendingIds())
    }

    @Test
    fun clearingAllPendingReturnsEmptySet() = runTest {
        val dataStore = FakePreferencesDataStore()
        val store = QuickCaptureAiSuggestionStore(dataStore)
        store.markPending(21L)
        store.clearPending(21L)

        assertEquals(emptySet<Long>(), store.pendingIds())
    }

    @Test
    fun removalIsVisibleToAnotherStoreInstance() = runTest {
        val dataStore = FakePreferencesDataStore()
        val first = QuickCaptureAiSuggestionStore(dataStore)
        first.save(2L, QuickCaptureAiSuggestion("shirt", emptyList(), emptyList(), 8L))
        first.remove(2L)

        assertNull(QuickCaptureAiSuggestionStore(dataStore).get(2L))
    }
}
