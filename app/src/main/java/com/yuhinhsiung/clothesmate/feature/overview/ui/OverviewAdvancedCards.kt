package com.yuhinhsiung.clothesmate.feature.overview.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yuhinhsiung.clothesmate.R
import com.yuhinhsiung.clothesmate.core.model.Season
import com.yuhinhsiung.clothesmate.ui.component.CmAsyncImage
import com.yuhinhsiung.clothesmate.ui.theme.ClothingColors
import com.yuhinhsiung.clothesmate.ui.theme.Dimens
import com.yuhinhsiung.clothesmate.ui.theme.LightAccent
import com.yuhinhsiung.clothesmate.ui.theme.LightOnMuted
import com.yuhinhsiung.clothesmate.ui.theme.accentText
import com.yuhinhsiung.clothesmate.ui.theme.onMuted

/** 穿着榜：Top / 压箱底，最多 5 行；点击进衣物详情。 */
@Composable
fun RankingCard(
    title: String,
    items: List<RankedClothing>,
    onItemClick: (Long) -> Unit,
    accent: Boolean,
) {
    Card(
        shape = RoundedCornerShape(Dimens.Radius.l),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(Dimens.Space.s4)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (accent) MaterialTheme.colorScheme.accentText else MaterialTheme.colorScheme.onSurface,
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(Dimens.Space.s2),
                modifier = Modifier.padding(top = Dimens.Space.s3),
            ) {
                items.forEach { ranked ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onItemClick(ranked.item.id) }
                            .padding(vertical = Dimens.Space.s1),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CmAsyncImage(
                            model = ranked.item.displayImagePath,
                            contentDescription = ranked.item.displayName,
                            modifier = Modifier
                                .size(Dimens.Chart.rankThumb)
                                .clip(RoundedCornerShape(Dimens.Radius.s)),
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = Dimens.Space.s3),
                        ) {
                            Text(
                                text = ranked.item.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = stringResource(R.string.overview_code, ranked.item.code),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onMuted,
                            )
                        }
                        Text(
                            text = if (ranked.item.wearCount == 0) {
                                stringResource(R.string.overview_never_worn)
                            } else {
                                stringResource(R.string.overview_worn_times, ranked.item.wearCount)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onMuted,
                        )
                    }
                }
            }
        }
    }
}

/** 近 30 天穿着热力图：7 列周网格，四档色阶 + 今日描边。 */
@Composable
fun WearHeatmapCard(days: List<HeatmapDay>) {
    val weekdayLabels = listOf(
        R.string.weekday_mon,
        R.string.weekday_tue,
        R.string.weekday_wed,
        R.string.weekday_thu,
        R.string.weekday_fri,
        R.string.weekday_sat,
        R.string.weekday_sun,
    )
    Card(
        shape = RoundedCornerShape(Dimens.Radius.l),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(Dimens.Space.s4)) {
            Text(
                text = stringResource(R.string.overview_heatmap_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.Space.s2),
                horizontalArrangement = Arrangement.spacedBy(Dimens.Chart.heatGap),
            ) {
                weekdayLabels.forEach { label ->
                    Text(
                        text = stringResource(label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            val leading = days.firstOrNull()?.date?.dayOfWeek?.value?.minus(1) ?: 0
            val cells: List<HeatmapDay?> = buildList {
                repeat(leading) { add(null) }
                addAll(days)
            }
            Column(
                modifier = Modifier.padding(top = Dimens.Space.s1),
                verticalArrangement = Arrangement.spacedBy(Dimens.Chart.heatGap),
            ) {
                cells.chunked(7).forEach { week ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.Chart.heatGap),
                    ) {
                        week.forEach { day ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (day != null) {
                                    val color = when {
                                        day.count >= 3 -> MaterialTheme.colorScheme.primary
                                        day.count == 2 -> MaterialTheme.colorScheme.secondary
                                        day.count == 1 -> MaterialTheme.colorScheme.primaryContainer
                                        else -> MaterialTheme.colorScheme.surface
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(Dimens.Radius.s))
                                            .background(color)
                                            .semantics {
                                                contentDescription = "${day.date} 穿着 ${day.count} 次"
                                            }
                                            .then(
                                                if (day.count == 0 || day.isToday) {
                                                    Modifier.border(
                                                        width = if (day.isToday) 2.dp else 1.dp,
                                                        color = if (day.isToday) {
                                                            MaterialTheme.colorScheme.primary
                                                        } else {
                                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                                        },
                                                        shape = RoundedCornerShape(Dimens.Radius.s),
                                                    )
                                                } else {
                                                    Modifier
                                                },
                                            ),
                                    )
                                }
                            }
                        }
                        repeat(7 - week.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            Text(
                text = stringResource(R.string.overview_heatmap_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onMuted,
                modifier = Modifier.padding(top = Dimens.Space.s2),
            )
        }
    }
}

/** 季节衣物占比：四季堆叠横条，段内百分比。 */
@Composable
fun SeasonDistributionCard(slices: List<SeasonSlice>, total: Int) {
    Card(
        shape = RoundedCornerShape(Dimens.Radius.l),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(Dimens.Space.s4)) {
            Text(
                text = stringResource(R.string.overview_season_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.Space.s3)
                    .height(Dimens.Chart.seasonBarHeight)
                    .clip(RoundedCornerShape(Dimens.Radius.s)),
                horizontalArrangement = Arrangement.Start,
            ) {
                slices.filter { it.count > 0 }.forEach { slice ->
                    val weight = slice.count.toFloat() / total
                    val color = SeasonChartColors[slice.season] ?: MaterialTheme.colorScheme.primary
                    Box(
                        modifier = Modifier
                            .weight(weight)
                            .fillMaxSize()
                            .background(color),
                        contentAlignment = Alignment.Center,
                    ) {
                        val percent = (slice.count * 100f / total).toInt()
                        if (percent >= 10) {
                            Text(
                                text = "$percent%",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (color.luminance() > 0.55f) Color.Black else Color.White,
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.Space.s2),
                horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s3),
            ) {
                slices.forEach { slice ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(Dimens.Icon.s)
                                .clip(CircleShape)
                                .background(SeasonChartColors[slice.season] ?: MaterialTheme.colorScheme.primary),
                        )
                        Text(
                            text = stringResource(
                                R.string.overview_season_legend,
                                slice.season.displayName,
                                slice.count,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onMuted,
                            modifier = Modifier.padding(start = Dimens.Space.s1),
                        )
                    }
                }
            }
        }
    }
}

/** 四季图表色：春绿 / 夏金 / 秋橙 / 冬蓝灰，全部取既有色板。 */
val SeasonChartColors: Map<Season, Color> = mapOf(
    Season.Spring to ClothingColors.Green,
    Season.Summer to LightAccent,
    Season.Autumn to ClothingColors.Orange,
    Season.Winter to LightOnMuted,
)
