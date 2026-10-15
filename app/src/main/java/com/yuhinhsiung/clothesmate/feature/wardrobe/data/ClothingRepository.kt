package com.yuhinhsiung.clothesmate.feature.wardrobe.data

import com.yuhinhsiung.clothesmate.core.database.dao.ClothingItemDao
import com.yuhinhsiung.clothesmate.core.database.dao.OutfitDao
import com.yuhinhsiung.clothesmate.core.database.entity.ClothingItemEntity
import com.yuhinhsiung.clothesmate.core.model.ClothingCategory
import com.yuhinhsiung.clothesmate.core.model.ClothingColor
import com.yuhinhsiung.clothesmate.core.model.ClothingItem
import com.yuhinhsiung.clothesmate.core.model.Season
import com.yuhinhsiung.clothesmate.core.model.toDomain
import com.yuhinhsiung.clothesmate.core.model.toEntity
import com.yuhinhsiung.clothesmate.core.database.entity.ClothingStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 衣物 Repository —— 开发文档 §2.1.2 W-04/W-05/W-06/W-07/W-09/W-13。
 *
 * 软删口径（W-13）：所有列表 / 查询 / 搜索 / 统计一律过滤 `deletedAt`；
 * 编号唯一性校验是唯一刻意的例外（全表 UNIQUE + 编号不回收，见 [isCodeAvailable]）。
 */
interface ClothingRepository : com.yuhinhsiung.clothesmate.feature.wardrobe.domain.CodeConflictChecker {

    /** 衣柜内衣物，按编号升序（W-04） */
    fun observeItemsInWardrobe(wardrobeId: Long): Flow<List<ClothingItem>>

    /** 全部衣物，按编号升序（换季模式、兜底抽取、导出用） */
    fun observeAllItems(): Flow<List<ClothingItem>>

    fun observeItem(id: Long): Flow<ClothingItem?>

    /** 含软删除的单件查询 —— 仅 W-13「已删除」占位卡与物理清除判断用 */
    suspend fun getItemIncludingDeleted(id: Long): ClothingItem?

    suspend fun getItem(id: Long): ClothingItem?

    suspend fun getItemsInWardrobe(wardrobeId: Long): List<ClothingItem>

    suspend fun getItemsByIds(ids: List<Long>): List<ClothingItem>

    /** 全部「正常」状态衣物（推荐候选、兜底抽取用；收起状态被排除） */
    suspend fun getNormalItems(): List<ClothingItem>

    /**
     * 编号唯一性校验（W-06 实时校验）。
     *
     * @param excludeId 编辑时排除自身
     * @return true = 可用；false = 已被占用
     */
    suspend fun isCodeAvailable(code: String, excludeId: Long = 0L): Boolean

    /** 新增衣物。code 由调用方经 [CodeAllocator] 发放或用户手动指定，此处只校验唯一性。 */
    suspend fun createItem(item: ClothingItem): Long

    /** 编辑（W-05/W-06） */
    suspend fun updateItem(item: ClothingItem)

    /** 软删除（W-13）。返回是否成功（衣物不存在时 false）。 */
    suspend fun softDelete(id: Long): Boolean

    /** 撤销软删除（W-13 Snackbar「撤销」） */
    suspend fun restore(id: Long)

    /** 一键切换 收起 / 恢复正常（W-09） */
    suspend fun toggleStored(id: Long)

    suspend fun setStored(id: Long, stored: Boolean)

    suspend fun moveToWardrobe(id: Long, wardrobeId: Long)

    suspend fun updateCutoutPath(id: Long, path: String?)

    /**
     * 清掉某衣物在穿搭关联表里的行（物理删除前置步骤）。
     *
     * 单独暴露而不是塞进 [deletePhysically]：调用方（衣柜删除 W-12）需要按
     * 「先清关联、再删行」的顺序自己控制，且 M1-20 删穿搭时也要先判断关联。
     */
    suspend fun deleteCrossRefsForItem(id: Long)

    suspend fun deletePhysically(id: Long)

    /**
     * 搜索 + 组合筛选（W-07 / M1-10）。
     *
     * 分词规则：按空格切分，各 token 之间 **AND**（全部命中才入选）。
     * 单 token 的命中范围：编号 / 名称（含「主色 + 类别」拼接兜底）/ 类别 / 颜色 / 风格 / 季节。
     *
     * @param query 原始输入，空串表示不限关键词
     * @param categoryKey 类别筛选，null = 不限
     * @param colorKey 颜色筛选，null = 不限
     * @param styleTag 风格筛选，null = 不限
     */
    fun search(
        query: String,
        categoryKey: String? = null,
        colorKey: String? = null,
        styleTag: String? = null,
    ): Flow<List<ClothingItem>>

    /** 衣柜内按状态筛选（M1-09 状态 chips：全部 / 正常 / 已收起 / 待完善） */
    fun observeItemsInWardrobeByStatus(
        wardrobeId: Long,
        status: WardrobeItemFilter,
    ): Flow<List<ClothingItem>>
}

