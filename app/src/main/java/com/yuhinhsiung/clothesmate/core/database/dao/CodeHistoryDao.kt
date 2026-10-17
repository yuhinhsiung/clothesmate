package com.yuhinhsiung.clothesmate.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yuhinhsiung.clothesmate.core.database.entity.ClothingCodeHistoryEntity

/**
 * 编号历史占用 DAO —— 开发文档 §2.1.3、review R-03。
 *
 * 一个编号一旦发放过就永远留痕（物理删除也不清），详见
 * [ClothingCodeHistoryEntity] 的表说明。
 */
@Dao
interface CodeHistoryDao {

    /**
     * 登记（或刷新）一个编号的持有人。
     *
     * **不覆盖已存在的行**：`recordedAt` 的语义是「这个号**首次**被登记的时间」，
     * 用 REPLACE 的话，连「只改备注」的编辑都会把时间刷成今天（review D-01 抓出的口径不一致）。
     * 因此拆成两步——不存在才插入（带首次时间），存在则只更新持有人。
     * 两步由调用方包在同一个事务里（`createItem` / `updateItem` 本就在 `withTransaction` 内）。
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entry: ClothingCodeHistoryEntity)

    /**
     * 只改持有人，保留首次登记时间。
     *
     * 正常情况下同一编号的持有人不会变（号被占就不放），这一步是**不变量的兜底**：
     * 万一库里有 holder 陈旧的行，写回时顺手修正成当前持有人。
     */
    @Query("UPDATE clothing_code_history SET holderId = :holderId WHERE code = :code")
    suspend fun updateHolder(code: String, holderId: Long)

    /** 该编号**首次**登记的时间；从未登记过返回 null */
    @Query("SELECT recordedAt FROM clothing_code_history WHERE code = :code")
    suspend fun recordedAt(code: String): Long?

    /** 该编号当前（或曾经最后）的持有人 id；从未登记过返回 null */
    @Query("SELECT holderId FROM clothing_code_history WHERE code = :code")
    suspend fun holderOf(code: String): Long?

    /**
     * 撤回一次**未完成的建档**留下的编号占用（review R-14 场景 A）。
     *
     * `holderId` 精确指认「这个号当前记在谁名下」，只有与本次回滚掉的那一行吻合时才删——
     * 别人占用的历史行不受影响，因此不会重新引入 R-03 的「物理删除后旧号可复用」漏洞。
     *
     * 触发场景：建档事务已提交（行 + 历史一起写入），随后图片归位失败、补偿把行删掉。
     * 此时这个号既没有真实衣物、也不该被永久认领——不撤回的话，**同一个草稿重试会被
     * 「编号已被使用」拦住**（用户什么都没得到，号却没了）。
     */
    @Query("DELETE FROM clothing_code_history WHERE code = :code AND holderId = :holderId")
    suspend fun deleteIfHeldBy(code: String, holderId: Long): Int

    @Query("SELECT COUNT(*) FROM clothing_code_history WHERE code = :code")
    suspend fun countByCode(code: String): Int

    /** 历史占用总数（导入 / 排错用） */
    @Query("SELECT COUNT(*) FROM clothing_code_history")
    suspend fun count(): Int
}