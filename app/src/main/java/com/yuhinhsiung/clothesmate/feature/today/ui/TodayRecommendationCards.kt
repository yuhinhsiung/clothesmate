package com.yuhinhsiung.clothesmate.feature.today.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yuhinhsiung.clothesmate.R
import com.yuhinhsiung.clothesmate.core.model.ClothingItem
import com.yuhinhsiung.clothesmate.core.recommendation.FallbackCombination
import com.yuhinhsiung.clothesmate.core.recommendation.RecommendationReason
import com.yuhinhsiung.clothesmate.core.recommendation.RecommendedOutfit
import com.yuhinhsiung.clothesmate.core.weather.WeatherSnapshot
import com.yuhinhsiung.clothesmate.ui.component.CmAsyncImage
import com.yuhinhsiung.clothesmate.ui.icon.AppIcons
import com.yuhinhsiung.clothesmate.ui.theme.Dimens
import com.yuhinhsiung.clothesmate.ui.theme.muted
import com.yuhinhsiung.clothesmate.ui.theme.onMuted

@Composable
internal fun RecommendationCard(
    recommended: RecommendedOutfit,
    weather: WeatherSnapshot?,
    confirming: Boolean,
    onOpen: () -> Unit,
    onWear: () -> Unit,
) {
    val outfit = recommended.outfit.outfit
    Card(
        modifier = Modifier
            .width(320.dp)
            .heightIn(min = 212.dp),
        shape = RoundedCornerShape(Dimens.Radius.l),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            Box(
                modifier = Modifier
                    .width(168.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.muted)
                    .semantics { contentDescription = outfit.displayName }
                    .clickable(onClick = onOpen),
            ) {
                CmAsyncImage(
                    model = outfit.coverPath,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(Dimens.Space.s3),
            ) {
                Text(
                    text = outfit.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(onClick = onOpen),
                )
                Spacer(Modifier.height(Dimens.Space.s1))
                Text(
                    text = recommendationReason(recommended, weather),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                recommendationReminder(recommended)?.let { reminder ->
                    Spacer(Modifier.height(Dimens.Space.s1))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = AppIcons.WeatherRainy,
                            contentDescription = null,
                            modifier = Modifier.size(Dimens.Icon.s),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(Dimens.Space.s1))
                        Text(
                            text = reminder,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(Modifier.height(Dimens.Space.s2))
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s1)) {
                    outfit.sceneTags.take(2).forEach { tag ->
                        TagPill(tag)
                    }
                    TagPill(stringResource(R.string.recommend_items, outfit.itemCount))
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onWear,
                    enabled = !confirming,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Dimens.minTouchTarget),
                ) {
                    if (confirming) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(Dimens.Icon.s),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(stringResource(R.string.recommend_wear))
                    }
                }
            }
        }
    }
}

@Composable
private fun TagPill(text: String) {
    Surface(
        shape = RoundedCornerShape(Dimens.Radius.s),
        color = MaterialTheme.colorScheme.muted,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onMuted,
            modifier = Modifier.padding(horizontal = Dimens.Space.s1, vertical = 2.dp),
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FallbackCard(
    fallback: FallbackCombination,
    onShuffle: () -> Unit,
    onSaveAsOutfit: () -> Unit,
    onCreateOutfit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.Radius.l),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(Dimens.Space.s4)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.fallback_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    shape = RoundedCornerShape(Dimens.Radius.s),
                    color = MaterialTheme.colorScheme.muted,
                ) {
                    Text(
                        text = stringResource(R.string.fallback_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onMuted,
                        modifier = Modifier.padding(horizontal = Dimens.Space.s2, vertical = 2.dp),
                    )
                }
            }
            Spacer(Modifier.height(Dimens.Space.s1))
            Text(
                text = stringResource(R.string.fallback_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onMuted,
            )
            Spacer(Modifier.height(Dimens.Space.s3))
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s2)) {
                fallback.items.take(3).forEach { item ->
                    FallbackThumb(item = item, modifier = Modifier.weight(1f))
                }
                if (fallback.items.isEmpty()) {
                    Text(
                        text = stringResource(R.string.fallback_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onMuted,
                    )
                }
            }
            Spacer(Modifier.height(Dimens.Space.s3))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s2)) {
                TextButton(onClick = onShuffle) {
                    Text(stringResource(R.string.fallback_shuffle))
                }
                TextButton(onClick = onCreateOutfit) {
                    Text(stringResource(R.string.fallback_create))
                }
                Button(onClick = onSaveAsOutfit) {
                    Text(stringResource(R.string.fallback_save))
                }
            }
        }
    }
}

