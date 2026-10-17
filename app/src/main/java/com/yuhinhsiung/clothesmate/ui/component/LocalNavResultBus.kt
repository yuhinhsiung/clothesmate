package com.yuhinhsiung.clothesmate.ui.component

import androidx.compose.runtime.staticCompositionLocalOf
import com.yuhinhsiung.clothesmate.ui.navigation.NavResultBus

/**
 * 导航结果总线的 CompositionLocal —— 供 NavHost 层的导航回调发布结果
 * （review B-01/B-04：`previousBackStackEntry.savedStateHandle` 在 type-safe 导航下
 * 写入整体失效，改为显式总线；见 [NavResultBus] KDoc）。
 *
 * 由 MainActivity 注入并提供，与 [LocalImageStorage] 同模式。
 */
val LocalNavResultBus = staticCompositionLocalOf<NavResultBus> {
    error("LocalNavResultBus not provided")
}
