package com.yuhinhsiung.clothesmate.core.recommendation

import com.yuhinhsiung.clothesmate.core.model.ClothingCategory
import com.yuhinhsiung.clothesmate.core.model.ClothingStatus
import com.yuhinhsiung.clothesmate.core.model.Season
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 可解释规则推荐引擎（开发文档 §5.2 / 开发计划 M3-03）。
 *
 * 纯 Kotlin；不读资源、不依赖 Room / Android。输入已是完整快照，
 * 分数与排序都可被 JVM 单测逐条断言。
 */
object RecommendationEngine {

    private const val TEMPERATURE_SCORE = 3
    private const val SCHEDULE_SCORE_PER_TAG = 2
    private const val STYLE_SCORE = 1
    private const val RECENTLY_WORN_SCORE = -2
    private const val NEVER_WORN_SCORE = 2
    private const val RAIN_READY_SCORE = 1
    private const val RECENT_DAYS = 30L
    private const val MAX_RESULTS = 5

    /** 返回按分数降序的 Top 3–5；没有候选时返回空，由调用方决定兜底。 */
    fun recommend(input: RecommendationInput): List<RecommendedOutfit> {
        val rainy = input.weather?.let { isRainOrSnow(it.phenomenon) } == true
        val targetSeasons = input.weather?.let(::seasonsForTemperature).orEmpty()

        return input.outfits
            .filter { it.isAvailable }
            .filterNot { outfit -> rainy && outfit.containsLongSkirt() }
            .map { outfit ->
                val reasons = mutableListOf<RecommendationReason>()
                var score = 0

                if (input.weather != null) {
                    val matchedSeason = outfit.matchedSeason(targetSeasons)
                    if (matchedSeason != null) {
                        score += TEMPERATURE_SCORE
                        reasons += RecommendationReason.Temperature(matchedSeason)
                    }
                }

                if (rainy) {
                    if (outfit.outfit.note?.contains(RAIN_KEYWORD) == true) {
                        score += RAIN_READY_SCORE
                        reasons += RecommendationReason.RainReady
                    }
                }

                val matchedSchedules = input.scheduleTags.intersect(
                    outfit.outfit.sceneTags.toSet(),
                )
                if (matchedSchedules.isNotEmpty()) {
                    score += matchedSchedules.size * SCHEDULE_SCORE_PER_TAG
                    matchedSchedules.sorted().forEach {
                        reasons += RecommendationReason.Schedule(it)
                    }
                }

                val style = input.stylePreference
                if (!style.isNullOrBlank() && style in outfit.outfit.styleTags) {
                    score += STYLE_SCORE
                    reasons += RecommendationReason.Style(style)
                }

                val lastWornAt = outfit.outfit.lastWornAt
                if (lastWornAt == null) {
                    score += NEVER_WORN_SCORE
                    reasons += RecommendationReason.NeverWorn
                } else {
                    val days = daysSince(lastWornAt, input.today)
                    if (days in 0 until RECENT_DAYS) {
                        score += RECENTLY_WORN_SCORE
                        // 今天已穿过：保留新鲜度扣分，但不再提示「0 天没穿」。
                        if (days > 0) {
                            reasons += RecommendationReason.RecentlyWorn(days.toInt())
                        }
                    }
                }

                RecommendedOutfit(
                    outfit = outfit,
                    score = score,
                    reasons = prioritise(reasons),
                )
            }
            .sortedWith(
                compareByDescending<RecommendedOutfit> { it.score }
                    .thenBy { it.outfit.outfit.lastWornAt ?: 0L }
                    .thenBy { it.outfit.outfit.id },
            )
            .take(MAX_RESULTS)
    }

    private fun prioritise(reasons: List<RecommendationReason>): List<RecommendationReason> {
        // 主理由优先「行程」，其次「从未穿过/天气」，最后才是「最近穿过」这种弱理由。
        val order = listOf(
            RecommendationReason.Schedule::class,
            RecommendationReason.NeverWorn::class,
            RecommendationReason.Temperature::class,
            RecommendationReason.RainReady::class,
            RecommendationReason.Style::class,
            RecommendationReason.RecentlyWorn::class,
        )
        return reasons.sortedBy { reason ->
            order.indexOfFirst { it.isInstance(reason) }.let { if (it < 0) order.size else it }
        }
    }

    private fun daysSince(lastWornAt: Long, today: LocalDate): Long {
        val wornDate = Instant.ofEpochMilli(lastWornAt)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        return ChronoUnit.DAYS.between(wornDate, today)
    }

    private val com.yuhinhsiung.clothesmate.core.model.OutfitWithItems.isAvailable: Boolean
        get() = isComplete &&
            items.isNotEmpty() &&
            items.none { it.status == ClothingStatus.STORED }

    private fun com.yuhinhsiung.clothesmate.core.model.OutfitWithItems.containsLongSkirt(): Boolean =
        items.any { it.categoryKey == ClothingCategory.SkirtLong.key }

    private fun com.yuhinhsiung.clothesmate.core.model.OutfitWithItems.matchedSeason(
        targets: Set<Season>,
    ): Season? = targets.firstOrNull { season ->
        outfit.seasonTags.any { it == season.key }
    }

    private fun isRainOrSnow(phenomenon: String): Boolean =
        phenomenon.contains("雨") || phenomenon.contains("雪")

    private const val RAIN_KEYWORD = "防雨"
}
