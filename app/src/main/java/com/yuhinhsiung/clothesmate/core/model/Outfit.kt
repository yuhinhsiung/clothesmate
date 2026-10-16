package com.yuhinhsiung.clothesmate.core.model

import com.yuhinhsiung.clothesmate.core.database.entity.OutfitCategoryEntity
import com.yuhinhsiung.clothesmate.core.database.entity.OutfitEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 穿搭展示模型 —— 开发文档 §2.3 / §3.2。
 *
 * 兜底口径（§2.3.3 / §3.2 `outfit.name`）：确认页不提供名称输入，`name` 为空时
 * 展示侧按创建日期生成（如「2026-10-11 的穿搭」）。
 */
data class Outfit(
    val id: Long,
    val name: String?,
    /** 实际穿搭照片相对路径列表，首图 = 封面（可为空列表 = 无照片穿搭） */
    val photoPaths: List<String>,
    val styleTags: List<String>,
    val sceneTags: List<String>,
    val seasonTags: List<String>,
    val note: String?,
    val wearCount: Int,
    val lastWornAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    /** 关联衣物件数（瀑布卡右上角 chip「4 件」；不含「已删除」占位） */
    val itemCount: Int = 0,
) {
    /** 封面 = 首图；无照片穿搭为 null（展示侧给占位） */
    val coverPath: String?
        get() = photoPaths.firstOrNull()

    /** 展示名：自定义名优先，否则按创建日期生成兜底 */
    val displayName: String
        get() = name?.takeIf { it.isNotBlank() } ?: fallbackDisplayName(createdAt)
}

/** 穿搭名兜底：按创建日期生成（开发文档 §2.3.3，如「2026-10-11 的穿搭」） */
fun fallbackDisplayName(createdAt: Long): String =
    "${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(createdAt))} 的穿搭"

/** 穿搭分类（O-01/O-02）。 */
data class OutfitCategory(
    val id: Long,
    val name: String,
    /** 分类内穿搭套数 */
    val outfitCount: Int = 0,
    /** 封面：默认取分类内最新一套有照片穿搭的封面（O-02「封面默认取最新一套图」） */
    val coverPath: String? = null,
)

/**
 * 穿搭详情页的衣物清单条目（§2.3.4）。
 *
 * W-13：软删除衣物关联保留，渲染为「已删除」占位卡（保留原编号，不可点击）。
 */
data class OutfitItemRef(
    val itemId: Long,
    val code: String,
    val displayName: String,
    val imagePath: String?,
    val wardrobeName: String?,
    val deleted: Boolean,
)

/** 穿搭详情聚合模型：穿搭 + 衣物清单（含已删除占位）+ 所属分类 */
data class OutfitDetail(
    val outfit: Outfit,
    val items: List<OutfitItemRef>,
    val categories: List<OutfitCategory>,
) {
    /** 软删除衣物数（删除弹窗「将同时彻底清除 N 件已删除衣物」用） */
    val deletedItemCount: Int
        get() = items.count { it.deleted }
}

/** 创建 / 编辑穿搭的入库载荷（M1-18）。照片路径已由表单层归位为相对路径。 */
data class OutfitDraft(
    val name: String? = null,
    val photoPaths: List<String>,
    val styleTags: List<String>,
    val sceneTags: List<String>,
    val seasonTags: List<String>,
    val note: String?,
    val clothingIds: List<Long>,
    val categoryIds: List<Long>,
)

fun OutfitEntity.toDomain(itemCount: Int = 0): Outfit = Outfit(
    id = id,
    name = name,
    photoPaths = photoPaths,
    styleTags = styleTags,
    sceneTags = sceneTags,
    seasonTags = seasonTags,
    note = note,
    wearCount = wearCount,
    lastWornAt = lastWornAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    itemCount = itemCount,
)

fun OutfitCategoryEntity.toDomain(outfitCount: Int = 0, coverPath: String? = null): OutfitCategory =
    OutfitCategory(
        id = id,
        name = name,
        outfitCount = outfitCount,
        coverPath = coverPath,
    )
