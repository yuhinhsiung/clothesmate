package com.yuhinhsiung.clothesmate.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 穿搭分类 —— 开发文档 §3.2 / §2.3.2。
 *
 * 一套穿搭可同时属于多个分类（多分类，§12.1 #6）；
 * 不属于任何分类的穿搭归入「未分类」（无关联记录 = 未分类）。
 */
@Entity(
    tableName = "outfit_category",
    indices = [
        // 分类默认按创建时间升序展示（sortOrder 取创建序，§2.3.2）
        Index(value = ["sortOrder"]),
    ],
)
data class OutfitCategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    val name: String,

    /** 封面对应穿搭 id；为空时展示侧取最新一套穿搭的图兜底（O-02） */
    val coverOutfitId: Long? = null,

    val sortOrder: Int,

    val createdAt: Long,
)
