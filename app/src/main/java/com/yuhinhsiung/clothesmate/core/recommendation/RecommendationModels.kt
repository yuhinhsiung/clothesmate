package com.yuhinhsiung.clothesmate.core.recommendation

import com.yuhinhsiung.clothesmate.core.model.ClothingItem
import com.yuhinhsiung.clothesmate.core.model.OutfitWithItems
import com.yuhinhsiung.clothesmate.core.model.Season
import com.yuhinhsiung.clothesmate.core.model.ClothingCategory
import com.yuhinhsiung.clothesmate.core.model.ClothingStatus

/**
 * 推荐引擎的纯 Kotlin 输入/输出（开发文档 §5，开发计划 M3-03）。
 *
 * 本文件不得出现任何 `android.*` import——推荐规则必须能在 JVM 单测里直接跑，
 * 不依赖 Context / Room / Compose。
 */
data class RecommendationInput(
    val outfits: List<OutfitWithItems>,
    /** null = 天气不可用，跳过温度与天气现象两条规则（§5.1） */
    val weather: WeatherFacts?,
    val scheduleTags: Set<String>,
    val today: java.time.LocalDate,
    /** 可选风格偏好；M3 不提供设置入口，只保留打分接口（§5.2） */
    val stylePreference: String? = null,
    /**
     * 全部「正常」衣物池（§5.3 兜底从所有衣物抽，不要求未入任何穿搭）。
     * 推荐主路径仍只看 [outfits]；此字段只服务兜底组合。
     */
    val allClothing: List<ClothingItem> = emptyList(),
)

/** 推荐需要的天气最小事实，避免把网络 DTO 拖进纯 Kotlin 规则层。 */
data class WeatherFacts(
    val minTemp: Int,
    val maxTemp: Int,
    val phenomenon: String,
) {
    val meanTemp: Double get() = (minTemp + maxTemp) / 2.0
}

/** 命中的规则，UI 据此本地化理由文案（不在引擎里硬编码中文）。 */
sealed interface RecommendationReason {
    data class Schedule(val tag: String) : RecommendationReason
    data object NeverWorn : RecommendationReason
    data class RecentlyWorn(val days: Int) : RecommendationReason
    data class Temperature(val season: Season) : RecommendationReason
    data object RainReady : RecommendationReason
    data class Style(val style: String) : RecommendationReason
}

data class RecommendedOutfit(
    val outfit: OutfitWithItems,
    val score: Int,
    /** 按优先级排序；first 为主理由，第二个（若有）为提醒 */
    val reasons: List<RecommendationReason>,
)

data class BatchPage(
    val entries: List<RecommendedOutfit>,
    /** true = 本次「换一批」从末批回到了第一批（UI 提示“已看完全部合适穿搭”） */
    val wrappedToFirst: Boolean,
)

/**
 * Top 3–5 的分批游标（T-04）。
 *
 * 固定窗口循环而非随机洗牌：同一轮里不会重复，全部看完后才回到第一批；
 * 候选数 ≤ 批大小时，点「换一批」只提示已看完，不改变顺序。
 */
class RecommendationBatch(
    private val all: List<RecommendedOutfit>,
    private val batchSize: Int = 3,
) {
    init {
        require(batchSize > 0) { "batchSize must be positive" }
    }

    private var pageIndex = 0

    val pageCount: Int
        get() = if (all.isEmpty()) 0 else (all.size + batchSize - 1) / batchSize

    fun current(): BatchPage =
        BatchPage(entries = pageEntries(pageIndex), wrappedToFirst = false)

    fun next(): BatchPage {
        if (pageCount <= 1) {
            return BatchPage(entries = pageEntries(0), wrappedToFirst = true)
        }
        pageIndex = (pageIndex + 1) % pageCount
        return BatchPage(entries = pageEntries(pageIndex), wrappedToFirst = pageIndex == 0)
    }

    private fun pageEntries(index: Int): List<RecommendedOutfit> =
        all.drop(index * batchSize).take(batchSize)
}

data class FallbackCombination(
    val items: List<ClothingItem>,
    val seed: Long,
    /** 公式所用的温度档，UI 可展示“夏日基础组合” */
    val season: Season?,
    val missingCategories: List<String> = emptyList(),
)

/**
 * 无合适穿搭时的单品组合兜底（§5.3）。
 *
 * 从各类别随机抽取「正常」衣物；不要求衣物未入任何穿搭——「还未建档」只表示
 * 这组搭配还没存成 Outfit。seed 可注入，便于单测复现。
 */
