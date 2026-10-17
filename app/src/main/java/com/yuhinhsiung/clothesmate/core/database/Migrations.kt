package com.yuhinhsiung.clothesmate.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 真实迁移 —— 开发文档 §4.4 第 8 条 / 开发计划 M1-01。
 *
 * M1–M3 允许 `fallbackToDestructiveMigration`（迭代期无敏感数据留存），但**有真迁移时
 * 一律优先走它**：本轮 review 的两处 schema 变更（R-03 编号历史、R-06 衣物顺序）
 * 都能无损搬迁既有数据，没有理由让用户丢掉衣柜。
 *
 * 迁移 SQL 与 Room 从实体生成的期望 schema 必须逐字对齐（表名、列名、NOT NULL、
 * 默认值、索引名），否则启动时会报 `Migration didn't properly handle`。
 * 校验方式：跑一次 `assembleDebug` 后比对 `app/schemas/…/2.json` 与本文件的 DDL。
 */
object Migrations {

    /**
     * v1 → v2：
     * ① 新建 `clothing_code_history`（R-03：编号不回收的持久化依据），
     *    并把现有衣物的编号**全量回填**为历史占用——否则老衣服物理删除后编号会「解禁」；
     * ② `outfit_item_cross_ref` 增 `position`（R-06：衣物业务顺序），
     *    按 rowid 顺序回填既有行，让已存下来的穿搭也拿到稳定顺序。
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // ① 编号历史占用
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `clothing_code_history` (
                    `code` TEXT NOT NULL,
                    `holderId` INTEGER NOT NULL,
                    `recordedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`code`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_clothing_code_history_holderId` " +
                    "ON `clothing_code_history` (`holderId`)",
            )
            // 回填：既有衣物（含软删除行）的编号一律视为已占用
            db.execSQL(
                """
                INSERT OR IGNORE INTO `clothing_code_history` (`code`, `holderId`, `recordedAt`)
                SELECT `code`, `id`, `createdAt` FROM `clothing_item`
                """.trimIndent(),
            )

            // ② 衣物业务顺序
            db.execSQL(
                "ALTER TABLE `outfit_item_cross_ref` " +
                    "ADD COLUMN `position` INTEGER NOT NULL DEFAULT 0",
            )
            // 回填：按 rowid（= 当初的插入顺序）在各自穿搭内编号
            db.execSQL(
                """
                UPDATE `outfit_item_cross_ref`
                SET `position` = (
                    SELECT COUNT(*) FROM `outfit_item_cross_ref` AS `prev`
                    WHERE `prev`.`outfitId` = `outfit_item_cross_ref`.`outfitId`
                      AND `prev`.`rowid` < `outfit_item_cross_ref`.`rowid`
                )
                """.trimIndent(),
            )
        }
    }

    /** 全部迁移，按序注册到 [DatabaseModule] */
    val ALL = arrayOf(MIGRATION_1_2)
}