package com.yuhinhsiung.clothesmate.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.yuhinhsiung.clothesmate.core.ai.QuickCaptureAiSuggestion
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * 连拍 AI 建议的本地暂存：最多保留 100 条，不参与衣物字段写入。
 *
 * 与衣物表单共用同一个应用级 `clothesmate_preferences` DataStore，使“退出连拍 →
 * 重新打开待完善衣物”仍能读到建议。DataStore 是唯一事实来源；不再用进程内静态
 * 缓存跨实例兜底（那会掩盖真实读写路径的问题）。
 */
@Singleton
class QuickCaptureAiSuggestionStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    /** 测试/兼容入口：使用该 Context 的应用偏好库。 */
    constructor(context: Context) : this(context.clothesMateDataStore)

    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), QuickCaptureAiSuggestion.serializer())

    suspend fun get(itemId: Long): QuickCaptureAiSuggestion? = mutex.withLock {
        read()[itemId.toString()]
    }

    suspend fun save(itemId: Long, suggestion: QuickCaptureAiSuggestion) = mutex.withLock {
        val current = read().toMutableMap()
        current[itemId.toString()] = suggestion
        write(trim(current))
    }

    suspend fun remove(itemId: Long) = mutex.withLock {
        val current = read().toMutableMap()
        if (current.remove(itemId.toString()) != null) write(current)
    }

    private suspend fun read(): Map<String, QuickCaptureAiSuggestion> {
        val raw = dataStore.data.first()[KEY] ?: return emptyMap()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyMap())
    }

    private fun trim(value: Map<String, QuickCaptureAiSuggestion>): Map<String, QuickCaptureAiSuggestion> =
        if (value.size <= MAX_ENTRIES) value else value.entries
            .sortedByDescending { it.value.createdAt }
            .take(MAX_ENTRIES)
            .associate { it.key to it.value }

    private suspend fun write(value: Map<String, QuickCaptureAiSuggestion>) {
        dataStore.edit { it[KEY] = json.encodeToString(serializer, value) }
    }

    private companion object {
        val KEY = stringPreferencesKey("quick_capture_ai_suggestions")
        const val MAX_ENTRIES = 100
    }
}
