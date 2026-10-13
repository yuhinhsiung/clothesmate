package com.yuhinhsiung.clothesmate.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.yuhinhsiung.clothesmate.core.database.dao.ClothingItemDao
import com.yuhinhsiung.clothesmate.core.database.dao.OutfitCategoryDao
import com.yuhinhsiung.clothesmate.core.database.dao.OutfitDao
import com.yuhinhsiung.clothesmate.core.database.dao.TagDictionaryDao
import com.yuhinhsiung.clothesmate.core.database.dao.WardrobeDao
import com.yuhinhsiung.clothesmate.core.database.dao.WearRecordDao
import com.yuhinhsiung.clothesmate.core.database.entity.ClothingItemEntity
import com.yuhinhsiung.clothesmate.core.database.entity.OutfitCategoryCrossRef
import com.yuhinhsiung.clothesmate.core.database.entity.OutfitCategoryEntity
import com.yuhinhsiung.clothesmate.core.database.entity.OutfitEntity
import com.yuhinhsiung.clothesmate.core.database.entity.OutfitItemCrossRef
import com.yuhinhsiung.clothesmate.core.database.entity.TagDictionaryEntity
import com.yuhinhsiung.clothesmate.core.database.entity.WardrobeEntity
import com.yuhinhsiung.clothesmate.core.database.entity.WearRecordEntity

/**
 * ClothesMate 本地数据库 —— 开发文档 §3.2 / §12.1 #3。
 *
 * 纯本地存储，无云同步：Room 是唯一事实来源，UI 用 Flow 订阅。
 *
 * 版本与迁移策略：M1–M3 允许 `fallbackToDestructiveMigration`（迭代期用户无敏感数据留存），
 * M4 起必须提供真实 Migration。schema 已导出到 `app/schemas/` 纳入版本控制，
 * 以便后续按名单编写迁移。
 */
@Database(
    entities = [
        WardrobeEntity::class,
        ClothingItemEntity::class,
        OutfitCategoryEntity::class,
        OutfitEntity::class,
        OutfitItemCrossRef::class,
        OutfitCategoryCrossRef::class,
        WearRecordEntity::class,
        TagDictionaryEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(ClothesMateTypeConverters::class)
abstract class ClothesMateDatabase : RoomDatabase() {
    abstract fun wardrobeDao(): WardrobeDao
    abstract fun clothingItemDao(): ClothingItemDao
    abstract fun outfitCategoryDao(): OutfitCategoryDao
    abstract fun outfitDao(): OutfitDao
    abstract fun wearRecordDao(): WearRecordDao
    abstract fun tagDictionaryDao(): TagDictionaryDao

    companion object {
        const val DATABASE_NAME = "clothesmate.db"
    }
}
