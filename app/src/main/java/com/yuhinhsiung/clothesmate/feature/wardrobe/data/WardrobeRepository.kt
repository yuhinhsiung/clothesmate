package com.yuhinhsiung.clothesmate.feature.wardrobe.data

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

    /** 某衣柜衣物数（删除弹层提示） */
    suspend fun clothingCount(id: Long): Int

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

    /** 件数实时查询（不含软删除） */
    private suspend fun clothingCountOf(id: Long): Int = wardrobeDao.observeClothingCounts()
        .first()
        .firstOrNull { it.wardrobeId == id }
        ?.count
        ?: 0

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

    override suspend fun persistOrder(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id ->
            val entity = wardrobeDao.findById(id) ?: return@forEachIndexed
            if (entity.sortOrder != index) {
                wardrobeDao.update(entity.copy(sortOrder = index))
            }
        }
    }

    override suspend fun deleteWardrobe(id: Long) {
        wardrobeDao.deleteById(id)
    }

    override suspend fun clothingCount(id: Long): Int = wardrobeDao.countByWardrobe(id)

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
     * ## 为什么删衣柜前必须清光衣物（踩过的坑）
     *
     * `clothing_item.wardrobeId` 的外键声明是 `onDelete = NO_ACTION`，看字面像
     * 「什么都不做、随便删父行」，**但 SQLite 的 NO_ACTION 仍然会做外键检查**
     * （它与 RESTRICT 的差别只在检查时机：NO_ACTION 推迟到语句结束，RESTRICT 立即）。
     * 所以只要还有任何衣物的 `wardrobeId` 指着这个衣柜，`DELETE FROM wardrobe`
     * 就抛 `SQLiteConstraintException: FOREIGN KEY constraint failed`。
     * 这正是 M1-04 测试首次跑起来时抓到的真 bug——首版把「一并删除」做成软删除，
     * 行还赖在表里，衣柜必然删不掉。
     *
     * ## 为什么不用 Room 事务
     *
     * 首选本该是 `androidx.room.withTransaction(db) { ... }`，但 **Room 2.8.5 的该扩展
     * 在 Kotlin 侧解析不了**：`room-ktx` 2.8.5 的 AAR 只剩一个版本文件（协程支持已挪去
     * runtime），而 runtime 里同时存在 `RoomDatabaseKt`（common）与
     * `RoomDatabaseKt__RoomDatabase_androidKt`（android）两个同名同签名的
     * `withTransaction`，`import androidx.room.withTransaction` 在 kotlinc 2.4.20 下报
     * `receiver type mismatch`（显式 cast receiver、显式类型参数均无效）。
     * 暂不为它动依赖，改为**补偿式顺序执行**。
     *
     * ## 补偿的边界（如实说明，不假装万能）
     *
     * ① 逐件处理衣物 → ② 删衣柜行 → ③ 通知调用方清图。
     * 第 ② 步失败时：转移路径可完整还原（把衣物移回来）；物理删除路径**不可**还原
     * （行已经没了，只能尽力补 cross ref）。这是相对真事务的已知让步，
     * 登记在开发计划 M1-04 完成记录里；真要用事务得先解决上面的编译问题。
     */
    override suspend fun deleteWithDestiny(
        wardrobeId: Long,
        destiny: WardrobeDeleteDestiny,
        onImagesDeleted: suspend (List<Long>) -> Unit,
    ): Int {
        val items = clothingRepository.getItemsInWardrobe(wardrobeId)

        // 防御自毁：目标就是被删的衣柜时，衣物无处可去。UI 层本就不该给这个组合
        // （只有一件衣柜时应引导先新建），这里兜底不让数据进入悬空状态。
        if (destiny is WardrobeDeleteDestiny.TransferTo && destiny.targetWardrobeId == wardrobeId) {
            throw IllegalArgumentException("转移目标不能是被删除的衣柜自身")
        }

        // 空衣柜：无子行要处理，直接删
        if (items.isEmpty()) {
            wardrobeDao.deleteById(wardrobeId)
            return 0
        }

        var wardrobeDeleted = false
        try {
            when (destiny) {
                is WardrobeDeleteDestiny.TransferTo -> {
                    items.forEach { item ->
                        clothingRepository.moveToWardrobe(item.id, destiny.targetWardrobeId)
                    }
                }

                WardrobeDeleteDestiny.DeleteAll -> {
                    items.forEach { item -> deleteItemPhysically(item.id) }
                }
            }
            wardrobeDao.deleteById(wardrobeId)
            wardrobeDeleted = true
        } catch (failure: Throwable) {
            // 只有转移路径能完整回滚（物理删除的行复活不了）
            if (!wardrobeDeleted && destiny is WardrobeDeleteDestiny.TransferTo) {
                items.forEach { item ->
                    clothingRepository.moveToWardrobe(item.id, wardrobeId)
                }
            }
            throw failure
        }

        // 衣柜行确认删除成功，才让调用方清文件——回滚与否都不该留下孤图
        runCatching { onImagesDeleted(items.map { it.id }) }
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
}
