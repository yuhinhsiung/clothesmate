package com.yuhinhsiung.clothesmate

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yuhinhsiung.clothesmate.core.backup.BackupManifest
import com.yuhinhsiung.clothesmate.core.backup.DefaultBackupRepository
import com.yuhinhsiung.clothesmate.core.database.ClothesMateDatabase
import com.yuhinhsiung.clothesmate.core.database.entity.ClothingItemEntity
import com.yuhinhsiung.clothesmate.core.database.entity.OutfitEntity
import com.yuhinhsiung.clothesmate.core.database.entity.WearRecordEntity
import com.yuhinhsiung.clothesmate.core.database.entity.WardrobeEntity
import com.yuhinhsiung.clothesmate.core.datastore.CodeCounterStore
import com.yuhinhsiung.clothesmate.core.datastore.InMemoryUserSettingsStore
import com.yuhinhsiung.clothesmate.core.image.DefaultImageStorage
import com.yuhinhsiung.clothesmate.feature.wardrobe.data.DefaultClothingRepository
import com.yuhinhsiung.clothesmate.feature.wardrobe.domain.CodeAllocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipFile

/** Regression: a non-empty database must produce a non-empty backup manifest. */
@RunWith(AndroidJUnit4::class)
class BackupExportDataTest {
    private lateinit var context: Context
    private lateinit var db: ClothesMateDatabase
    private lateinit var root: File
    private val json = Json { ignoreUnknownKeys = true }

    private class Counter : CodeCounterStore {
        private var value = 1L
        override suspend fun currentValue() = value
        override suspend fun setValue(value: Long) { this.value = value }
        override suspend fun nextCode(existsConflict: suspend (Long) -> Boolean): Long {
            while (existsConflict(value)) value++
            return value++
        }
    }

    @Before fun before() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, ClothesMateDatabase::class.java).build()
        root = File(context.cacheDir, "backup-export-${System.nanoTime()}").apply { mkdirs() }
    }

    @After fun after() { db.close(); root.deleteRecursively() }

    @Test fun exportWithRowsWritesRowsToManifest() = runBlocking {
        val wardrobeId = db.wardrobeDao().insert(
            WardrobeEntity(id = 20, name = "测试衣柜", note = null, coverImagePath = null, sortOrder = 0, createdAt = 1),
        )
        db.clothingItemDao().insert(
            ClothingItemEntity(
                id = 30, code = "1", wardrobeId = wardrobeId, category = "shirt", colors = listOf("red"),
                styleTags = emptyList(), seasonTags = listOf("spring"), originalImagePath = null,
                cutoutImagePath = null, brand = null, name = "红T恤", note = null, wearCount = 0,
                deletedAt = null, createdAt = 1, updatedAt = 1,
            ),
        )
        val storage = DefaultImageStorage(File(root, "images"), Dispatchers.IO)
        val clothingRepo = DefaultClothingRepository(db, db.clothingItemDao(), db.outfitDao(), db.codeHistoryDao())
        val repo = DefaultBackupRepository(
            context, db, db.backupDao(), db.wardrobeDao(), db.clothingItemDao(), db.codeHistoryDao(),
            db.outfitDao(), db.outfitCategoryDao(), db.wearRecordDao(), db.tagDictionaryDao(),
            storage, CodeAllocator(Counter(), clothingRepo), InMemoryUserSettingsStore(),
        )
        val file = File(root, "backup.zip")
        val summary = repo.export(Uri.fromFile(file), "password123")
        assertEquals(1, summary.wardrobes)
        assertEquals(1, summary.clothing)
        val manifest = ZipFile(file).use { zip ->
            val text = zip.getInputStream(zip.getEntry("manifest.json")).bufferedReader().readText()
            json.decodeFromString(BackupManifest.serializer(), text)
        }
        assertEquals(1, manifest.wardrobes.size)
        assertEquals(1, manifest.clothing.size)
        assertEquals("1", manifest.clothing.single().code)
    }

    @Test fun exportClearImportRestoresAllCoreCategories() = runBlocking {
        val settings = InMemoryUserSettingsStore()
        settings.setWeatherCity("杭州")
        settings.setStylePreference("简约")
        val wardrobeId = db.wardrobeDao().insert(
            WardrobeEntity(id = 20, name = "测试衣柜", note = null, coverImagePath = null, sortOrder = 0, createdAt = 1),
        )
        db.clothingItemDao().insert(
            ClothingItemEntity(
                id = 30, code = "1", wardrobeId = wardrobeId, category = "shirt", colors = listOf("red"),
                styleTags = emptyList(), seasonTags = listOf("spring"), originalImagePath = null,
                cutoutImagePath = null, brand = null, name = "红T恤", note = null, wearCount = 1,
                deletedAt = null, createdAt = 1, updatedAt = 1,
            ),
        )
        val outfitId = db.outfitDao().insert(
            OutfitEntity(id = 40, name = "测试穿搭", photoPaths = emptyList(), styleTags = emptyList(),
                sceneTags = emptyList(), seasonTags = emptyList(), note = null, wearCount = 1,
                lastWornAt = 1792281600000, createdAt = 1, updatedAt = 1),
        )
        db.wearRecordDao().insert(
            WearRecordEntity(id = 50, date = "2026-10-20", outfitId = outfitId,
                scheduleTags = listOf("通勤"), weatherJson = null, note = "ok", createdAt = 1),
        )
        val storage = DefaultImageStorage(File(root, "images"), Dispatchers.IO)
        val clothingRepo = DefaultClothingRepository(db, db.clothingItemDao(), db.outfitDao(), db.codeHistoryDao())
        val repo = DefaultBackupRepository(
            context, db, db.backupDao(), db.wardrobeDao(), db.clothingItemDao(), db.codeHistoryDao(),
            db.outfitDao(), db.outfitCategoryDao(), db.wearRecordDao(), db.tagDictionaryDao(),
            storage, CodeAllocator(Counter(), clothingRepo), settings,
        )
        val file = File(root, "roundtrip.zip")
        repo.export(Uri.fromFile(file), "password123")
        db.clearAllTables()
        settings.setWeatherCity("上海")
        settings.setStylePreference("通勤")
        repo.import(Uri.fromFile(file), "password123")
        assertEquals(1, db.backupDao().wardrobes().size)
        assertEquals(1, db.backupDao().clothing().size)
        assertEquals(1, db.backupDao().outfits().size)
        assertEquals(1, db.backupDao().wearRecords().size)
        assertEquals("杭州", settings.settings.first().weatherCity)
        assertEquals("简约", settings.settings.first().stylePreference)
    }
}
