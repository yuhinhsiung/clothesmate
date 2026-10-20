package com.yuhinhsiung.clothesmate.feature.outfit.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yuhinhsiung.clothesmate.core.database.ClothesMateDatabase
import com.yuhinhsiung.clothesmate.core.database.entity.ClothingItemEntity
import com.yuhinhsiung.clothesmate.core.database.entity.OutfitEntity
import com.yuhinhsiung.clothesmate.core.database.entity.OutfitItemCrossRef
import com.yuhinhsiung.clothesmate.core.database.entity.WardrobeEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OutfitDaoItemLookupTest {

    private lateinit var database: ClothesMateDatabase

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, ClothesMateDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        database.wardrobeDao().insertAll(
            listOf(
                WardrobeEntity(
                    id = 1L, name = "主卧", note = null, coverImagePath = null,
                    sortOrder = 0, createdAt = 0L,
                ),
            ),
        )
        database.clothingItemDao().insertAll(
            listOf(
                ClothingItemEntity(
                    id = 101L,
                    code = "1",
                    wardrobeId = 1L,
                    category = "shirt",
                    colors = listOf("white"),
                    styleTags = emptyList(),
                    seasonTags = listOf("spring"),
                    originalImagePath = null,
                    cutoutImagePath = null,
                    brand = null,
                    name = null,
                    note = null,
                    deletedAt = null,
                    createdAt = 0L,
                    updatedAt = 0L,
                ),
            ),
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun outfitsForItemAreOrderedByLastWornThenCreatedAndIgnoreOtherItems() = runTest {
        val dao = database.outfitDao()
        val recent = outfit(id = 1L, lastWornAt = 200L, createdAt = 10L)
        val old = outfit(id = 2L, lastWornAt = 100L, createdAt = 20L)
        val neverNew = outfit(id = 3L, lastWornAt = null, createdAt = 400L)
        val neverOld = outfit(id = 4L, lastWornAt = null, createdAt = 300L)
        dao.insert(recent)
        dao.insert(old)
        dao.insert(neverNew)
        dao.insert(neverOld)
        dao.insertCrossRefs(
            listOf(
                OutfitItemCrossRef(recent.id, 101L),
                OutfitItemCrossRef(old.id, 101L),
                OutfitItemCrossRef(neverNew.id, 101L),
                OutfitItemCrossRef(neverOld.id, 101L),
            ),
        )

        val result = dao.observeOutfitsForItem(101L).first()

        assertEquals(listOf(1L, 2L, 3L, 4L), result.map { it.id })
        assertEquals(emptyList<OutfitEntity>(), dao.observeOutfitsForItem(999L).first())
    }

    private fun outfit(id: Long, lastWornAt: Long?, createdAt: Long) = OutfitEntity(
        id = id,
        photoPaths = emptyList(),
        styleTags = emptyList(),
        sceneTags = emptyList(),
        seasonTags = emptyList(),
        note = null,
        wearCount = 0,
        lastWornAt = lastWornAt,
        createdAt = createdAt,
        updatedAt = createdAt,
    )
}
