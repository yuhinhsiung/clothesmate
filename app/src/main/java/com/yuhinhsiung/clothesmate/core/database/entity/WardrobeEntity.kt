package com.yuhinhsiung.clothesmate.core.database.entity

import kotlinx.serialization.Serializable

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 衣柜 —— 开发文档 §3.2。
 *
 * 一个衣柜对应一个实体储物空间（主卧衣柜、玄关衣帽架、储物箱…）。
 * 衣物必须归属于某个衣柜；首启无任何衣柜，须用户自行创建（§12.1 #10）。
 */
@Entity(
    tableName = "wardrobe",
    indices = [
        // 拖拽排序后按 sortOrder 取列表（W-08）
        Index(value = ["sortOrder"]),
    ],
)
@Serializable
data class WardrobeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    val name: String,

    /** 备注（位置描述等） */
    val note: String?,

    /** 封面图相对路径，如 `wardrobe_cover/{id}.jpg`（§3.3） */
    val coverImagePath: String?,

    /** 排序权重：长按拖拽持久化（W-08） */
    val sortOrder: Int,

    val createdAt: Long,
)
