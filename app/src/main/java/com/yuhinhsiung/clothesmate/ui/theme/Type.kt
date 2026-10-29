package com.yuhinhsiung.clothesmate.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.yuhinhsiung.clothesmate.R

/**
 * Atelier 排印 —— 开发文档 §6.4 / tokens.json。
 *
 * 中文与功能数字一律系统无衬线；Playfair 仅品牌词 `ClothesMate`。
 * 所有应用说明文字 ≥14sp，禁用态不缩小字号。
 */

val PlayfairDisplay = FontFamily(
    Font(R.font.playfair_display_regular, FontWeight.Normal),
    Font(R.font.playfair_display_semibold, FontWeight.SemiBold),
)

private val ComfortableLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun systemStyle(
    size: Int,
    lineHeight: Int,
    weight: FontWeight,
) = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    lineHeightStyle = ComfortableLineHeight,
)

/** 仅品牌词使用；不是通用标题字体。 */
val BrandTextStyle = TextStyle(
    fontFamily = PlayfairDisplay,
    fontWeight = FontWeight.Normal,
    fontSize = 26.sp,
    lineHeight = 32.sp,
    lineHeightStyle = ComfortableLineHeight,
)

val ClothesMateTypography = Typography(
    // 概览大数字：metric 32/40，系统字体，等宽数字由组件设置
    displayLarge = systemStyle(32, 40, FontWeight.SemiBold),
    displayMedium = systemStyle(28, 36, FontWeight.SemiBold),
    displaySmall = systemStyle(26, 32, FontWeight.Normal),

    // 根页主标题 title.hero 28/36
    headlineLarge = systemStyle(28, 36, FontWeight.SemiBold),
    // 详情标题 title.page 22/30
    headlineMedium = systemStyle(22, 30, FontWeight.SemiBold),
    // 紧凑/吸顶标题 title.compact 20/26
    headlineSmall = systemStyle(20, 26, FontWeight.SemiBold),

    // 分区标题 title.section 18/26
    titleLarge = systemStyle(18, 26, FontWeight.SemiBold),
    // 正文强调 16/24
    titleMedium = systemStyle(16, 24, FontWeight.SemiBold),
    // 字段与 chips 14/20
    titleSmall = systemStyle(14, 20, FontWeight.Medium),

    bodyLarge = systemStyle(16, 24, FontWeight.Normal),
    bodyMedium = systemStyle(14, 20, FontWeight.Normal),
    bodySmall = systemStyle(14, 20, FontWeight.Normal),

    // 主按钮 16/24 600
    labelLarge = systemStyle(16, 24, FontWeight.SemiBold),
    // 导航、标签 14/20 500
    labelMedium = systemStyle(14, 20, FontWeight.Medium),
    // 辅助说明 14/20 400，不再使用 11/12sp
    labelSmall = systemStyle(14, 20, FontWeight.Normal),
)
