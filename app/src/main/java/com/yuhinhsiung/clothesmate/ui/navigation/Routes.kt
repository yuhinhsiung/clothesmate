package com.yuhinhsiung.clothesmate.ui.navigation

import kotlinx.serialization.Serializable

/**
 * 全 App 路由定义 —— 类型安全导航（Navigation Compose 2.10 + kotlinx serialization，
 * review E-b：路由串散落两处、参数命名漂移，M1-14/16 路由增长前迁移 @Serializable）。
 *
 * 约定：
 * - 一级 Tab 为 `data object`；二级页为 `data class`（携带参数）。
 * - 命名不带 `Route` 后缀——那会与同名页面 Composable 冲突（编译器二义）。
 * - ViewModel 经 `SavedStateHandle.toRoute<T>()` 自取参数，不经形参传递（review E-c）。
 * - 底部导航显隐按 `NavDestination.hasRoute(KClass)` 判定（ClothesMateNavHost）。
 */

// ── 一级 Tab（开发文档 §6.1，仅这 4 页显示底部导航）──────────────────

@Serializable
data object TodayTab

@Serializable
data object WardrobeTab

@Serializable
data object OutfitTab

@Serializable
data object OverviewTab

// ── 衣柜 / 衣物 ─────────────────────────────────────────────────

/** 衣柜详情（M1-09） */
@Serializable
data class WardrobeDetail(val wardrobeId: Long)

/** 衣物详情（M1-11） */
@Serializable
data class ClothingDetail(val itemId: Long)

/**
 * 衣物表单 —— M1-14 录入确认页与 M1-12 编辑页共用一个路由。
 *
 * - `itemId` 非空 = 编辑模式（预填既有衣物，照片只读展示）；
 * - 否则 = 创建模式（`imageUri` 必由上游给出：拍照页产出或相册选图）。
 * - `wardrobeId` 创建模式的衣柜预选（从衣柜详情页 FAB 进入时带上该衣柜）。
 */
@Serializable
data class ClothingForm(
    val itemId: Long? = null,
    val imageUri: String? = null,
    val wardrobeId: Long? = null,
)

/** 全局搜索结果页（M1-16，二级页无底部导航） */
@Serializable
data object Search

// ── 拍照页（M1-14 / M1-18 共用）────────────────────────────────

/** 拍照页拍完去哪。 */
@Serializable
enum class CaptureTarget {
    /** 录入衣物：拍完带图进衣物确认页（M1-14） */
    CLOTHING_NEW,

    /** 创建穿搭：拍完带图进穿搭确认页（M1-18 首批照片） */
    OUTFIT_NEW,

    /** 穿搭确认页内追加：拍完经 SavedStateHandle 回传给上一页（M1-18） */
    OUTFIT_APPEND,
}

@Serializable
data class Capture(
    val target: CaptureTarget = CaptureTarget.CLOTHING_NEW,
    /** 衣物预选衣柜（从衣柜详情页 FAB 进入时带上） */
    val wardrobeId: Long? = null,
)

// ── 穿搭 ───────────────────────────────────────────────────────

/**
 * 穿搭瀑布流（M1-19）。`categoryId = null` 表示「全部穿搭」入口（O-04）；
 * 非空为分类内瀑布流（O-03）。两者同一组件、数据源参数区分（开发计划 M1-19 备注）。
 */
@Serializable
data class OutfitWaterfall(val categoryId: Long? = null)

/** 穿搭详情（M1-20）。命名避让核心领域模型 `core.model.OutfitDetail`，故带 Args 后缀。 */
@Serializable
data class OutfitDetailArgs(val outfitId: Long)

/**
 * 穿搭表单 —— M1-18 创建流程的确认穿搭信息页，编辑复用（预填）。
 *
 * `photoUris` 创建流程入口（FAB 面板拍照/相册先行）带来的**首批**照片，`\n` 分隔
 * （Uri 不含换行；List<String> 走导航序列化不可靠，取稳妥字符串）。
 * 页内的追加拍照/相册经 SavedStateHandle 回传，不走本参数。
 */
@Serializable
data class OutfitForm(
    val outfitId: Long? = null,
    val photoUris: String? = null,
)

/** 衣物选择器（M1-18，全屏二级页；结果经 SavedStateHandle 回传） */
@Serializable
data object ClothingPicker

// ── SavedStateHandle 回传 key（不走路由参数的结果传递） ──────────

/** 拍照页（OUTFIT_APPEND）→ 穿搭确认页：追加照片的 Uri 字符串 */
const val OUTFIT_APPEND_URI_KEY = "outfit_append_uri"

/** 衣物选择器 → 穿搭确认页：已选衣物 id 列表（ArrayList<Long>，选择顺序） */
const val PICKED_ITEM_IDS_KEY = "picked_item_ids"

/** 穿搭表单 → 穿搭详情：编辑保存成功回执 */
const val OUTFIT_SAVED_KEY = "outfit_saved"

/** 编辑衣物表单 → 衣物详情：保存成功回执（M1-12 Snackbar） */
const val CLOTHING_EDIT_SAVED_KEY = "clothing_edit_saved"
