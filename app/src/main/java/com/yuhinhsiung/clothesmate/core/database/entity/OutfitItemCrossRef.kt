package com.yuhinhsiung.clothesmate.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * 穿搭 - 衣物关联 —— 开发文档 §3.2。
 *
 * 联合主键 (outfitId, clothingItemId)。
 * 衣物软删除（W-13）后关联**保留**，穿搭详情联查时该衣物渲染为「已删除」占位卡（可显示原编号）。
 * 删除穿搭时清理本表对应行；若因此软删除衣物不再被任何现存穿搭引用，该衣物物理删除（W-13）。
 */
@Entity(
    tableName = "outfit_item_cross_ref",
    primaryKeys = ["outfitId", "clothingItemId"],
    foreignKeys = [
        ForeignKey(
            entity = OutfitEntity::class,
            parentColumns = ["id"],
            childColumns = ["outfitId"],
            // 级联由 Repository 事务控制：删穿搭需先判断 W-13 物理清除，不能在 DB 层静默级联
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ClothingItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["clothingItemId"],
            // 衣物软删除不删关联；物理清除时才能删本行
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["clothingItemId"]),
    ],
)
data class OutfitItemCrossRef(
    val outfitId: Long,
    val clothingItemId: Long,
)
