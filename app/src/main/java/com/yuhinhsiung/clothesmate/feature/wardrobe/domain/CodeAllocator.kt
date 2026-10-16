package com.yuhinhsiung.clothesmate.feature.wardrobe.domain

import com.yuhinhsiung.clothesmate.core.datastore.CodeCounterStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 编号冲突判定 —— 供 [CodeAllocator] 查询某编号是否已被占用。
 *
 * 抽象成窄接口是为了让发放逻辑可脱离 Room / Android 单测（CLAUDE.md：
 * 「推荐引擎／Repository 必须可直测」）。实现方为衣物 Repository，
 * 数据库层真实覆盖的集成测试另走 Room 内存库。
 */
interface CodeConflictChecker {
    /**
     * @param code 候选编号（十进制字符串）
     * @return true = 已被占用。**必须覆盖软删除行**（编号不回收 + code 全表 UNIQUE，
     *         见 review C-01），否则会出现校验通过但 insert 撞约束。
     */
    suspend fun isCodeTaken(code: String): Boolean
}

/**
 * 编号发放器 —— 开发文档 §2.1.3 / 开发计划 M1-02。
 *
 * 算法：
 * ```text
 * candidate = 计数器当前值
 * while (编号 candidate 已存在):
 *     candidate += 1
 * 发放 candidate
 * 计数器 = candidate + 1
 * ```
 *
 * 语义要点（均有单测，见 CodeAllocatorTest）：
 * - 用户手动指定的号码不占用计数器序列（手动用 500，自动发放仍从原序列继续）
 * - 删除衣物后编号不回收，软删行同样参与冲突判定
 * - 并发发放由 [CodeCounterStore] 内的互斥锁串行化，不重不漏
 */
@Singleton
class CodeAllocator @Inject constructor(
    private val counterStore: CodeCounterStore,
    private val conflictChecker: CodeConflictChecker,
) {
    /** 发放一个编号 */
    suspend fun nextCode(): String = counterStore
        .nextCode { candidate -> conflictChecker.isCodeTaken(candidate.toString()) }
        .toString()

    /**
     * 预览下一个可用编号，**不推进计数器**——录入确认页（M1-14）的编号预填用。
     * 真正保存时仍走 [nextCode]：即使预览后有并发发放，保存时会拿到新的可用号。
     */
    suspend fun peekNextCode(): String {
        var candidate = counterStore.currentValue()
        while (conflictChecker.isCodeTaken(candidate.toString())) {
            candidate += 1
        }
        return candidate.toString()
    }

    /** 当前计数器值，仅供连拍前预览「将发放 {起始}–{结束}」（M4-02） */
    suspend fun peekNextValue(): Long = counterStore.currentValue()
}
