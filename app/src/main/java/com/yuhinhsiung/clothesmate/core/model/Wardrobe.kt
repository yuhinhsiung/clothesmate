package com.yuhinhsiung.clothesmate.core.model

import com.yuhinhsiung.clothesmate.core.database.entity.WardrobeEntity

/**
 * 衣柜展示模型（含实时衣物计数，用于列表卡件数展示 W-03）。
 */
data class Wardrobe(
    val id: Long,
    val name: String,
    val note: String?,
    val coverImagePath: String?,
    val sortOrder: Int,
    val createdAt: Long,
    /** 衣物数（不含软删除），由 DAO 的 GROUP BY 查询实时算出 */
    val clothingCount: Int = 0,
)

fun WardrobeEntity.toDomain(clothingCount: Int = 0): Wardrobe = Wardrobe(
    id = id,
    name = name,
    note = note,
    coverImagePath = coverImagePath,
    sortOrder = sortOrder,
    createdAt = createdAt,
    clothingCount = clothingCount,
)
