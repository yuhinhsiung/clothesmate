package com.yuhinhsiung.clothesmate.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yuhinhsiung.clothesmate.core.database.ClothesMateDatabase
import com.yuhinhsiung.clothesmate.core.database.entity.ClothingItemEntity
import com.yuhinhsiung.clothesmate.core.model.ClothingStatus
import com.yuhinhsiung.clothesmate.core.database.entity.OutfitItemCrossRef
import com.yuhinhsiung.clothesmate.core.database.entity.WardrobeEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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
 * 衣物 DAO 集成测试 —— Room 内存库，用真实 UNIQUE 约束与索引。
 *
 * 重点守住两件容易回归的事：
 * 1. **review C-01**：`countByCode` 的判定范围必须覆盖软删行（code 是全表 UNIQUE 索引）。
 *    若有人加回 `deletedAt IS NULL` 过滤，[软删占用的编号仍判定为已占用] 会失败。
 * 2. **W-13**：软删项不出现在任何列表/搜索；撤销后完整复活。
 *
 * 用 [org.junit.runner.RunWith] AndroidJUnit4 + ApplicationProvider 需要 instrumentation，
 * 故本文件位于 `src/androidTest`（真库约束，Robolectric 不是选项）。
 */
@RunWith(AndroidJUnit4::class)
class ClothingItemDaoTest {

