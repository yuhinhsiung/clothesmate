package com.yuhinhsiung.clothesmate.feature.wardrobe.domain

import com.yuhinhsiung.clothesmate.core.datastore.CodeCounterStore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 编号发放测试 —— 开发计划 M1-02 / 开发文档 §2.1.3 要求的 6 个场景。
 *
 * 纯 JVM 单测：计数器与冲突判定都用内存 fake，不依赖 Android / Room，
 * 因此可在 `src/test` 下直跑（CLAUDE.md：「推荐引擎与 Repository 必须可直测」）。
 * 「校验覆盖软删行」与 code 全表 UNIQUE 的一致性，另由
 * [com.yuhinhsiung.clothesmate.core.database.dao.ClothingItemDaoTest] 用 Room 内存库保障。
 */
class CodeAllocatorTest {

    /** 内存计数器：算法与 DataStore 实现一致（含互斥），只是不落盘 */
    private class FakeCounterStore(initial: Long) : CodeCounterStore {
        private val mutex = Mutex()
        private var value: Long = initial

        /** 已被占用的编号。软删行也留在这里（编号不回收，review C-01 口径） */
        val taken = mutableSetOf<String>()

        override suspend fun currentValue(): Long = mutex.withLock { value }

        override suspend fun setValue(newValue: Long) = mutex.withLock {
            value = newValue
        }

        override suspend fun nextCode(existsConflict: suspend (Long) -> Boolean): Long =
            mutex.withLock {
                var candidate = value
                while (existsConflict(candidate)) candidate += 1
                value = candidate + 1
                candidate
            }
    }

    private val counter = FakeCounterStore(CodeCounterStore.INITIAL_CODE)

    private val allocator = CodeAllocator(
        counterStore = counter,
        conflictChecker = object : CodeConflictChecker {
            override suspend fun isCodeTaken(code: String): Boolean = code in counter.taken
        },
    )

    // ── 场景 1：从 1 起始顺序递增 ───────────────────────────────

    @Test
    fun `场景1 从1起始顺序递增`() = runTest {
        assertEquals("1", allocator.nextCode())
        assertEquals("2", allocator.nextCode())
        assertEquals("3", allocator.nextCode())
        assertEquals("计数器跟随前移", 4L, counter.currentValue())
    }

    // ── 场景 2：计数器 98 且 98 被手动占用 → 发 99，计数器 100 ──

    @Test
    fun `场景2 计数器98且98被手动占用则发99计数器100`() = runTest {
        counter.setValue(98L)
        counter.taken += "98"

        assertEquals("99", allocator.nextCode())

        assertEquals(100L, counter.currentValue())
    }

    @Test
    fun `场景2b 连续占用则连续顺延`() = runTest {
        counter.setValue(98L)
        counter.taken += "98"
        counter.taken += "99"
        counter.taken += "100"

        assertEquals("101", allocator.nextCode())

        assertEquals(102L, counter.currentValue())
    }

    // ── 场景 3：用户手动占用 500 后，自动发放仍按原序列 ────────

    @Test
    fun `场景3 用户手动占用500后自动发放仍按计数器原序列`() = runTest {
        counter.setValue(5L)
        // 用户手动占了远离计数器位置的 500
        counter.taken += "500"

        assertEquals("5", allocator.nextCode())
        assertEquals("6", allocator.nextCode())
        assertEquals("7", allocator.nextCode())
        assertEquals("不因 500 而跳号", 8L, counter.currentValue())
    }

    // ── 场景 4：删除衣物后编号不回收 ────────────────────────────

    @Test
    fun `场景4 删除衣物后编号不回收`() = runTest {
        assertEquals("1", allocator.nextCode())
        assertEquals("2", allocator.nextCode())

        // 软删编号 1：仍留在 taken 中（软删行参与唯一性判定）
        counter.taken += "1"

        assertEquals("3", allocator.nextCode())
        assertTrue("软删的 1 仍视为已占用", "1" in counter.taken)
    }

    @Test
    fun `场景4b 已删除的编号不会回到发放池`() = runTest {
        allocator.nextCode() // 1
        counter.taken += "1"
        // review §6.3 弱断言修正：此前返回值弃置、只断言自己刚插入的 taken（恒真）。
        // 现在捕获发放结果并断言：不含已删的 1，且彼此不重复
        val issued = mutableListOf<String>()
        repeat(10) { issued += allocator.nextCode() }
        assertFalse("已删除的编号 1 不应再发放", "1" in issued)
        assertEquals("发放序列不重复", 10, issued.toSet().size)
    }

    // ── 场景 5：移入其他衣柜，编号不变 ──────────────────────────
    // 编号与衣柜无关：Repository.moveToWardrobe 只改 wardrobeId，
    // 由 ClothingRepositoryTest 覆盖，此处不重复。

    // ── 场景 6：并发连续发放不重复 ─────────────────────────────

    @Test
    fun `场景6 并发发放不重复且连续`() = runTest {
        counter.setValue(1L)
        // 计数器内部靠互斥锁串行化「读→判冲突→写」，并发发放才不重不漏
        val codes = kotlinx.coroutines.coroutineScope {
            (1..200).map { async { allocator.nextCode() } }.awaitAll()
        }

        assertEquals(200, codes.size)
        assertEquals("编号不得重复", 200, codes.distinct().size)
        assertEquals(
            "互斥正确时编号应连续无洞",
            (1..200).map { it.toString() },
            codes.map { it.toInt() }.sorted().map { it.toString() },
        )
    }

    // ── 顺延边界 ────────────────────────────────────────────────

    @Test
    fun `起始值即被占用时顺延到首个空闲号`() = runTest {
        counter.setValue(1L)
        counter.taken += "1"
        counter.taken += "2"
        counter.taken += "3"
        assertEquals("4", allocator.nextCode())
    }
}
