package com.yuhinhsiung.clothesmate.feature.overview.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuhinhsiung.clothesmate.R
import com.yuhinhsiung.clothesmate.core.model.ClothingColor
import com.yuhinhsiung.clothesmate.core.model.Season
import com.yuhinhsiung.clothesmate.ui.component.CmAsyncImage
import com.yuhinhsiung.clothesmate.ui.component.EmptyStateSpec
import com.yuhinhsiung.clothesmate.ui.icon.AppIcons
import com.yuhinhsiung.clothesmate.ui.theme.ChartCategoryColors
import com.yuhinhsiung.clothesmate.ui.theme.ClothingColors
import com.yuhinhsiung.clothesmate.ui.theme.Dimens
import com.yuhinhsiung.clothesmate.ui.theme.muted
import com.yuhinhsiung.clothesmate.ui.theme.onMuted
import com.yuhinhsiung.clothesmate.ui.theme.PlayfairDisplay
import androidx.compose.ui.text.font.FontWeight

/**
 * 概览页基础版 —— 开发计划 M1-22 / 开发文档 §2.5.2、UI 文档 §7.5、mockup-10。
 *
 * 3 个数字卡（衣柜/衣物/穿搭，Playfair 大数字）+ 类别分布环形图（玫瑰→粉→金→中性灰
 * 系列，中心总件数 + 图例）+ 颜色分布色块条形图（衣物真实颜色，花色 = 多色斜纹段）。
 * M1 静态展示；顶栏右侧 `settings` = 设置页唯一入口（§6.2，页面随 M2-05 交付）。
 *
 * ⚠️ 与开发计划 M1-22 的一处经论证偏差：环形图用 Canvas 而非 Vico——Vico 3.3.1 的
 * PieChartHost/rememberPieChart/SliceProvider API 为静态 8 扇区图引入动效插值、
 * SliceLabel、PieSize 体系整套复杂度，且颜色分布条形图（花色斜纹段）本就需自绘；
 * 两图统一 Canvas 保持视觉一致，配色全部取 [ChartCategoryColors] token（不另造色）。
 * M4-01 进阶图表若需动效再评估接入。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewRoute(
    onOpenSettings: () -> Unit = {},
    onAddClothing: () -> Unit = {},
    onClothingClick: (Long) -> Unit = {},
    onCategoryClick: (String) -> Unit = {},
    viewModel: OverviewViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.tab_overview),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                },
                // 设置页唯一入口（§6.2 / §7.5）；页面本体随 M2-05 交付
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = AppIcons.Settings,
                            contentDescription = stringResource(R.string.settings_entry),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        com.yuhinhsiung.clothesmate.ui.component.AdaptiveContent {
        when {
            uiState.loading -> {
                Column(modifier = Modifier.padding(innerPadding).padding(Dimens.pagePadding)) {
                    repeat(3) {
                        com.yuhinhsiung.clothesmate.ui.component.ShimmerCard(height = 120.dp)
                    }
                }
            }

            // 概览无数据（§6.9）：录入衣物后才有全貌
            uiState.clothingCount == 0 && uiState.outfitCount == 0 -> {
                com.yuhinhsiung.clothesmate.ui.component.EmptyState(
                    spec = EmptyStateSpec(
                        icon = AppIcons.Overview,
                        title = stringResource(R.string.empty_overview_title),
                        description = null,
                        actionLabel = stringResource(R.string.clothing_add),
                        onAction = onAddClothing,
                    ),
                    modifier = Modifier.padding(innerPadding),
                )
            }

            else -> {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    val statsRow: @Composable () -> Unit = {
                        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s2)) {
                            NumberCard(label = stringResource(R.string.overview_wardrobes), count = uiState.wardrobeCount, modifier = Modifier.weight(1f))
                            NumberCard(label = stringResource(R.string.overview_clothes), count = uiState.clothingCount, modifier = Modifier.weight(1f))
                            NumberCard(label = stringResource(R.string.overview_outfits), count = uiState.outfitCount, modifier = Modifier.weight(1f))
                        }
                    }
                    if (maxWidth >= 600.dp) {
                        Column(
                            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Dimens.pagePadding),
                            verticalArrangement = Arrangement.spacedBy(Dimens.Space.s4),
                        ) {
                            statsRow()
                            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s4)) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.Space.s4)) {
                                    if (uiState.categorySlices.isNotEmpty()) CategoryDonutCard(uiState, onCategoryClick = onCategoryClick)
                                    if (uiState.colorCounts.isNotEmpty()) ColorDistributionCard(uiState.colorCounts)
                                }
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.Space.s4)) {
                                    if (uiState.topWorn.isNotEmpty()) RankingCard(title = stringResource(R.string.overview_top_worn), items = uiState.topWorn, onItemClick = onClothingClick, accent = false)
                                    if (uiState.leastWorn.isNotEmpty()) RankingCard(title = stringResource(R.string.overview_least_worn), items = uiState.leastWorn, onItemClick = onClothingClick, accent = true)
                                    if (uiState.heatmap.isNotEmpty()) WearHeatmapCard(uiState.heatmap, uiState.legacyWearCount)
                                    if (uiState.seasonTotal > 0) SeasonDistributionCard(uiState.seasonSlices, uiState.seasonTotal)
                                }
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Dimens.pagePadding),
                            verticalArrangement = Arrangement.spacedBy(Dimens.Space.s4),
                        ) {
                            statsRow()
                            if (uiState.categorySlices.isNotEmpty()) CategoryDonutCard(uiState, onCategoryClick = onCategoryClick)
                            if (uiState.colorCounts.isNotEmpty()) ColorDistributionCard(uiState.colorCounts)
                            if (uiState.topWorn.isNotEmpty()) RankingCard(title = stringResource(R.string.overview_top_worn), items = uiState.topWorn, onItemClick = onClothingClick, accent = false)
                            if (uiState.leastWorn.isNotEmpty()) RankingCard(title = stringResource(R.string.overview_least_worn), items = uiState.leastWorn, onItemClick = onClothingClick, accent = true)
                            if (uiState.heatmap.isNotEmpty()) WearHeatmapCard(uiState.heatmap, uiState.legacyWearCount)
                            if (uiState.seasonTotal > 0) SeasonDistributionCard(uiState.seasonSlices, uiState.seasonTotal)
                        }
                    }
                }
            }
        }
        }
    }
}

/** 数字卡：Playfair 大数字 + Label（§7.5）。 */
@Composable
private fun NumberCard(
    label: String,
    count: Int,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(Dimens.Radius.l),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Dimens.Space.s4),
        ) {
            Text(
                text = count.toString(),
                fontFamily = PlayfairDisplay,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onMuted,
            )
        }
    }
}

