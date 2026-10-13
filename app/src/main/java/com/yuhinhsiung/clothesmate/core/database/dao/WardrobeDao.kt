package com.yuhinhsiung.clothesmate.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.yuhinhsiung.clothesmate.core.database.entity.WardrobeEntity
import kotlinx.coroutines.flow.Flow

/**
 * 衣柜 DAO —— 开发文档 §2.1.2 W-01/W-02/W-03/W-08/W-12。
 */
@Dao
interface WardrobeDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(wardrobe: WardrobeEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(wardrobes: List<WardrobeEntity>): List<Long>

    @Update
    suspend fun update(wardrobe: WardrobeEntity)

    @Delete
    suspend fun delete(wardrobe: WardrobeEntity)

    @Query("DELETE FROM wardrobe WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM wardrobe WHERE id = :id")
    suspend fun findById(id: Long): WardrobeEntity?

    @Query("SELECT * FROM wardrobe WHERE id IN (:ids)")
    suspend fun findByIds(ids: List<Long>): List<WardrobeEntity>

    @Query("SELECT * FROM wardrobe ORDER BY sortOrder ASC, createdAt ASC")
    fun observeAll(): Flow<List<WardrobeEntity>>

    @Query("SELECT * FROM wardrobe ORDER BY sortOrder ASC, createdAt ASC")
    suspend fun getAll(): List<WardrobeEntity>

    @Query("SELECT COUNT(*) FROM wardrobe")
    suspend fun count(): Int

    /**
     * 各衣柜衣物数量（不含软删除）—— 衣柜列表卡件数展示（W-03）。
     * 返回 wardrobeId → count 映射。
     */
    @Query(
        """
        SELECT wardrobeId, COUNT(*) AS count FROM clothing_item
        WHERE deletedAt IS NULL
        GROUP BY wardrobeId
        """,
    )
    fun observeClothingCounts(): Flow<List<WardrobeClothingCount>>
}

/** 衣柜衣物计数投影（DAO 查询结果） */
data class WardrobeClothingCount(
    val wardrobeId: Long,
    val count: Int,
)
