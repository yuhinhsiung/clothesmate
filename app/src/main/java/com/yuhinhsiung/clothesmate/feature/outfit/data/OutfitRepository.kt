package com.yuhinhsiung.clothesmate.feature.outfit.data

import com.yuhinhsiung.clothesmate.core.database.dao.ClothingItemDao
import com.yuhinhsiung.clothesmate.core.database.dao.OutfitCategoryDao
import com.yuhinhsiung.clothesmate.core.database.dao.OutfitDao
import com.yuhinhsiung.clothesmate.core.database.dao.WardrobeDao
import com.yuhinhsiung.clothesmate.core.database.entity.OutfitCategoryCrossRef
import com.yuhinhsiung.clothesmate.core.database.entity.OutfitEntity
import com.yuhinhsiung.clothesmate.core.database.entity.OutfitItemCrossRef
import com.yuhinhsiung.clothesmate.core.model.Outfit
import com.yuhinhsiung.clothesmate.core.model.OutfitCategory
import com.yuhinhsiung.clothesmate.core.model.OutfitDetail
import com.yuhinhsiung.clothesmate.core.model.OutfitDraft
import com.yuhinhsiung.clothesmate.core.model.OutfitItemRef
import com.yuhinhsiung.clothesmate.core.model.fallbackDisplayName
import com.yuhinhsiung.clothesmate.core.model.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 穿搭 Repository —— 开发文档 §2.3 / §3.2，开发计划 M1-17~M1-20。
 *
 * 跨 feature 边界口径：本实现只注入 **core 层 DAO**（Outfit / Category / ClothingItem /
 * Wardrobe），不引用 wardrobe feature 的 Repository 实现——CLAUDE.md「feature 不得直接
 * 引用其他 feature 的 Repository 实现」。衣物 / 衣柜展示字段（编号、衣柜名）由 DAO 直查。
 *
 * ## 删除的两条连带（W-13 / M1-20）
 *
 * - 穿搭照片目录 `outfit/{id}/` 整删（[onDeleted] 回调里由 UI 层经 ImageStorage 执行，
 *   与衣柜删除同模式：Repository 不碰文件 IO）；
 * - 其引用的**软删除衣物**若因此不再被任何现存穿搭引用 → 物理删除（行 + 关联 + 图片），
 *   返回值即被清除的件数，供删除 Snackbar 文案。
 */
interface OutfitRepository {

    /** 全部穿搭（创建时间降序），带衣物件数（瀑布卡「4 件」chip） */
    fun observeOutfits(): Flow<List<Outfit>>

    fun observeOutfitDetail(outfitId: Long): Flow<OutfitDetail?>

    suspend fun getOutfit(outfitId: Long): Outfit?

    /** 某穿搭所属分类 id 集合（编辑预填 M1-18） */
    suspend fun getCategoryIdsForOutfit(outfitId: Long): List<Long>

    /** 某穿搭关联的衣物 id 列表（编辑预填 M1-18，顺序与创建时一致） */
    suspend fun getClothingIds(outfitId: Long): List<Long>

    /** 分类内穿搭（O-03）；与「全部穿搭」同数据源、参数区分（开发计划 M1-19 备注） */
    fun observeOutfitsInCategory(categoryId: Long): Flow<List<Outfit>>

    suspend fun createOutfit(draft: OutfitDraft): Long

    /** 创建流程照片两步落位的第二步：insert 拿到 id 后写回照片相对路径列表（M1-18） */
    suspend fun updatePhotoPaths(outfitId: Long, photoPaths: List<String>)

    /** 编辑（M1-18 编辑复用）。wearCount / lastWornAt / createdAt 保留原值。 */
    suspend fun updateOutfit(outfitId: Long, draft: OutfitDraft)

    /**
     * 删除穿搭（M1-20 / W-13）。行 + 两张 cross ref + 软删衣物连带判定。
     *
     * @param onDeleted 删除成功后回调 (outfitId, 被物理清除的软删衣物 id 列表)，
     *   UI 层据此清图片文件（穿搭照片目录 + 被清衣物原图/抠图）。
     * @return 被连带物理清除的软删除衣物件数（Snackbar 文案）
     */
    suspend fun deleteOutfit(
        outfitId: Long,
        onDeleted: suspend (outfitId: Long, clearedItemIds: List<Long>) -> Unit = { _, _ -> },
    ): Int

    /** 删除确认弹窗预计算：将随本穿搭一并彻底清除的软删除衣物件数 */
    suspend fun countClearableDeletedItems(outfitId: Long): Int

