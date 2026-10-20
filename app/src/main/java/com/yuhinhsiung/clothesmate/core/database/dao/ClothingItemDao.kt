package com.yuhinhsiung.clothesmate.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.yuhinhsiung.clothesmate.core.database.entity.ClothingItemEntity
import kotlinx.coroutines.flow.Flow

/**
 * 衣物 DAO —— 开发文档 §2.1.2 W-04/W-05/W-06/W-07/W-09/W-13。
 *
 * 软删除口径：默认查询带 `deletedAt IS NULL` 过滤（W-13）。刻意**不**过滤的例外只有
 * [countByCode]（编号全表唯一、不回收）与 [findByIdsIncludingDeleted]（W-13 物理清除判断
 * 与「已删除」占位卡渲染）。
 *
 * 搜索为**片段方法**：W-07 要求空格分词 AND，而 SQLite 无内建 split 函数，
 * 故 Repository 层把 "红 上衣" 拆成 token 后逐段查询再取交集，不在此处拼动态 SQL。
 */
@Dao
interface ClothingItemDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: ClothingItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(items: List<ClothingItemEntity>): List<Long>

    @Update
    suspend fun update(item: ClothingItemEntity)

    @Query("UPDATE clothing_item SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, updatedAt: Long)

    @Query("UPDATE clothing_item SET status = :status, updatedAt = :updatedAt WHERE id IN (:ids) AND deletedAt IS NULL")
    suspend fun updateStatusByIds(ids: List<Long>, status: String, updatedAt: Long)

    @Query("UPDATE clothing_item SET wardrobeId = :wardrobeId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateWardrobe(id: Long, wardrobeId: Long, updatedAt: Long)

    @Query("UPDATE clothing_item SET code = :code, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateCode(id: Long, code: String, updatedAt: Long)

    @Query("UPDATE clothing_item SET cutoutImagePath = :path, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateCutoutPath(id: Long, path: String?, updatedAt: Long)

    /**
     * 「今天穿这套」联动（T-05）：单件衣物 wearCount +1、lastWornAt = 当天。
     * 与 wear_record 写入、穿搭 wearCount +1 必须同事务提交（M3-06）。
     */
    @Query("UPDATE clothing_item SET wearCount = wearCount + 1, lastWornAt = :today, updatedAt = :today WHERE id = :id")
    suspend fun incrementWearCount(id: Long, today: Long)

    /** 穿着确认（M3-06）：lastWornAt 只前进不回拨，补记旧日期不影响“最近穿过”。 */
    @Query(
        """
        UPDATE clothing_item SET
            wearCount = wearCount + 1,
            lastWornAt = CASE
                WHEN lastWornAt IS NULL OR lastWornAt < :wornAt THEN :wornAt
                ELSE lastWornAt
            END,
            updatedAt = CASE WHEN updatedAt < :wornAt THEN :wornAt ELSE updatedAt END
        WHERE id = :id
        """,
    )
    suspend fun recordWear(id: Long, wornAt: Long)

    /** 撤销软删除：清除 deletedAt 标记（W-13 Snackbar 撤销） */
    @Query("UPDATE clothing_item SET deletedAt = NULL, updatedAt = :updatedAt WHERE id = :id")
    suspend fun restore(id: Long, updatedAt: Long)

    /**
     * 彻底删除衣物行（W-13 后半段：软删衣物在所属穿搭全被删除后物理清除）。
     * 调用方须先清理 [OutfitItemDao] 中该衣物的关联——外键为 NO_ACTION，
     * 残留关联会导致约束失败。
     */
    @Query("DELETE FROM clothing_item WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * 按 id 取衣物，**不排除软删除**——用于 W-13 的物理清除判断与「已删除」占位卡渲染
     * （穿搭详情联查需要拿到已软删衣物以显示其原编号）。
     */
    @Query("SELECT * FROM clothing_item WHERE id IN (:ids)")
    suspend fun findByIdsIncludingDeleted(ids: List<Long>): List<ClothingItemEntity>

    @Query("SELECT * FROM clothing_item WHERE id = :id")
    suspend fun findById(id: Long): ClothingItemEntity?

    @Query("SELECT * FROM clothing_item WHERE id IN (:ids) AND deletedAt IS NULL")
    suspend fun findByIds(ids: List<Long>): List<ClothingItemEntity>

    /**
     * 单件观察。⚠️ 刻意**不过滤** `deletedAt`：衣物详情页删除后仍需拿到该行
     * （软删只是打标记，行还在），「已删除」占位卡与 W-13 物理清除判断同源。
     * 调用方若只要有效衣物，自行按 `deletedAt == null` 判断。
     */
    @Query("SELECT * FROM clothing_item WHERE id = :id")
    fun observeById(id: Long): Flow<ClothingItemEntity?>

    /** 衣柜内衣物（W-04），按编号升序 */
    @Query(
        """
        SELECT * FROM clothing_item
        WHERE wardrobeId = :wardrobeId AND deletedAt IS NULL
        ORDER BY CAST(code AS INTEGER) ASC
        """,
    )
    fun observeByWardrobe(wardrobeId: Long): Flow<List<ClothingItemEntity>>

    @Query(
        """
        SELECT * FROM clothing_item
        WHERE deletedAt IS NULL
        ORDER BY CAST(code AS INTEGER) ASC
        """,
    )
    fun observeAll(): Flow<List<ClothingItemEntity>>

    /**
     * **含软删除**的全部衣物（响应式）。
     *
     * 用途：穿搭详情需要「持续订阅」衣物表才谈得上实时——软删 / 撤销要立刻把有效件数
     * 从 1 变 0 再变回 1，改名字也要立刻反映到清单上（review R-11）。
     * 详情不能像列表页那样只订阅未删除行，否则软删那一刻占位卡与件数都不会动。
     */
    @Query("SELECT * FROM clothing_item")
    fun observeAllIncludingDeleted(): Flow<List<ClothingItemEntity>>

    /**
     * 编号唯一性校验（W-06 实时校验 + §2.1.3 计数器冲突顺延）。
     * 传 excludeId 以支持编辑时排除自身。
     *
     * ⚠️ 刻意**不过滤** `deletedAt`：开发文档 §2.1.3 规定「删除衣物后其编号不回收复用」，
     * 且 `code` 是全表 UNIQUE 索引。校验范围必须与 DB 约束一致（都覆盖软删除行），
     * 否则会出现「校验通过但 insert 撞 SQLiteConstraintException」的崩溃路径。
     */
    @Query(
        """
        SELECT COUNT(*) FROM clothing_item
        WHERE code = :code AND id != :excludeId
        """,
    )
    suspend fun countByCode(code: String, excludeId: Long = 0L): Int

    /** 状态切换：收起 / 恢复正常（W-09） */
    @Query("SELECT * FROM clothing_item WHERE status = :status AND deletedAt IS NULL")
    fun observeByStatus(status: String): Flow<List<ClothingItemEntity>>

    /** 全部正常状态衣物（换季模式、兜底抽取、推荐候选用） */
    @Query("SELECT * FROM clothing_item WHERE status = :status AND deletedAt IS NULL")
    suspend fun getByStatus(status: String): List<ClothingItemEntity>

    /**
     * 搜索片段：命中编号（W-07）。分词如下方说明，由 Repository 逐 token 调用后取交集。
     *
     * 所有 LIKE 查询统一 `ESCAPE '\'`（review D-a）：用户输入含 `%`/`_` 时按字面匹配，
     * 不再当作通配符命中全表。Repository 侧负责把 token 里的 `\`/`%`/`_` 转义后传入。
     */
    @Query(
        """
        SELECT * FROM clothing_item
        WHERE deletedAt IS NULL
          AND code LIKE '%' || :query || '%' ESCAPE '\'
        ORDER BY CAST(code AS INTEGER) ASC
        """,
    )
    fun searchByCode(query: String): Flow<List<ClothingItemEntity>>

    /**
     * 搜索片段：命中名称与类别（W-07）。「主色 + 类别」拼接兜底的完整串匹配
     * 在 Repository 侧做内存 contains（review D-j）。
     */
    @Query(
        """
        SELECT * FROM clothing_item
        WHERE deletedAt IS NULL
          AND (
            name LIKE '%' || :query || '%' ESCAPE '\'
            OR category LIKE '%' || :query || '%' ESCAPE '\'
          )
        ORDER BY CAST(code AS INTEGER) ASC
        """,
    )
    fun searchByNameAndCategory(query: String): Flow<List<ClothingItemEntity>>

    /**
     * 搜索片段：命中颜色 key（W-07）。colors 为 JSON 数组字符串（如 `["red","white"]`），
     * 以 `"key"` 带引号包含匹配，避免 `white` 命中 `whitish` 之类误配。
     */
    @Query(
        """
        SELECT * FROM clothing_item
        WHERE deletedAt IS NULL AND colors LIKE '%"' || :colorKey || '"%' ESCAPE '\'
        ORDER BY CAST(code AS INTEGER) ASC
        """,
    )
    fun searchByColorKey(colorKey: String): Flow<List<ClothingItemEntity>>

    /** 搜索片段：命中风格标签（W-07），JSON 数组包含匹配 */
    @Query(
        """
        SELECT * FROM clothing_item
        WHERE deletedAt IS NULL AND styleTags LIKE '%"' || :query || '"%' ESCAPE '\'
        ORDER BY CAST(code AS INTEGER) ASC
        """,
    )
    fun searchByStyleTag(query: String): Flow<List<ClothingItemEntity>>

    /**
     * 搜索片段：命中类别（W-07）。[categoryKeys] 为枚举小类 key 列表——
     * 用户输「上衣」这类大类名时由 Repository 展开成该大类全部小类再查。
     */
    @Query(
        """
        SELECT * FROM clothing_item
        WHERE deletedAt IS NULL AND category IN (:categoryKeys)
        ORDER BY CAST(code AS INTEGER) ASC
        """,
    )
    fun searchByCategoryKeys(categoryKeys: List<String>): Flow<List<ClothingItemEntity>>

    /** 搜索片段：命中季节标签（W-07），JSON 数组包含匹配 */
    @Query(
        """
        SELECT * FROM clothing_item
        WHERE deletedAt IS NULL AND seasonTags LIKE '%"' || :query || '"%' ESCAPE '\'
        ORDER BY CAST(code AS INTEGER) ASC
        """,
    )
    fun searchBySeasonTag(query: String): Flow<List<ClothingItemEntity>>

    /** 按衣柜取全部衣物（转移衣物 W-12） */
    @Query("SELECT * FROM clothing_item WHERE wardrobeId = :wardrobeId AND deletedAt IS NULL")
    suspend fun getByWardrobe(wardrobeId: Long): List<ClothingItemEntity>

    /**
     * 按衣柜取**含软删除**的全部衣物 —— 删除衣柜时的专用查询（review R-01）。
     *
     * `clothing_item.wardrobeId` 的外键是 NO_ACTION，**软删除行的外键依然指向本衣柜**，
     * 漏掉它们会导致 `DELETE FROM wardrobe` 抛 `FOREIGN KEY constraint failed`；
     * 更糟的是「一并删除」路径会先物理删掉正常行、再在删衣柜时失败，留下半截数据。
     */
    @Query("SELECT * FROM clothing_item WHERE wardrobeId = :wardrobeId")
    suspend fun getByWardrobeIncludingDeleted(wardrobeId: Long): List<ClothingItemEntity>

    /** 某衣柜下衣物数（删除衣柜弹层提示 W-12） */
    @Query("SELECT COUNT(*) FROM clothing_item WHERE wardrobeId = :wardrobeId AND deletedAt IS NULL")
    suspend fun countByWardrobe(wardrobeId: Long): Int

    /**
     * 某衣柜下**含软删除**的衣物数（review R-01）。
     *
     * 与 [countByWardrobe] 的分工：列表卡 / 弹层「内有 N 件」展示**可见件数**（不含软删），
     * 「一并删除」的实际影响面用本方法——两个口径必须分开，否则弹层说 2 件、实际删 3 件。
     */
    @Query("SELECT COUNT(*) FROM clothing_item WHERE wardrobeId = :wardrobeId")
    suspend fun countByWardrobeIncludingDeleted(wardrobeId: Long): Int

    /** 类别分布（概览页 M1-22，不含软删除） */
    @Query(
        """
        SELECT category AS category, COUNT(*) AS count FROM clothing_item
        WHERE deletedAt IS NULL AND category IS NOT NULL
        GROUP BY category
        """,
    )
    fun observeCategoryCounts(): Flow<List<CategoryCount>>

    /** 颜色分布（概览页 M1-22）。colors 为 JSON 数组字符串，按包含关系统计各色 key。 */
    @Query(
        """
        SELECT COUNT(*) FROM clothing_item
        WHERE deletedAt IS NULL AND colors LIKE '%"' || :colorKey || '"%' ESCAPE '\'
        """,
    )
    fun observeColorCount(colorKey: String): Flow<Int>

    /** 待完善判定：category 为空即待完善（开发文档 §2.2.4） */
    @Query("SELECT * FROM clothing_item WHERE category IS NULL AND deletedAt IS NULL")
    fun observeIncomplete(): Flow<List<ClothingItemEntity>>
}

/** 类别计数投影（DAO 查询结果） */
data class CategoryCount(
    val category: String?,
    val count: Int,
)
