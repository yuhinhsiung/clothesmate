package com.yuhinhsiung.clothesmate.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 编号计数器 —— 开发文档 §2.1.3「衣物编号方案」。
 *
 * 独立递增计数器，**与已发放的编号解耦**。发号算法：
 *
 * ```text
 * candidate = 计数器当前值
 * while (编号 candidate 已存在):
 *     candidate += 1          // 被占用（含用户手动占用的）则顺延
 * 发放 candidate
 * 计数器 = candidate + 1
 * ```
 *
 * 关键语义：
 * - 用户手动指定号码**不影响**计数器序列（手动用 500，下次自动发放仍按原序列继续）
 * - 删除衣物后编号**不回收**（计数器只增不减）
 * - 衣物移入其他衣柜时编号**不变**
 *
 * ⚠️ 「编号是否已存在」的判定必须覆盖**软删除行**（code 全表 UNIQUE 索引同口径），
 * 否则会出现「校验通过但 insert 撞 SQLiteConstraintException」——见 review C-01，
 * 由 [com.yuhinhsiung.clothesmate.core.database.dao.ClothingItemDao.countByCode] 保证。
 */
interface CodeCounterStore {

    /** 当前计数器值（下一个待尝试的候选号）。从未发放过时为 [INITIAL_CODE]。 */
    suspend fun currentValue(): Long

    /** 覆盖计数器（仅导入恢复用，正常业务流程不该调） */
    suspend fun setValue(value: Long)

    /**
     * 原子地执行一次发号。
     *
     * @param existsConflict 判定某编号是否已被占用（**含软删除行**）的挂起函数
     * @return 发放出去的编号
     */
    suspend fun nextCode(existsConflict: suspend (Long) -> Boolean): Long

    companion object {
        /** 首个编号（开发文档 §2.1.3：从 1 开始顺序递增） */
        const val INITIAL_CODE = 1L
    }
}

/** DataStore 的 key。实现类专用，故放在文件私有层级而不是接口的 companion 里。 */
private val KEY_CODE_COUNTER = longPreferencesKey("clothing_code_counter")

/**
 * DataStore 实现。
 *
 * 并发保护：连续发放（M4 快速连拍一次发 10 个）与跨页发放时，两个协程可能同时读到
 * 同一计数器值而发出重复编号。「读 → 判冲突 → 写」跨多步，需应用层互斥保证原子性。
 */
@Singleton
class DataStoreCodeCounterStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : CodeCounterStore {

    private val mutex = Mutex()

    override suspend fun currentValue(): Long =
        dataStore.data.map { it[KEY_CODE_COUNTER] ?: CodeCounterStore.INITIAL_CODE }.first()

    override suspend fun setValue(value: Long) {
        dataStore.edit { it[KEY_CODE_COUNTER] = value }
    }

    override suspend fun nextCode(existsConflict: suspend (Long) -> Boolean): Long =
        mutex.withLock {
            var candidate = currentValue()
            while (existsConflict(candidate)) {
                candidate += 1
            }
            // 计数器前移到 candidate 之后：无论本次是否顺延过，序列都从这继续
            dataStore.edit { it[KEY_CODE_COUNTER] = candidate + 1 }
            candidate
        }
}
