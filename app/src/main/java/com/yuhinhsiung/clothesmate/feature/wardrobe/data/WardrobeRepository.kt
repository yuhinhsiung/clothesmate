package com.yuhinhsiung.clothesmate.feature.wardrobe.data

import com.yuhinhsiung.clothesmate.core.database.dao.WardrobeDao
import com.yuhinhsiung.clothesmate.core.database.entity.WardrobeEntity
import com.yuhinhsiung.clothesmate.core.model.Wardrobe
import com.yuhinhsiung.clothesmate.core.model.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
     * 删除衣柜并处理衣物去向（W-12）。
     *
     * @param destiny [WardrobeDeleteDestiny] 转移 / 一并删除
     * @param onImagesDeleted 图片清理回调（文件系统副作用由 image 层执行，
     *        Repository 不直接碰 IO，便于单测替换）
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
 * - [DeleteAll] 一并删除其中衣物（软删除，编号不回收）
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

    override suspend fun getWardrobe(id: Long): Wardrobe? {
        val entity = wardrobeDao.findById(id) ?: return null
        val count = wardrobeDao.observeClothingCounts()
            .map { counts -> counts.find { it.wardrobeId == id }?.count ?: 0 }
            .first()
        return entity.toDomain(clothingCount = count)
    }

    override suspend fun getAllWardrobes(): List<Wardrobe> =
        wardrobeDao.getAll().map { it.toDomain() }

    override suspend fun wardrobeCount(): Int = wardrobeDao.count()

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

    override suspend fun deleteWithDestiny(
        wardrobeId: Long,
        destiny: WardrobeDeleteDestiny,
        onImagesDeleted: suspend (List<Long>) -> Unit,
    ): Int {
        val items = clothingRepository.getItemsInWardrobe(wardrobeId)
        when (destiny) {
            is WardrobeDeleteDestiny.TransferTo -> {
                // 转移：只改 wardrobeId，编号、图片、穿着次数一律不变（开发文档 §2.1.3）
                items.forEach { item ->
                    clothingRepository.moveToWardrobe(item.id, destiny.targetWardrobeId)
                }
            }

            WardrobeDeleteDestiny.DeleteAll -> {
                // 一并删除 = 全部软删除（编号不回收、可走撤销路径），不物理删文件
                items.forEach { item -> clothingRepository.softDelete(item.id) }
            }
        }
        wardrobeDao.deleteById(wardrobeId)
        return items.size
    }
}
