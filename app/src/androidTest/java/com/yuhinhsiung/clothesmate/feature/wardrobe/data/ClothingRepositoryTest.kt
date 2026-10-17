package com.yuhinhsiung.clothesmate.feature.wardrobe.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yuhinhsiung.clothesmate.core.database.ClothesMateDatabase
import com.yuhinhsiung.clothesmate.core.model.ClothingStatus
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
        repository = DefaultClothingRepository(database, database.clothingItemDao(), database.outfitDao(), database.codeHistoryDao())
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

    // ── review 修复回归锁定 ────────────────────────────────────

    /** review D-f：「007」与「7」必须视为同一编号（纯数字 + 去前导零归一） */
    @Test
    fun `编号_007归一为7且与7冲突`() = runTest {
        val id = repository.createItem(item("007"))
        assertEquals("入库即归一", "7", repository.getItem(id)!!.code)

        // 归一后 007 与 7 是同一个编号，唯一性校验按归一值拦截
        val failure = runCatching { repository.createItem(item("7")) }
        assertTrue("007 与 7 应视为同号", failure.isFailure)
        assertTrue(
            "拒绝原因应是编号冲突而非格式",
            failure.exceptionOrNull()?.message?.contains("已被使用") == true,
        )
    }

    /** review D-f：非纯数字编号在写入侧被拒 */
    @Test
    fun `编号_非纯数字被拒`() = runTest {
        assertTrue(runCatching { repository.createItem(item("10a")) }.isFailure)
        val id = repository.createItem(item(" 012 "))
        assertEquals("带空白数字允许且去前导零", "12", repository.getItem(id)!!.code)
    }

    /** review D-j：「主色 · 类别」拼接完整串整句搜索要能命中（字段级 LIKE 够不着） */
    @Test
    fun `搜索_拼接展示名完整串命中`() = runTest {
        repository.createItem(item("1", colors = listOf("white"), category = "shirt"))
        repository.createItem(item("2", colors = listOf("red"), category = "shirt"))

        // 整句输入会被分词成「白」「·」「衬衫」三段 AND：
        // 「·」单段没有任何字段能 LIKE 命中，全靠展示名 contains 兜底收口
        val hits = repository.search("白 · 衬衫").first()
        assertEquals(listOf("1"), hits.map { it.code })
    }

    /** review D-a：LIKE 通配符按字面匹配，输「%」不再命中全表 */
    @Test
    fun `搜索_通配符按字面匹配`() = runTest {
        repository.createItem(item("1", colors = listOf("white"), category = "shirt"))
        repository.createItem(item("2", colors = listOf("red"), category = "pants"))

        // 「%」是字面字符，任何字段都不含它 → 0 命中（修复前会命中全部）
        assertTrue(repository.search("%").first().isEmpty())
        assertTrue(repository.search("_").first().isEmpty())
    }

    // ── review R-03：编号历史占用（物理删除后也不回收） ──────────

    /** 物理删除会抹掉唯一性校验的唯一证据——本用例是 R-03 的现场复现。 */
    @Test
    fun `物理删除后旧编号不可被另一件衣物重新使用`() = runTest {
        val id = repository.createItem(item("500"))
        repository.softDelete(id)
        repository.deleteCrossRefsForItem(id)
        repository.deletePhysically(id)

        assertNull(database.clothingItemDao().findById(id))
        assertFalse("物理删除后 500 仍是已占用", repository.isCodeAvailable("500"))
        assertTrue("自动发号的冲突判定同样要看到它", repository.isCodeTaken("500"))

        val error = runCatching { repository.createItem(item("500")) }.exceptionOrNull()
        assertTrue("另一件衣物不能拿走这个号", error is IllegalArgumentException)
    }

    /** 关闭条件之一：手动高位号不能把自动序列推高。 */
    @Test
    fun `历史占用不推进自动序列`() = runTest {
        repository.createItem(item("500"))

        // 计数器内是外部的 CodeCounterStore；这里锁的是 Repository 侧的口径：
        // 500 被判为已占用，但 1、2、3…的可用性完全不受影响
        assertTrue(repository.isCodeTaken("500"))
        assertFalse(repository.isCodeTaken("1"))
        assertFalse(repository.isCodeTaken("2"))
    }

    /** 关闭条件之一：改号后旧号仍被占用，新号归本件。 */
    @Test
    fun `改号后旧号仍不可被他人使用`() = runTest {
        val id = repository.createItem(item("7"))
        val created = repository.getItem(id)!!

        repository.updateItem(created.copy(code = "9"))

        assertEquals("9", repository.getItem(id)!!.code)
        assertFalse("旧号 7 不回收", repository.isCodeAvailable("7", excludeId = 0L))
        // 本件自己可以改回自己用过的号（历史行的 holder 仍是它）
        assertTrue(repository.isCodeAvailable("7", excludeId = id))
        // 别人不行
        assertFalse(repository.isCodeAvailable("7", excludeId = 12345L))
    }

    /** 关闭条件之一：软删除 + 撤销两个阶段都不放号。 */
    @Test
    fun `软删除与撤销期间编号始终不可被占用`() = runTest {
        val id = repository.createItem(item("42"))

        repository.softDelete(id)
        assertFalse(repository.isCodeAvailable("42"))

        repository.restore(id)
        assertFalse(repository.isCodeAvailable("42"))
        // 撤销后持有人仍是本件
        assertTrue(repository.isCodeAvailable("42", excludeId = id))
    }

    @Test
    fun `创建编号时同步登记历史占用`() = runTest {
        val id = repository.createItem(item("8"))
        assertEquals(id, database.codeHistoryDao().holderOf("8"))
    }

    /**
     * review D-01：`recordedAt` 的语义是**首次登记时间**，不能被后续写入刷新。
     *
     * 旧实现用 `@Insert(REPLACE)` + 每次 `System.currentTimeMillis()`，连「只改备注」
     * 的编辑都会把时间刷成今天。这里先把首次时间改成一个可辨认的旧值，再走一次编辑：
     * 若实现退回 REPLACE 写法，断言会立刻失败。
     */
    @Test
    fun `编号历史的首次登记时间不被后续编辑覆盖`() = runTest {
        val id = repository.createItem(item("9"))
        // 人为把首次时间改成旧值（DAO 没有「改时间」的口子，正是希望它永远别被改）
        database.openHelper.writableDatabase.execSQL(
            "UPDATE clothing_code_history SET recordedAt = 12345 WHERE code = '9'",
        )

        repository.updateItem(repository.getItem(id)!!.copy(note = "只改备注"))

        assertEquals(
            "只改备注不得刷新首次登记时间",
            12345L,
            database.codeHistoryDao().recordedAt("9"),
        )
        assertEquals("持有人仍应是本件", id, database.codeHistoryDao().holderOf("9"))
    }
}
