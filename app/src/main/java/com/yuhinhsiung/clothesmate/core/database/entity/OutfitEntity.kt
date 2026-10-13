package com.yuhinhsiung.clothesmate.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 穿搭 —— 开发文档 §3.2 / §2.3.3。
 *
 * `photoPaths` 支持多张，首图 = 封面，上限 9 张；空数组 = 无照片穿搭。
 * `name` 可空，为空时展示侧按创建日期生成兜底（如「2026-10-11 的穿搭」）。
 */
@Entity(
    tableName = "outfit",
    indices = [
        // 新鲜度打分与非空过滤查询（§5.2）
        Index(value = ["lastWornAt"]),
        Index(value = ["wearCount"]),
        Index(value = ["createdAt"]),
    ],
)
data class OutfitEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    val name: String? = null,

    /** 实际穿搭照片相对路径 JSON 数组，首图 = 封面，上限 9 张 */
    val photoPaths: List<String>,

    /** 风格标签 JSON 数组 */
    val styleTags: List<String>,

    /** 场景标签 JSON 数组（通勤/运动/约会…） */
    val sceneTags: List<String>,

    /** 季节标签 JSON 数组（与衣物同词表 spring/summer/autumn/winter） */
    val seasonTags: List<String>,

    val note: String? = null,

    /** 穿过次数，由穿着记录事务维护（T-05） */
    val wearCount: Int = 0,

    val lastWornAt: Long? = null,

    val createdAt: Long,

    val updatedAt: Long,
)
