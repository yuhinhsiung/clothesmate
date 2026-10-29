package com.yuhinhsiung.clothesmate.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Atelier 语义色板 —— 开发文档 §6.4、`design-system/.../tokens.json`。
 *
 * 所有页面只允许经 [AtelierColors] / MaterialTheme 语义插槽取色；
 * 这里用旧常量名保留兼容，值全部换为 M6 token，避免页面各自写死。
 */

// ── 浅色 ───────────────────────────────────────────────────────
val LightCanvas = Color(0xFFF7F6F2)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceSubtle = Color(0xFFEEEEE8)
val LightTextPrimary = Color(0xFF242720)
val LightTextSecondary = Color(0xFF62665D)
val LightTextDisabled = Color(0xFF63685D)
val LightBorderDecorative = Color(0xFFDDDFD6)
val LightOutlineControl = Color(0xFF7B8074)
val LightOutlineSubtle = Color(0xFF989D90)
val LightPrimary = Color(0xFFBE185D)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightSelection = Color(0xFFFBEAF0)
val LightImageCanvas = Color(0xFFECEBE5)
val LightSuccessText = Color(0xFF366243)
val LightSuccessContainer = Color(0xFFEAF1E9)
val LightError = Color(0xFFA92832)
val LightErrorContainer = Color(0xFFFCEBEC)
val LightAiText = Color(0xFF8A4B00)
val LightAiContainer = Color(0xFFFFF1D9)
val LightAiAccent = Color(0xFFD97706)
val LightScrim = Color(0x66000000)
val LightFocusRing = Color(0xFFBE185D)

// 兼容旧命名：旧页面仍引用这些常量。
val LightOnPrimaryContainer = LightTextPrimary
val LightPrimaryContainer = LightSelection
val LightSecondary = Color(0xFF9E5E70)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFF4E3E7)
val LightOnSecondaryContainer = Color(0xFF3B1821)
val LightAccent = LightAiAccent
val LightOnAccent = Color(0xFF2A1503)
val LightBackground = LightCanvas
val LightOnBackground = LightTextPrimary
val LightOnSurface = LightTextPrimary
val LightMuted = LightSurfaceSubtle
val LightOnMuted = LightTextSecondary
val LightOutline = LightOutlineControl
val LightOnError = Color(0xFFFFFFFF)
val LightAccentText = LightAiText
val LightStatusOkText = LightSuccessText

// ── 深色 ───────────────────────────────────────────────────────
val DarkCanvas = Color(0xFF171816)
val DarkSurface = Color(0xFF22231F)
val DarkSurfaceSubtle = Color(0xFF2C2E28)
val DarkTextPrimary = Color(0xFFF4F4ED)
val DarkTextSecondary = Color(0xFFB4B7AC)
val DarkTextDisabled = Color(0xFFA0A698)
val DarkBorderDecorative = Color(0xFF3E4138)
val DarkOutlineControl = Color(0xFF8D9483)
val DarkOutlineSubtle = Color(0xFF72796B)
val DarkPrimary = Color(0xFFE48AA9)
val DarkOnPrimary = Color(0xFF401027)
val DarkSelection = Color(0xFF432635)
val DarkImageCanvas = Color(0xFF30312C)
val DarkSuccessText = Color(0xFFAFCEB3)
val DarkSuccessContainer = Color(0xFF26392B)
val DarkError = Color(0xFFFFB3B6)
val DarkErrorContainer = Color(0xFF482529)
val DarkAiText = Color(0xFFF4C16C)
val DarkAiContainer = Color(0xFF3D301B)
val DarkAiAccent = Color(0xFFF4C16C)
val DarkScrim = Color(0x99000000)
val DarkFocusRing = Color(0xFFE48AA9)

val DarkOnPrimaryContainer = DarkTextPrimary
val DarkPrimaryContainer = DarkSelection
val DarkSecondary = Color(0xFFC292A2)
val DarkOnSecondary = Color(0xFF401027)
val DarkSecondaryContainer = Color(0xFF4A2B35)
val DarkOnSecondaryContainer = Color(0xFFF4DDE3)
val DarkAccent = DarkAiAccent
val DarkOnAccent = Color(0xFF2A1503)
val DarkBackground = DarkCanvas
val DarkOnBackground = DarkTextPrimary
val DarkOnSurface = DarkTextPrimary
val DarkMuted = DarkSurfaceSubtle
val DarkOnMuted = DarkTextSecondary
val DarkOutline = DarkOutlineControl
val DarkOnError = Color(0xFF4A0B0B)
val DarkAccentText = DarkAiText
val DarkStatusOkText = DarkSuccessText

// ── 数据可视化 ─────────────────────────────────────────────────
val HeatmapLight = listOf(
    Color(0xFFEEEEE8), Color(0xFFDCDCD3), Color(0xFFB9BCB0),
    Color(0xFF888F7E), Color(0xFF555E4C),
)
val HeatmapDark = listOf(
    Color(0xFF2C2E28), Color(0xFF44483E), Color(0xFF626A57),
    Color(0xFF89947C), Color(0xFFB6C0AA),
)
val CategoryLight = listOf(Color(0xFF65765A), Color(0xFFAEA58B), Color(0xFF9E5E70))
val CategoryDark = listOf(Color(0xFFAAB99C), Color(0xFFCEC4A8), Color(0xFFC292A2))