/** 类别分布环形图卡：Canvas donut（按 [ChartCategoryColors] 顺序取色）+ 中心总件数 + 图例。 */
@Composable
private fun CategoryDonutCard(uiState: OverviewUiState, onCategoryClick: (String) -> Unit) {
    Card(
        shape = RoundedCornerShape(Dimens.Radius.l),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(Dimens.Space.s4)) {
            Text(
                text = stringResource(R.string.overview_category_chart),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            // 视觉建议 3：360dp + 200% 字号下，固定 168dp 的环形图仍占掉一半宽度，
            // 右侧图例只剩百来 dp，「连衣裙」被拆成「连衣 / 裙」。内容宽度不够时
            // 改为上下布局——图例拿到整行宽度，字号不必缩。
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.Space.s3),
            ) {
                val stackVertically = maxWidth < DonutLegendSideBySideMinWidth
                val chart: @Composable () -> Unit = {
                    Box(contentAlignment = Alignment.Center) {
                        DonutChart(
                            slices = uiState.categorySlices,
                            onSliceClick = { topKey -> onCategoryClick(topKey) },
                            modifier = Modifier.size(Dimens.Chart.donutSize),
                        )
                        // 中心总件数（Playfair）
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = uiState.clothingCount.toString(),
                                fontFamily = PlayfairDisplay,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.displaySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = stringResource(R.string.overview_total_items),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onMuted,
                            )
                        }
                    }
                }
                val legend: @Composable () -> Unit = {
                    uiState.categorySlices.forEachIndexed { index, slice ->
                        LegendRow(
                            color = ChartCategoryColors[index % ChartCategoryColors.size],
                            label = slice.topName,
                            count = slice.count,
                            modifier = Modifier.clickable { onCategoryClick(slice.topKey) },
                        )
                    }
                }

                if (stackVertically) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        chart()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = Dimens.Space.s3),
                            verticalArrangement = Arrangement.spacedBy(Dimens.Space.s1),
                        ) {
                            legend()
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        chart()
                        Spacer(modifier = Modifier.width(Dimens.Space.s4))
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(Dimens.Space.s1),
                        ) {
                            legend()
                        }
                    }
                }
            }
        }
    }
}

/** 环形图与图例并排所需的最小内容宽度；不足则改为上下布局（视觉建议 3） */
private val DonutLegendSideBySideMinWidth = 300.dp

