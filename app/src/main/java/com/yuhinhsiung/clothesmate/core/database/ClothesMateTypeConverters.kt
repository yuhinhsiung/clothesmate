package com.yuhinhsiung.clothesmate.core.database

import android.util.Log
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
            // review D-i：坏数据静默归空数组是丢数据无痕迹——至少留一条日志，
            // 行为本身保持宽松（不因一条坏字段炸掉整个查询）
            runCatching {
                Json.decodeFromString(ListSerializer(String.serializer()), value)
            }.onFailure { t ->
                Log.w(LOG_TAG, "多值字段解析失败，按空数组降级：$value", t)
            }.getOrDefault(emptyList())
        }

    private companion object {
        const val LOG_TAG = "ClothesMateConverters"
    }
}
