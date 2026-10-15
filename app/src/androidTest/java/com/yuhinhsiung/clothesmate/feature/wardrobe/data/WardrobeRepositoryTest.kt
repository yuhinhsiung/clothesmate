package com.yuhinhsiung.clothesmate.feature.wardrobe.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yuhinhsiung.clothesmate.core.database.ClothesMateDatabase
import com.yuhinhsiung.clothesmate.core.database.entity.ClothingItemEntity
import com.yuhinhsiung.clothesmate.core.database.entity.ClothingStatus
import com.yuhinhsiung.clothesmate.core.database.entity.WardrobeEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 衣柜 Repository 测试 —— 开发计划 M1-04 / 开发文档 §2.1.2 W-02/W-08/W-12。
 *
 * 用 Room 内存库（真 UNIQUE / 外键约束）：W-12 的「转移后编号不变」「一并删除后查不到」
 * 都依赖真实数据行为，内存 fake 验证不了。
 */
@RunWith(AndroidJUnit4::class)
class WardrobeRepositoryTest {

    private lateinit var database: ClothesMateDatabase
    private lateinit var repository: WardrobeRepository
    private lateinit var clothingRepository: ClothingRepository


    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, ClothesMateDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        clothingRepository = DefaultClothingRepository(database.clothingItemDao(), database.outfitDao())
        repository = DefaultWardrobeRepository(
            wardrobeDao = database.wardrobeDao(),
            clothingRepository = clothingRepository
        )
    }

    @After
    fun tearDown() = database.close()

    private suspend fun newWardrobe(name: String, order: Int = 0): Long =
        database.wardrobeDao().insert(
            WardrobeEntity(name = name, note = null, coverImagePath = null, sortOrder = order, createdAt = 0L),
        )

    private suspend fun newItem(code: String, wardrobeId: Long, name: String? = null): Long =
        database.clothingItemDao().insert(
            ClothingItemEntity(
                code = code,
                wardrobeId = wardrobeId,
                category = "shirt",
                colors = listOf("white"),
                styleTags = emptyList(),
                seasonTags = emptyList(),
                originalImagePath = null,
                cutoutImagePath = null,
                brand = null,
                name = name,
                note = null,
                wearCount = 0,
                lastWornAt = null,
                status = ClothingStatus.NORMAL,
                deletedAt = null,
                createdAt = 0L,
                updatedAt = 0L,
            ),
        )

    /** 取衣物的当前状态（编辑后回读用） */
    private suspend fun rawItem(id: Long) = database.clothingItemDao().findById(id)

    // ── CRUD（W-01/W-02） ────────────────────────────────────────

    @Test
    fun `新建衣柜trim名称并把sortOrder追加到末尾`() = runTest {
        val first = repository.createWardrobe("  主卧大衣柜  ", "  朝南  ", null)
        val second = repository.createWardrobe("玄关衣帽架", null, null)

        assertEquals("主卧大衣柜", repository.getWardrobe(first)!!.name)
        assertEquals("朝南", repository.getWardrobe(first)!!.note)
        // sortOrder 追加：第二个大于第一个（列表按 sortOrder 升序 → 新建者排末尾）
        assertTrue(repository.getWardrobe(second)!!.sortOrder > repository.getWardrobe(first)!!.sortOrder)
    }

    @Test
    fun `空备注存为null而非空串`() = runTest {
        val id = repository.createWardrobe("衣柜", "   ", null)
        assertNull(repository.getWardrobe(id)!!.note)
    }

    @Test
    fun `编辑衣柜保留sortOrder与创建时间、改写名称封面`() = runTest {
        val id = newWardrobe("旧名", order = 5)
        val before = database.wardrobeDao().findById(id)!!

        repository.updateWardrobe(id, "  新名  ", null, "wardrobe_cover/$id.jpg")

        val after = database.wardrobeDao().findById(id)!!
        assertEquals("新名", after.name)
        assertEquals("wardrobe_cover/$id.jpg", after.coverImagePath)
        // 编辑不该动排序与建档时间
        assertEquals(5, after.sortOrder)
        assertEquals(before.createdAt, after.createdAt)
    }

    @Test
    fun `编辑不存在的衣柜静默返回不抛异常`() = runTest {
        repository.updateWardrobe(404L, "名", null, null)
        assertNull(repository.getWardrobe(404L))
    }

    // ── 件数实时刷新（W-03） ─────────────────────────────────────

    @Test
    fun `观察列表的件数不含软删除`() = runTest {
        val w = newWardrobe("衣柜")
        newItem("1", w)
        newItem("2", w)

        val before = repository.observeWardrobes().first().single()
        assertEquals(2, before.clothingCount)

        clothingRepository.softDelete(newItem("3", w))
        val after = repository.observeWardrobes().first().single()
        assertEquals(2, after.clothingCount)
    }

    /** M1-08 选择衣柜弹层要展示件数，走的是 getAllWardrobes 这条路 */
    @Test
    fun `getAllWardrobes也带件数（选择衣柜弹层不能显示0件）`() = runTest {
        val a = newWardrobe("A")
        val b = newWardrobe("B")
        newItem("1", a)
        newItem("2", a)
        newItem("3", b)

        val all = repository.getAllWardrobes().associate { it.id to it.clothingCount }
        assertEquals(2, all[a])
        assertEquals(1, all[b])
    }

    @Test
    fun `getWardrobe单个查询也带件数`() = runTest {
        val w = newWardrobe("衣柜")
        newItem("1", w)
        assertEquals(1, repository.getWardrobe(w)!!.clothingCount)
    }

    // ── 拖拽排序（W-08） ────────────────────────────────────────

    @Test
    fun `拖拽后sortOrder重写为连续索引且顺序与传入一致`() = runTest {
        val a = newWardrobe("A", 0)
        val b = newWardrobe("B", 1)
        val c = newWardrobe("C", 2)

        // 模拟把 c 拖到最前
        repository.persistOrder(listOf(c, b, a))

        assertEquals(
            listOf("C", "B", "A"),
            repository.observeWardrobes().first().map { it.name },
        )
        assertEquals(
            listOf(0, 1, 2),
            repository.observeWardrobes().first().map { it.sortOrder },
        )
    }

    @Test
    fun `persistOrder忽略已不存在的id（崩溃恢复场景）`() = runTest {
        val a = newWardrobe("A", 0)
        repository.persistOrder(listOf(999L, a))
        assertEquals("A", repository.observeWardrobes().first().single().name)
    }

    @Test
    fun `杀进程后顺序保持（重开Repository读同库）`() = runTest {
        val a = newWardrobe("A", 0)
        val b = newWardrobe("B", 1)
        repository.persistOrder(listOf(b, a))

        // 模拟重启：丢掉 Repository 实例、从同一内存库重建
        val revived = DefaultWardrobeRepository(database.wardrobeDao(), clothingRepository)
        assertEquals(listOf("B", "A"), revived.observeWardrobes().first().map { it.name })
    }

    // ── W-12 删除去向 ───────────────────────────────────────────

    @Test
    fun `转移到目标衣柜后wardrobeId变更、编号与衣物数不变`() = runTest {
        val from = newWardrobe("源衣柜")
        val to = newWardrobe("目标衣柜")
        val i1 = newItem("101", from)
        val i2 = newItem("102", from)

        val moved = repository.deleteWithDestiny(
            wardrobeId = from,
            destiny = WardrobeDeleteDestiny.TransferTo(to),
        )

        assertEquals(2, moved)
        assertEquals(to, rawItem(i1)!!.wardrobeId)
        assertEquals(to, rawItem(i2)!!.wardrobeId)
        // 编号绝对不变（§2.1.3：移柜不改号）
        assertEquals("101", rawItem(i1)!!.code)
        assertEquals("102", rawItem(i2)!!.code)
        // 目标衣柜件数落到位
        assertEquals(2, repository.getWardrobe(to)!!.clothingCount)
    }

    @Test
    fun `一并删除是物理删除、行与衣柜一起消失`() = runTest {
        val w = newWardrobe("待删")
        val i1 = newItem("101", w)
        val i2 = newItem("102", w)

        val deleted = repository.deleteWithDestiny(w, WardrobeDeleteDestiny.DeleteAll)

        assertEquals(2, deleted)
        assertNull(repository.getWardrobe(w))
        assertTrue(clothingRepository.getItemsInWardrobe(w).isEmpty())
        // 物理删除：连 findById 都查不到（软删至少还能查到 deletedAt 非空的行）
        assertNull(database.clothingItemDao().findById(i1))
        assertNull(database.clothingItemDao().findById(i2))
    }

    @Test
    fun `一并删除的衣物若被穿搭引用、其关联也一并清掉`() = runTest {
        // 外键是 NO_ACTION：关联行不清，删衣物这一步就会被 SQLite 拦下
        val w = newWardrobe("待删")
        val item = newItem("101", w)
        val outfitId = database.outfitDao().insert(
            com.yuhinhsiung.clothesmate.core.database.entity.OutfitEntity(
                name = null,
                photoPaths = emptyList(),
                styleTags = emptyList(),
                sceneTags = emptyList(),
                seasonTags = emptyList(),
                note = null,
                wearCount = 0,
                lastWornAt = null,
                createdAt = 0L,
                updatedAt = 0L,
            ),
        )
        database.outfitDao().insertCrossRef(
            com.yuhinhsiung.clothesmate.core.database.entity.OutfitItemCrossRef(outfitId, item),
        )

        repository.deleteWithDestiny(w, WardrobeDeleteDestiny.DeleteAll)

        assertTrue(database.outfitDao().getClothingIds(outfitId).isEmpty())
        assertNull(database.clothingItemDao().findById(item))
    }

    @Test
    fun `转移到自己是自毁操作、应被拦下而不是留下悬空衣物`() = runTest {
        // UI 文档 §7.2：仅剩一个衣柜时选项①自动选定该柜。但删掉唯一衣柜时
        // 衣物没有别的去处——这是 UI 层必须拦住的情况（应引导先新建）。
        // Repository 层的责任是：不许把数据留在「衣物指向不存在的衣柜」的状态。
        val only = newWardrobe("唯一衣柜")
        newItem("101", only)

        val error = runCatching {
            repository.deleteWithDestiny(only, WardrobeDeleteDestiny.TransferTo(only))
        }.exceptionOrNull()

        // 两种可接受收场：抛异常（衣柜还在、数据完整），或 wardrobeId 被改到不存在处（不可接受）
        assertTrue("应抛异常而非留悬空引用", error != null)
        assertNotNull(repository.getWardrobe(only))
        assertEquals(1, clothingRepository.getItemsInWardrobe(only).size)
    }

    @Test
    fun `空衣柜删除返回0且不触发图片回调`() = runTest {
        val w = newWardrobe("空衣柜")
        val seen = mutableListOf<Long>()

        val moved = repository.deleteWithDestiny(
            wardrobeId = w,
            destiny = WardrobeDeleteDestiny.DeleteAll,
            onImagesDeleted = { id -> seen += id },
        )

        assertEquals(0, moved)
        assertTrue("无衣物可清理，回调不该被触发", seen.isEmpty())
        assertNull(repository.getWardrobe(w))
    }

    @Test
    fun `删除衣柜后未删衣物变成悬空引用时应被回调而非静默`() = runTest {
        // 事务保证：删衣柜与处理衣物同进同退。这里锁的是「回调只收到确实处理过的衣物」。
        val w = newWardrobe("衣柜")
        val i1 = newItem("101", w)
        val i2 = newItem("102", w)

        val seen = mutableListOf<Long>()
        repository.deleteWithDestiny(
            wardrobeId = w,
            destiny = WardrobeDeleteDestiny.DeleteAll,
            onImagesDeleted = { id -> seen += id },
        )

        assertEquals(listOf(i1, i2), seen)
    }
}
