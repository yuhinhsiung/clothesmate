package com.yuhinhsiung.clothesmate.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.yuhinhsiung.clothesmate.core.database.entity.OutfitCategoryCrossRef
import com.yuhinhsiung.clothesmate.core.database.entity.OutfitCategoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * 穿搭分类 DAO —— 开发文档 §2.3.2 O-01/O-02/O-05。
 *
 * 删除分类仅解除穿搭与该分类的关联（删 cross ref），穿搭本身保留（O-05）。
 */
@Dao
interface OutfitCategoryDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(category: OutfitCategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCrossRef(ref: OutfitCategoryCrossRef)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRefs(refs: List<OutfitCategoryCrossRef>)

    @Update
    suspend fun update(category: OutfitCategoryEntity)

    @Query("DELETE FROM outfit_category WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM outfit_category WHERE id = :id")
    suspend fun findById(id: Long): OutfitCategoryEntity?

    /** 默认按创建时间升序（sortOrder 取创建序，§2.3.2；暂不支持手动拖拽） */
    @Query("SELECT * FROM outfit_category ORDER BY sortOrder ASC, createdAt ASC")
    fun observeAll(): Flow<List<OutfitCategoryEntity>>

    @Query("SELECT * FROM outfit_category ORDER BY sortOrder ASC, createdAt ASC")
    suspend fun getAll(): List<OutfitCategoryEntity>

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM outfit_category")
    suspend fun maxSortOrder(): Int

    /** 某分类下的穿搭 id（分类内瀑布流 O-03） */
    @Query("SELECT outfitId FROM outfit_category_cross_ref WHERE categoryId = :categoryId")
    fun observeOutfitIds(categoryId: Long): Flow<List<Long>>

    @Query("SELECT outfitId FROM outfit_category_cross_ref WHERE categoryId = :categoryId")
    suspend fun getOutfitIds(categoryId: Long): List<Long>

    /** 删除分类时只解除关联（O-05） */
    @Query("DELETE FROM outfit_category_cross_ref WHERE categoryId = :categoryId")
    suspend fun deleteCrossRefsByCategory(categoryId: Long)

    /** 删除穿搭时清理其分类关联 */
    @Query("DELETE FROM outfit_category_cross_ref WHERE outfitId = :outfitId")
    suspend fun deleteCrossRefsByOutfit(outfitId: Long)

    /** 某分类的穿搭套数 */
    @Query("SELECT COUNT(*) FROM outfit_category_cross_ref WHERE categoryId = :categoryId")
    fun observeOutfitCount(categoryId: Long): Flow<Int>

    /** 全部分类-穿搭关联（响应式）——分类首页套数与封面联查（M1-17） */
    @Query("SELECT outfitId, categoryId FROM outfit_category_cross_ref")
    fun observeAllRefs(): Flow<List<OutfitCategoryCrossRef>>

    /** 某穿搭所属的全部分类 id（穿搭详情 M1-20 / 编辑预填 M1-18） */
    @Query("SELECT categoryId FROM outfit_category_cross_ref WHERE outfitId = :outfitId")
    suspend fun getCategoryIdsForOutfit(outfitId: Long): List<Long>

    /** 同上，响应式版——详情页持续订阅分类关联变化用（review R-11 同类问题） */
    @Query("SELECT categoryId FROM outfit_category_cross_ref WHERE outfitId = :outfitId")
    fun observeCategoryIdsForOutfit(outfitId: Long): Flow<List<Long>>
}
