package com.yuhinhsiung.clothesmate.feature.wardrobe.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yuhinhsiung.clothesmate.core.database.ClothesMateDatabase
import com.yuhinhsiung.clothesmate.core.database.entity.ClothingStatus
import com.yuhinhsiung.clothesmate.core.model.ClothingItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 衣物 Repository 测试 —— 开发计划 M1-10 / M1-13 验收项。
 *
 * 覆盖开发文档 W-06/W-07/W-09/W-13 的口径：
 * - 搜索「红 上衣」只返回同时命中两者的结果（空格分词 AND）
 * - 数字串命中编号
 * - 软删项不出现在任何列表 / 搜索
 * - 已收起项不出在推荐候选（getNormalItems）
 * - 结构筛选（类别 / 颜色 / 风格）与关键词叠加
 */
@RunWith(AndroidJUnit4::class)
class ClothingRepositoryTest {

    private lateinit var database: ClothesMateDatabase
    private lateinit var repository: ClothingRepository

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, ClothesMateDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = DefaultClothingRepository(database.clothingItemDao(), database.outfitDao())
        // 外键需要父行：先建两个衣柜
        database.wardrobeDao().insertAll(
            listOf(
                com.yuhinhsiung.clothesmate.core.database.entity.WardrobeEntity(
                    id = 1L, name = "主卧衣柜", note = null, coverImagePath = null,
                    sortOrder = 0, createdAt = 0L,
                ),
                com.yuhinhsiung.clothesmate.core.database.entity.WardrobeEntity(
                    id = 2L, name = "次卧衣柜", note = null, coverImagePath = null,
                    sortOrder = 1, createdAt = 0L,
                ),
            ),
        )
    }

    @After
    fun tearDown() = database.close()

    private fun item(
        code: String,
        wardrobeId: Long = 1L,
        name: String? = null,
        category: String? = "shirt",
        colors: List<String> = listOf("white"),
        styleTags: List<String> = listOf("休闲"),
        seasonTags: List<String> = listOf("spring"),
        status: String = ClothingStatus.NORMAL,
    ) = ClothingItem(
        id = 0L,
        code = code,
        wardrobeId = wardrobeId,
        categoryKey = category,
        colorKeys = colors,
        styleTags = styleTags,
        seasonTags = seasonTags,
        originalImagePath = "clothing/original/$code.jpg",
        cutoutImagePath = null,
        brand = null,
        name = name,
        note = null,
        wearCount = 0,
        lastWornAt = null,
        status = status,
        createdAt = 0L,
        updatedAt = 0L,
    )

    // ── W-07：搜索分词 AND ──────────────────────────────────────

    @Test
    fun `搜索_红上衣只返回同时命中颜色与类别的结果`() = runTest {
        repository.createItem(item("1", name = "红衬衫", colors = listOf("red")))   // 命中两者
        repository.createItem(item("2", name = "红裙子", colors = listOf("red"), category = "pants"))
        repository.createItem(item("3", name = "白衬衫", colors = listOf("white"))) // 只命中类别

        // "红 上衣"：红 命中 item1/2，上衣 命中 item1/3 → 交集只剩 1
        val hits = repository.search("红 上衣").first()
        assertEquals(listOf("1"), hits.map { it.code })
    }

    @Test
    fun `搜索_数字串命中编号`() = runTest {
        repository.createItem(item("102"))
        repository.createItem(item("210"))
        repository.createItem(item("999"))

        assertEquals(setOf("102", "210"), repository.search("10").first().map { it.code }.toSet())
        assertEquals(listOf("102"), repository.search("102").first().map { it.code })
    }

    @Test
    fun `搜索_跨衣柜`() = runTest {
        repository.createItem(item("1", wardrobeId = 1L, styleTags = listOf("复古")))
        repository.createItem(item("2", wardrobeId = 2L, styleTags = listOf("复古")))

        val hits = repository.search("复古").first()
        assertEquals(setOf("1", "2"), hits.map { it.code }.toSet())
    }

    @Test
    fun `搜索_命中名称拼接兜底`() = runTest {
        // 开发文档 §3.2 name：为空时展示与搜索一律用「主色 + 类别」拼接兜底。
        // 关键差别：SQL LIKE 只查得到 name 列，兜底名是展示层拼出来的，
        // 故 Repository 必须额外把「中文颜色名」「中文类别名」补进命中范围（否则永远搜不到）。
        repository.createItem(item("1", name = null, colors = listOf("red"), category = "shirt"))
        repository.createItem(item("2", name = null, colors = listOf("white"), category = "shirt"))

        val hits = repository.search("红色").first()
        assertEquals(listOf("1"), hits.map { it.code })
    }

    @Test
    fun `搜索_命中季节标签_key与中文都能命中`() = runTest {
        repository.createItem(item("1", seasonTags = listOf("winter")))
        repository.createItem(item("2", seasonTags = listOf("summer")))

        // W-07：用户既可输 key 也可输中文（季节词表 spring/summer/autumn/winter ↔ 春夏秋冬）
        assertEquals(listOf("1"), repository.search("winter").first().map { it.code })
        assertEquals(listOf("1"), repository.search("冬").first().map { it.code })
    }

    // ── W-13：软删过滤 ─────────────────────────────────────────

    @Test
    fun `软删项不出现在列表与搜索`() = runTest {
        val keep = repository.createItem(item("1", name = "保留"))
        val drop = repository.createItem(item("2", name = "删除"))
        // 为防「保留」这条也命中的是软删那条，改成明显不会撞的名字
        assertTrue(repository.softDelete(drop))

        assertEquals(listOf(keep), repository.observeAllItems().first().map { it.id })
        // 搜「删除」：命中的是软删项 → 结果应空（而不是空列表等于空的误判）
        assertTrue(repository.search("删除").first().isEmpty())
        // 搜「保留」仍命中的是未删项
        assertEquals(listOf(keep), repository.search("保留").first().map { it.id })

        // getItem 取不到软删项，但 getItemIncludingDeleted 取得到（占位卡要原编号）
        assertNull(repository.getItem(drop))
        assertEquals("2", repository.getItemIncludingDeleted(drop)?.code)
    }

    @Test
    fun `撤销后完整复活`() = runTest {
        val id = repository.createItem(item("1"))
        assertTrue(repository.softDelete(id))
        assertTrue(repository.observeAllItems().first().isEmpty())

        repository.restore(id)

        // deletedAt 应被清除，重新出现在列表
        assertEquals(listOf(id), repository.observeAllItems().first().map { it.id })
        assertEquals(id, repository.getItem(id)!!.id)
    }

    @Test
    fun `重复软删返回false`() = runTest {
        val id = repository.createItem(item("1"))
        assertTrue(repository.softDelete(id))
        assertFalse(repository.softDelete(id))
    }

    // ── W-09：收起状态 ─────────────────────────────────────────

    @Test
    fun `收起衣物不进推荐候选`() = runTest {
        val normal = repository.createItem(item("1"))
        val stored = repository.createItem(item("2", status = ClothingStatus.STORED))

        val candidates = repository.getNormalItems().map { it.id }
        assertTrue(normal in candidates)
        assertFalse(stored in candidates)
    }

    @Test
    fun `一键切换收起与恢复`() = runTest {
        val id = repository.createItem(item("1"))
        repository.toggleStored(id)
        assertEquals(ClothingStatus.STORED, repository.getItem(id)!!.status)
        repository.toggleStored(id)
        assertEquals(ClothingStatus.NORMAL, repository.getItem(id)!!.status)
    }

    // ── W-06：编号唯一性 ──────────────────────────────────────

    @Test
    fun `创建重复编号被拒`() = runTest {
        repository.createItem(item("42"))
        val error = runCatching { repository.createItem(item("42")) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `编辑时可保留自身编号`() = runTest {
        val id = repository.createItem(item("42"))
        val item2 = repository.createItem(item("43"))
        // 把 43 改成 42 应被拒（他人占用）
        val error = runCatching {
            repository.updateItem(repository.getItem(item2)!!.copy(code = "42"))
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)

        // 自身不变更则可通过
        repository.updateItem(repository.getItem(id)!!.copy(name = "改名"))
        assertEquals("改名", repository.getItem(id)!!.name)
    }

    // ── W-12：转移衣柜 ────────────────────────────────────────

    @Test
    fun `转移衣柜编号与图片路径不变`() = runTest {
        val id = repository.createItem(item("7", wardrobeId = 1L))
        repository.moveToWardrobe(id, 2L)

        val moved = repository.getItem(id)!!
        assertEquals("7", moved.code)
        assertEquals(2L, moved.wardrobeId)
        assertEquals("clothing/original/7.jpg", moved.originalImagePath)
    }

    // ── 状态筛选（M1-09 衣柜详情 chips）────────────────────────

    @Test
    fun `衣柜详情状态筛选_待完善判定为category为空`() = runTest {
        val normal = repository.createItem(item("1"))
        val storedId = repository.createItem(item("2", status = ClothingStatus.STORED))
        val incomplete = repository.createItem(item("3", category = null))

        // 待完善与 正常 是交叉维度（category 为空但 status 可为 normal），
        // 所以「正常」里含 item3、「全部」里含 3 件 —— chips 计数口径如此。
        assertEquals(3, repository.observeItemsInWardrobeByStatus(1L, WardrobeItemFilter.All)
            .first().size)
        assertEquals(
            setOf(normal, incomplete),
            repository.observeItemsInWardrobeByStatus(1L, WardrobeItemFilter.Normal)
                .first().map { it.id }.toSet(),
        )
        assertEquals(
            listOf(storedId),
            repository.observeItemsInWardrobeByStatus(1L, WardrobeItemFilter.Stored)
                .first().map { it.id },
        )
        assertEquals(
            listOf(incomplete),
            repository.observeItemsInWardrobeByStatus(1L, WardrobeItemFilter.Incomplete)
                .first().map { it.id },
        )
    }

    // ── 展示名兜底（开发文档 §3.2 name）────────────────────────

    @Test
    fun `展示名_自定义优先否则主色加类别拼接`() = runTest {
        repository.createItem(item("1", name = "最爱的衬衫", colors = listOf("white"), category = "shirt"))
        repository.createItem(item("2", name = null, colors = listOf("red"), category = "shirt"))

        val all = repository.observeAllItems().first().associateBy { it.code }
        assertEquals("最爱的衬衫", all["1"]!!.displayName)
        // 兜底名用词表里的 displayName（「红」而非「红色」）
        assertEquals("红 · 衬衫", all["2"]!!.displayName)
    }

    @Test
    fun `待完善判定_category为空`() = runTest {
        repository.createItem(item("1", category = "shirt"))
        val incomplete = repository.createItem(item("2", category = null))
        assertTrue(repository.getItem(incomplete)!!.isIncomplete)
        assertFalse(repository.getItem(1L)!!.isIncomplete)
    }
}
