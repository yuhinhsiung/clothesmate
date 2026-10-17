package com.yuhinhsiung.clothesmate.ui.component

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yuhinhsiung.clothesmate.ui.theme.Dimens
import com.yuhinhsiung.clothesmate.ui.theme.LocalReducedMotion
import com.yuhinhsiung.clothesmate.ui.theme.muted
import com.yuhinhsiung.clothesmate.ui.theme.onMuted

/**
 * 空状态与骨架屏 —— UI 设计文档 §6.9。
 *
 * 全 App 的空态统一走这里，保证「图标 + 主文案 + 副文案 + 主按钮」四段结构与文案一致；
 * 文案表逐条收录在 [EmptyStateSpec] 里（含跨里程碑项，随对应阶段启用）。
 */

/**
 * 空态四要素。
 *
 * @property icon UI 文档 §5.1 图标名对应的 ImageVector
 * @property title 主文案
 * @property description 副文案，可省（表格里该场景若为「—」则传 null）
 * @property actionLabel 主按钮文案，null = 无主按钮（如搜索无结果）
 * @property onAction 主按钮回调，null 时按钮不渲染
 */
data class EmptyStateSpec(
    val icon: ImageVector,
    val title: String,
    val description: String? = null,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
)

/**
 * 空态容器：`muted` 底圆形 120dp + 线性图标 48dp（`onMuted`）+ 文案 + 主按钮（§6.9）。
 *
 * ## 矮视口自适应（review UI-02）
 *
 * 横屏（约 800×360dp）时空衣柜页顶栏、信息条、筛选行占掉大半，剩下给空态的空间
 * 不到 150dp：固定 120dp 的装饰圆 + 四要素被压成一条线，标题、副文案、「+ 录衣服」
 * 全部不可见，UI 树里也没有任何可滚动容器，用户无从找回。两条兜底同时上：
 *
 * 1. **紧凑模式**：可用高度低于阈值时收小装饰圆与图标、压缩间距，减少装饰占比；
 * 2. **纵向可滚**：内容用 `heightIn(min = 视口高)` 撑满视口，放得下时依旧居中，
 *    放不下时可滚动——大字号等更极端的情形也不会再把内容吞掉。
 *
 * 全 App 共用本组件，所有调用页一并受益。
 */
@Composable
fun EmptyState(
    spec: EmptyStateSpec,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val bounded = maxHeight != Dp.Infinity
        val compact = bounded && maxHeight < CompactHeightThreshold
        val circleSize = if (compact) Dimens.emptyStateCircle / 2 else Dimens.emptyStateCircle
        val iconSize = if (compact) {
            Dimens.Icon.m
        } else {
            Dimens.Icon.l + Dimens.Space.s4
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = if (bounded) maxHeight else 0.dp)
                .padding(Dimens.Space.s6),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(circleSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.muted),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = spec.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onMuted,
                    modifier = Modifier.size(iconSize),
                )
            }

            Text(
                text = spec.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = if (compact) Dimens.Space.s3 else Dimens.Space.s5),
            )

            spec.description?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Dimens.Space.s2),
                )
            }

            if (spec.actionLabel != null && spec.onAction != null) {
                Button(
                    onClick = spec.onAction,
                    shape = RoundedCornerShape(Dimens.Radius.m),
                    modifier = Modifier.padding(top = if (compact) Dimens.Space.s4 else Dimens.Space.s6),
                ) {
                    Text(
                        text = spec.actionLabel,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

/** 判定「矮视口」的高度阈值（横屏手机约 360dp 高，减去顶栏后远低于此值） */
private val CompactHeightThreshold = 360.dp

/**
 * 骨架屏卡片 —— §6.9「列表加载：骨架屏（灰粉 shimmer 卡片占位），不用转圈菊花」。
 *
 * @param reducedMotion 系统「减弱动态效果」开启时停在静帧，不做呼吸动画
 *        （§8.2 纪律，由调用方经 [LocalReducedMotion] 传入）
 */
@Composable
fun ShimmerCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Dimens.Radius.m),
    height: Dp = 96.dp,
) {
    val reduced = LocalReducedMotion.current
    val alpha = if (reduced) {
        0.5f
    } else {
        val transition = rememberInfiniteTransition(label = "shimmer")
        val animated by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.75f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = Dimens.Duration.Slow * 2),
            ),
            label = "shimmer-alpha",
        )
        animated
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .alpha(alpha)
            .clip(shape)
            .background(MaterialTheme.colorScheme.muted),
    )
}

/** 列表骨架占位：按 [count] 张 [ShimmerCard] 竖直堆叠，间距 8dp。 */
@Composable
fun ShimmerList(
    modifier: Modifier = Modifier,
    count: Int = 4,
    itemHeight: Dp = 96.dp,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.Space.s2),
    ) {
        repeat(count) {
            ShimmerCard(height = itemHeight)
        }
    }
}
