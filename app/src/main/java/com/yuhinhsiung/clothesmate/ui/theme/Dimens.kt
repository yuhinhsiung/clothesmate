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

    /** 禁用态不透明度（UI 文档 §6.3「禁用态：透明度 38%」） */
    const val DisabledAlpha = 0.38f

    /**
     * 「已收起」衣物卡的压暗系数（UI 文档 §6.5：压暗 + 底条两件套，review U-a）。
     * 收起是「暂时不看」而非「删除」，压暗但不隐去。
     */
    const val StoredDimAlpha = 0.5f

    /**
     * 详情页属性卡左列标签的最小宽度（review C-17）。
     * 用 min 而非固定值：系统放大字号时标签可以变宽，不会被截断。
     */
    val AttrLabelMinWidth = 64.dp

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

    /**
     * 照片上限（穿搭照片与录入照片均 9 张，开发文档 §3.2）
     *
     * 注意：图片压缩规格（长边 1600px / JPEG 质量 85 / 500KB）**不在这里**，
     * 而在 `core/image/ImageSpec`——那是逻辑层参数，ui/theme 不应反向依赖 core。
     * 这里保留 [ImageMaxEdge] / [ImageJpegQuality] 仅供 UI 预览与占位计算引用同值，
     * 实际压缩一律走 [com.yuhinhsiung.clothesmate.core.image.ImageSpec]。
     */
    const val MaxPhotos = 9

    /** 原图压缩：长边 ≤1600px、JPEG 质量 85（开发文档 §3.3）。规范值见 `core/image/ImageSpec` */
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

    /**
     * 概览图表（Canvas 自绘）几何 —— review B-14 收口：此前为页面内私有 const。
     * 环形图直径 / 环宽；颜色分布条高度。
     */
    object Chart {
        val donutSize = 168.dp
        val ringStroke = 28.dp
        val barHeight = 20.dp
    }

    /** 穿搭表单的照片块宽（4:5 横滑列，§7.4）—— review B-14 */
    val formPhotoWidth = 140.dp

    /** 穿搭表单「包含衣物」缩略块（88×110，§7.4）—— review B-14 */
    val formThumbWidth = 88.dp
    val formThumbHeight = 110.dp

    /** 选择器选中态序号圆点直径（§7.4）—— review B-14 */
    val orderBadgeSize = 24.dp

    /** 拍照页中央引导框高度（§7.3 示意框）—— review B-14 */
    val captureGuideHeight = 260.dp

    /** 设置页 AI 配置状态点直径（UI 文档 §7.6 绿/灰/红三态） */
    val statusDotSize = 8.dp

    /** 设置页「测试连接」次按钮高度（mockup-12 为 38dp，落在 4/8 网格上取 40dp） */
    val settingsTestButtonHeight = 40.dp

    /** 设置页深色模式三段控件高度（与表单控件同量级） */
    val settingsSegmentHeight = 40.dp

    /** 抠图预览页棋盘格单元边长（表达透明背景，§7.3） */
    val checkerCell = 16.dp
}