    /** 全部分类（创建序），带套数与封面（O-02） */
    fun observeCategories(): Flow<List<OutfitCategory>>

    suspend fun createCategory(name: String): Long

    suspend fun renameCategory(categoryId: Long, name: String)

    /** 删除分类只解除关联（O-05），穿搭行保留、无关联者归「未分类」 */
    suspend fun deleteCategory(categoryId: Long)
}

@Singleton
class DefaultOutfitRepository @Inject constructor(
    private val outfitDao: OutfitDao,
    private val categoryDao: OutfitCategoryDao,
    private val clothingDao: ClothingItemDao,
    private val wardrobeDao: WardrobeDao,
) : OutfitRepository {

    override fun observeOutfits(): Flow<List<Outfit>> =
        combine(outfitDao.observeAll(), outfitDao.observeItemRefs()) { outfits, refs ->
            val counts = refs.groupingBy { it.outfitId }.eachCount()
            outfits.map { it.toDomain(itemCount = counts[it.id] ?: 0) }
        }

    override fun observeOutfitDetail(outfitId: Long): Flow<OutfitDetail?> =
        combine(
            outfitDao.observeById(outfitId),
            wardrobeDao.observeAll(),
            categoryDao.observeAll(),
        ) { outfit, wardrobes, categories ->
            outfit ?: return@combine null
            // 衣物清单必须**含软删除行**（W-13「已删除」占位卡渲染）——
            // 不能用 observeAll()（它过滤 deletedAt，占位卡会凭空消失，
            // 该缺口由 OutfitRepositoryTest.softDeletedItemsAreNotCountedInDetailItems 锁定）。
            // 已知让步：衣物字段级变更不会主动触发本流重发（combine 源不含衣物表），
            // 从衣物编辑页返回详情页时 WhileSubscribed 重订阅会取到新值。
            val itemIds = outfitDao.getClothingIds(outfitId)
            val items = if (itemIds.isEmpty()) {
                emptyList()
            } else {
                clothingDao.findByIdsIncludingDeleted(itemIds)
            }
            val categoryIds = categoryDao.getCategoryIdsForOutfit(outfitId).toSet()
            val itemRefs = items.mapNotNull { entity ->
                val domain = entity.toDomain()
                OutfitItemRef(
                    itemId = domain.id,
                    code = domain.code,
                    displayName = domain.displayName,
                    imagePath = domain.displayImagePath,
                    wardrobeName = wardrobes.firstOrNull { it.id == domain.wardrobeId }?.name,
                    // 软删除衣物：占位卡保留原编号、不可点击（W-13）
                    deleted = entity.deletedAt != null,
                )
            }
            OutfitDetail(
                outfit = outfit.toDomain(itemCount = itemRefs.count { !it.deleted }),
                items = itemRefs,
                categories = categories.filter { it.id in categoryIds }.map { it.toDomain() },
            )
        }

    override suspend fun getOutfit(outfitId: Long): Outfit? =
        outfitDao.findById(outfitId)?.toDomain()

    override suspend fun getCategoryIdsForOutfit(outfitId: Long): List<Long> =
        categoryDao.getCategoryIdsForOutfit(outfitId)

    override suspend fun getClothingIds(outfitId: Long): List<Long> =
        outfitDao.getClothingIds(outfitId)

    override suspend fun updatePhotoPaths(outfitId: Long, photoPaths: List<String>) {
        // DAO 的参数是 List<String>，Room 经 TypeConverter 自动序列化（见 OutfitDao.updatePhotoPaths）
        outfitDao.updatePhotoPaths(
            id = outfitId,
            photoPaths = photoPaths,
            updatedAt = System.currentTimeMillis(),
        )
    }

    override fun observeOutfitsInCategory(categoryId: Long): Flow<List<Outfit>> =
        combine(categoryDao.observeOutfitIds(categoryId), outfitDao.observeAll()) { ids, outfits ->
            if (ids.isEmpty()) {
                emptyList()
            } else {
                val idSet = ids.toSet()
                outfits.filter { it.id in idSet }.map { it.toDomain() }
            }
        }

    override suspend fun createOutfit(draft: OutfitDraft): Long {
        val now = System.currentTimeMillis()
        val id = outfitDao.insert(
            OutfitEntity(
                name = draft.name?.trim()?.takeIf { it.isNotEmpty() },
                photoPaths = draft.photoPaths,
                styleTags = draft.styleTags,
                sceneTags = draft.sceneTags,
                seasonTags = draft.seasonTags,
                note = draft.note?.trim()?.takeIf { it.isNotEmpty() },
                createdAt = now,
                updatedAt = now,
            ),
        )
        outfitDao.insertCrossRefs(draft.clothingIds.map { OutfitItemCrossRef(id, it) })
        categoryDao.insertCrossRefs(draft.categoryIds.map { OutfitCategoryCrossRef(id, it) })
        return id
    }

    override suspend fun updateOutfit(outfitId: Long, draft: OutfitDraft) {
        val existing = outfitDao.findById(outfitId) ?: return
        outfitDao.update(
            existing.copy(
                name = draft.name?.trim()?.takeIf { it.isNotEmpty() },
                photoPaths = draft.photoPaths,
                styleTags = draft.styleTags,
                sceneTags = draft.sceneTags,
                seasonTags = draft.seasonTags,
                note = draft.note?.trim()?.takeIf { it.isNotEmpty() },
                updatedAt = System.currentTimeMillis(),
            ),
        )
        // 关联全量重写（多对多增量合并复杂度不值当，个人量级直接重建）
        outfitDao.deleteCrossRefsByOutfit(outfitId)
        outfitDao.insertCrossRefs(draft.clothingIds.map { OutfitItemCrossRef(outfitId, it) })
        categoryDao.deleteCrossRefsByOutfit(outfitId)
        categoryDao.insertCrossRefs(draft.categoryIds.map { OutfitCategoryCrossRef(outfitId, it) })
    }

    override suspend fun deleteOutfit(
        outfitId: Long,
        onDeleted: suspend (Long, List<Long>) -> Unit,
    ): Int {
        val itemIds = outfitDao.getClothingIds(outfitId)
        // 分类 / 衣物关联先行清理（outfit 行 FK 是 CASCADE，但显式删除语义更清楚，
        // 且 CASCADE 不会触发 W-13 的连带判定——那要逐件查剩余引用）
        categoryDao.deleteCrossRefsByOutfit(outfitId)
        outfitDao.deleteCrossRefsByOutfit(outfitId)
        outfitDao.deleteById(outfitId)

        // W-13 连带：软删衣物若不再被任何现存穿搭引用 → 物理删除
        val cleared = itemIds.mapNotNull { itemId ->
            val entity = clothingDao.findById(itemId) ?: return@mapNotNull null
            if (entity.deletedAt != null && outfitDao.getOutfitIdsForItem(itemId).isEmpty()) {
                clothingDao.deleteById(itemId)
                itemId
            } else {
                null
            }
        }
        onDeleted(outfitId, cleared)
        return cleared.size
    }

    override suspend fun countClearableDeletedItems(outfitId: Long): Int {
        val itemIds = outfitDao.getClothingIds(outfitId)
        return itemIds.count { itemId ->
            val entity = clothingDao.findById(itemId)
            entity != null && entity.deletedAt != null &&
                outfitDao.getOutfitIdsForItem(itemId) == listOf(outfitId)
        }
    }

    override fun observeCategories(): Flow<List<OutfitCategory>> =
        combine(
            categoryDao.observeAll(),
            categoryDao.observeAllRefs(),
            outfitDao.observeAll(),
        ) { categories, refs, outfits ->
            val outfitsByCategory = refs.groupBy({ it.categoryId }, { it.outfitId })
            categories.map { entity ->
                val outfitIds = outfitsByCategory[entity.id].orEmpty()
                // 封面：分类内最新一套**有照片**穿搭的封面（O-02「默认取最新一套图」）
                val cover = outfits
                    .filter { it.id in outfitIds && it.photoPaths.isNotEmpty() }
                    .maxByOrNull { it.createdAt }
                    ?.photoPaths
                    ?.firstOrNull()
                entity.toDomain(outfitCount = outfitIds.size, coverPath = cover)
            }
        }

    override suspend fun createCategory(name: String): Long {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "分类名不能为空" }
        return categoryDao.insert(
            com.yuhinhsiung.clothesmate.core.database.entity.OutfitCategoryEntity(
                name = trimmed,
                sortOrder = categoryDao.maxSortOrder() + 1,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun renameCategory(categoryId: Long, name: String) {
        val existing = categoryDao.findById(categoryId) ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        categoryDao.update(existing.copy(name = trimmed))
    }

    override suspend fun deleteCategory(categoryId: Long) {
        // O-05：仅解除关联，穿搭行保留
        categoryDao.deleteCrossRefsByCategory(categoryId)
        categoryDao.deleteById(categoryId)
    }
}
