package com.yuhinhsiung.clothesmate.feature.today.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yuhinhsiung.clothesmate.core.database.ClothesMateDatabase
import com.yuhinhsiung.clothesmate.core.database.entity.ClothingItemEntity
import com.yuhinhsiung.clothesmate.core.database.entity.OutfitEntity
import com.yuhinhsiung.clothesmate.core.database.entity.OutfitItemCrossRef
import com.yuhinhsiung.clothesmate.core.database.entity.WardrobeEntity
import com.yuhinhsiung.clothesmate.core.model.ClothingStatus
import com.yuhinhsiung.clothesmate.core.model.WearException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

/**
 * 穿着事务集成测试（M3-06 / M3-08）—— 真 Room 内存库。
 *
 * 重点守住：
 * 1. wear_record、穿搭计数、每件衣物计数在一次提交里；
 * 2. 同日可记录多套，计数逐次 +1；
 * 3. 补记旧日期不把 lastWornAt 回拨；
 * 4. 收起衣物拒绝写入，且不留下半条记录。
 */
@RunWith(AndroidJUnit4::class)
class WearRepositoryTest {

    private lateinit var database: ClothesMateDatabase
    private lateinit var repository: WearRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, ClothesMateDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = DefaultWearRepository(
            db = database,
            wearDao = database.wearRecordDao(),
            outfitDao = database.outfitDao(),
            clothingDao = database.clothingItemDao(),
            wardrobeDao = database.wardrobeDao(),
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `确认穿着同事务写入记录并递增穿搭与衣物计数`() = runTest {
        val (outfitId, itemIds) = seedOutfit()
        val today = LocalDate.now()
        val record = repository.confirmWear(
            outfitId = outfitId,
            date = today,
            scheduleTags = listOf("通勤", "会议"),
            weatherJson = """{"city":"杭州"}""",
            note = "见客户",
        )

        assertEquals(today, record.date)
        assertEquals(listOf("通勤", "会议"), record.scheduleTags)
        assertEquals("见客户", record.note)
        assertEquals(2, record.items.size)
        assertTrue(record.items.any { it.code == "101" && it.wardrobeName == "主卧衣柜" })

        val outfit = database.outfitDao().findById(outfitId)!!
        assertEquals(1, outfit.wearCount)
        assertEquals(startOfDay(today), outfit.lastWornAt)
        itemIds.forEach { id ->
            val item = database.clothingItemDao().findById(id)!!
            assertEquals(1, item.wearCount)
            assertEquals(startOfDay(today), item.lastWornAt)
        }
        assertEquals(1, database.wearRecordDao().getByDate(today.toString()).size)
    }

    @Test
    fun `同日多套逐条累加且每条记录独立`() = runTest {
        val (outfitId, _) = seedOutfit()
        val today = LocalDate.now()
        repository.confirmWear(outfitId, today, listOf("通勤"), null, null)
        repository.confirmWear(outfitId, today, listOf("约会"), null, null)

        val records = repository.observeRecords(today).first()
        assertEquals(2, records.size)
        assertEquals(2, database.outfitDao().findById(outfitId)!!.wearCount)
        assertEquals(2, database.clothingItemDao().findById(1L)!!.wearCount)
        assertEquals(listOf("通勤"), records[0].scheduleTags)
        assertEquals(listOf("约会"), records[1].scheduleTags)
    }

    @Test
    fun `补记旧日期只加计数不回拨lastWornAt`() = runTest {
        val (outfitId, itemIds) = seedOutfit()
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        repository.confirmWear(outfitId, today, emptyList(), null, null)
        repository.confirmWear(outfitId, yesterday, emptyList(), null, null)

        assertEquals(2, database.outfitDao().findById(outfitId)!!.wearCount)
        assertEquals(startOfDay(today), database.outfitDao().findById(outfitId)!!.lastWornAt)
        itemIds.forEach { id ->
            assertEquals(startOfDay(today), database.clothingItemDao().findById(id)!!.lastWornAt)
        }
        assertEquals(2, repository.observeRecordsInMonth(
            java.time.YearMonth.from(today),
        ).first().size)
    }

    @Test
    fun `收起衣物拒绝穿着且不留下记录`() = runTest {
        val (outfitId, itemIds) = seedOutfit()
        database.clothingItemDao().updateStatus(itemIds.first(), ClothingStatus.STORED, 0L)
        val today = LocalDate.now()
        val error = runCatching {
            repository.confirmWear(outfitId, today, listOf("通勤"), null, null)
        }.exceptionOrNull()

        assertTrue(error is WearException)
        assertEquals(0, database.wearRecordDao().getByDate(today.toString()).size)
        assertEquals(0, database.outfitDao().findById(outfitId)!!.wearCount)
    }

    @Test
    fun `衣物计数失败时整笔事务回滚`() = runTest {
        val (outfitId, itemIds) = seedOutfit()
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER fail_wear_update BEFORE UPDATE ON clothing_item
            BEGIN
                SELECT RAISE(ABORT, 'boom');
            END;
            """.trimIndent(),
        )
        val today = LocalDate.now()
        val error = runCatching {
            repository.confirmWear(outfitId, today, listOf("通勤"), null, null)
        }.exceptionOrNull()

        assertNotNull(error)
        assertEquals(0, database.wearRecordDao().getByDate(today.toString()).size)
        assertEquals(0, database.outfitDao().findById(outfitId)!!.wearCount)
        itemIds.forEach { id ->
            assertEquals(0, database.clothingItemDao().findById(id)!!.wearCount)
        }
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_wear_update")
    }

    @Test
    fun `备注可补写且快照字段保留`() = runTest {
        val (outfitId, _) = seedOutfit()
        val today = LocalDate.now()
        val record = repository.confirmWear(
            outfitId, today, listOf("通勤"), """{"text":"晴"}""", null,
        )
        repository.updateNote(record.id, "  晚上有风  ")

        val stored = database.wearRecordDao().getByDate(today.toString()).single()
        assertEquals("晚上有风", stored.note)
        assertEquals("""{"text":"晴"}""", stored.weatherJson)
        assertNotNull(stored.id)
    }

    private suspend fun seedOutfit(): Pair<Long, List<Long>> {
        val wardrobeId = database.wardrobeDao().insert(
            WardrobeEntity(
                name = "主卧衣柜",
                note = null,
                coverImagePath = null,
                sortOrder = 0,
                createdAt = 0L,
            ),
        )
        val first = database.clothingItemDao().insert(
            item("101", wardrobeId, "shirt"),
        )
        val second = database.clothingItemDao().insert(
            item("102", wardrobeId, "pants"),
        )
        val outfitId = database.outfitDao().insert(
            OutfitEntity(
                name = "测试穿搭",
                photoPaths = listOf("clothing/cutout/101.png"),
                styleTags = listOf("通勤"),
                sceneTags = listOf("通勤"),
                seasonTags = listOf("spring"),
                note = null,
                createdAt = 0L,
                updatedAt = 0L,
            ),
        )
        database.outfitDao().insertCrossRefs(
            listOf(
                OutfitItemCrossRef(outfitId, first, 0),
                OutfitItemCrossRef(outfitId, second, 1),
            ),
        )
        return outfitId to listOf(first, second)
    }

    private fun item(code: String, wardrobeId: Long, category: String) = ClothingItemEntity(
        code = code,
        wardrobeId = wardrobeId,
        category = category,
        colors = listOf("red"),
        styleTags = listOf("通勤"),
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

    private fun startOfDay(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