@Composable
private fun FallbackThumb(item: ClothingItem, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(Dimens.Radius.m))
                .background(MaterialTheme.colorScheme.muted),
        ) {
            CmAsyncImage(
                model = item.displayImagePath,
                contentDescription = item.displayName,
                modifier = Modifier.fillMaxWidth().fillMaxHeight(),
            )
        }
        Spacer(Modifier.height(Dimens.Space.s1))
        Text(
            text = item.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun recommendationReason(
    recommended: RecommendedOutfit,
    weather: WeatherSnapshot?,
): String {
    // UI 文档 §7.1 的温度模板同时需要「天气」与「行程」，从主/次理由里取行程标签。
    val scheduleTag = recommended.reasons
        .filterIsInstance<RecommendationReason.Schedule>()
        .firstOrNull()
        ?.tag
        ?: recommended.outfit.outfit.sceneTags.firstOrNull()
        ?: stringResource(R.string.reason_daily)
    return when (val reason = recommended.reasons.firstOrNull()) {
        is RecommendationReason.Temperature -> if (weather != null) {
            if (weather.text.isBlank()) {
                stringResource(
                    R.string.reason_temperature_simple,
                    weather.minTemp,
                    weather.maxTemp,
                    scheduleTag,
                )
            } else {
                stringResource(
                    R.string.reason_temperature,
                    weather.minTemp,
                    weather.maxTemp,
                    weather.text,
                    scheduleTag,
                )
            }
        } else {
            stringResource(R.string.reason_schedule, scheduleTag)
        }

        is RecommendationReason.Schedule -> stringResource(R.string.reason_schedule, reason.tag)
        RecommendationReason.NeverWorn -> stringResource(R.string.reason_never)
        is RecommendationReason.RecentlyWorn -> stringResource(R.string.reason_days, reason.days)
        RecommendationReason.RainReady -> stringResource(R.string.reason_rain)
        is RecommendationReason.Style -> stringResource(R.string.reason_style, reason.style)
        null -> stringResource(R.string.reason_generic)
    }
}

@Composable
private fun recommendationReminder(recommended: RecommendedOutfit): String? {
    val reminder = recommended.reasons.drop(1).firstOrNull {
        it is RecommendationReason.RainReady
    }
    return if (reminder != null) stringResource(R.string.reason_rain) else null
}

/**
 * M6 今日主推荐：单套完整照片 + 流式动作，不再用 320dp 横向卡。
 *
 * 顺序：标签/件数 → 完整照片 → 换一套/记穿 → 推荐理由。照片槽上限 300dp，
 * contain 不裁人；0 照片才用衣物组合兜底。
 */
@Composable
internal fun TodayHeroCard(
    recommended: RecommendedOutfit,
    weather: WeatherSnapshot?,
    position: Int,
    total: Int,
    confirming: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onOpen: () -> Unit,
    onWear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val outfit = recommended.outfit.outfit
    val fontScale = LocalDensity.current.fontScale
    val photoHeight = when {
        fontScale >= 1.9f -> 160.dp
        fontScale >= 1.4f -> 220.dp
        else -> 200.dp
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = outfit.identificationLabel(),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.recommend_page_position, position, total),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onMuted,
                )
            }
            TextButton(onClick = onOpen) {
                Text(stringResource(R.string.recommend_detail))
            }
        }
        Spacer(Modifier.height(Dimens.Space.s3))

        val cover = outfit.coverPath
        if (cover != null) {
            CmAsyncImage(
                model = cover,
                contentDescription = outfit.identificationLabel(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(photoHeight)
                    .clip(RoundedCornerShape(Dimens.Radius.m))
                    .clickable(onClick = onOpen),
            )
        } else {
            OutfitCollage(
                items = recommended.outfit.items,
                onClick = onOpen,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(photoHeight)
                    .clip(RoundedCornerShape(Dimens.Radius.m)),
            )
        }
        Spacer(Modifier.height(Dimens.Space.s3))

        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s3)) {
            OutlinedButton(
                onClick = if (position <= 1) onPrev else onPrev,
                enabled = total > 1 && !confirming,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = Dimens.buttonHeight),
            ) { Text(stringResource(R.string.recommend_switch)) }
            Button(
                onClick = onWear,
                enabled = !confirming,
                modifier = Modifier
                    .weight(1.4f)
                    .heightIn(min = Dimens.buttonHeight),
            ) {
                if (confirming) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(Dimens.Icon.s),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.recommend_wear))
                }
            }
        }

        Spacer(Modifier.height(Dimens.Space.s3))
        Text(
            text = stringResource(R.string.recommend_reason_title),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.height(Dimens.Space.s1))
        Text(
            text = recommendationReason(recommended, weather),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onMuted,
        )
        recommendationReminder(recommended)?.let { reminder ->
            Spacer(Modifier.height(Dimens.Space.s1))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = AppIcons.WeatherRainy,
                    contentDescription = null,
                    modifier = Modifier.size(Dimens.Icon.s),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(Dimens.Space.s1))
                Text(
                    text = reminder,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** 无照片穿搭的 4:5 组合兜底；最多 4 件 + N，不冒充真人照片。 */
@Composable
private fun OutfitCollage(
    items: List<ClothingItem>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown = items.take(4)
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.muted,
        shape = RoundedCornerShape(Dimens.Radius.m),
    ) {
        Row(Modifier.fillMaxSize()) {
            shown.forEach { item ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    CmAsyncImage(
                        model = item.cutoutImagePath ?: item.originalImagePath,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            if (items.size > 4) {
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "+${items.size - 4}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onMuted,
                    )
                }
            }
        }
    }
}

/** M6 识别口径：已有场景标签 + 件数；无标签只写件数，不生成日期标题。 */
@Composable
internal fun com.yuhinhsiung.clothesmate.core.model.Outfit.identificationLabel(): String =
    sceneTags.firstOrNull()?.let { tag ->
        stringResource(R.string.recommend_scene_items, tag, itemCount)
    } ?: stringResource(R.string.outfit_items_only, itemCount)
