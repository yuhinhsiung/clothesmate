package com.yuhinhsiung.clothesmate.feature.today.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.yuhinhsiung.clothesmate.core.database.TagDictionarySeeder
import com.yuhinhsiung.clothesmate.core.database.dao.TagDictionaryDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** 今日行程选择（按日期持久化；跨天自动视为空）。 */
data class ScheduleSelection(
    val tags: Set<String>,
    val date: LocalDate?,
)

interface ScheduleSelectionStore {
    fun observe(): Flow<ScheduleSelection>
    suspend fun save(date: LocalDate, tags: Set<String>)
}

@Singleton
class DataStoreScheduleSelectionStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
) : ScheduleSelectionStore {

    override fun observe(): Flow<ScheduleSelection> =
        dataStore.data
            .catch { error ->
                if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
                else throw error
            }
            .map { prefs ->
                val rawDate = prefs[KEY_DATE]
                val tags = prefs[KEY_TAGS]?.let { raw ->
                    runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
                }.orEmpty().toSet()
                ScheduleSelection(
                    tags = tags,
                    date = rawDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                )
            }

    override suspend fun save(date: LocalDate, tags: Set<String>) {
        dataStore.edit { prefs ->
            prefs[KEY_DATE] = date.toString()
            prefs[KEY_TAGS] = json.encodeToString(ListSerializer, tags.sorted())
        }
    }

    private companion object {
        val KEY_DATE = stringPreferencesKey("today_schedule_date")
        val KEY_TAGS = stringPreferencesKey("today_schedule_tags")
        val ListSerializer = ListSerializer(String.serializer())
    }
}

/** 行程标签词源：预设固定 + tag_dictionary 里用户新增的场景标签。 */
@Singleton
class ScheduleTagRepository @Inject constructor(
    private val tagDictionaryDao: TagDictionaryDao,
) {
    val presets: List<String> = TagDictionarySeeder.SCENE_PRESETS

    fun observeTags(): Flow<List<String>> =
        tagDictionaryDao.observeByType(TagDictionarySeeder.TYPE_SCENE).map { entries ->
            val custom = entries
                .filterNot { it.isPreset }
                .map { it.value }
                .distinct()
                .filterNot { it in presets }
            presets + custom
        }

    suspend fun addCustom(raw: String): String? {
        val value = raw.trim()
        if (value.isEmpty()) return null
        tagDictionaryDao.insertOrIncrement(
            type = TagDictionarySeeder.TYPE_SCENE,
            value = value,
            isPreset = value in presets,
        )
        return value
    }
}
