package com.yuhinhsiung.clothesmate.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.yuhinhsiung.clothesmate.core.ai.QuickCaptureAiSuggestion
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val Context.quickCaptureAiDataStore by preferencesDataStore("quick_capture_ai")

/** 连拍 AI 建议的本地暂存：最多保留 100 条，不参与衣物字段写入。 */
@Singleton
class QuickCaptureAiSuggestionStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), QuickCaptureAiSuggestion.serializer())

    suspend fun get(itemId: Long): QuickCaptureAiSuggestion? = mutex.withLock {
        read()[itemId.toString()]
    }

    suspend fun save(itemId: Long, suggestion: QuickCaptureAiSuggestion) = mutex.withLock {
        val current = read().toMutableMap()
        current[itemId.toString()] = suggestion
        val trimmed = if (current.size <= MAX_ENTRIES) {
            current
        } else {
            current.entries.sortedByDescending { it.value.createdAt }
                .take(MAX_ENTRIES)
                .associate { it.key to it.value }
        }
        write(trimmed)
    }

    suspend fun remove(itemId: Long) = mutex.withLock {
        val current = read().toMutableMap()
        if (current.remove(itemId.toString()) != null) write(current)
    }

    private suspend fun read(): Map<String, QuickCaptureAiSuggestion> {
        val raw = context.quickCaptureAiDataStore.data.first()[KEY] ?: return emptyMap()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyMap())
    }

    private suspend fun write(value: Map<String, QuickCaptureAiSuggestion>) {
        context.quickCaptureAiDataStore.edit { it[KEY] = json.encodeToString(serializer, value) }
    }

    private companion object {
        val KEY = stringPreferencesKey("quick_capture_ai_suggestions")
        const val MAX_ENTRIES = 100
    }
}