/** 照片蒙层前景：照片本身恒为图像内容，不随主题反色。 */
val OnPhoto = Color(0xFFFFFFFF)
val PhotoScrim = Color(0xB3000000)
val PhotoScrimLight = Color(0x66000000)

/** 旧图表列表保留，值换成 Atelier 类别序列 + 12 色，供旧 Canvas 使用。 */
val ChartCategoryColors = listOf(
    CategoryLight[0], CategoryLight[1], CategoryLight[2],
    ClothingColors.Gray, ClothingColors.Brown, ClothingColors.Pink,
)

val StatusOk = LightSuccessText

/**
 * 页面可直接读取的完整语义色集。MaterialTheme 只覆盖 M3 插槽，
 * 照片底、AI 容器、热力色阶等无法一一对应，统一从这里取。
 */
data class AtelierColors(
    val canvas: Color,
    val surface: Color,
    val surfaceSubtle: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textDisabled: Color,
    val borderDecorative: Color,
    val outlineControl: Color,
    val outlineSubtle: Color,
    val actionPrimary: Color,
    val onActionPrimary: Color,
    val selection: Color,
    val imageCanvas: Color,
    val successText: Color,
    val successContainer: Color,
    val errorText: Color,
    val errorContainer: Color,
    val aiText: Color,
    val aiContainer: Color,
    val aiAccent: Color,
    val scrim: Color,
    val focusRing: Color,
    val heatmap: List<Color>,
    val categories: List<Color>,
    val isDark: Boolean,
)

val LightAtelierColors = AtelierColors(
    canvas = LightCanvas,
    surface = LightSurface,
    surfaceSubtle = LightSurfaceSubtle,
    textPrimary = LightTextPrimary,
    textSecondary = LightTextSecondary,
    textDisabled = LightTextDisabled,
    borderDecorative = LightBorderDecorative,
    outlineControl = LightOutlineControl,
    outlineSubtle = LightOutlineSubtle,
    actionPrimary = LightPrimary,
    onActionPrimary = LightOnPrimary,
    selection = LightSelection,
    imageCanvas = LightImageCanvas,
    successText = LightSuccessText,
    successContainer = LightSuccessContainer,
    errorText = LightError,
    errorContainer = LightErrorContainer,
    aiText = LightAiText,
    aiContainer = LightAiContainer,
    aiAccent = LightAiAccent,
    scrim = LightScrim,
    focusRing = LightFocusRing,
    heatmap = HeatmapLight,
    categories = CategoryLight,
    isDark = false,
)

val DarkAtelierColors = AtelierColors(
    canvas = DarkCanvas,
    surface = DarkSurface,
    surfaceSubtle = DarkSurfaceSubtle,
    textPrimary = DarkTextPrimary,
    textSecondary = DarkTextSecondary,
    textDisabled = DarkTextDisabled,
    borderDecorative = DarkBorderDecorative,
    outlineControl = DarkOutlineControl,
    outlineSubtle = DarkOutlineSubtle,
    actionPrimary = DarkPrimary,
    onActionPrimary = DarkOnPrimary,
    selection = DarkSelection,
    imageCanvas = DarkImageCanvas,
    successText = DarkSuccessText,
    successContainer = DarkSuccessContainer,
    errorText = DarkError,
    errorContainer = DarkErrorContainer,
    aiText = DarkAiText,
    aiContainer = DarkAiContainer,
    aiAccent = DarkAiAccent,
    scrim = DarkScrim,
    focusRing = DarkFocusRing,
    heatmap = HeatmapDark,
    categories = CategoryDark,
    isDark = true,
)

val LocalAtelierColors = staticCompositionLocalOf { LightAtelierColors }

/** 衣物预设 12 色（业务词表，不是主题 token）。 */
object ClothingColors {
    private fun argb(rgb: Int): Color = Color(0xFF000000L or rgb.toLong())

    val Black = argb(com.yuhinhsiung.clothesmate.core.model.ClothingColor.Black.rgb)
    val White = argb(com.yuhinhsiung.clothesmate.core.model.ClothingColor.White.rgb)
    val Gray = argb(com.yuhinhsiung.clothesmate.core.model.ClothingColor.Gray.rgb)
    val Red = argb(com.yuhinhsiung.clothesmate.core.model.ClothingColor.Red.rgb)
    val Orange = argb(com.yuhinhsiung.clothesmate.core.model.ClothingColor.Orange.rgb)
    val Yellow = argb(com.yuhinhsiung.clothesmate.core.model.ClothingColor.Yellow.rgb)
    val Green = argb(com.yuhinhsiung.clothesmate.core.model.ClothingColor.Green.rgb)
    val Blue = argb(com.yuhinhsiung.clothesmate.core.model.ClothingColor.Blue.rgb)
    val Purple = argb(com.yuhinhsiung.clothesmate.core.model.ClothingColor.Purple.rgb)
    val Brown = argb(com.yuhinhsiung.clothesmate.core.model.ClothingColor.Brown.rgb)
    val Pink = argb(com.yuhinhsiung.clothesmate.core.model.ClothingColor.Pink.rgb)
    val Multi = argb(com.yuhinhsiung.clothesmate.core.model.ClothingColor.Multi.rgb)

    fun dotColor(color: com.yuhinhsiung.clothesmate.core.model.ClothingColor): Color =
        argb(color.rgb)
}
