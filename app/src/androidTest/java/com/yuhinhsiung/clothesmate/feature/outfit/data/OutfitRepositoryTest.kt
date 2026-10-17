package com.yuhinhsiung.clothesmate.feature.outfit.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yuhinhsiung.clothesmate.core.database.ClothesMateDatabase
import com.yuhinhsiung.clothesmate.core.database.entity.ClothingItemEntity
import com.yuhinhsiung.clothesmate.core.database.entity.OutfitCategoryEntity
import com.yuhinhsiung.clothesmate.core.database.entity.OutfitEntity
import com.yuhinhsiung.clothesmate.core.database.entity.WardrobeEntity
import com.yuhinhsiung.clothesmate.core.model.OutfitDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 穿搭 Repository 测试 —— 开发计划 M1-17 / M1-20 验收项。
 *
 * 覆盖：
 * - **M1-20 核心**：删除穿搭后，不再被任何现存穿搭引用的软删除衣物被物理删除
 *   （行 + cross ref），仍被引用的保留（W-13 连带规则）
 * - 删除分类只解除关联，穿搭行保留（O-05）；无关联穿搭归「未分类」
 * - 创建 / 编辑 roundtrip（衣物与分类关联重建）
 * - 分类封面取最新一套有照片穿搭（O-02）
 *
 * ⚠️ 口径边界（review §6.3）：M1-20 验收原文为「行 + cross ref + **图片**」。
 * 本测试锁定 DB 两半；**图片半截由 UI 层经 `onDeleted` 回调接线**（review B-03 修复，
 * 见 OutfitDetailViewModel.delete），文件层的路径与删除语义由 core/image 的
 * ImageStorageTest 锁定；「删除穿搭 → 文件消失」的端到端为真机走查取证
 * （`run-as … find files/images`），不在 JVM/Room 单测射程内。
 */
@RunWith(AndroidJUnit4::class)
class OutfitRepositoryTest {

