package com.yuhinhsiung.clothesmate.core.database

import android.content.Context
import androidx.room.Room
import com.yuhinhsiung.clothesmate.core.database.dao.ClothingItemDao
import com.yuhinhsiung.clothesmate.core.database.dao.OutfitCategoryDao
import com.yuhinhsiung.clothesmate.core.database.dao.OutfitDao
import com.yuhinhsiung.clothesmate.core.database.dao.TagDictionaryDao
import com.yuhinhsiung.clothesmate.core.database.dao.WardrobeDao
import com.yuhinhsiung.clothesmate.core.database.dao.WearRecordDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 数据库注入 —— 开发计划 P0-04 / 开发文档 §4.2。
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ClothesMateDatabase =
        Room.databaseBuilder(
            context,
            ClothesMateDatabase::class.java,
            ClothesMateDatabase.DATABASE_NAME,
        )
            // M1–M3 迭代期：schema 变更直接重建。M4 起必须改为真实 Migration，
            // 以免用户丢数据（开发计划 M1-01 / §4.4 第 8 条）。
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideWardrobeDao(db: ClothesMateDatabase): WardrobeDao = db.wardrobeDao()

    @Provides
    fun provideClothingItemDao(db: ClothesMateDatabase): ClothingItemDao = db.clothingItemDao()

    @Provides
    fun provideOutfitCategoryDao(db: ClothesMateDatabase): OutfitCategoryDao = db.outfitCategoryDao()

    @Provides
    fun provideOutfitDao(db: ClothesMateDatabase): OutfitDao = db.outfitDao()

    @Provides
    fun provideWearRecordDao(db: ClothesMateDatabase): WearRecordDao = db.wearRecordDao()

    @Provides
    fun provideTagDictionaryDao(db: ClothesMateDatabase): TagDictionaryDao = db.tagDictionaryDao()
}
