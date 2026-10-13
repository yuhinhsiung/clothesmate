package com.yuhinhsiung.clothesmate.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * 穿搭 - 分类关联（多分类）—— 开发文档 §3.2 / §2.3.2。
 *
 * 联合主键 (outfitId, categoryId)。
 * 无关联记录的穿搭 = 未分类；删除分类时删除对应关联记录即可（O-05）。
 */
@Entity(
    tableName = "outfit_category_cross_ref",
    primaryKeys = ["outfitId", "categoryId"],
    foreignKeys = [
        ForeignKey(
            entity = OutfitEntity::class,
            parentColumns = ["id"],
            childColumns = ["outfitId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = OutfitCategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["categoryId"]),
    ],
)
data class OutfitCategoryCrossRef(
    val outfitId: Long,
    val categoryId: Long,
)
