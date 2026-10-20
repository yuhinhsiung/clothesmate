package com.yuhinhsiung.clothesmate.core.database.entity

import kotlinx.serialization.Serializable

import androidx.room.Entity
import androidx.room.Index

/**
 * 标签词典 —— 开发文档 §3.2 / §3.4。
 *
 * 存储预设 + 用户自定义的风格（style）/ 场景（scene）/ 季节（season）标签，
 * 供录入时联想选择、保证标签一致性。
 * 写入前 trim，type + value 唯一；联想建议按使用频率降序。
 */
@Entity(
    tableName = "tag_dictionary",
    indices = [
        // 自定义标签去重：type + value 唯一
        Index(value = ["type", "value"], unique = true),
    ],
)
@Serializable
data class TagDictionaryEntity(
    @androidx.room.PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    /** style / scene / season */
    val type: String,

    val value: String,

    /** 是否系统预设（预设不可删除） */
    val isPreset: Boolean = false,

    /** 使用次数，联想排序按此降序 */
    val usageCount: Int = 0,
)
