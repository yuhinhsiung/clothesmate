package com.yuhinhsiung.clothesmate.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 品牌色板 —— UI 设计文档 §2.1 / §2.2，唯一视觉准绳。
 *
 * 纪律：全工程禁止写死色值，一律经本文件或 MaterialTheme 间接取用。
 * 深色模式独立校验对比度，不假设浅色模式结论成立。
 */

// ── 浅色模式（默认）─────────────────────────────────────────────

/** 品牌主色（玫瑰）：主按钮、选中态、关键强调 */
val LightPrimary = Color(0xFFBE185D)
val LightOnPrimary = Color(0xFFFFFFFF)
/** 玫瑰浅底：选中卡片底、标签底 */
val LightPrimaryContainer = Color(0xFFFCE7F0)
val LightOnPrimaryContainer = Color(0xFF4A0E27)
/** 次级粉：辅助强调、进度、小面积装饰 */
val LightSecondary = Color(0xFFEC4899)
/** 该粉亮度中等，配深色文字保证对比度 */
val LightOnSecondary = Color(0xFF3D0A22)
/**
 * 次级容器色。刻意**显式指定**：M3 该插槽默认回落基线淡紫灰（≈#E8DEF8），
 * 与本案品牌玫瑰色板无关。次按钮 / chip 早期容器与玫瑰浅底同值，保持单屏 ≤10% 玫瑰占比。
 */
val LightSecondaryContainer = LightPrimaryContainer
val LightOnSecondaryContainer = LightOnPrimaryContainer
/** 琥珀金：仅用于 AI 标识（✨）与稀有高亮（待完善 / 压箱底），每屏各 ≤1 处 */
val LightAccent = Color(0xFFD97706)
val LightOnAccent = Color(0xFF2A1503)
/** 页面大背景：极浅玫瑰白 */
val LightBackground = Color(0xFFFDF2F8)
val LightOnBackground = Color(0xFF1F1A1D)
/** 卡片、弹层、底部导航 */
val LightSurface = Color(0xFFFFFFFF)
val LightOnSurface = Color(0xFF1F1A1D)
/** 次级底色（分组区、输入框底） */
val LightMuted = Color(0xFFFBF1F5)
val LightOnMuted = Color(0xFF5C4756)
/** 分割线、描边 */
val LightOutline = Color(0xFFF0D9E3)
val LightError = Color(0xFFDC2626)
val LightOnError = Color(0xFFFFFFFF)

// ── 深色模式 ───────────────────────────────────────────────────

val DarkPrimary = Color(0xFFFDA4AF)
val DarkOnPrimary = Color(0xFF561024)
val DarkPrimaryContainer = Color(0xFF7F1D43)
val DarkOnPrimaryContainer = Color(0xFFFFD9E2)
val DarkSecondary = Color(0xFFF9A8D4)
val DarkOnSecondary = Color(0xFF3D0A22)
/** 深色下次级容器同用玫瑰深容器，理由同浅色侧 */
val DarkSecondaryContainer = DarkPrimaryContainer
val DarkOnSecondaryContainer = DarkOnPrimaryContainer
val DarkAccent = Color(0xFFFBBF24)
val DarkOnAccent = Color(0xFF2A1503)
/** 暖调近黑：不用纯黑，纯黑显冷硬 */
val DarkBackground = Color(0xFF171215)
val DarkOnBackground = Color(0xFFF2E7EC)
val DarkSurface = Color(0xFF221A1D)
val DarkOnSurface = Color(0xFFF2E7EC)
val DarkMuted = Color(0xFF2A2024)
val DarkOnMuted = Color(0xFFB69FAD)
/** 深底下分割线必须仍可见 */
val DarkOutline = Color(0xFF4A3941)
val DarkError = Color(0xFFF87171)
val DarkOnError = Color(0xFF4A0B0B)

// ── 图表色阶（概览页，取自主色系，不另造色）────────────────────

/** 类别分布环形图：玫瑰 → 粉 → 金 → 中性灰（UI 文档 §7.5） */
val ChartCategoryColors = listOf(
    Color(0xFFBE185D),
    Color(0xFFEC4899),
    Color(0xFFF9A8D4),
    Color(0xFFD97706),
    Color(0xFF9CA3AF),
    Color(0xFFD1D5DB),
    Color(0xFF6B7280),
    Color(0xFFFBCFE8),
)

/** 衣物预设 12 色（开发文档 §3.4），用于颜色分布图与色点展示 */
object ClothingColors {
    val Black = Color(0xFF1F1A1D)
    val White = Color(0xFFFFFFFF)
    val Gray = Color(0xFF9CA3AF)
    val Red = Color(0xFFDC2626)
    val Orange = Color(0xFFEA580C)
    val Yellow = Color(0xFFFACC15)
    val Green = Color(0xFF16A34A)
    val Blue = Color(0xFF2563EB)
    val Purple = Color(0xFF9333EA)
    val Brown = Color(0xFF92400E)
    val Pink = Color(0xFFEC4899)
    /** 花色：以多色斜纹段表达，见 UI 文档 §7.5 */
    val Multi = Color(0xFFDB2777)
}
