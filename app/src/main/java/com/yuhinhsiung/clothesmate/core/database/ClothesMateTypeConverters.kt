package com.yuhinhsiung.clothesmate.core.database

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * 多值字段（List<String> ↔ JSON 数组字符串）—— 开发文档 §3.2 / §4.4。
 *
 * 口径：JSON 数组字符串，默认空数组 `[]`，**不允许 null 混用**。
 */
class ClothesMateTypeConverters {

    @TypeConverter
    fun stringListToJson(value: List<String>?): String =
        Json.encodeToString(ListSerializer(String.serializer()), value ?: emptyList())

    @TypeConverter
    fun jsonToStringList(value: String?): List<String> =
        if (value.isNullOrEmpty()) {
            emptyList()
        } else {
            runCatching {
                Json.decodeFromString(ListSerializer(String.serializer()), value)
            }.getOrDefault(emptyList())
        }
}
