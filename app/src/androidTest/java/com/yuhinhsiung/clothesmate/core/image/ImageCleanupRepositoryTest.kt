package com.yuhinhsiung.clothesmate.core.image

import android.util.Base64
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yuhinhsiung.clothesmate.core.database.ClothesMateDatabase
import com.yuhinhsiung.clothesmate.core.database.entity.ClothingItemEntity
import com.yuhinhsiung.clothesmate.core.database.entity.WardrobeEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ImageCleanupRepositoryTest {

    private lateinit var database: ClothesMateDatabase
    private lateinit var root: File
    private lateinit var storage: ImageStorage
    private lateinit var repository: ImageCleanupRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, ClothesMateDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        root = File(context.cacheDir, "cleanup-test-${System.nanoTime()}").apply { mkdirs() }
        storage = DefaultImageStorage(root, Dispatchers.IO)
        repository = DefaultImageCleanupRepository(database.clothingItemDao(), storage)
    }

    @After
    fun tearDown() {
        database.close()
        root.deleteRecursively()
    }

    @Test
    fun onlyItemsWithCutoutAreCountedAndOriginalRemoved() = runTest {
        database.wardrobeDao().insert(WardrobeEntity(name = "主卧衣柜", note = null, coverImagePath = null, sortOrder = 0, createdAt = 1L))
        val withCutout = item(id = 1L, original = "clothing/original/1.jpg", cutout = "clothing/cutout/1.png")
        val noCutout = item(id = 2L, original = "clothing/original/2.jpg", cutout = null)
        database.clothingItemDao().insert(withCutout)
        database.clothingItemDao().insert(noCutout)

        val originalOne = storage.clothingOriginalFile(1L).apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(4))
        }
        val cutoutOne = storage.clothingCutoutFile(1L).apply {
            parentFile?.mkdirs()
            writeBytes(ONE_PIXEL_PNG)
        }
        val originalTwo = storage.clothingOriginalFile(2L).apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(3))
        }

        assertEquals(CleanupEstimate(count = 1, bytes = 4L), repository.estimate())

        val result = repository.cleanup()
        assertEquals(1, result.count)
        assertEquals(4L, result.bytes)
        assertFalse(originalOne.exists())
        assertTrue(cutoutOne.exists())
        assertTrue(originalTwo.exists())
        assertTrue(storage.clothingCutoutFile(1L).exists())
        assertNull(database.clothingItemDao().findById(1L)?.originalImagePath)
        assertEquals("clothing/original/2.jpg", database.clothingItemDao().findById(2L)?.originalImagePath)
    }

    @Test
    fun cutoutFileMissingPreservesOriginalAndDatabasePath() = runTest {
        database.wardrobeDao().insert(WardrobeEntity(name = "主卧衣柜", note = null, coverImagePath = null, sortOrder = 0, createdAt = 1L))
        database.clothingItemDao().insert(
            item(id = 3L, original = "clothing/original/3.jpg", cutout = "clothing/cutout/3.png"),
        )
        val original = storage.clothingOriginalFile(3L).apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(8))
        }

        assertEquals(CleanupEstimate(count = 0, bytes = 0L), repository.estimate())
        assertEquals(CleanupResult(count = 0, bytes = 0L), repository.cleanup())
        assertTrue(original.exists())
        assertEquals("clothing/original/3.jpg", database.clothingItemDao().findById(3L)?.originalImagePath)
    }

    @Test
    fun undecodableCutoutPreservesOriginal() = runTest {
        database.wardrobeDao().insert(WardrobeEntity(name = "主卧衣柜", note = null, coverImagePath = null, sortOrder = 0, createdAt = 1L))
        database.clothingItemDao().insert(
            item(id = 4L, original = "clothing/original/4.jpg", cutout = "clothing/cutout/4.png"),
        )
        val original = storage.clothingOriginalFile(4L).apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(8))
        }
        storage.clothingCutoutFile(4L).apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(2))
        }

        assertEquals(CleanupEstimate(count = 0, bytes = 0L), repository.estimate())
        assertEquals(CleanupResult(count = 0, bytes = 0L), repository.cleanup())
        assertTrue(original.exists())
        assertEquals("clothing/original/4.jpg", database.clothingItemDao().findById(4L)?.originalImagePath)
    }

    private suspend fun item(id: Long, original: String?, cutout: String?): ClothingItemEntity {
        return ClothingItemEntity(
            id = id,
            code = id.toString(),
            wardrobeId = 1L,
            category = "t_shirt",
            colors = listOf("red"),
            styleTags = emptyList(),
            seasonTags = emptyList(),
            originalImagePath = original,
            cutoutImagePath = cutout,
            brand = null,
            name = null,
            note = null,
            createdAt = id,
            updatedAt = id,
        )
    }

    private companion object {
        val ONE_PIXEL_PNG: ByteArray = Base64.decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl2n2QAAAAASUVORK5CYII=",
            Base64.DEFAULT,
        )
    }
}
