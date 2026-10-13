package com.yuhinhsiung.clothesmate.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 间距 / 圆角 / 图标尺寸 / 动效时长常量 —— UI 设计文档 §4.2 / §4.1 / §5 / §8.1。
 *
 * 组件内禁止写死 dp 数字，一律引用本对象。
 */
object Dimens {

    /**
     * 4 / 8dp 网格间距。UI 文档 §4.2 无 space-7（32dp 即 space-6，48dp 为 space-8）。
     * 纪律：不出现 13dp / 15dp 这类随机值。
     */
    object Space {
        val s1 = 4.dp
        val s2 = 8.dp
        val s3 = 12.dp
        val s4 = 16.dp
        val s5 = 24.dp
        val s6 = 32.dp
        val s8 = 48.dp
    }

    /** 页面左右边距统一 16dp；瀑布流列间距 8dp */
    val pagePadding = Space.s4
    val waterfallColumnSpacing = Space.s2

    object Radius {
        /** radius-s 8dp —— chip、输入框 */
        val s = 8.dp
        /** radius-m 16dp —— 按钮、小卡片 */
        val m = 16.dp
        /** radius-l 20dp —— 大卡片、弹层 */
        val l = 20.dp
        /** radius-xl 28dp —— 底部弹窗、大容器 */
        val xl = 28.dp
    }

    /**
     * 图标尺寸 token（UI 文档 §5）。
     * 触控区统一不小于 [minTouchTarget] = 48dp，视觉小于此时扩大热区。
     */
    object Icon {
        val s = 18.dp
        val m = 24.dp
        val l = 32.dp
    }

    /** 全部可点元素触控目标下限 */
    val minTouchTarget = 48.dp

    /** 主按钮高度（UI 文档 §6.3） */
    val buttonHeight = 52.dp

    /** FAB：常态 56dp，滚动时缩小为 40dp 迷你态（UI 文档 §6.4，M3 行为） */
    val fabSize = 56.dp
    val fabSizeMini = 40.dp

    /** 底部导航高度（含安全区，UI 文档 §6.1） */
    val bottomBarHeight = 80.dp

    /** 拍照页底部操作区（UI 文档 §7.3「高约 120dp」） */
    val cameraBottomBarHeight = 120.dp

    /** 快门：外圈白环 72dp + 内 56dp 实心 */
    val shutterOuter = 72.dp
    val shutterInner = 56.dp

    /** 空态图标容器直径（UI 文档 §6.9） */
    val emptyStateCircle = 120.dp

    /** 照片上限（穿搭照片与录入照片均 9 张，开发文档 §3.2） */
    const val MaxPhotos = 9

    /** 原图压缩：长边 ≤1600px、JPEG 质量 85（开发文档 §3.3） */
    const val ImageMaxEdge = 1600
    const val ImageJpegQuality = 85

    /** 动效时长 token（UI 文档 §8.1） */
    object Duration {
        const val Instant = 100
        const val Fast = 200
        const val Normal = 300
        const val Slow = 450
        /** 粒子庆祝上限 */
        const val Celebration = 600
    }

    /** 入场动画位移与缩放（UI 文档 §8.2：上移 16dp + 缩放 0.92→1，每项延迟 60ms） */
    val enterOffsetY = 16.dp
    const val EnterScaleFrom = 0.92f
    const val EnterStaggerDelayMs = 60L
    /** Tab 切换上移 */
    val tabSwitchOffsetY = 4.dp

    /** 分割线 / 描边粗细（UI 文档 §4.3、§6.1 底部导航顶部分割线） */
    val dividerThickness = 1.dp

    /**
     * 阴影（UI 文档 §4.3）：柔和克制，浅色模式生效。
     * 深色模式**不用阴影**，靠表面色明度差分層（见 Theme.kt 的 DarkColors）。
     * 卡片 2dp / 底部弹窗 8dp。
     */
    object Elevation {
        val card = 2.dp
        val sheet = 8.dp
        /** 关闭 tonal elevation（细分层改用明度差，不用泛灰罩层；见 UI 文档 §9 深色模式） */
        val none = 0.dp
    }

    /** 衣物网格列数（衣柜详情、衣物选择器，UI 文档 §7.2/§7.4） */
    const val ClothingGridColumns = 3

    /** 侧面移除 x 图标 */
    val closeButtonSize = 24.dp
}