    private lateinit var database: ClothesMateDatabase
    private lateinit var dao: ClothingItemDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, ClothesMateDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.clothingItemDao()
    }

    @After
    fun tearDown() = database.close()

    private suspend fun wardrobe(id: Long = 1L): Long =
        database.wardrobeDao().insert(
            WardrobeEntity(
                id = id,
                name = "主卧衣柜",
                note = null,
                coverImagePath = null,
                sortOrder = 0,
                createdAt = 0L,
            ),
        )

    private fun item(code: String, wardrobeId: Long) = ClothingItemEntity(
        code = code,
        wardrobeId = wardrobeId,
        category = "shirt",
        colors = listOf("white"),
        styleTags = listOf("休闲"),
        seasonTags = listOf("spring"),
        originalImagePath = "clothing/original/$code.jpg",
        cutoutImagePath = null,
        brand = null,
        name = null,
        note = null,
        wearCount = 0,
        lastWornAt = null,
        status = ClothingStatus.NORMAL,
        deletedAt = null,
        createdAt = 0L,
        updatedAt = 0L,
    )

    // ── C-01：编号唯一性判定覆盖软删行 ──────────────────────────

    @Test
    fun `软删占用的编号仍判定为已占用`() = runTest {
        wardrobe()
        val id = dao.insert(item("102", wardrobeId = 1L))
        // 软删
        val deleted = dao.findById(id)!!.copy(deletedAt = System.currentTimeMillis())
        dao.update(deleted)

        // 全表唯一口径：即使已软删，102 仍不可用（编号不回收）
        assertEquals(1, dao.countByCode("102"))
        assertFalse(dao.countByCode("102", excludeId = 999L) == 0)
    }

    @Test
    fun `编辑自身编号时可通过唯一性校验`() = runTest {
        wardrobe()
        val id = dao.insert(item("7", wardrobeId = 1L))

        // 排除自身 → 可用（否则编辑页永远无法保存）
        assertEquals(0, dao.countByCode("7", excludeId = id))
        // 不排除自身 → 已被自己占用
        assertEquals(1, dao.countByCode("7", excludeId = 0L))
    }

    @Test
    fun `插入重复编号抛SQLiteConstraintException`() = runTest {
        wardrobe()
        dao.insert(item("50", wardrobeId = 1L))
        val error = runCatching { dao.insert(item("50", wardrobeId = 1L)) }.exceptionOrNull()
        assertNotNull("code 全表唯一，重复插入应失败", error)
    }

    // ── W-13：软删过滤与撤销 ───────────────────────────────────

    @Test
    fun `软删项不出现在任何列表查询`() = runTest {
        wardrobe()
        val keep = dao.insert(item("1", wardrobeId = 1L))
        val drop = dao.insert(item("2", wardrobeId = 1L))
        dao.update(dao.findById(drop)!!.copy(deletedAt = 1L))

        assertEquals(listOf(keep), dao.observeByWardrobe(1L).first().map { it.id })
        assertEquals(listOf(keep), dao.observeAll().first().map { it.id })
        // 但 findById 仍能取到（穿搭详情「已删除」占位卡要拿原编号）
        assertNotNull(dao.findById(drop))
    }

    @Test
    fun `撤销软删除后完整复活`() = runTest {
        wardrobe()
        val id = dao.insert(item("3", wardrobeId = 1L))
        dao.update(dao.findById(id)!!.copy(deletedAt = 5L))
        assertEquals(emptyList<Long>(), dao.observeAll().first().map { it.id })

        dao.restore(id, updatedAt = 10L)

        val restored = dao.findById(id)!!
        assertNull(restored.deletedAt)
        assertEquals(listOf(id), dao.observeAll().first().map { it.id })
    }

    @Test
    fun `物理删除需先清理cross_ref否则外键约束失败`() = runTest {
        // 这是 review C-03 记录的缺口场景：FK 为 NO_ACTION，不先删关联会约束失败
        wardrobe()
        val clothingId = dao.insert(item("9", wardrobeId = 1L))
        val outfitId = database.outfitDao().insert(
            com.yuhinhsiung.clothesmate.core.database.entity.OutfitEntity(
                photoPaths = emptyList(),
                styleTags = emptyList(),
                sceneTags = emptyList(),
                seasonTags = emptyList(),
                createdAt = 0L,
                updatedAt = 0L,
            ),
        )
        database.outfitDao().insertCrossRef(OutfitItemCrossRef(outfitId, clothingId))

        // 不清理关联直接删衣物 → 约束失败
        val withoutCleanup = runCatching { dao.deleteById(clothingId) }.exceptionOrNull()
        assertNotNull("残留 cross ref 时物理删除应失败", withoutCleanup)

        // 先清关联再删 → 成功
        database.outfitDao().deleteCrossRefsByClothingItem(clothingId)
        dao.deleteById(clothingId)
        assertNull(dao.findById(clothingId))
    }

    // ── 穿着次数联动（T-05 第三处更新，M3-06 用）─────────────────

    @Test
    fun `incrementWearCount同时更新lastWornAt`() = runTest {
        wardrobe()
        val id = dao.insert(item("11", wardrobeId = 1L))
        dao.incrementWearCount(id, today = 12345L)

        val worn = dao.findById(id)!!
        assertEquals(1, worn.wearCount)
        assertEquals(12345L, worn.lastWornAt)
    }

    // ── 状态查询（W-09 / 换季 / 兜底抽取）──────────────────────

    @Test
    fun `收起状态只被getByStatus取到且默认查询过滤`() = runTest {
        wardrobe()
        val normalId = dao.insert(item("20", wardrobeId = 1L))
        val storedId = dao.insert(item("21", wardrobeId = 1L))
        dao.updateStatus(storedId, ClothingStatus.STORED, updatedAt = 0L)

        val normal = dao.getByStatus(ClothingStatus.NORMAL).map { it.id }
        val stored = dao.getByStatus(ClothingStatus.STORED).map { it.id }
        assertEquals(listOf(normalId), normal)
        assertEquals(listOf(storedId), stored)
    }

    @Test
    fun `更新衣柜时编号与图片路径不变`() = runTest {
        wardrobe(id = 1L)
        wardrobe(id = 2L)
        val id = dao.insert(item("30", wardrobeId = 1L))
        dao.updateWardrobe(id, wardrobeId = 2L, updatedAt = 0L)

        val moved = dao.findById(id)!!
        assertEquals("30", moved.code)
        assertEquals(2L, moved.wardrobeId)
        assertEquals("clothing/original/30.jpg", moved.originalImagePath)
    }

    // ── 待完善判定与统计 ────────────────────────────────────────

    @Test
    fun `category为空即待完善`() = runTest {
        wardrobe()
        val complete = dao.insert(item("40", wardrobeId = 1L))
        val incomplete = dao.insert(item("41", wardrobeId = 1L).copy(category = null))

        val ids = dao.observeIncomplete().first().map { it.id }
        assertEquals(listOf(incomplete), ids)
        assertTrue(ids.none { it == complete })
    }

    @Test
    fun `类别统计不含软删与空类别`() = runTest {
        wardrobe()
        dao.insert(item("50", wardrobeId = 1L).copy(category = "shirt"))
        dao.insert(item("51", wardrobeId = 1L).copy(category = "shirt"))
        dao.insert(item("52", wardrobeId = 1L).copy(category = "pants"))
        val dropped = dao.insert(item("53", wardrobeId = 1L).copy(category = "shirt"))
        dao.update(dao.findById(dropped)!!.copy(deletedAt = 1L))
        dao.insert(item("54", wardrobeId = 1L).copy(category = null))

        val counts = dao.observeCategoryCounts().first().associate { it.category to it.count }
        assertEquals(2, counts["shirt"])
        assertEquals(1, counts["pants"])
        assertFalse(counts.containsKey(null))
    }

    @Test
    fun `颜色统计按JSON包含匹配`() = runTest {
        wardrobe()
        dao.insert(item("60", wardrobeId = 1L).copy(colors = listOf("white", "black")))
        dao.insert(item("61", wardrobeId = 1L).copy(colors = listOf("white")))
        dao.insert(item("62", wardrobeId = 1L).copy(colors = listOf("black")))

        assertEquals(2, dao.observeColorCount("white").first())
        assertEquals(2, dao.observeColorCount("black").first())
    }

    // ── 搜索片段方法（W-07，M1-10 用）──────────────────────────

    @Test
    fun `搜索片段各字段独立命中`() = runTest {
        wardrobe()
        dao.insert(item("70", wardrobeId = 1L).copy(name = "白衬衫", styleTags = listOf("通勤")))
        dao.insert(item("71", wardrobeId = 1L).copy(colors = listOf("red"), seasonTags = listOf("winter")))

        // 编号搜索是子串匹配（LIKE '%q%'），"7" 会同时命中 70 与 71
        assertEquals(setOf("70", "71"), dao.searchByCode("7").first().map { it.code }.toSet())
        assertEquals(listOf("70"), dao.searchByCode("70").first().map { it.code })
        assertEquals(1, dao.searchByNameAndCategory("白").first().size)
        assertEquals(1, dao.searchByColorKey("red").first().size)
        assertEquals(1, dao.searchByStyleTag("通勤").first().size)
        assertEquals(1, dao.searchBySeasonTag("winter").first().size)
    }
}
