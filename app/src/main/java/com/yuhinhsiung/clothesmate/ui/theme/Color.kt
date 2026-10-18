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
/**
 * 小字号**文字**用的深琥珀金（U01）：`LightAccent #D97706` 对白仅 3.19:1，
 * 不满足正文 4.5:1；图标/描边仍用 `lightAccent`（图形 3:1 达标）。
 * `#92400E` 对白约 7.0:1。
 */
val LightAccentText = Color(0xFF92400E)
/** 小字号成功绿文字（U01）：`StatusOk #16A34A` 对白 3.30:1 → 深绿约 6.4:1 */
val LightStatusOkText = Color(0xFF166534)

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
/** 深色下小字号琥珀文字：沿用 `DarkAccent`（对深底对比充足） */
val DarkAccentText = DarkAccent
/** 深色下小字号成功绿文字（U01）：浅绿对深底约 10:1 */
val DarkStatusOkText = Color(0xFF86EFAC)

// ── 图表色阶（概览页，取自主色系，不另造色）────────────────────

/**
 * 照片 / 相机深色蒙层上的前景色（瀑布卡名称白字、拍照页控件白图标）。
 * 明暗两模式同值——蒙层本身恒为深色，前景必须恒为浅色才可读
 * （UI 文档 §6.5「白字 + 照片底部 20% 黑色渐变蒙层」、§7.3 拍照页「深色半透明底」）。
 */
val OnPhoto = Color(0xFFFFFFFF)
/** 照片上的深色渐变蒙层（瀑布卡名称衬底、拍照页底部操作区） */
val PhotoScrim = Color(0xB3000000)
/** 照片上的轻蒙层：瀑布卡底部渐变的落色、件数 chip 的 40% 黑底（UI 文档 §6.5） */
val PhotoScrimLight = Color(0x66000000)

/** 类别分布环形图：玫瑰 → 粉 → 金 → 中性灰（UI 文档 §7.5）。
 *
 * review B-20：此前含 4 个色板外裸 hex（#9CA3AF/#D1D5DB/#6B7280/#FBCFE8），
 * 违反 §11「不另造色」；现全部改为**已有色板常量的引用**——§2.1 语义 token
 * （玫瑰/粉/金/灰紫/浅粉描边）+ §3.4 衣物 12 色板（中性灰/棕/粉），零新增色值。 */
val ChartCategoryColors = listOf(
    LightPrimary,          // 玫瑰（品牌主色）
    LightSecondary,        // 粉（次级粉）
    LightAccent,           // 金（琥珀）
    ClothingColors.Gray,   // 中性灰（衣物 12 色板）
    LightOnMuted,          // 灰紫（辅助文字色）
    LightOutline,          // 浅粉（描边色）
    ClothingColors.Brown,  // 棕（衣物 12 色板）
    ClothingColors.Pink,   // 粉（衣物 12 色板）
)

/** 衣物预设 12 色（开发文档 §3.4），用于颜色分布图与色点展示。
 *
 * M2-02 起色值的**唯一来源**是 `core.model.ClothingColor.rgb`（本地颜色提取的 Lab 锚点
 * 与 UI 色点必须是同一组值，否则「提取出红、显示成粉」这类分叉无从发现）。
 */
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
    /** 花色：以多色斜纹段表达，见 UI 文档 §7.5 */
    val Multi = argb(com.yuhinhsiung.clothesmate.core.model.ClothingColor.Multi.rgb)

    /**
     * 颜色枚举 → Compose.Color。用于衣物详情/选择的色点、概览颜色分布条。
     *
     * 花色（multi）返回 [Multi]：UI 文档 §6.5 说花色用「多色斜纹段」表达，
     * 那是图表侧的做法；单色点场景用这个洋红近似即可。
     */
    fun dotColor(color: com.yuhinhsiung.clothesmate.core.model.ClothingColor): Color =
        argb(color.rgb)
}

/**
 * 状态色（设置页 AI 配置状态点，UI 文档 §7.6「绿=可用 / 灰=未配置 / 红=失败」）。
 *
 * 色板里没有单独定义「成功绿」——直接引用 §3.4 衣物 12 色板的绿，避免另造色值；
 * 灰用 `onMuted`、红用 `error`（语义插槽）。
 */
val StatusOk = ClothingColors.Green
