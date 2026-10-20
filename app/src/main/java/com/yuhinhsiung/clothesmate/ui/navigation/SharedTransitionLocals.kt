package com.yuhinhsiung.clothesmate.ui.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * M4-07 共享元素转场的路由级作用域。
 *
 * NavHost 的内容 lambda 在不同 Navigation 版本里同时提供
 * `AnimatedContentScope`（导航转场）与 `SharedTransitionScope`（根布局），
 * 这里显式下传，避免屏幕内部用 `AnimatedVisibilityScope` 时拿错层级。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

val LocalAnimatedVisibilityScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }


/**
 * 衣物照片从网格到详情共用同一个 key；未处于导航转场（如预览/测试）时
 * 自动退化为普通 Modifier，不要求调用方提供额外作用域。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedClothingImage(clothingId: Long): Modifier {
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val animatedScope = LocalAnimatedVisibilityScope.current ?: return this
    return with(sharedScope) {
        this@sharedClothingImage.sharedElement(
            sharedContentState = rememberSharedContentState("clothing-image-$clothingId"),
            animatedVisibilityScope = animatedScope,
        )
    }
}
