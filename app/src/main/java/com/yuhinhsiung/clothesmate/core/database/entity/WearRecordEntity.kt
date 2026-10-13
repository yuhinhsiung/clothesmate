package com.yuhinhsiung.clothesmate.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 穿着记录 —— 开发文档 §3.2 / §2.4.2 T-05。
 *
 * `outfitId` 可空（补记场景：当日未从穿搭中选）。
 * **同一天允许多条记录**（§12.1 #13）：每次确认各写一条、穿搭与衣物 wearCount 各 +1，不回滚。
 */
@Entity(
    tableName = "wear_record",
    foreignKeys = [
        ForeignKey(
            entity = OutfitEntity::class,
            parentColumns = ["id"],
            childColumns = ["outfitId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        // 日历视图按日期查当日记录（T-06）
        Index(value = ["date"]),
        Index(value = ["outfitId"]),
    ],
)
data class WearRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    /** 日期 yyyy-MM-dd */
    val date: String,

    val outfitId: Long? = null,

    /** 当日行程标签 JSON 数组（快照） */
    val scheduleTags: List<String>,

    /** 当日天气快照 JSON；为空表示当日未取到天气 */
    val weatherJson: String? = null,

    /** 当日心情 / 备注 */
    val note: String? = null,

    val createdAt: Long,
)
