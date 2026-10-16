package com.yuhinhsiung.clothesmate.core.model

import com.yuhinhsiung.clothesmate.core.database.entity.ClothingItemEntity

/**
 * 衣物展示模型 —— UI 层消费，把 DB 的 key 与相对路径解成可直接渲染的值。
 *
 * 兜底口径（开发文档 §3.2 `name`）：`name` 为空时用「主色 + 类别」拼接展示名，如「白色 · 衬衫」。
 */
data class ClothingItem(
    val id: Long,
    /** 用户可见编号，纯数字 */
    val code: String,
    val wardrobeId: Long,
    val categoryKey: String?,
    val colorKeys: List<String>,
    val styleTags: List<String>,
    val seasonTags: List<String>,
    val originalImagePath: String?,
    val cutoutImagePath: String?,
    val brand: String?,
    val name: String?,
    val note: String?,
    val wearCount: Int,
    val lastWornAt: Long?,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
) {
    /** 展示名：自定义名优先，否则「主色 + 类别」拼接兜底 */
    val displayName: String
        get() = name?.takeIf { it.isNotBlank() } ?: fallbackDisplayName

    private val fallbackDisplayName: String
        get() {
            val colorName = colorKeys.firstNotNullOfOrNull { ClothingColor.fromKey(it)?.displayName }
            val categoryName = categoryKey?.let { ClothingCategory.fromKey(it)?.displayName }
            return when {
                colorName != null && categoryName != null -> "$colorName · $categoryName"
                categoryName != null -> categoryName
                colorName != null -> colorName
                else -> "未分类衣物"
            }
        }

    /** 待完善判定：类别为空即待完善（开发文档 §2.2.4） */
    val isIncomplete: Boolean
        get() = categoryKey.isNullOrBlank()

    /** 是否已收起（W-09：收起不参与推荐、选择列表默认隐藏） */
    val isStored: Boolean
        get() = status == ClothingStatus.STORED

    /** 列表展示优先用抠图，无则原图（M1 阶段 cutoutImagePath 为空属正常） */
    val displayImagePath: String?
        get() = cutoutImagePath ?: originalImagePath
}

fun ClothingItemEntity.toDomain(): ClothingItem = ClothingItem(
    id = id,
    code = code,
    wardrobeId = wardrobeId,
    categoryKey = category,
    colorKeys = colors,
    styleTags = styleTags,
    seasonTags = seasonTags,
    originalImagePath = originalImagePath,
    cutoutImagePath = cutoutImagePath,
    brand = brand,
    name = name,
    note = note,
    wearCount = wearCount,
    lastWornAt = lastWornAt,
    status = status,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/**
 * 领域模型 → 实体。
 *
 * ⚠️ review D-h：[deletedAt] 显式传参，**不再默认 null**——直接写库的场景
 * （编辑衣物）必须带上库里的原值，误传 null 会把软删除标记清掉、变相复活已删衣物。
 * Repository 的 updateItem 路径用 `existing.deletedAt` 回填。
 */
fun ClothingItem.toEntity(deletedAt: Long? = null): ClothingItemEntity = ClothingItemEntity(
    id = id,
    code = code,
    wardrobeId = wardrobeId,
    category = categoryKey,
    colors = colorKeys,
    styleTags = styleTags,
    seasonTags = seasonTags,
    originalImagePath = originalImagePath,
    cutoutImagePath = cutoutImagePath,
    brand = brand,
    name = name,
    note = note,
    wearCount = wearCount,
    lastWornAt = lastWornAt,
    status = status,
    deletedAt = deletedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
