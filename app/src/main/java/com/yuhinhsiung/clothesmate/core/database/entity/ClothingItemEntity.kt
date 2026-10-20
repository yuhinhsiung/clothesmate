package com.yuhinhsiung.clothesmate.core.database.entity

import kotlinx.serialization.Serializable

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.yuhinhsiung.clothesmate.core.model.ClothingStatus

/**
 * 衣物 —— 开发文档 §3.2。
 *
 * 关键口径：
 * - `code` 全局唯一纯数字编号，UNIQUE 约束兜底（§2.1.3）
 * - `deletedAt` 非空即已软删除，正常查询一律过滤（W-13）；编号不回收
 * - `status` 仅 `normal` / `stored`（收起）；收起不参与推荐、选择列表默认隐藏（W-09）
 * - DB 只存相对路径（§3.3），绝不存 `/storage/emulated` 绝对路径
 */
@Entity(
    tableName = "clothing_item",
    foreignKeys = [
        ForeignKey(
            entity = WardrobeEntity::class,
            parentColumns = ["id"],
            childColumns = ["wardrobeId"],
            // 删除衣柜时其衣物另有「转移 / 一并删除」弹层处理（W-12），
            // 不依赖 DB 级联，避免误删；此处保留外键以约束引用完整性
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        // 编号唯一性查询（手动修改实时校验 W-06）
        Index(value = ["code"], unique = true),
        // 衣柜内衣物列表（W-04）
        Index(value = ["wardrobeId"]),
        // 软删除过滤（所有默认查询带 deletedAt IS NULL）
        Index(value = ["deletedAt"]),
        // 类别分布统计 / 类别筛选
        Index(value = ["category"]),
    ],
)
@Serializable
data class ClothingItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    /** 用户可见编号，纯数字（如 "102"），全局唯一 */
    val code: String,

    val wardrobeId: Long,

    /** 类别两级枚举 key（如 "shirt"）；为空 = 待完善（开发文档 §2.2.4） */
    val category: String?,

    /** 预设 12 色枚举 key 的 JSON 数组：black/white/gray/red/orange/yellow/green/blue/purple/brown/pink/multi */
    val colors: List<String>,

    /** 风格标签 JSON 数组 */
    val styleTags: List<String>,

    /** 适合季节 JSON 数组：spring/summer/autumn/winter（§3.4 全站统一词表） */
    val seasonTags: List<String>,

    /** 原始照片相对路径 */
    val originalImagePath: String?,

    /** 抠图 PNG 相对路径；空 = 未抠图（M1 阶段的正常状态） */
    val cutoutImagePath: String?,

    val brand: String?,

    /**
     * 名称（可选）。为空时展示与搜索一律用「主色 + 类别」拼接兜底，如"白色 · 衬衫"。
     * 录入确认页不提供输入，编辑页可自定义命名。
     */
    val name: String?,

    val note: String?,

    /** 累计穿着次数，由穿着记录事务维护（T-05） */
    val wearCount: Int = 0,

    /** 最近穿着日期（epoch millis），未穿过为空 */
    val lastWornAt: Long? = null,

    /** 衣物状态：`normal` 正常 / `stored` 收起（取值常量唯一定义在 core/model/Vocabulary.kt） */
    val status: String = ClothingStatus.NORMAL,

    /** 软删除标记：非空即已删除，正常查询过滤；编号不回收 */
    val deletedAt: Long? = null,

    val createdAt: Long,

    val updatedAt: Long,
)
