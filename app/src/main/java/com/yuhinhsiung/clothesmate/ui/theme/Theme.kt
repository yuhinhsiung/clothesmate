package com.yuhinhsiung.clothesmate.ui.theme

import android.content.res.Configuration
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/**
 * 主题入口 —— UI 设计文档 §2.1 / §2.2 / §11。
 *
 * 固定品牌玫瑰色 #BE185D，**不启用动态取色（Monet）**：动态色会冲淡品牌识别。
 */
private val LightColors = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightAccent,
    onTertiary = LightOnAccent,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightMuted,
    onSurfaceVariant = LightOnMuted,
    outline = LightOutline,
    outlineVariant = LightOutline,
    error = LightError,
    onError = LightOnError,
)

private val DarkColors = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkAccent,
    onTertiary = DarkOnAccent,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkMuted,
    onSurfaceVariant = DarkOnMuted,
    outline = DarkOutline,
    outlineVariant = DarkOutline,
    error = DarkError,
    onError = DarkOnError,
)

/**
 * 是否开启了系统「减弱动态效果」。
 *
 * 开启后入场动画与粒子庆祝全部跳过，直接呈现最终状态（UI 文档 §8.2 纪律）。
 * 读的是 [Settings.Global.TRANSITION_ANIMATION_SCALE]，0 即视为关闭动效。
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val scale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.TRANSITION_ANIMATION_SCALE,
            1f,
        )
        scale == 0f
    }
}

/**
 * App 主题。
 *
 * @param darkTheme 深色模式开关，默认跟随系统；设置页可锁定（M2-05）
 * @param content 内容
 */
@Composable
fun ClothesMateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    // 「减弱动态效果」经 CompositionLocal 下发，动效组件统一读取，避免各页面重复判断
    val reducedMotion = rememberReducedMotion()
    CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ClothesMateTypography,
            shapes = ClothesMateShapes,
            content = content,
        )
    }
}

/**
 * 语义 token 快捷访问。
 *
 * 本案的设计 token 与 Material3 插槽并非一一对应，为避免调用方到处写
 * `MaterialTheme.colorScheme.surfaceVariant` 这种看不出语义的表达，在这里
 * 按 UI 设计文档的 token 名（§2.1/§2.2）暴露语义化别名。
 *
 * 全工程一律经这些别名取色，不直接引用 lightColorScheme 的插槽名。
 */

/** `muted`：次级底色（分组区、输入框底）—— 映射到 surfaceVariant 插槽 */
val ColorScheme.muted: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.surfaceVariant

/** `onMuted`：辅助文字（说明、时间、次要信息）—— 映射到 onSurfaceVariant 插槽 */
val ColorScheme.onMuted: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.onSurfaceVariant

/**
 * 「减弱动态效果」开关。开启时所有入场动画与粒子庆祝跳过，直接呈现终态。
 * 全局读一处，动效组件通过 [LocalReducedMotion].current 取用。
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

/** 当前是否为横屏配置（UI 文档 §4.5 小屏与横屏各验证一次） */
val isLandscape: Boolean
    @Composable
    get() = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

/** 仅供 IDE 预览，与运行期主题保持一致 */
@Composable
fun ClothesMatePreviewTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = ClothesMateTypography,
        shapes = ClothesMateShapes,
        content = content,
    )
}
