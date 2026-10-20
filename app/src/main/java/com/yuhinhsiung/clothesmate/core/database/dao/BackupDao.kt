package com.yuhinhsiung.clothesmate.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.yuhinhsiung.clothesmate.core.database.entity.ClothingCodeHistoryEntity
import com.yuhinhsiung.clothesmate.core.database.entity.ClothingItemEntity
import com.yuhinhsiung.clothesmate.core.database.entity.OutfitCategoryCrossRef
import com.yuhinhsiung.clothesmate.core.database.entity.OutfitCategoryEntity
import com.yuhinhsiung.clothesmate.core.database.entity.OutfitEntity
import com.yuhinhsiung.clothesmate.core.database.entity.OutfitItemCrossRef
import com.yuhinhsiung.clothesmate.core.database.entity.TagDictionaryEntity
import com.yuhinhsiung.clothesmate.core.database.entity.WardrobeEntity
import com.yuhinhsiung.clothesmate.core.database.entity.WearRecordEntity

/**
 * 导出用的全量只读查询。M4-05 的 zip 备份在事务快照内读取这些表；
 * 导入走各 Repository 的写入口，不直接复用本 DAO。
 */
@Dao
interface BackupDao {
    @Query("SELECT * FROM wardrobe ORDER BY id")
    suspend fun wardrobes(): List<WardrobeEntity>

    @Query("SELECT * FROM clothing_item WHERE deletedAt IS NULL ORDER BY id")
    suspend fun clothing(): List<ClothingItemEntity>

    @Query("SELECT * FROM clothing_item WHERE code = :code LIMIT 1")
    suspend fun clothingByCode(code: String): ClothingItemEntity?

    /** 全部编号（含软删除）；导入编号分配用。 */
    @Query("SELECT code FROM clothing_item")
    suspend fun clothingCodes(): List<String>

    @Query("SELECT * FROM clothing_code_history ORDER BY code")
    suspend fun codeHistory(): List<ClothingCodeHistoryEntity>

    @Query("SELECT * FROM outfit_category ORDER BY id")
    suspend fun outfitCategories(): List<OutfitCategoryEntity>

    @Query("SELECT * FROM outfit ORDER BY id")
    suspend fun outfits(): List<OutfitEntity>

    @Query("SELECT * FROM outfit_item_cross_ref ORDER BY outfitId, position")
    suspend fun outfitItems(): List<OutfitItemCrossRef>

    @Query("SELECT * FROM outfit_category_cross_ref")
    suspend fun outfitCategoryRefs(): List<OutfitCategoryCrossRef>

    @Query("SELECT * FROM wear_record ORDER BY id")
    suspend fun wearRecords(): List<WearRecordEntity>

    @Query("SELECT * FROM tag_dictionary ORDER BY id")
    suspend fun tags(): List<TagDictionaryEntity>
}
