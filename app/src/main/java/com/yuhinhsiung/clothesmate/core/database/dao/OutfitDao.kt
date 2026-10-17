package com.yuhinhsiung.clothesmate.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.yuhinhsiung.clothesmate.core.database.entity.OutfitEntity
import com.yuhinhsiung.clothesmate.core.database.entity.OutfitItemCrossRef
import kotlinx.coroutines.flow.Flow

/**
 * 穿搭 DAO —— 开发文档 §2.3.2 O-03/O-04、§2.3.4 穿搭详情。
 */
@Dao
interface OutfitDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(outfit: OutfitEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCrossRef(ref: OutfitItemCrossRef)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRefs(refs: List<OutfitItemCrossRef>)

    @Update
    suspend fun update(outfit: OutfitEntity)

    @Query("DELETE FROM outfit WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM outfit WHERE id = :id")
    suspend fun findById(id: Long): OutfitEntity?

    @Query("SELECT * FROM outfit WHERE id = :id")
    fun observeById(id: Long): Flow<OutfitEntity?>

    @Query("SELECT * FROM outfit ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<OutfitEntity>>

    @Query("SELECT * FROM outfit WHERE id IN (:ids) ORDER BY createdAt DESC")
    fun observeByIds(ids: List<Long>): Flow<List<OutfitEntity>>

    @Query("SELECT COUNT(*) FROM outfit")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM outfit")
    suspend fun count(): Int

    /** 删除穿搭时清理其衣物关联（M1-20 连带物理清除判断在 Repository 事务内完成） */
    @Query("DELETE FROM outfit_item_cross_ref WHERE outfitId = :outfitId")
    suspend fun deleteCrossRefsByOutfit(outfitId: Long)

    /**
     * 彻底删除衣物时清理其关联（W-13）。衣物行的 FK 为 NO_ACTION，不先删本表会约束失败。
     * W-13 判定「该衣物是否还被任何现存穿搭引用」用 [getOutfitIdsForItem]，返回空即可物理删。
     */
    @Query("DELETE FROM outfit_item_cross_ref WHERE clothingItemId = :clothingItemId")
    suspend fun deleteCrossRefsByClothingItem(clothingItemId: Long)

    /**
     * 某穿搭关联的衣物 id，**按业务顺序**（review R-06）。
     *
     * 原实现无 `ORDER BY`，顺序取决于 SQLite 扫描计划——保存一次就把选择器里
     * 用户排好的顺序丢掉。现由 `outfit_item_cross_ref.position` 承载。
     */
    @Query(
        """
        SELECT clothingItemId FROM outfit_item_cross_ref
        WHERE outfitId = :outfitId
        ORDER BY position ASC, clothingItemId ASC
        """,
    )
    suspend fun getClothingIds(outfitId: Long): List<Long>

    /** 全部穿搭的衣物关联（推荐引擎衣物可用性判定 §5.2） */
    @Query("SELECT outfitId, clothingItemId, position FROM outfit_item_cross_ref")
    suspend fun getAllItemRefs(): List<OutfitItemCrossRef>

    /** 全部衣物关联（响应式版）——瀑布卡件数 chip、穿搭列表联查（M1-19） */
    @Query("SELECT outfitId, clothingItemId, position FROM outfit_item_cross_ref")
    fun observeItemRefs(): Flow<List<OutfitItemCrossRef>>

    /**
     * 某穿搭的衣物关联（响应式）——详情页持续订阅用（review R-11）。
     *
     * 带上 `position` 是为了详情仍能按业务顺序重建清单；订阅它则让「加/删衣物关联」
     * 也能实时反映到打开的详情页上。
     */
    @Query(
        """
        SELECT outfitId, clothingItemId, position FROM outfit_item_cross_ref
        WHERE outfitId = :outfitId
        ORDER BY position ASC, clothingItemId ASC
        """,
    )
    fun observeItemRefsOf(outfitId: Long): Flow<List<OutfitItemCrossRef>>

    /** 某衣物出现在哪些穿搭里（一衣多穿 W-10 / M4-03） */
    @Query("SELECT outfitId FROM outfit_item_cross_ref WHERE clothingItemId = :clothingItemId")
    suspend fun getOutfitIdsForItem(clothingItemId: Long): List<Long>

    /** 某衣物被几套穿搭引用（M1-13 删除前警示，review R-10） */
    @Query("SELECT COUNT(*) FROM outfit_item_cross_ref WHERE clothingItemId = :clothingItemId")
    suspend fun countOutfitsForItem(clothingItemId: Long): Int

    /**
     * 「今天穿这套」联动（T-05）：穿搭 wearCount +1、lastWornAt = 当天。
     * 与 wear_record 写入、每件衣物 +1 必须同事务提交。
     */
    @Query("UPDATE outfit SET wearCount = wearCount + 1, lastWornAt = :today, updatedAt = :today WHERE id = :id")
    suspend fun incrementWearCount(id: Long, today: Long)

    /** 确认穿着后写封面之外的路径维护：编辑照片列表。List<String> 经 TypeConverter 自动序列化 */
    @Query("UPDATE outfit SET photoPaths = :photoPaths, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updatePhotoPaths(id: Long, photoPaths: List<String>, updatedAt: Long)
}
