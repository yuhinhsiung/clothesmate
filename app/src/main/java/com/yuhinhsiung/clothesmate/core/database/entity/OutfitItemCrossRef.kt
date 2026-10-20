package com.yuhinhsiung.clothesmate.core.database.entity

import kotlinx.serialization.Serializable

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * 穿搭 - 衣物关联 —— 开发文档 §3.2。
 *
 * 联合主键 (outfitId, clothingItemId)。
 * 衣物软删除（W-13）后关联**保留**，穿搭详情联查时该衣物渲染为「已删除」占位卡（可显示原编号）。
 * 删除穿搭时清理本表对应行；若因此软删除衣物不再被任何现存穿搭引用，该衣物物理删除（W-13）。
 *
 * ## [position]（review R-06 修复）
 *
 * 衣物在穿搭里的**业务顺序**（0 起）。M1-18 的选择器给每件衣物标了序号、确认页横滑列按
 * 选择顺序排，但本表原先只有联合主键、`getClothingIds()` 也没有 `ORDER BY`——保存一次
 * 顺序就丢，详情页变成按 SQLite 扫描序显示。现由本列承载顺序，查询一律按它排序；
 * 详情联查（`findByIdsIncludingDeleted` 的 `IN (...)` 不保序）也按 id 列表重建顺序。
 *
 * 列默认值 0 是为 v1→v2 迁移的 `ALTER TABLE ... ADD COLUMN` 服务（SQLite 要求
 * NOT NULL 新列带默认值）；迁移里同时按 rowid 回填既有行的真实顺序。
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
@Serializable
data class OutfitItemCrossRef(
    val outfitId: Long,
    val clothingItemId: Long,
    /** 业务顺序，0 起；详情 / 编辑预填一律按它升序 */
    @ColumnInfo(defaultValue = "0")
    val position: Int = 0,
)