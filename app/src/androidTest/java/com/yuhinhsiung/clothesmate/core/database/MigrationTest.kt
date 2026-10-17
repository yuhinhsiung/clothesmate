package com.yuhinhsiung.clothesmate.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 真迁移测试 —— v1 → v2（[Migrations.MIGRATION_1_2]）。
 *
 * ## 为什么必须测
 *
 * 装了旧版本 App 的设备上跑的是 v1 库；一旦迁移 SQL 与 Room 从实体生成的期望 schema
 * 有半字出入，Room 会在启动时抛 `Migration didn't properly handle …` 直接崩，
 * 而 `fallbackToDestructiveMigration` **救不了**——它只在「没有迁移路径」时兜底。
 * 所以每次改 schema 都要在这里跑一遍：建 v1 库 → 埋数据 → 跑迁移 + 校验 → 断言数据。
 *
 * 期望 schema 由 [MigrationTestHelper] 从 `androidTest` 资产里读取
 * （`app/build.gradle.kts` 已把 `app/schemas` 挂进 assets）。
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ClothesMateDatabase::class.java,
    )

    @Test
    fun migrate1To2_backfillsCodeHistoryIncludingSoftDeleted() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                "INSERT INTO `wardrobe` (`id`,`name`,`note`,`coverImagePath`,`sortOrder`,`createdAt`) " +
                    "VALUES (1,'主卧衣柜',NULL,NULL,0,100)",
            )
            // 正常衣物 No.3 与**软删除**衣物 No.500
            db.execSQL(
                "INSERT INTO `clothing_item` (`id`,`code`,`wardrobeId`,`category`,`colors`," +
                    "`styleTags`,`seasonTags`,`originalImagePath`,`cutoutImagePath`,`brand`,`name`," +
                    "`note`,`wearCount`,`lastWornAt`,`status`,`deletedAt`,`createdAt`,`updatedAt`) " +
                    "VALUES (3,'3',1,'shirt','[\"white\"]','[]','[]',NULL,NULL,NULL,NULL,NULL,0,NULL,'normal',NULL,300,300)",
            )
            db.execSQL(
                "INSERT INTO `clothing_item` (`id`,`code`,`wardrobeId`,`category`,`colors`," +
                    "`styleTags`,`seasonTags`,`originalImagePath`,`cutoutImagePath`,`brand`,`name`," +
                    "`note`,`wearCount`,`lastWornAt`,`status`,`deletedAt`,`createdAt`,`updatedAt`) " +
                    "VALUES (9,'500',1,NULL,'[]','[]','[]',NULL,NULL,NULL,NULL,NULL,0,NULL,'normal',777,900,900)",
            )
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            2,
            true,
            Migrations.MIGRATION_1_2,
        )

        // ① 编号历史：现存行（含软删除行）全部回填为「已占用」
        assertEquals(
            "软删除行的编号也必须进入历史占用表（R-03：物理删除后旧号不得被重新使用）",
            3L,
            holderOf(migrated, "3"),
        )
        assertEquals(9L, holderOf(migrated, "500"))

        migrated.close()
    }

    @Test
    fun migrate1To2_backfillsOutfitItemPositionByInsertionOrder() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                "INSERT INTO `wardrobe` (`id`,`name`,`note`,`coverImagePath`,`sortOrder`,`createdAt`) " +
                    "VALUES (1,'衣柜',NULL,NULL,0,100)",
            )
            listOf(1L, 2L, 3L).forEach { id ->
                db.execSQL(
                    "INSERT INTO `clothing_item` (`id`,`code`,`wardrobeId`,`category`,`colors`," +
                        "`styleTags`,`seasonTags`,`originalImagePath`,`cutoutImagePath`,`brand`,`name`," +
                        "`note`,`wearCount`,`lastWornAt`,`status`,`deletedAt`,`createdAt`,`updatedAt`) " +
                        "VALUES ($id,'$id',1,NULL,'[]','[]','[]',NULL,NULL,NULL,NULL,NULL,0,NULL,'normal',NULL,0,0)",
                )
            }
            db.execSQL(
                "INSERT INTO `outfit` (`id`,`name`,`photoPaths`,`styleTags`,`sceneTags`,`seasonTags`," +
                    "`note`,`wearCount`,`lastWornAt`,`createdAt`,`updatedAt`) " +
                    "VALUES (7,NULL,'[]','[]','[]','[]',NULL,0,NULL,0,0)",
            )
            // 插入顺序 2 → 1 → 3：迁移后的 position 应当正是这个顺序
            listOf(2L, 1L, 3L).forEach { itemId ->
                db.execSQL(
                    "INSERT INTO `outfit_item_cross_ref` (`outfitId`,`clothingItemId`) VALUES (7,$itemId)",
                )
            }
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            2,
            true,
            Migrations.MIGRATION_1_2,
        )

        val ordered = mutableListOf<Long>()
        migrated.query(
            "SELECT `clothingItemId` FROM `outfit_item_cross_ref` " +
                "WHERE `outfitId` = 7 ORDER BY `position` ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) ordered += cursor.getLong(0)
        }
        assertEquals(
            "既有穿搭的衣物顺序按原插入顺序回填（R-06），不能全被压成 0",
            listOf(2L, 1L, 3L),
            ordered,
        )

        migrated.close()
    }

    private fun holderOf(db: androidx.sqlite.db.SupportSQLiteDatabase, code: String): Long? =
        db.query("SELECT `holderId` FROM `clothing_code_history` WHERE `code` = ?", arrayOf(code))
            .use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}