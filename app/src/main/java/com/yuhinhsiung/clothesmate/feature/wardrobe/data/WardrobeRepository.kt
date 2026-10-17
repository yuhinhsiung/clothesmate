package com.yuhinhsiung.clothesmate.feature.wardrobe.data

import androidx.room.withTransaction
import com.yuhinhsiung.clothesmate.core.database.dao.WardrobeDao
import com.yuhinhsiung.clothesmate.core.database.entity.WardrobeEntity
import com.yuhinhsiung.clothesmate.core.model.Wardrobe
import com.yuhinhsiung.clothesmate.core.model.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 衣柜 Repository —— 开发文档 §2.1.2 W-01/W-02/W-03/W-08/W-12。
 *
 * 单 app module 单数据源：Room 是唯一事实来源，UI 经 Flow 订阅。
 */
interface WardrobeRepository {
    /** 全部衣柜，按 sortOrder 升序；件数实时刷新（W-03） */
    fun observeWardrobes(): Flow<List<Wardrobe>>

    suspend fun getWardrobe(id: Long): Wardrobe?

    suspend fun getAllWardrobes(): List<Wardrobe>

    suspend fun wardrobeCount(): Int

    /** 新建（W-01）。返回新衣柜 id，调用方用于「保存后自动选中并回传」。 */
    suspend fun createWardrobe(
        name: String,
        note: String?,
        coverImagePath: String?,
    ): Long

    /** 编辑（W-02）。名称必填由调用方（UI）校验，此处信任入参并兜底 trim。 */
    suspend fun updateWardrobe(
        id: Long,
        name: String,
        note: String?,
        coverImagePath: String?,
    )

    /**
     * 拖拽排序持久化（W-08）：按传入顺序重写 sortOrder。
     * 用索引重排而非交换，保证任意拖动结果都能落成连续权重。
     */
    suspend fun persistOrder(orderedIds: List<Long>)

    /** 删除衣柜本身（衣物去向由调用方先经 [deleteWithDestiny] 处理，见 W-12） */
    suspend fun deleteWardrobe(id: Long)

    /** 某衣柜衣物数（删除弹层提示；**不含软删除**，展示口径） */
    suspend fun clothingCount(id: Long): Int

    /**
     * 某衣柜**含软删除**的衣物数（review R-01）。
     *
     * 与 [clothingCount] 分开：弹层「内有 N 件衣物」展示可见件数（不含软删），
     * 「一并删除」的实际影响面更大，UI 需要据此补一句「含已删除条目」。
     */
    suspend fun clothingCountIncludingDeleted(id: Long): Int

    /**
     * 删除衣柜并处理衣物去向（W-12 / UI 文档 §7.2）。
     *
     * ⚠️ 语义见 [DefaultWardrobeRepository] 上的长篇说明：两条去向一条是转移（可逆）、
     * 一条是物理删除（不可逆），且删衣柜行前**必须**先清光衣物（外键会拦）。
     *
     * @param destiny [WardrobeDeleteDestiny] 转移 / 一并删除
     * @param onImagesDeleted 图片清理回调。在衣柜行删除**成功后**调用，
     *       保证只清「确实随衣柜一起删掉了」的衣物；Repository 不直接碰文件 IO，
     *       便于测试替换为收件箱。
     * @return 实际处理的衣物数，用于 Snackbar 文案（「18 件衣物已移到 主卧大衣柜」）
     */
    suspend fun deleteWithDestiny(
        wardrobeId: Long,
        destiny: WardrobeDeleteDestiny,
        onImagesDeleted: suspend (List<Long>) -> Unit = {},
    ): Int
}

/**
 * W-12 衣物去向。删除衣柜时由用户选择：
 * - [TransferTo] 转移到其他衣柜（仅剩一个衣柜时调用方自动填该柜）
 * - [DeleteAll] 一并删除其中衣物——**物理删除，不可恢复**（UI 文档 §7.2 的警示文案）
 */
sealed interface WardrobeDeleteDestiny {
    data class TransferTo(val targetWardrobeId: Long) : WardrobeDeleteDestiny
    data object DeleteAll : WardrobeDeleteDestiny
}