/** 衣柜详情页的状态筛选（M1-09） */
enum class WardrobeItemFilter {
    All,
    Normal,
    Stored,
    Incomplete,
}

@Singleton
class DefaultClothingRepository @Inject constructor(
    private val dao: ClothingItemDao,
    private val outfitDao: OutfitDao,
) : ClothingRepository {

    override fun observeItemsInWardrobe(wardrobeId: Long): Flow<List<ClothingItem>> =
        dao.observeByWardrobe(wardrobeId).map { list -> list.map { it.toDomain() } }

    override fun observeAllItems(): Flow<List<ClothingItem>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeItem(id: Long): Flow<ClothingItem?> =
        dao.observeById(id).map { it?.toDomain() }

    override suspend fun getItemIncludingDeleted(id: Long): ClothingItem? =
        dao.findById(id)?.toDomain()

    override suspend fun getItem(id: Long): ClothingItem? {
        val entity = dao.findById(id) ?: return null
        return if (entity.deletedAt != null) null else entity.toDomain()
    }

    override suspend fun getItemsInWardrobe(wardrobeId: Long): List<ClothingItem> =
        dao.getByWardrobe(wardrobeId).map { it.toDomain() }

    override suspend fun getItemsByIds(ids: List<Long>): List<ClothingItem> =
        if (ids.isEmpty()) {
            emptyList()
        } else {
            dao.findByIds(ids).map { it.toDomain() }
        }

    override suspend fun getNormalItems(): List<ClothingItem> =
        dao.getByStatus(ClothingStatus.NORMAL).map { it.toDomain() }

    /**
     * W-06 实时校验：某编号是否可用。
     *
     * 直接查 DAO（带 excludeId），比先问 [isCodeTaken] 再排除更直白；
     * [isCodeTaken] 面向 [com.yuhinhsiung.clothesmate.feature.wardrobe.domain.CodeAllocator]
     * 的全表判定（发放时不排除任何 id）。
     */
    override suspend fun isCodeAvailable(code: String, excludeId: Long): Boolean =
        dao.countByCode(code, excludeId) == 0

    /**
     * CodeConflictChecker 实现：全表判定（含软删行），不排除任何 id。
     * DAO 的 countByCode 刻意不过滤 deletedAt，与 code 的全表 UNIQUE 索引同口径。
     */
    override suspend fun isCodeTaken(code: String): Boolean =
        dao.countByCode(code, excludeId = 0L) > 0

    override suspend fun createItem(item: ClothingItem): Long {
        require(item.code.isNotBlank()) { "编号不能为空" }
        require(isCodeAvailable(item.code)) { "编号 ${item.code} 已被使用" }
        return dao.insert(item.toEntity())
    }

    override suspend fun updateItem(item: ClothingItem) {
        require(item.code.isNotBlank()) { "编号不能为空" }
        require(isCodeAvailable(item.code, excludeId = item.id)) { "编号 ${item.code} 已被使用" }
        val existing = dao.findById(item.id) ?: return
        // 保留软删除标记与创建时间：编辑不应复活已删衣物，也不应改写建档时间
        dao.update(
            item.toEntity().copy(
                deletedAt = existing.deletedAt,
                createdAt = existing.createdAt,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun softDelete(id: Long): Boolean {
        val existing = dao.findById(id) ?: return false
        if (existing.deletedAt != null) return false
        val now = System.currentTimeMillis()
        dao.update(existing.copy(deletedAt = now, updatedAt = now))
        return true
    }

    override suspend fun restore(id: Long) {
        dao.restore(id, System.currentTimeMillis())
    }

    override suspend fun toggleStored(id: Long) {
        val existing = dao.findById(id) ?: return
        val target = if (existing.status == ClothingStatus.STORED) {
            ClothingStatus.NORMAL
        } else {
            ClothingStatus.STORED
        }
        dao.updateStatus(id, target, System.currentTimeMillis())
    }

    override suspend fun setStored(id: Long, stored: Boolean) {
        val target = if (stored) ClothingStatus.STORED else ClothingStatus.NORMAL
        dao.updateStatus(id, target, System.currentTimeMillis())
    }

    override suspend fun moveToWardrobe(id: Long, wardrobeId: Long) {
        dao.updateWardrobe(id, wardrobeId, System.currentTimeMillis())
    }

    override suspend fun updateCutoutPath(id: Long, path: String?) {
        dao.updateCutoutPath(id, path, System.currentTimeMillis())
    }

    override suspend fun deleteCrossRefsForItem(id: Long) {
        outfitDao.deleteCrossRefsByClothingItem(id)
    }

    override suspend fun deletePhysically(id: Long) {
        dao.deleteById(id)
    }

    override fun search(
        query: String,
        categoryKey: String?,
        colorKey: String?,
        styleTag: String?,
    ): Flow<List<ClothingItem>> {
        val tokens = query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }

        // 无任何条件：直接返回全部衣物（搜索结果页初始态）
        if (tokens.isEmpty() && categoryKey == null && colorKey == null && styleTag == null) {
            return dao.observeAll().map { list -> list.map { it.toDomain() } }
        }

        // 结构化筛选（类别 / 颜色 / 风格）走内存过滤：数据量为个人衣柜量级（百~千件），
        // 先把 DAO 的片段查询结果取交集再筛，比在 SQL 里动态拼条件更易读且不易注入。
        return combineKeywordFlows(tokens)
            .map { candidates -> applyStructuredFilters(candidates, categoryKey, colorKey, styleTag) }
    }

    /**
     * 关键词片段取交集：每个 token 必须在「编号 / 名称 / 类别 / 颜色 / 风格 / 季节」
     * 至少一处命中（DAO 各片段方法内部是 OR，token 之间是 AND）。
     */
    private fun combineKeywordFlows(tokens: List<String>): Flow<List<ClothingItemEntity>> {
        if (tokens.isEmpty()) return dao.observeAll()
        val perToken = tokens.map { token -> keywordFlow(token) }
        return kotlinx.coroutines.flow.combine(perToken) { lists ->
            // AND 语义：按 id 取交集
            var acc: Set<Long>? = null
            val idToList = mutableMapOf<Long, ClothingItemEntity>()
            lists.forEach { list ->
                val ids = list.map { it.id }.toSet()
                acc = if (acc == null) ids else acc.intersect(ids)
                list.forEach { idToList[it.id] = it }
            }
            val keep = acc ?: emptySet()
            idToList.values.filter { it.id in keep }
                .sortedBy { it.code.toLongOrNull() ?: Long.MAX_VALUE }
        }
    }

    /** 单 token 的命中范围（DAO 片段 OR 组合，外加中英文枚举词翻译后的匹配） */
    private fun keywordFlow(token: String): Flow<List<ClothingItemEntity>> {
        val codeFlow = dao.searchByCode(token)
        val nameCategoryFlow = dao.searchByNameAndCategory(token)
        val styleFlow = dao.searchByStyleTag(token)
        val seasonFlow = dao.searchBySeasonTag(token)

        // 用户输的是中文，DB 存的是枚举 key（category / colors）——须先翻译再查，
        // 否则「红」「上衣」「衬衫」这类口语词一条都命中不了（M1-10 的实际缺口）。
        val colorKey = ClothingColor.searchAliases[token]?.key
        val categoryKeys = ClothingCategory.searchAliases[token]
        val seasonKey = Season.searchAliases[token]?.key

        val colorFlow = colorKey?.let { dao.searchByColorKey(it) }
        val categoryFlow = categoryKeys
            ?.takeIf { it.isNotEmpty() }
            ?.map { it.key }
            ?.let { keys -> dao.searchByCategoryKeys(keys) }
        // 季节：DAO 已按 key 匹配，用户输 key（"winter"）时直接命中；
        // 用户输中文（"冬"）时再由 searchBySeasonTag 走中文显示名匹配失败，
        // 故显式翻译成 key 再补一次查询。
        val seasonKeyFlow = seasonKey?.takeIf { it != token }?.let { dao.searchBySeasonTag(it) }

        val flows = buildList {
            add(codeFlow)
            add(nameCategoryFlow)
            add(styleFlow)
            add(seasonFlow)
            colorFlow?.let { add(it) }
            categoryFlow?.let { add(it) }
            seasonKeyFlow?.let { add(it) }
        }
        return kotlinx.coroutines.flow.combine(flows) { lists ->
            val merged = LinkedHashMap<Long, ClothingItemEntity>()
            lists.forEach { list -> list.forEach { merged[it.id] = it } }
            merged.values.sortedBy { it.code.toLongOrNull() ?: Long.MAX_VALUE }
        }
    }

    /** 结构化筛选 + 「主色 + 类别」拼接名的兜底匹配 */
    private suspend fun applyStructuredFilters(
        candidates: List<ClothingItemEntity>,
        categoryKey: String?,
        colorKey: String?,
        styleTag: String?,
    ): List<ClothingItem> = candidates
        .asSequence()
        .filter { categoryKey == null || it.category == categoryKey }
        .filter { colorKey == null || colorKey in it.colors }
        .filter { styleTag == null || styleTag in it.styleTags }
        .map { it.toDomain() }
        .toList()

    override fun observeItemsInWardrobeByStatus(
        wardrobeId: Long,
        status: WardrobeItemFilter,
    ): Flow<List<ClothingItem>> {
        val source: Flow<List<ClothingItemEntity>> = dao.observeByWardrobe(wardrobeId)
        return source.map { entities ->
            entities.filter { entity ->
                when (status) {
                    WardrobeItemFilter.All -> true
                    WardrobeItemFilter.Normal -> entity.status == ClothingStatus.NORMAL
                    WardrobeItemFilter.Stored -> entity.status == ClothingStatus.STORED
                    // 待完善判定：category 为空即待完善（开发文档 §2.2.4）
                    WardrobeItemFilter.Incomplete -> entity.category.isNullOrBlank()
                }
            }.map { it.toDomain() }
        }
    }
}
