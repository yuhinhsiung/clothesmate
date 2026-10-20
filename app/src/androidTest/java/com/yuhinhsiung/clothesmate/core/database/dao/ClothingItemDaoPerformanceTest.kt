package com.yuhinhsiung.clothesmate.core.database.dao

import android.os.SystemClock
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
 * M4-09 性能基线：1000 件衣物时列表查询仍应远低于一帧预算。
 *
 * 这是「数据库查询」基线，不替代 UI 滚动 gfxinfo 测量；但能在 CI 上守住
 * `deletedAt` 索引被误删、或有人给列表加 N+1 查询造成的数量级回退。
 */
@RunWith(AndroidJUnit4::class)
class ClothingItemDaoPerformanceTest {

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

    @Test
    fun thousandItemsListQueryStaysWithinBudget() = runTest {
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
                category = if (index % 2 == 0) "shirt" else null,
                colors = listOf("white"),
                styleTags = emptyList(),
                seasonTags = listOf("spring"),
                originalImagePath = null,
                cutoutImagePath = null,
                brand = null,
                name = null,
                note = null,
                wearCount = index % 7,
                lastWornAt = null,
                status = ClothingStatus.NORMAL,
                deletedAt = null,
                createdAt = index.toLong(),
                updatedAt = index.toLong(),
            )
        }
        dao.insertAll(items)

        val started = SystemClock.elapsedRealtime()
        val loaded = dao.observeAll().first()
        val elapsed = SystemClock.elapsedRealtime() - started

        assertEquals(1000, loaded.size)
        assertTrue("1000 件列表查询耗时 ${elapsed}ms，超过 500ms 预算", elapsed < 500)
    }
}
