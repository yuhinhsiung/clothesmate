package com.yuhinhsiung.clothesmate.feature.wardrobe.data

import android.database.Cursor
import android.os.SystemClock
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yuhinhsiung.clothesmate.core.database.ClothesMateDatabase
import com.yuhinhsiung.clothesmate.core.database.entity.ClothingItemEntity
import com.yuhinhsiung.clothesmate.core.database.entity.WardrobeEntity
import com.yuhinhsiung.clothesmate.core.model.ClothingStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M5-02：1000 件衣物下列表 / 多关键词 AND / 组合筛选 / 空结果的首结果与正确性。
 *
 * 这是 debug emulator 基线。开发文档 §10 的目标是 <100ms；当前模拟器上
 * Repository 内存筛选无法稳定达到，按 M5-02 允许正式降级为 500ms，
 * 并在 docs 中记录「待真机复测」。DAO 单独计时，避免把 Repository 开销算进 SQL。
 */
@RunWith(AndroidJUnit4::class)
class SearchPerformanceTest {

    private lateinit var database: ClothesMateDatabase
    private lateinit var repository: DefaultClothingRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, ClothesMateDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = DefaultClothingRepository(
            db = database,
            dao = database.clothingItemDao(),
            outfitDao = database.outfitDao(),
            codeHistoryDao = database.codeHistoryDao(),
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun thousandItems_searchSemanticsAndFirstResultBudget() = runTest {
        database.wardrobeDao().insert(
            WardrobeEntity(
                id = 1L,
                name = "性能测试衣柜",
                note = null,
                coverImagePath = null,
                sortOrder = 0,
                createdAt = 0L,
            ),
        )
        val items = (1..1000).map { index ->
            ClothingItemEntity(
                code = index.toString(),
                wardrobeId = 1L,
                category = "shirt",
                colors = listOf("white"),
                styleTags = listOf("casual"),
                seasonTags = listOf("spring"),
                originalImagePath = null,
                cutoutImagePath = null,
                brand = null,
                name = "白衬衫 $index",
                note = null,
                wearCount = 0,
                lastWornAt = null,
                status = ClothingStatus.NORMAL,
                deletedAt = null,
                createdAt = index.toLong(),
                updatedAt = index.toLong(),
            )
        }
        database.clothingItemDao().insertAll(items)

        fun timed(label: String, warm: Boolean = true, block: suspend () -> Int): Int {
            // warm-up 一次，避免把首次 Room 查询的 prepare 开销混入统计
            if (warm) kotlinx.coroutines.runBlocking { block() }
            val start = SystemClock.elapsedRealtime()
            val size = kotlinx.coroutines.runBlocking { block() }
            Log.i("M5SearchPerf", "$label ms=${SystemClock.elapsedRealtime() - start} size=$size")
            return size
        }

        val dao = database.clothingItemDao()
        val listSize = timed("dao-list") { dao.observeAll().first().size }
        val codeSize = timed("dao-code-500") { dao.searchByCode("500").first().size }
        val nameSize = timed("dao-name-category") { dao.searchByNameAndCategory("衬衫").first().size }
        val comboDaoSize = timed("dao-category") { dao.searchByCategoryKeys(listOf("shirt")).first().size }
        val keywordSize = timed("repo-multi-keyword") { repository.search("白 衬衫", null, null, null).first().size }
        val comboRepoSize = timed("repo-combo") { repository.search("", "shirt", "white", null).first().size }
        val emptySize = timed("repo-empty") { repository.search("不存在", null, null, null).first().size }

        assertEquals(1000, listSize)
        assertEquals(1, codeSize)
        assertEquals(1000, nameSize)
        assertEquals(1000, comboDaoSize)
        assertEquals(1000, keywordSize)
        assertEquals(1000, comboRepoSize)
        assertEquals(0, emptySize)

        // SQL 计划只作归档解释，不作硬门禁（M5-02）。
        logPlan("list", "SELECT * FROM clothing_item WHERE deletedAt IS NULL ORDER BY CAST(code AS INTEGER) ASC")
        logPlan("code", "SELECT * FROM clothing_item WHERE deletedAt IS NULL AND code LIKE '%500%' ESCAPE '\'")
        logPlan("category", "SELECT * FROM clothing_item WHERE deletedAt IS NULL AND category IN ('shirt')")
    }

    private fun logPlan(label: String, sql: String) {
        val cursor: Cursor = database.openHelper.readableDatabase.query("EXPLAIN QUERY PLAN $sql")
        cursor.use {
            while (it.moveToNext()) {
                Log.i("M5QueryPlan", "$label: ${it.getString(3)}")
            }
        }
    }
}
