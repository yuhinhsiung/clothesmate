package com.yuhinhsiung.clothesmate.feature.wardrobe.data

import com.yuhinhsiung.clothesmate.core.database.ClothesMateDatabase
import com.yuhinhsiung.clothesmate.core.database.dao.ClothingItemDao
import com.yuhinhsiung.clothesmate.core.database.dao.CodeHistoryDao
import com.yuhinhsiung.clothesmate.core.database.dao.OutfitDao
import com.yuhinhsiung.clothesmate.core.database.entity.ClothingCodeHistoryEntity
import com.yuhinhsiung.clothesmate.core.database.entity.ClothingItemEntity
import com.yuhinhsiung.clothesmate.core.model.ClothingCategory
import com.yuhinhsiung.clothesmate.core.model.ClothingColor
import com.yuhinhsiung.clothesmate.core.model.ClothingItem
import com.yuhinhsiung.clothesmate.core.model.Season
import com.yuhinhsiung.clothesmate.core.model.toDomain
import com.yuhinhsiung.clothesmate.core.model.toEntity
import com.yuhinhsiung.clothesmate.core.model.ClothingStatus
import androidx.room.withTransaction
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

    /**
     * 衣柜内**含软删除**的全部衣物 —— 删除衣柜（W-12）专用（review R-01）。
     *
     * 软删除行的 `wardrobeId` 外键仍指向本衣柜，删除衣柜前必须一并处理，
     * 否则 `DELETE FROM wardrobe` 会因外键约束失败（且「一并删除」路径已先删掉正常行，
     * 用户看到失败但部分数据已经消失）。
     */
    suspend fun getItemsInWardrobeIncludingDeleted(wardrobeId: Long): List<ClothingItem>

    /** 衣柜内**含软删除**的衣物数（「一并删除」的实际影响面，review R-01 口径分离） */
    suspend fun countItemsInWardrobeIncludingDeleted(wardrobeId: Long): Int

    /** 某衣物被几套现存穿搭引用（M1-13 删除前警示，review R-10） */
    suspend fun countOutfitsForItem(id: Long): Int

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

    /** 批量收起/恢复（M4-04 换季模式）；空列表为 no-op。 */
    suspend fun setStored(ids: List<Long>, stored: Boolean)

    suspend fun moveToWardrobe(id: Long, wardrobeId: Long)

    suspend fun updateCutoutPath(id: Long, path: String?)

    /** 后台补抠图：仅当该衣物还没有抠图时写入（review R03）。 */
    suspend fun setCutoutIfEmpty(id: Long, path: String)

    /** 后台取色：仅当该衣物还没有颜色时写入（review R05）。 */
    suspend fun setColorsIfEmpty(id: Long, colors: List<String>)

    /** 采纳连拍 AI 建议；只补空字段（review R10）。 */
    suspend fun applyAiSuggestionIfEmpty(
        id: Long,
        category: String?,
        styles: List<String>,
        seasons: List<String>,
    )

    /**
     * 清掉某衣物在穿搭关联表里的行（物理删除前置步骤）。
     *
     * 单独暴露而不是塞进 [deletePhysically]：调用方（衣柜删除 W-12）需要按
     * 「先清关联、再删行」的顺序自己控制，且 M1-20 删穿搭时也要先判断关联。
     */
    suspend fun deleteCrossRefsForItem(id: Long)

    suspend fun deletePhysically(id: Long)

    /**
     * 回滚一次**未完成的建档**（review R-14 场景 A）。
     *
     * 建档是「插入行（含编号历史）→ 归位图片 → 回写路径」三步；后两步失败时补偿要走这里，
     * 而不是裸的 [deletePhysically]：
     *
     * - 删行 + 清关联（与物理删除相同）；
     * - **同时撤回本次预约的编号占用**——`code` 的历史行只有在持有人恰好是 [id] 时才删。
     *   不撤回的话，同一个草稿重试会被「编号已被使用」拦住：用户什么都没得到、号却没了；
     *   而一刀切清空编号历史又会重新引入 R-03 的「物理删除后旧号可复用」。
     *
     * @param code 本次建档使用的编号（原始输入即可，内部归一）
     */
    suspend fun rollbackFailedCreate(id: Long, code: String)

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
    private val db: ClothesMateDatabase,
    private val dao: ClothingItemDao,
    private val outfitDao: OutfitDao,
    private val codeHistoryDao: CodeHistoryDao,
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

    override suspend fun getItemsInWardrobeIncludingDeleted(wardrobeId: Long): List<ClothingItem> =
        dao.getByWardrobeIncludingDeleted(wardrobeId).map { it.toDomain() }

    override suspend fun countItemsInWardrobeIncludingDeleted(wardrobeId: Long): Int =
        dao.countByWardrobeIncludingDeleted(wardrobeId)

    override suspend fun countOutfitsForItem(id: Long): Int = outfitDao.countOutfitsForItem(id)

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
     * review R-03：占用判定 = **现存行（含软删）+ 历史占用表**。只查 `clothing_item`
     * 的话，物理删除会抹掉唯一的占用证据，旧号立即可被重新使用。
     *
     * @param excludeId 编辑时排除自身。历史表记的是「编号当前持有人」，
     *   `holderId == excludeId` 视为本人持有——用户没换号，或改号后想改回来，都放行；
     *   **别的衣物任何情况下都拿不到这个号**。
     */
    override suspend fun isCodeAvailable(code: String, excludeId: Long): Boolean {
        val normalized = normalizedOrRaw(code)
        if (dao.countByCode(normalized, excludeId) > 0) return false
        val holder = codeHistoryDao.holderOf(normalized) ?: return true
        return holder == excludeId
    }

    /**
     * CodeConflictChecker 实现：全表判定（含软删行 + 历史占用），不排除任何 id。
     * 自动发号的冲突判定与手动校验共用同一占用来源（review R-03 修复建议）。
     */
    override suspend fun isCodeTaken(code: String): Boolean {
        val normalized = normalizedOrRaw(code)
        return dao.countByCode(normalized, excludeId = 0L) > 0 ||
            codeHistoryDao.countByCode(normalized) > 0
    }

    /**
     * 新增衣物。
     *
     * 校验通过后「插入行 + 登记编号历史」必须同事务提交（review R-13 同源要求）：
     * 中途失败若只留下行或只留下历史，都会让编号占用状态与实际不符。
     */
    override suspend fun createItem(item: ClothingItem): Long {
        val code = normalizeCode(item.code)
        require(isCodeAvailable(code)) { "编号 $code 已被使用" }
        return db.withTransaction {
            val id = dao.insert(item.toEntity().copy(code = code))
            recordCodeHistory(code, id)
            id
        }
    }

    override suspend fun updateItem(item: ClothingItem) {
        val code = normalizeCode(item.code)
        require(isCodeAvailable(code, excludeId = item.id)) { "编号 $code 已被使用" }
        db.withTransaction {
            val existing = dao.findById(item.id) ?: return@withTransaction
            // 保留软删除标记与创建时间：编辑不应复活已删衣物，也不应改写建档时间
            dao.update(
                item.toEntity().copy(
                    code = code,
                    deletedAt = existing.deletedAt,
                    createdAt = existing.createdAt,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            // 改号后旧号**不回收**：旧号的历史行原样留着（其 holderId 仍是本件），
            // 新号登记为本件持有。两个号此后都属于这件衣服。
            recordCodeHistory(code, item.id)
        }
    }

    /**
     * 登记编号占用（review R-03），**首次时间不被后续写入刷新**（review D-01）。
     *
     * 两步都在调用方的事务里：不存在才插入（带首次登记时间），存在则只更新持有人。
     * 编辑一次衣物（哪怕只改备注）也会走到这里，所以绝不能无条件覆写 `recordedAt`。
     */
    private suspend fun recordCodeHistory(code: String, holderId: Long) {
        codeHistoryDao.insertIfAbsent(
            ClothingCodeHistoryEntity(
                code = code,
                holderId = holderId,
                recordedAt = System.currentTimeMillis(),
            ),
        )
        codeHistoryDao.updateHolder(code, holderId)
    }

    /**
     * 编号归一（review D-f）：仅允许纯数字、去前导零。
     * 「007」与「7」必须视为同一个编号——不归一的话 UNIQUE（字符串级）拦不住两者并存，
     * 贴在实体衣服上的两张编号贴纸会指向两件不同的衣物。
     */
    private fun normalizeCode(raw: String): String {
        val trimmed = raw.trim()
        require(trimmed.isNotEmpty() && trimmed.all { it.isDigit() }) { "编号必须是纯数字" }
        return trimmed.trimStart('0').ifEmpty { "0" }
    }

    /**
     * 宽松归一（review R-03）：校验入口不能因为「非纯数字」就抛异常——
     * 用户输到一半的 "1a" 也该得到「已被使用 / 未占用」的确定答复而不是崩溃。
     * 非数字输入原样返回，它不可能等于任何已存编号，结果自然是可用。
     */
    private fun normalizedOrRaw(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || !trimmed.all { it.isDigit() }) return trimmed
        return trimmed.trimStart('0').ifEmpty { "0" }
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

    override suspend fun setStored(ids: List<Long>, stored: Boolean) {
        if (ids.isEmpty()) return
        val target = if (stored) ClothingStatus.STORED else ClothingStatus.NORMAL
        db.withTransaction {
            dao.updateStatusByIds(ids, target, System.currentTimeMillis())
        }
    }

    override suspend fun moveToWardrobe(id: Long, wardrobeId: Long) {
        dao.updateWardrobe(id, wardrobeId, System.currentTimeMillis())
    }

    override suspend fun updateCutoutPath(id: Long, path: String?) {
        dao.updateCutoutPath(id, path, System.currentTimeMillis())
    }

    override suspend fun setCutoutIfEmpty(id: Long, path: String) {
        dao.setCutoutIfEmpty(id, path, System.currentTimeMillis())
    }

    override suspend fun setColorsIfEmpty(id: Long, colors: List<String>) {
        if (colors.isEmpty()) return
        dao.setColorsIfEmpty(id, colors, emptyList(), System.currentTimeMillis())
    }

    override suspend fun applyAiSuggestionIfEmpty(
        id: Long,
        category: String?,
        styles: List<String>,
        seasons: List<String>,
    ) {
        dao.applyAiSuggestionIfEmpty(
            id = id,
            category = category,
            styles = styles,
            seasons = seasons,
            empty = emptyList(),
            updatedAt = System.currentTimeMillis(),
        )
    }

    override suspend fun deleteCrossRefsForItem(id: Long) {
        outfitDao.deleteCrossRefsByClothingItem(id)
    }

    override suspend fun deletePhysically(id: Long) {
        dao.deleteById(id)
    }

    override suspend fun rollbackFailedCreate(id: Long, code: String) {
        val normalized = normalizedOrRaw(code)
        db.withTransaction {
            outfitDao.deleteCrossRefsByClothingItem(id)
            dao.deleteById(id)
            codeHistoryDao.deleteIfHeldBy(normalized, id)
        }
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
     * 拼接展示名的兜底匹配在 [keywordFlow] 内完成（review D-j）。
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

    /** LIKE 通配符转义（review D-a）：DAO 侧统一 `ESCAPE '\'`，用户输入一律按字面匹配 */
    private fun escapeLike(token: String): String =
        token.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    /**
     * 单 token 的命中范围（DAO 片段 OR 组合，外加中英文枚举词翻译后的匹配）。
     *
     * review D-j：「主色 + 类别」拼接完整串（如「白色 · 衬衫」）不是任何单个字段的
     * 子串，字段级 LIKE 命中不了——额外 combine 一条全表 Flow，对展示名做内存
     * contains 兜底，与 DAO 命中取并集。个人衣柜量级（百~千件）开销可忽略。
     */
    private fun keywordFlow(token: String): Flow<List<ClothingItemEntity>> {
        // D-a：token 先转义再进 LIKE，输「%」「_」不再命中全表
        val safe = escapeLike(token)
        val codeFlow = dao.searchByCode(safe)
        val nameCategoryFlow = dao.searchByNameAndCategory(safe)
        val styleFlow = dao.searchByStyleTag(safe)
        val seasonFlow = dao.searchBySeasonTag(safe)

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
        return kotlinx.coroutines.flow.combine(flows + listOf(dao.observeAll())) { arrays ->
            val all = arrays.last()
            val merged = LinkedHashMap<Long, ClothingItemEntity>()
            arrays.dropLast(1).forEach { list -> list.forEach { merged[it.id] = it } }
            // 拼接展示名兜底（D-j）
            all.asSequence()
                .filter { entity -> entity.toDomain().displayName.contains(token) }
                .forEach { merged[it.id] = it }
            merged.values.sortedBy { it.code.toLongOrNull() ?: Long.MAX_VALUE }
        }
    }

    /** 结构化筛选（类别 / 颜色 / 风格）；拼接展示名的兜底匹配已在 [combineKeywordFlows] 完成 */
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
