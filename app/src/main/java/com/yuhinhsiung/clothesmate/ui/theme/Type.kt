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
 * 字体排印 —— UI 设计文档 §3.2 字级表。
 *
 * 中文用系统默认无衬线（靠字重 + 字号 + 颜色建立层级），不打包中文字体；
 * 英文 Display 级装饰用打包的 Playfair Display（仅 400/600，SIL OFL 1.1）。
 * 功能信息（编号、数量）不用衬线，保证识别效率。
 */

val PlayfairDisplay = FontFamily(
    Font(R.font.playfair_display_regular, FontWeight.Normal),
    Font(R.font.playfair_display_semibold, FontWeight.SemiBold),
)

/**
 * 中文段落排版：行内留白令长文本更透气（UI 文档 §3.2「中文行高加大到 1.6 倍」）。
 * 关闭 trim 以保证中英文混排时不被裁切。
 */
private val ComfortableLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

/**
 * Playfair 仅用于英文装饰词与概览大数字；中文场景由系统字体承担，
 * 故用 [PlayfairDisplay] 打底的样式仅在无中文文本处使用。
 */
private fun displayStyle(size: Int, lineHeight: Int) = TextStyle(
    fontFamily = PlayfairDisplay,
    fontWeight = FontWeight.SemiBold,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    lineHeightStyle = ComfortableLineHeight,
)

val ClothesMateTypography = Typography(
    // 34 / 42sp —— 今日页问候语、概览大数字（Playfair 600 + 中文 Bold）
    displayLarge = displayStyle(34, 42),
    displayMedium = displayStyle(28, 36),
    displaySmall = displayStyle(24, 32),

    // 24 / 32sp Bold —— 页面主标题
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        lineHeightStyle = ComfortableLineHeight,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        lineHeightStyle = ComfortableLineHeight,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        lineHeightStyle = ComfortableLineHeight,
    ),

    // 20 / 28sp Bold —— 卡片组标题、详情页标题
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        lineHeightStyle = ComfortableLineHeight,
    ),
    // 17 / 24sp Medium —— 列表项主标题、分类名
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        lineHeightStyle = ComfortableLineHeight,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        lineHeightStyle = ComfortableLineHeight,
    ),

    // 16 / 26sp Regular —— 正文
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 26.sp,
        lineHeightStyle = ComfortableLineHeight,
    ),
    // 14 / 22sp Regular —— 次要说明文字（正文下限）
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        lineHeightStyle = ComfortableLineHeight,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        lineHeightStyle = ComfortableLineHeight,
    ),

    // 14 / 20sp Medium —— 按钮文字
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        lineHeightStyle = ComfortableLineHeight,
    ),
    // 12 / 16sp Medium —— 标签 chip、角标、编号
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        lineHeightStyle = ComfortableLineHeight,
    ),
    // 11 / 14sp Medium —— 最小辅助信息（下限，不再小）
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        lineHeightStyle = ComfortableLineHeight,
    ),
)