    private lateinit var database: ClothesMateDatabase
    private lateinit var repository: OutfitRepository

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, ClothesMateDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = DefaultOutfitRepository(
            database,
            database.outfitDao(),
            database.outfitCategoryDao(),
            database.clothingItemDao(),
            database.wardrobeDao(),
        )
        // 外键需要父行：衣柜 + 两件衣物
        database.wardrobeDao().insertAll(
            listOf(
                WardrobeEntity(
                    id = 1L, name = "主卧衣柜", note = null, coverImagePath = null,
                    sortOrder = 0, createdAt = 0L,
                ),
            ),
        )
        database.clothingItemDao().insertAll(
            listOf(
                clothingEntity(id = 101L, code = "1"),
                clothingEntity(id = 102L, code = "2"),
                clothingEntity(id = 103L, code = "3"),
            ),
        )
    }

    @After
    fun tearDown() = database.close()

    private fun clothingEntity(
        id: Long,
        code: String,
        deletedAt: Long? = null,
    ) = ClothingItemEntity(
        id = id,
        code = code,
        wardrobeId = 1L,
        category = "shirt",
        colors = listOf("white"),
        styleTags = listOf("休闲"),
        seasonTags = listOf("spring"),
        originalImagePath = "clothing/original/$code.jpg",
        cutoutImagePath = null,
        brand = null,
        name = null,
        note = null,
        deletedAt = deletedAt,
        createdAt = 0L,
        updatedAt = 0L,
    )

    private suspend fun outfitEntity(
        id: Long,
        createdAt: Long,
        photos: List<String> = emptyList(),
    ): Long = database.outfitDao().insert(
        OutfitEntity(
            id = id,
            photoPaths = photos,
            styleTags = listOf("休闲"),
            sceneTags = emptyList(),
            seasonTags = listOf("spring"),
            createdAt = createdAt,
            updatedAt = createdAt,
        ),
    )

    private fun draft(
        clothingIds: List<Long>,
        categoryIds: List<Long> = emptyList(),
        photos: List<String> = emptyList(),
    ) = OutfitDraft(
        photoPaths = photos,
        styleTags = listOf("简约"),
        sceneTags = listOf("通勤"),
        seasonTags = listOf("spring"),
        note = null,
        clothingIds = clothingIds,
        categoryIds = categoryIds,
    )

    // ── W-13 连带物理清除（M1-20 核心） ─────────────────────────

    @Test
    fun deleteOutfitPhysicallyClearsSoftDeletedItemsNoLongerReferenced() = runTest {
        val outfitId = outfitEntity(1L, createdAt = 0L)
        database.outfitDao().insertCrossRefs(
            listOf(
                com.yuhinhsiung.clothesmate.core.database.entity.OutfitItemCrossRef(outfitId, 101L),
                com.yuhinhsiung.clothesmate.core.database.entity.OutfitItemCrossRef(outfitId, 102L),
            ),
        )
        // 101 软删且只被本穿搭引用 → 应被物理清除；102 正常 → 保留
        database.clothingItemDao().update(
            clothingEntity(id = 101L, code = "1", deletedAt = 123L),
        )

        val cleared = repository.deleteOutfit(outfitId)

        assertEquals(1, cleared)
        // 101 已物理删除（findByIdsIncludingDeleted 也查不到）
        assertNull(database.clothingItemDao().findById(101L))
        // 102 保留
        assertNotNull(database.clothingItemDao().findById(102L))
        // 关联行全清
        assertTrue(database.outfitDao().getClothingIds(outfitId).isEmpty())
    }

    @Test
    fun deleteOutfitKeepsSoftDeletedItemsStillReferencedByOtherOutfits() = runTest {
        val outfitA = outfitEntity(1L, createdAt = 0L)
        val outfitB = outfitEntity(2L, createdAt = 1L)
        database.outfitDao().insertCrossRefs(
            listOf(
                com.yuhinhsiung.clothesmate.core.database.entity.OutfitItemCrossRef(outfitA, 101L),
                com.yuhinhsiung.clothesmate.core.database.entity.OutfitItemCrossRef(outfitB, 101L),
            ),
        )
        database.clothingItemDao().update(
            clothingEntity(id = 101L, code = "1", deletedAt = 123L),
        )

        val cleared = repository.deleteOutfit(outfitA)

        assertEquals(0, cleared)
        // 101 仍被 outfitB 引用 → 软删行保留
        assertNotNull(database.clothingItemDao().findById(101L))
    }

    @Test
    fun countClearableDeletedItemsMatchesPendingClearance() = runTest {
        val outfitId = outfitEntity(1L, createdAt = 0L)
        database.outfitDao().insertCrossRefs(
            listOf(
                com.yuhinhsiung.clothesmate.core.database.entity.OutfitItemCrossRef(outfitId, 101L),
                com.yuhinhsiung.clothesmate.core.database.entity.OutfitItemCrossRef(outfitId, 102L),
            ),
        )
        database.clothingItemDao().update(
            clothingEntity(id = 101L, code = "1", deletedAt = 123L),
        )

        assertEquals(1, repository.countClearableDeletedItems(outfitId))
        assertEquals(0, repository.countClearableDeletedItems(404L))
    }

    // ── 分类（O-05 / O-02） ─────────────────────────────────────

    @Test
    fun deleteCategoryOnlyRemovesAssociationOutfitKept() = runTest {
        val categoryId = database.outfitCategoryDao().insert(
            OutfitCategoryEntity(name = "通勤", sortOrder = 0, createdAt = 0L),
        )
        val outfitId = repository.createOutfit(draft(clothingIds = listOf(101L), categoryIds = listOf(categoryId)))

        repository.deleteCategory(categoryId)

        // 分类行删除；穿搭行保留；关联清除 → 归「未分类」
        assertNull(database.outfitCategoryDao().findById(categoryId))
        assertNotNull(database.outfitDao().findById(outfitId))
        assertTrue(
            database.outfitCategoryDao().observeAllRefs().first()
                .none { it.outfitId == outfitId },
        )
        // 穿搭的衣物关联不受影响
        assertEquals(listOf(101L), database.outfitDao().getClothingIds(outfitId))
    }

    @Test
    fun updateOutfitRebuildsBothCrossRefs() = runTest {
        val catA = database.outfitCategoryDao().insert(
            OutfitCategoryEntity(name = "A", sortOrder = 0, createdAt = 0L),
        )
        val catB = database.outfitCategoryDao().insert(
            OutfitCategoryEntity(name = "B", sortOrder = 1, createdAt = 1L),
        )
        val outfitId = repository.createOutfit(
            draft(clothingIds = listOf(101L), categoryIds = listOf(catA)),
        )

        repository.updateOutfit(
            outfitId,
            draft(clothingIds = listOf(102L, 103L), categoryIds = listOf(catB)),
        )

        assertEquals(listOf(102L, 103L), database.outfitDao().getClothingIds(outfitId))
        assertEquals(
            listOf(catB),
            database.outfitCategoryDao().getCategoryIdsForOutfit(outfitId),
        )
    }

    @Test
    fun observeCategoriesCoverEqualsLatestOutfitWithPhotos() = runTest {
        val categoryId = database.outfitCategoryDao().insert(
            OutfitCategoryEntity(name = "通勤", sortOrder = 0, createdAt = 0L),
        )
        // 旧的有照片、新的没照片 → 封面应取「最新一套**有照片**」
        outfitEntity(10L, createdAt = 100L, photos = listOf("outfit/10/0.jpg"))
        outfitEntity(11L, createdAt = 200L, photos = emptyList())
        database.outfitCategoryDao().insertCrossRefs(
            listOf(
                com.yuhinhsiung.clothesmate.core.database.entity.OutfitCategoryCrossRef(10L, categoryId),
                com.yuhinhsiung.clothesmate.core.database.entity.OutfitCategoryCrossRef(11L, categoryId),
            ),
        )

        val categories = repository.observeCategories().first()
        val target = categories.first { it.id == categoryId }

        assertEquals(2, target.outfitCount)
        assertEquals("outfit/10/0.jpg", target.coverPath)
    }

    @Test
    fun softDeletedItemsAreNotCountedInDetailItems() = runTest {
        val outfitId = outfitEntity(1L, createdAt = 0L)
        database.outfitDao().insertCrossRefs(
            listOf(
                com.yuhinhsiung.clothesmate.core.database.entity.OutfitItemCrossRef(outfitId, 101L),
                com.yuhinhsiung.clothesmate.core.database.entity.OutfitItemCrossRef(outfitId, 102L),
            ),
        )
        database.clothingItemDao().update(
            clothingEntity(id = 102L, code = "2", deletedAt = 9L),
        )

        val detail = repository.observeOutfitDetail(outfitId).first()

        assertNotNull(detail)
        // 101 正常 + 102 软删占位（保留编号）
        assertEquals(2, detail!!.items.size)
        assertTrue(detail.items.first { it.itemId == 102L }.deleted)
        assertEquals("2", detail.items.first { it.itemId == 102L }.code)
        assertFalse(detail.items.first { it.itemId == 101L }.deleted)
        // 有效件数不含软删占位
        assertEquals(1, detail.outfit.itemCount)
        assertEquals(1, detail.deletedItemCount)
    }

    // ── review R-06：衣物选择顺序必须持久化 ──────────────────────

    @Test
    fun createOutfitPersistsClothingSelectionOrder() = runTest {
        val outfitId = repository.createOutfit(draft(clothingIds = listOf(103L, 101L, 102L)))

        assertEquals(
            "保存后顺序必须与选择顺序一致（原先按 SQLite 扫描序，重进详情就变序）",
            listOf(103L, 101L, 102L),
            database.outfitDao().getClothingIds(outfitId),
        )
        // 详情联查（IN (...) 不保序）也按同一顺序重建
        val detail = repository.observeOutfitDetail(outfitId).first()!!
        assertEquals(listOf(103L, 101L, 102L), detail.items.map { it.itemId })
    }

    @Test
    fun updateOutfitKeepsSelectionOrderAfterRebuild() = runTest {
        val outfitId = repository.createOutfit(draft(clothingIds = listOf(101L, 102L, 103L)))

        repository.updateOutfit(outfitId, draft(clothingIds = listOf(102L, 103L, 101L)))

        assertEquals(listOf(102L, 103L, 101L), repository.getClothingIds(outfitId))
        assertEquals(
            listOf(102L, 103L, 101L),
            repository.observeOutfitDetail(outfitId).first()!!.items.map { it.itemId },
        )
    }

    @Test
    fun removingMiddleItemKeepsRemainingOrder() = runTest {
        val outfitId = repository.createOutfit(draft(clothingIds = listOf(103L, 101L, 102L)))

        repository.updateOutfit(outfitId, draft(clothingIds = listOf(103L, 102L)))

        assertEquals(listOf(103L, 102L), repository.getClothingIds(outfitId))
    }

    // ── review R-11：件数口径三处一致 ───────────────────────────

    @Test
    fun itemCountExcludesSoftDeletedAndRefreshesOnRestore() = runTest {
        val categoryId = database.outfitCategoryDao().insert(
            OutfitCategoryEntity(name = "通勤", sortOrder = 0, createdAt = 0L),
        )
        val outfitId = repository.createOutfit(
            draft(clothingIds = listOf(101L, 102L, 103L), categoryIds = listOf(categoryId)),
        )

        // 三处口径必须一致
        assertEquals(3, repository.observeOutfits().first().single().itemCount)
        assertEquals(3, repository.observeOutfitsInCategory(categoryId).first().single().itemCount)
        assertEquals(3, repository.observeOutfitDetail(outfitId).first()!!.outfit.itemCount)

        // 软删一件：全部 / 分类内 / 详情都变 2，绝不能出现 0
        database.clothingItemDao().update(clothingEntity(id = 102L, code = "2", deletedAt = 5L))
        assertEquals(2, repository.observeOutfits().first().single().itemCount)
        assertEquals(2, repository.observeOutfitsInCategory(categoryId).first().single().itemCount)
        assertEquals(2, repository.observeOutfitDetail(outfitId).first()!!.outfit.itemCount)

        // 撤销软删：回到 3（值变化即证明订阅链是活的）
        database.clothingItemDao().restore(102L, updatedAt = 6L)
        assertEquals(3, repository.observeOutfits().first().single().itemCount)
        assertEquals(3, repository.observeOutfitsInCategory(categoryId).first().single().itemCount)
    }

    // ── review R-11：**持续订阅**必须随衣物表变化重发 ─────────────

    /**
     * 与 [itemCountExcludesSoftDeletedAndRefreshesOnRestore] 的关键差别：那条每次 `.first()`
     * **重新订阅**，只能证明「重查正确」；本条**保持同一个订阅**，才证明详情是实时刷新的。
     *
     * 旧实现只组合 outfit / wardrobe / category 三张表，衣物是一次性查出来的，
     * 所以软删衣物后这个订阅会一直停在 1 件、占位卡也不出现（review R-11 独立探针抓出）。
     *
     * 用 `runBlocking` + 真实等待而非 `runTest` 的虚拟时间：Room 的 Flow 在别的线程发射，
     * 虚拟时钟会在真实发射到达之前就把 `withTimeout` 走完，测出来的是假超时。
     */
    @Test
    fun activeDetailSubscriptionRefreshesOnSoftDeleteAndRestore() = runBlocking {
        val outfitId = repository.createOutfit(draft(clothingIds = listOf(101L, 102L)))

        val counts = MutableStateFlow<Int?>(null)
        val collectJob = launch(Dispatchers.Default) {
            repository.observeOutfitDetail(outfitId).collect { counts.value = it?.outfit?.itemCount }
        }
        try {
            withTimeout(5_000) { counts.filterNotNull().first() }
            assertEquals(2, counts.value)

            // 同一个订阅：软删一件 → 立刻变 1（占位卡同步出现）
            database.clothingItemDao().update(clothingEntity(id = 102L, code = "2", deletedAt = 9L))
            assertTrue(
                "R-11：详情订阅在软删后必须自动重发（停在 ${counts.value}）",
                withTimeoutOrNull(2_000) { counts.filter { it == 1 }.first(); true } ?: false,
            )
            val softDeleted = repository.observeOutfitDetail(outfitId).first()!!
            assertTrue("占位卡必须同步出现", softDeleted.items.first { it.itemId == 102L }.deleted)

            // 撤销 → 立刻回到 2
            database.clothingItemDao().restore(102L, updatedAt = 10L)
            assertTrue(
                "R-11：撤销后必须回到 2 件（停在 ${counts.value}）",
                withTimeoutOrNull(2_000) { counts.filter { it == 2 }.first(); true } ?: false,
            )
        } finally {
            collectJob.cancelAndJoin()
        }
    }

    /** 改名也要实时反映到打开的详情上，不能靠「退出很久再重进」 */
    @Test
    fun activeDetailSubscriptionRefreshesOnClothingRename() = runBlocking {
        val outfitId = repository.createOutfit(draft(clothingIds = listOf(101L)))

        val names = MutableStateFlow<String?>(null)
        val collectJob = launch(Dispatchers.Default) {
            repository.observeOutfitDetail(outfitId).collect { names.value = it?.items?.firstOrNull()?.displayName }
        }
        try {
            withTimeout(5_000) { names.filterNotNull().first() }

            database.clothingItemDao().update(clothingEntity(id = 101L, code = "1").copy(name = "旧蓝毛衣"))
            assertTrue(
                "R-11：改名必须实时反映（停在 ${names.value}）",
                withTimeoutOrNull(2_000) { names.filter { it == "旧蓝毛衣" }.first(); true } ?: false,
            )
        } finally {
            collectJob.cancelAndJoin()
        }
    }

    // ── review R-13：多表操作必须原子 ───────────────────────────

    /**
     * 注入方式：衣物关联里塞一个不存在的衣物 id。`outfit_item_cross_ref.clothingItemId`
     * 的外键是 NO_ACTION（SQLite 的 ON CONFLICT 不覆盖外键），写入必然失败。
     *
     * 修复前的行为：主行已经插入成功、异常抛出时表单还没拿到 id，补偿逻辑找不到那条
     * 孤儿穿搭——库里留下一条无关联的穿搭。现在整段在一个事务里，必须零残留。
     */
    @Test
    fun createOutfitRollsBackEntirelyWhenCrossRefWriteFails() = runTest {
        val error = runCatching {
            repository.createOutfit(draft(clothingIds = listOf(101L, 999_999L)))
        }.exceptionOrNull()

        assertNotNull("外键不存在，写入应当失败", error)
        assertEquals("不能留下孤儿穿搭主行", 0, database.outfitDao().count())
        assertTrue(database.outfitDao().getAllItemRefs().isEmpty())
    }

    @Test
    fun updateOutfitRollsBackAndKeepsOldRefsWhenNewRefsFail() = runTest {
        val catA = database.outfitCategoryDao().insert(
            OutfitCategoryEntity(name = "A", sortOrder = 0, createdAt = 0L),
        )
        val outfitId = repository.createOutfit(
            draft(clothingIds = listOf(101L, 102L), categoryIds = listOf(catA)),
        )

        val error = runCatching {
            repository.updateOutfit(
                outfitId,
                draft(clothingIds = listOf(103L, 999_999L), categoryIds = emptyList()),
            )
        }.exceptionOrNull()

        assertNotNull(error)
        assertEquals(
            "旧衣物关联必须完整保留（原先「先删后插」失败会永久丢失）",
            listOf(101L, 102L),
            repository.getClothingIds(outfitId),
        )
        assertEquals(
            "旧分类关联同样保留",
            listOf(catA),
            repository.getCategoryIdsForOutfit(outfitId),
        )
    }
}
