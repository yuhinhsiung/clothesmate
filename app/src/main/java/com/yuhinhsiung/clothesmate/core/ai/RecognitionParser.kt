package com.yuhinhsiung.clothesmate.core.ai

import com.yuhinhsiung.clothesmate.core.model.ClothingCategory
import com.yuhinhsiung.clothesmate.core.model.Season
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.contentOrNull

/**
 * AI 响应的**纯解析层** —— 与 HTTP 解耦、JVM 可测（M2-03 验收：正常 JSON / 非 JSON /
 * 空 content 容错）。
 *
 * 模型的输出并不总像提示词要求的那样干净：常见形态有 ```json 围栏、前后解释文字、
 * 类别给中文名、季节给中文、标签用顿号分隔。这里尽量宽容，**只接受能映射到词表的值**，
 * 其余丢弃——宁可字段留空，也不把幻觉值写进表单。
 */
object RecognitionParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** 最多采纳 5 个风格标签（UI chips 容量与单屏可读性） */
    const val MAX_STYLE_TAGS = 5

    /** @return null = 无法解析或三个字段全空（调用方按失败降级） */
    fun parse(content: String?): AiRecognition? {
        if (content.isNullOrBlank()) return null
        val objectText = extractJsonObject(content) ?: return null
        val element = runCatching { json.parseToJsonElement(objectText) }.getOrNull() ?: return null
        val root = element as? JsonObject ?: return null

        val category = parseCategory(firstString(root, "category", "类别", "type", "类目"))
        val styles = parseStyleTags(
            root["styleTags"] ?: root["style_tags"] ?: root["styles"] ?: root["风格标签"],
        ).take(MAX_STYLE_TAGS)
        val seasons = parseSeasons(
            root["seasonTags"] ?: root["season_tags"] ?: root["seasons"] ?: root["季节"],
        )

        return AiRecognition(categoryKey = category, styleTags = styles, seasonKeys = seasons)
            .takeUnless { it.isEmpty }
    }

    /** 去掉 Markdown 围栏与前后解释，只保留第一个 `{...}` 块。 */
    internal fun extractJsonObject(content: String): String? {
        val text = content.trim()
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return text.substring(start, end + 1)
    }

    private fun firstString(root: JsonObject, vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key -> jsonString(root[key]) }

    private fun jsonString(element: JsonElement?): String? = when (element) {
        is JsonPrimitive -> element.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        else -> null
    }

    private fun parseCategory(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        ClothingCategory.fromKey(trimmed.lowercase())?.let { return it.key }
        ClothingCategory.fromKey(trimmed)?.let { return it.key }

        // 大类（中文 / 英文 key）先于搜索别名判断：搜索别名把「上衣」展开成
        // 该大类全部小类，取第一个会错误落到 T恤；大类语义应回落 *_other（§3.4）
        topLevelFallback(trimmed.lowercase())?.let { return it }
        topLevelFallback(trimmed)?.let { return it }

        // 中文小类名 / 口语别名
        ClothingCategory.searchAliases[trimmed]?.firstOrNull()?.let { return it.key }

        // 英文常见变体（模型可能会输出 "t-shirt" / "trousers"）
        val normalized = trimmed.lowercase().replace('-', '_').replace(' ', '_')
        ClothingCategory.fromKey(normalized)?.let { return it.key }
        when (normalized) {
            "tshirt", "tee", "t_shirt" -> return ClothingCategory.TShirt.key
            "trousers", "jeans", "pants" -> return ClothingCategory.Pants.key
            "skirt" -> return ClothingCategory.SkirtShort.key
            "long_skirt", "maxi_skirt" -> return ClothingCategory.SkirtLong.key
            "down_jacket", "puffer" -> return ClothingCategory.Down.key
            "sneaker" -> return ClothingCategory.Sneakers.key
            "loafer", "leather_shoes" -> return ClothingCategory.Loafers.key
            "boot" -> return ClothingCategory.Boots.key
            "sandal" -> return ClothingCategory.Sandals.key
            "bag", "purse" -> return ClothingCategory.Handbag.key
            "shoulder_bag", "crossbody" -> return ClothingCategory.ShoulderBag.key
            "back_pack" -> return ClothingCategory.Backpack.key
            "cap", "beanie" -> return ClothingCategory.Hat.key
            "accessory", "accessories", "jewelry" -> return ClothingCategory.AccOther.key
        }

        // 未细分的归 *_other（开发文档 §3.4）
        return topLevelFallback(normalized)?.let { ClothingCategory.fromKey(it)?.key }
    }

    /** 大类 key / 中文名 → DB 可存的小类 key（未细分的存大类对应 *_other）。 */
    private fun topLevelFallback(raw: String): String? = when (raw) {
        "top", "上衣" -> ClothingCategory.TopOther.key
        "outer", "外套" -> ClothingCategory.OuterOther.key
        "bottom", "下装" -> ClothingCategory.BottomOther.key
        "shoes", "鞋子" -> ClothingCategory.ShoesOther.key
        "bag", "包包" -> ClothingCategory.BagOther.key
        "acc", "配饰" -> ClothingCategory.AccOther.key
        "hat", "帽子" -> ClothingCategory.Hat.key
        "dress", "连衣裙" -> ClothingCategory.Dress.key
        else -> null
    }

    private fun parseStyleTags(element: JsonElement?): List<String> {
        val values = parseStringList(element)
        return values
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    private fun parseSeasons(element: JsonElement?): List<String> {
        val values = parseStringList(element)
        val mapped = values.mapNotNull { raw ->
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return@mapNotNull null
            Season.fromKey(trimmed.lowercase())?.key
                ?: Season.fromKey(trimmed)?.key
                ?: Season.searchAliases[trimmed]?.key
        }
        return mapped.distinct()
    }

    /** 字符串数组 / 单字符串（逗号、顿号、斜杠分隔）都接受。 */
    private fun parseStringList(element: JsonElement?): List<String> = when (element) {
        is JsonArray -> element.mapNotNull { jsonString(it) }
        is JsonPrimitive -> {
            val content = element.contentOrNull.orEmpty()
            content.split(',', '，', '、', '/', '|')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }

        else -> emptyList()
    }

    /** 兼容部分端点把 `choices[0].message.content` 返回成数组结构的情形。 */
    fun extractContent(response: JsonObject): String? {
        val content = response["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("message")?.jsonObject?.get("content") ?: return null
        return when (content) {
            is JsonPrimitive -> content.contentOrNull
            is JsonArray -> content.mapNotNull { part ->
                when (part) {
                    is JsonPrimitive -> part.contentOrNull
                    is JsonObject -> jsonString(part["text"])
                    else -> null
                }
            }.joinToString("")

            else -> null
        }?.takeIf { it.isNotBlank() }
    }
}
