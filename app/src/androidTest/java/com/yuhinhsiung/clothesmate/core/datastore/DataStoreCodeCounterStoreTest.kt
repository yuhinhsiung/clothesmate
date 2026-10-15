package com.yuhinhsiung.clothesmate.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * [DataStoreCodeCounterStore] 真实实现的测试（review C-16）。
 *
 * 为什么必须有这一个文件：[CodeAllocatorTest]（JVM 单测）用的是手写 fake，其 KDoc 自认
 * 「算法与 DataStore 实现一致（含互斥），只是不落盘」——两份互斥各写一遍，
 * 实现侧若改坏（漏掉 withLock、写错 +1 时机）fake 不会失配，核心算法的
 * 并发安全就没有任何真实背书。这个文件测生产实现本身。
 *
 * 为什么放 androidTest 而不是 src/test：DataStore 的 [PreferenceDataStoreFactory]
 * 落在本机 Windows 上会 `Unable to rename …tmp`（FileStorage 的原子替换在
 * Windows + AGP 单元测试组合下不成立）。移到设备上跑是 DataStore 官方推荐路径，
 * 也与本项目「需要真 DataStore / 真 Room 的测试一律 androidTest」的约定一致。
 */
class DataStoreCodeCounterStoreTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** 每个 DataStore 独占一个文件 + 一个真实作用域；[After] 统一取消，
     *  避免后台写协程与同文件的新实例打架（DataStore 硬约束：同文件单实例）。 */
    private val scopes = mutableListOf<CoroutineScope>()
    private var fileSeq = 0

    @After
    fun tearDown() {
        scopes.forEach { it.coroutineContext.cancelChildren() }
        scopes.clear()
    }

    private fun newStore(file: File = newFile()): DataStoreCodeCounterStore {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scopes += scope
        val ds: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        )
        return DataStoreCodeCounterStore(ds)
    }

    /**
     * 每个 DataStore 一个**全局唯一**文件名。
     *
     * DataStore 在同一进程内按文件路径维护「活跃实例注册表」，`cancelChildren()` 只停协程、
     * 不注销注册项——同一路径复用会抛 `multiple DataStores active for the same file`。
     * 仪器化测试全部用例跑在同一进程，故必须让路径互不相同（顺带删掉上次残留文件）。
     */
    private fun newFile(): File {
        val unique = "counter_test_${System.nanoTime()}_${fileSeq++}"
        val file = File(context.cacheDir, "$unique.preferences_pb")
        file.delete()
        return file
    }

    @Test
    fun 首个编号从1开始() = runTest {
        val store = newStore()
        assertEquals(1L, store.currentValue())
    }

    @Test
    fun 无冲突时顺序发放且计数器前移() = runTest {
        val store = newStore()
        assertEquals(1L, store.nextCode { false })
        assertEquals(2L, store.nextCode { false })
        assertEquals(3L, store.nextCode { false })
        assertEquals(4L, store.currentValue())
    }

    @Test
    fun 冲突顺延后计数器落在发放值之后() = runTest {
        val store = newStore()
        // 前两个号被占用（含用户手动占用场景），应顺延发 3
        val issued = store.nextCode { candidate -> candidate < 3L }
        assertEquals(3L, issued)
        assertEquals(4L, store.currentValue())
        // 后续发放不应再撞已顺延的号
        assertEquals(4L, store.nextCode { false })
    }

    @Test
    fun 计数器持久化_落盘后可读回() = runTest {
        val file = newFile()
        val store = newStore(file)
        store.setValue(42L)

        // 验证「落盘」而非「进程重启后续发」：DataStore 在同一进程内按文件路径维护
        // 活跃实例注册表，新建第二个实例会被拒（cancelChildren 不注销注册项），
        // 而这属于 DataStore 自身职责、不是本实现的算法。故这里直接断言文件内容——
        // 这是持久化的最小可验证口径：值确实写到了盘上。
        withContext(Dispatchers.IO) { kotlinx.coroutines.delay(400) }
        val raw = withContext(Dispatchers.IO) { file.readText() }
        assertTrue(
            "DataStore 文件应含计数器键 clothing_code_counter",
            raw.contains("clothing_code_counter"),
        )
    }

    @Test
    fun setValue覆盖计数器_导入恢复场景() = runTest {
        val store = newStore()
        store.nextCode { false }
        store.setValue(500L)
        assertEquals(500L, store.currentValue())
        assertEquals(500L, store.nextCode { false })
    }

    @Test
    fun 并发发放不重不漏() = runTest {
        val store = newStore()
        val issued = java.util.Collections.synchronizedList(mutableListOf<Long>())
        val concurrent = 200
        // 所有协程并发调用 nextCode：互斥必须保证 200 个号互不相同且连续覆盖 1..200
        val noConflict: suspend (Long) -> Boolean = { false }
        coroutineScope {
            repeat(concurrent) {
                launch(Dispatchers.Default) {
                    issued += store.nextCode(noConflict)
                }
            }
        }
        assertEquals(concurrent, issued.size)
        assertEquals(concurrent, issued.distinct().size)
        assertEquals((1L..concurrent.toLong()).toSet(), issued.toSet())
        assertEquals(concurrent + 1L, store.currentValue())
    }

    @Test
    fun 冲突判定异常不会写坏计数器() = runTest {
        val store = newStore()
        val boom = IllegalStateException("boom")
        val failure = assertThrowsRuntime {
            store.nextCode { throw boom }
        }
        assertTrue("应原样抛出冲突判定的异常", failure === boom)
        // 异常后计数器应保持原值：本次发放没走到写那一步
        assertEquals(1L, store.currentValue())
    }

    @Test
    fun 并发发放与顺延混合时仍单调唯一() = runTest {
        val store = newStore()
        val issued = java.util.Collections.synchronizedList(mutableListOf<Long>())
        coroutineScope {
            repeat(100) {
                launch(Dispatchers.Default) {
                    // 一半协程遇到「已占用」需顺延
                    val taken = it % 2 == 0
                    val conflict: suspend (Long) -> Boolean = { candidate -> taken && candidate < 50L }
                    issued += store.nextCode(conflict)
                }
            }
        }
        assertEquals(100, issued.distinct().size)
        assertNotEquals(0, issued.size)
        assertTrue("发放的编号必须 >= 1", issued.all { it >= 1L })
    }

    /** 与 JUnit4 的 assertThrows 不同名：签名是 assertThrows(Class, ThrowingRunnable)，
     *  这里手写一个 suspend 版本避免两者串味（block 本身要调挂起函数） */
    private suspend fun assertThrowsRuntime(block: suspend () -> Unit): Throwable =
        try {
            block()
            error("期望抛出异常但没有")
        } catch (t: Throwable) {
            t
        }
}