/** 环形图：等宽圆环逐扇区绘制（-90° 起笔，无间隙即首尾相接）。 */
@Composable
private fun DonutChart(
    slices: List<CategorySlice>,
    onSliceClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sliceColors = slices.mapIndexed { index, _ ->
        ChartCategoryColors[index % ChartCategoryColors.size]
    }
    val total = slices.sumOf { it.count }.coerceAtLeast(1)
    Canvas(
        modifier = modifier.pointerInput(slices) {
            detectTapGestures { tap ->
                val center = Offset(size.width / 2f, size.height / 2f)
                val dx = tap.x - center.x
                val dy = tap.y - center.y
                val radius = minOf(size.width, size.height) / 2f
                if (dx * dx + dy * dy > radius * radius) return@detectTapGestures
                // atan2 以 3 点钟为 0°；扇区从 -90° 起笔
                var angle = Math.toDegrees(kotlin.math.atan2(dy, dx).toDouble()).toFloat() + 90f
                if (angle < 0f) angle += 360f
                var start = 0f
                slices.forEach { slice ->
                    val sweep = 360f * slice.count / total
                    if (angle >= start && angle < start + sweep) {
                        onSliceClick(slice.topKey)
                        return@detectTapGestures
                    }
                    start += sweep
                }
            }
        },
    ) {
        val stroke = Dimens.Chart.ringStroke.toPx()
        val diameter = minOf(size.width, size.height) - stroke
        val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
        val arcSize = androidx.compose.ui.geometry.Size(diameter, diameter)
        var startAngle = -90f
        slices.forEachIndexed { index, slice ->
            val sweep = 360f * slice.count / total
            drawArc(
                color = sliceColors[index],
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Butt),
            )
            startAngle += sweep
        }
    }
}

/** 颜色分布卡：单条堆叠色块条（真实颜色；花色 = 多色斜纹段）+ 图例。 */
@Composable
private fun ColorDistributionCard(colorCounts: List<ColorCount>) {
    Card(
        shape = RoundedCornerShape(Dimens.Radius.l),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(Dimens.Space.s4)) {
            Text(
                text = stringResource(R.string.overview_color_chart),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val total = colorCounts.sumOf { it.count }.coerceAtLeast(1)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.Space.s3)
                    .height(Dimens.Chart.barHeight)
                    .clip(RoundedCornerShape(Dimens.Radius.s)),
                horizontalArrangement = Arrangement.Start,
            ) {
                colorCounts.forEach { segment ->
                    val weight = segment.count.toFloat() / total
                    if (segment.color == ClothingColor.Multi) {
                        // 花色 = 多色斜纹段（UI 文档 §7.5）
                        MultiColorStripe(
                            colors = colorCounts.map { ClothingColors.dotColor(it.color) },
                            modifier = Modifier.weight(weight),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .weight(weight)
                                .fillMaxSize()
                                .background(ClothingColors.dotColor(segment.color))
                                // review B-20：白段在白卡上须有描边才可见（同 ColorDot 口径）
                                .then(
                                    if (segment.color == ClothingColor.White) {
                                        Modifier.border(1.dp, MaterialTheme.colorScheme.outline)
                                    } else {
                                        Modifier
                                    },
                                ),
                        )
                    }
                }
            }
            // 图例
            Column(
                verticalArrangement = Arrangement.spacedBy(Dimens.Space.s1),
                modifier = Modifier.padding(top = Dimens.Space.s3),
            ) {
                colorCounts.forEach { segment ->
                    LegendRow(
                        color = ClothingColors.dotColor(segment.color),
                        label = segment.color.displayName,
                        count = segment.count,
                        isMulti = segment.color == ClothingColor.Multi,
                    )
                }
            }
        }
    }
}

/** 花色斜纹段：底色取花色洋红近似，叠加多色斜线（示意多色混合）。 */
@Composable
private fun MultiColorStripe(
    colors: List<Color>,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val stripeColors = colors.take(5).ifEmpty { listOf(ClothingColors.Multi) }
        // 底色
        drawRect(color = stripeColors.first(), size = size)
        // 斜纹
        val stripeWidth = size.width / 6
        var offset = -size.height
        var colorIndex = 1 % stripeColors.size
        while (offset < size.width + size.height) {
            drawLine(
                color = stripeColors[colorIndex % stripeColors.size],
                start = Offset(offset, size.height),
                end = Offset(offset + size.height, 0f),
                strokeWidth = stripeWidth / 2,
            )
            offset += stripeWidth
            colorIndex++
        }
    }
}

@Composable
private fun LegendRow(
    color: Color,
    label: String,
    count: Int,
    isMulti: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (isMulti) {
            MultiColorStripe(
                colors = listOf(
                    com.yuhinhsiung.clothesmate.ui.theme.ClothingColors.White,
                    com.yuhinhsiung.clothesmate.ui.theme.ClothingColors.Pink,
                    com.yuhinhsiung.clothesmate.ui.theme.ClothingColors.Blue,
                    com.yuhinhsiung.clothesmate.ui.theme.ClothingColors.Yellow,
                ),
                modifier = Modifier
                    .size(Dimens.Icon.s)
                    .clip(CircleShape),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(Dimens.Icon.s)
                    .clip(CircleShape)
                    .background(color)
                    // review B-20：白色图例点加描边，否则浅色模式下不可见
                    .then(
                        if (color == ClothingColors.White) {
                            Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        } else {
                            Modifier
                        },
                    ),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(start = Dimens.Space.s2),
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onMuted,
        )
    }
}