@Singleton
class DefaultWardrobeRepository @Inject constructor(
    private val db: com.yuhinhsiung.clothesmate.core.database.ClothesMateDatabase,
    private val wardrobeDao: WardrobeDao,
    private val clothingRepository: ClothingRepository,
) : WardrobeRepository {

    override fun observeWardrobes(): Flow<List<Wardrobe>> =
        combine(
            wardrobeDao.observeAll(),
            wardrobeDao.observeClothingCounts(),
        ) { entities, counts ->
            val countMap = counts.associate { it.wardrobeId to it.count }
            entities.map { it.toDomain(clothingCount = countMap[it.id] ?: 0) }
        }

    /**
     * 单件查询。件数实时算，**不返回 0 兜底**——选择衣柜弹层（M1-08）与删除弹层
     * 都展示件数，返回 0 会误导用户。
     */
    override suspend fun getWardrobe(id: Long): Wardrobe? {
        val entity = wardrobeDao.findById(id) ?: return null
        return entity.toDomain(clothingCount = clothingCountOf(id))
    }

    /**
     * 全部衣柜（一次性快照，非观察）。
     *
     * 同样带件数：选择衣柜弹层、导入导出等不走 Flow 的调用方也要正确件数。
     * 一次 GROUP BY 查完再组装，避免 N+1 查询。
     */
    override suspend fun getAllWardrobes(): List<Wardrobe> {
        val snapshot = wardrobeDao.getAll()
        if (snapshot.isEmpty()) return emptyList()
        val countMap = wardrobeDao.observeClothingCounts().first()
            .associate { it.wardrobeId to it.count }
        return snapshot.map { it.toDomain(clothingCount = countMap[it.id] ?: 0) }
    }

    override suspend fun wardrobeCount(): Int = wardrobeDao.count()

    /** 件数实时查询（不含软删除）。review D-e：定向 COUNT，不走全表 GROUP BY */
    private suspend fun clothingCountOf(id: Long): Int = wardrobeDao.countByWardrobe(id)

    override suspend fun createWardrobe(
        name: String,
        note: String?,
        coverImagePath: String?,
    ): Long {
        val now = System.currentTimeMillis()
        // sortOrder 取当前最大值 +1，追加到末尾；拖拽时再由 persistOrder 重排
        val nextOrder = wardrobeDao.maxSortOrder() + 1
        return wardrobeDao.insert(
            WardrobeEntity(
                name = name.trim(),
                note = note?.trim()?.takeIf { it.isNotEmpty() },
                coverImagePath = coverImagePath,
                sortOrder = nextOrder,
                createdAt = now,
            ),
        )
    }

    override suspend fun updateWardrobe(
        id: Long,
        name: String,
        note: String?,
        coverImagePath: String?,
    ) {
        val existing = wardrobeDao.findById(id) ?: return
        wardrobeDao.update(
            existing.copy(
                name = name.trim(),
                note = note?.trim()?.takeIf { it.isNotEmpty() },
                coverImagePath = coverImagePath,
            ),
        )
    }

    /**
     * 拖拽排序持久化（W-08）：按传入顺序重写 sortOrder。
     *
     * review D-d：逐条定向 UPDATE（不再 findById+copy+update 读改写）。
     * 重排是**全覆盖写**——中途失败会留下局部旧序，但下次拖拽会整体重写，
     * 可自愈；列表本身仍按 (sortOrder, createdAt) 双键稳定排序，不会乱。
     */
    override suspend fun persistOrder(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id ->
            wardrobeDao.updateSortOrder(id, index)
        }
    }

    override suspend fun deleteWardrobe(id: Long) {
        wardrobeDao.deleteById(id)
    }

    override suspend fun clothingCount(id: Long): Int = wardrobeDao.countByWardrobe(id)

    override suspend fun clothingCountIncludingDeleted(id: Long): Int =
        clothingRepository.countItemsInWardrobeIncludingDeleted(id)

    /**
     * 删除衣柜并处理衣物去向（W-12 / UI 文档 §7.2）。
     *
     * ## 两条去向的真实语义
     *
     * - **转移到其他衣柜**：只改 `wardrobeId`，编号、图片、穿着次数一律不变
     *   （§2.1.3「衣物移入其他衣柜时编号不变」）。可逆。
     * - **一并删除**：**物理删除**衣物。UI 文档 §7.2 给这个选项的警示文案是
     *   「此操作不可恢复」，开发文档 W-13 对彻底删除的定义也是「行 + 关联 + 图片文件」，
     *   所以这里不能退化成软删除。
     *
     * ## 必须处理**软删除行**（review R-01 修复）
     *
     * `clothing_item.wardrobeId` 的外键声明是 `onDelete = NO_ACTION`，看字面像
     * 「什么都不做、随便删父行」，**但 SQLite 的 NO_ACTION 仍然会做外键检查**
     * （它与 RESTRICT 的差别只在检查时机：NO_ACTION 推迟到语句结束，RESTRICT 立即）。
     * 所以只要还有任何衣物的 `wardrobeId` 指着这个衣柜，`DELETE FROM wardrobe`
     * 就抛 `SQLiteConstraintException: FOREIGN KEY constraint failed`。
     * 这正是 M1-04 测试首次跑起来时抓到的真 bug——首版把「一并删除」做成软删除，
     * 行还赖在表里，衣柜必然删不掉。
     *
     * 但「任何衣物」**包含软删除行**：它们的 `deletedAt` 非空、不出现在列表里、
     * 也不计入「内有 N 件」的展示件数，外键却照样指着本衣柜。M1 首版用的
     * `getItemsInWardrobe()` 带 `deletedAt IS NULL`，于是「衣柜里只剩一件软删衣物、
     * 弹层显示 0 件」时删除必然失败；混合数据下「一并删除」还会先物理删掉正常行、
     * 再在删衣柜那步失败，用户看到失败但数据已经少了一半。
     * 现在统一用 [ClothingRepository.getItemsInWardrobeIncludingDeleted]：
     * 转移时连同软删行一起转移（`deletedAt` 原样保留），彻底删除时一并清理关联与行。
     *
     * ## 原子性（review R-13 同源要求）
     *
     * 数据库步骤全部包在 `db.withTransaction { }` 里：逐件处理衣物 + 删衣柜行要么
     * 全部生效、要么全部回滚。文件清理**放在提交之后**——回滚与否都不该留下孤图，
     * 也不该在事务里做 IO。
     *
     * （旧注释记的「Room 2.8.5 的 withTransaction 在 Kotlin 侧解析不了」已复核为
     * **不成立**：`androidx.room.withTransaction` 在本工程 Kotlin 2.4.20 + Room 2.8.5
     * 下正常编译，补偿式顺序执行因此不再需要。）
     */
    override suspend fun deleteWithDestiny(
        wardrobeId: Long,
        destiny: WardrobeDeleteDestiny,
        onImagesDeleted: suspend (List<Long>) -> Unit,
    ): Int {
        // 防御自毁：目标就是被删的衣柜时，衣物无处可去。UI 层本就不该给这个组合
        // （只有一件衣柜时应引导先新建），这里兜底不让数据进入悬空状态。
        if (destiny is WardrobeDeleteDestiny.TransferTo && destiny.targetWardrobeId == wardrobeId) {
            throw IllegalArgumentException("转移目标不能是被删除的衣柜自身")
        }

        // 含软删除行——它们是外键的真实约束对象（见上方说明）
        val items = clothingRepository.getItemsInWardrobeIncludingDeleted(wardrobeId)

        db.withTransaction {
            when (destiny) {
                is WardrobeDeleteDestiny.TransferTo -> {
                    items.forEach { item ->
                        // 只改 wardrobeId；deletedAt 由 updateWardrobe 保留原值
                        clothingRepository.moveToWardrobe(item.id, destiny.targetWardrobeId)
                    }
                }

                WardrobeDeleteDestiny.DeleteAll -> {
                    items.forEach { item -> deleteItemPhysically(item.id) }
                }
            }
            wardrobeDao.deleteById(wardrobeId)
        }

        // 衣柜行确认删除（事务已提交）后，才让调用方清文件——回滚就绝不会留下孤图。
        // review D-m：清理失败记日志后继续（行已删、删除不可回滚），不能把
        // 已成功的删除伪装成失败，也不能无痕迹地吞掉。
        runCatching { onImagesDeleted(items.map { it.id }) }
            .onFailure { t ->
                android.util.Log.w(
                    LOG_TAG,
                    "衣物图片清理失败（衣柜 $wardrobeId 已删，孤儿文件待后续清理）",
                    t,
                )
            }
        return items.size
    }

    /**
     * 物理删除单件衣物（W-13 后半段）。
     *
     * 两步顺序不可交换：`outfit_item_cross_ref.clothingItemId` 的外键也是 NO_ACTION，
     * 关联行还在时删衣物同样被拦，故必须先清关联再删行。
     */
    private suspend fun deleteItemPhysically(id: Long) {
        clothingRepository.deleteCrossRefsForItem(id)
        clothingRepository.deletePhysically(id)
    }

    private companion object {
        const val LOG_TAG = "WardrobeRepository"
    }
}
