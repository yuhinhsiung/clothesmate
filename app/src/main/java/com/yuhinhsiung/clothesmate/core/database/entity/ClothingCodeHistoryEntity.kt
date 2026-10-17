package com.yuhinhsiung.clothesmate.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 编号历史占用 —— 开发文档 §2.1.3「编号不回收复用」的持久化依据（review R-03 修复）。
 *
 * ## 为什么需要单独一张表
 *
 * 原实现的唯一性校验只查 `clothing_item`：**物理删除会连带抹掉唯一的占用证据**。
 * 之后手动填回同一编号能通过校验，对「手动用 500、计数器仍在低位」的衣物，
 * 物理删除后自动发号将来也会重新走到并发出 500。计数器只增不减只能保护
 * 「已经推进过的自动号码」，管不住高位手动号与改号后的旧号。
 *
 * ## 口径
 *
 * - 每件衣物**发放/修改出一个编号时**即登记一行；物理删除**不删**该行；
 * - 校验（手动唯一性 + 自动发号冲突判定）同时看 `clothing_item` 与本表；
 * - [holderId] = 该编号**当前持有人**的衣物 id。编辑时「排除自身」的语义由此表达：
 *   `isCodeAvailable(code, excludeId = X)` 在 `holderId == X` 时放行——用户没换号，
 *   或者改号后想改回来，都属同一件衣服的合法操作；**别的衣物**任何情况下都拿不到这个号。
 * - 手动号码**不推进**计数器（`CodeCounterStore` 语义不变）：本表只做「已占用」判定，
 *   自动序列仍从计数器当前值起走，撞上历史号时按既有规则顺延。
 */
@Entity(
    tableName = "clothing_code_history",
    indices = [
        // 按持有人反查（改号时清理旧号占用属可选优化；此处只服务于排错与将来的一衣多穿）
        Index(value = ["holderId"]),
    ],
)
data class ClothingCodeHistoryEntity(
    /** 纯数字编号（归一后的字符串，与 `clothing_item.code` 同口径） */
    @PrimaryKey val code: String,

    /**
     * 当前持有该编号的衣物 id。
     *
     * ⚠️ **不是数据库级外键**：衣物物理删除后本行仍保留（这正是「编号不回收」的落点），
     * 那时这个 id 指向一行已经不存在的衣物。它只用于表达「编辑时排除自身」，
     * 因此不能当成强引用去 join。
     */
    val holderId: Long,

    /**
     * **首次**登记时间（epoch millis）——不是「最近一次写入时间」。
     * 后续编辑（改备注等）只会更新 [holderId]，不会刷新它。
     */
    val recordedAt: Long,
)