fun buildFallbackCombination(
    input: RecommendationInput,
    seed: Long = System.nanoTime(),
): FallbackCombination? {
    val normal = input.allClothing
        .ifEmpty { input.outfits.flatMap { it.items } }
        .filter { it.status == ClothingStatus.NORMAL }
        .distinctBy { it.id }
    if (normal.isEmpty()) return null

    val season = input.weather?.let(::seasonForTemperature)
    val plan = fallbackPlan(season)
    val random = kotlin.random.Random(seed)
    val chosen = mutableListOf<ClothingItem>()
    val missing = mutableListOf<String>()

    plan.forEach { slot ->
        val pool = normal.filter { item -> slot.categoryKeys.any { it == item.categoryKey } }
            .ifEmpty { normal.filter { item -> item.categoryKey in slot.fallbackTopKeys } }
        val pick = pool.randomOrNull(random)
        if (pick == null) {
            missing += slot.displayName
        } else if (chosen.none { it.id == pick.id }) {
            chosen += pick
        }
    }

    // review R05：不再用「剩余衣物」跨槽补位。分类不全时宁可比 3 件少，
    // 也不能把三件 T 恤凑成一套“搭配”；单件仍不叫组合。
    if (chosen.size < 2) return null
    return FallbackCombination(
        items = chosen,
        seed = seed,
        season = season,
        missingCategories = missing,
    )
}

private data class FallbackSlot(
    val displayName: String,
    val categoryKeys: Set<String>,
    val fallbackTopKeys: Set<String>,
)

private fun fallbackPlan(season: Season?): List<FallbackSlot> = when (season) {
    Season.Summer -> listOf(
        FallbackSlot(
            displayName = "上衣",
            categoryKeys = topKeys,
            fallbackTopKeys = setOf("top"),
        ),
        FallbackSlot(
            displayName = "短裤/半身裙",
            categoryKeys = setOf(
                ClothingCategory.Shorts.key,
                ClothingCategory.SkirtShort.key,
            ),
            fallbackTopKeys = setOf("bottom"),
        ),
        FallbackSlot(
            displayName = "凉鞋",
            categoryKeys = setOf(ClothingCategory.Sandals.key),
            fallbackTopKeys = setOf("shoes"),
        ),
    )

    Season.Winter -> listOf(
        FallbackSlot("上衣", topKeys, setOf("top")),
        FallbackSlot(
            "长裤",
            setOf(ClothingCategory.Pants.key),
            setOf("bottom"),
        ),
        FallbackSlot("外套", outerKeys, setOf("outer")),
        FallbackSlot(
            "靴子/运动鞋",
            setOf(ClothingCategory.Boots.key, ClothingCategory.Sneakers.key),
            setOf("shoes"),
        ),
    )

    Season.Spring, Season.Autumn -> listOf(
        FallbackSlot("上衣", topKeys, setOf("top")),
        FallbackSlot(
            "长裤/半身裙",
            setOf(
                ClothingCategory.Pants.key,
                ClothingCategory.SkirtShort.key,
            ),
            setOf("bottom"),
        ),
        FallbackSlot(
            "运动鞋/乐福鞋",
            setOf(ClothingCategory.Sneakers.key, ClothingCategory.Loafers.key),
            setOf("shoes"),
        ),
    )

    // 天气不可用：只做“上衣 + 下装 + 鞋”的基础组合，不假装知道季节。
    null -> listOf(
        FallbackSlot("上衣", topKeys, setOf("top")),
        FallbackSlot("下装", bottomKeys, setOf("bottom")),
        FallbackSlot("鞋子", shoesKeys, setOf("shoes")),
    )
}

private fun <T> List<T>.randomOrNull(random: kotlin.random.Random): T? =
    if (isEmpty()) null else this[random.nextInt(size)]

private val topKeys: Set<String> = ClothingCategory.entries
    .filter { it.topKey == "top" }
    .map { it.key }
    .toSet()

private val outerKeys: Set<String> = ClothingCategory.entries
    .filter { it.topKey == "outer" }
    .map { it.key }
    .toSet()

private val bottomKeys: Set<String> = ClothingCategory.entries
    .filter { it.topKey == "bottom" }
    .map { it.key }
    .toSet()

private val shoesKeys: Set<String> = ClothingCategory.entries
    .filter { it.topKey == "shoes" }
    .map { it.key }
    .toSet()

/**
 * 温度 → 季节词表。注意文档区间在 15.5°C 这类小数处没有定义，这里按
 * `>=16` / `>=8` 连续划分，避免出现无法命中的空洞。
 */
fun seasonForTemperature(weather: WeatherFacts): Season = when {
    weather.meanTemp >= 26.0 -> Season.Summer
    weather.meanTemp >= 16.0 -> Season.Spring
    weather.meanTemp >= 8.0 -> Season.Autumn
    else -> Season.Winter
}

/** 同温度档可能命中两个季节标签（如 16–25 命中春/秋）。 */
fun seasonsForTemperature(weather: WeatherFacts): Set<Season> = when {
    weather.meanTemp >= 26.0 -> setOf(Season.Summer)
    weather.meanTemp >= 16.0 -> setOf(Season.Spring, Season.Autumn)
    weather.meanTemp >= 8.0 -> setOf(Season.Autumn, Season.Winter)
    else -> setOf(Season.Winter)
}
