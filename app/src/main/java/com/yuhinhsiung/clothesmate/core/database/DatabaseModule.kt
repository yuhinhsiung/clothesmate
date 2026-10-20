package com.yuhinhsiung.clothesmate.core.database

import android.content.Context
import androidx.room.Room
import com.yuhinhsiung.clothesmate.core.database.dao.BackupDao
import com.yuhinhsiung.clothesmate.core.database.dao.ClothingItemDao
import com.yuhinhsiung.clothesmate.core.database.dao.CodeHistoryDao
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
            // 真迁移优先（§4.4 第 8 条）：v1→v2 无损搬迁编号历史与衣物顺序
            .addMigrations(*Migrations.ALL)
            // M4 起不再允许破坏性降级：所有 schema 变更必须补 Migrations + MigrationTest；
            // 未覆盖的版本直接失败，避免静默清空用户数据（review 附加要求）。
            .build()

    @Provides
    fun provideBackupDao(db: ClothesMateDatabase): BackupDao = db.backupDao()

    @Provides
    fun provideWardrobeDao(db: ClothesMateDatabase): WardrobeDao = db.wardrobeDao()

    @Provides
    fun provideClothingItemDao(db: ClothesMateDatabase): ClothingItemDao = db.clothingItemDao()

    @Provides
    fun provideCodeHistoryDao(db: ClothesMateDatabase): CodeHistoryDao = db.codeHistoryDao()

    @Provides
    fun provideOutfitCategoryDao(db: ClothesMateDatabase): OutfitCategoryDao = db.outfitCategoryDao()

    @Provides
    fun provideOutfitDao(db: ClothesMateDatabase): OutfitDao = db.outfitDao()

    @Provides
    fun provideWearRecordDao(db: ClothesMateDatabase): WearRecordDao = db.wearRecordDao()

    @Provides
    fun provideTagDictionaryDao(db: ClothesMateDatabase): TagDictionaryDao = db.tagDictionaryDao()
}
