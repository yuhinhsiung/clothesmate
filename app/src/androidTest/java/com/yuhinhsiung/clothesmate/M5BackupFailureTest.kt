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
import com.yuhinhsiung.clothesmate.core.database.entity.TagDictionaryEntity
import com.yuhinhsiung.clothesmate.core.database.entity.WardrobeEntity
import com.yuhinhsiung.clothesmate.core.datastore.CodeCounterStore
import com.yuhinhsiung.clothesmate.core.datastore.InMemoryUserSettingsStore
import com.yuhinhsiung.clothesmate.core.image.DefaultImageStorage
import com.yuhinhsiung.clothesmate.core.image.ImageStorage
import com.yuhinhsiung.clothesmate.feature.wardrobe.data.DefaultClothingRepository
import com.yuhinhsiung.clothesmate.feature.wardrobe.domain.CodeAllocator
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** R01/R05 regressions: image destination failures must fail the import, and rollback must clean files. */
@RunWith(AndroidJUnit4::class)
class M5BackupFailureTest {
    private lateinit var context: Context
    private lateinit var root: File

    private class Counter : CodeCounterStore {
        var value = 1L
        override suspend fun currentValue() = value
        override suspend fun setValue(value: Long) { this.value = value }
        override suspend fun nextCode(existsConflict: suspend (Long) -> Boolean): Long {
            while (existsConflict(value)) value++
            return value++
        }
    }

    @Before fun before() {
        context = ApplicationProvider.getApplicationContext()
        root = File(context.cacheDir, "m5-backup-failure-${System.nanoTime()}").apply { mkdirs() }
    }

    @After fun after() { root.deleteRecursively() }

    private fun repo(db: ClothesMateDatabase, storage: ImageStorage): DefaultBackupRepository {
        val clothing = DefaultClothingRepository(db, db.clothingItemDao(), db.outfitDao(), db.codeHistoryDao())
        return DefaultBackupRepository(
            context, db, db.backupDao(), db.wardrobeDao(), db.clothingItemDao(), db.codeHistoryDao(),
            db.outfitDao(), db.outfitCategoryDao(), db.wearRecordDao(), db.tagDictionaryDao(),
            storage, CodeAllocator(Counter(), clothing), InMemoryUserSettingsStore(),
        )
    }

    private fun zip(withTag: Boolean = false): Uri {
        val manifest = BackupManifest(
            exportedAt = 1,
            wardrobes = listOf(WardrobeEntity(20, "review", null, null, 0, 1)),
            clothing = listOf(
                ClothingItemEntity(
                    id = 30, code = "9", wardrobeId = 20, category = "shirt", colors = emptyList(),
                    styleTags = emptyList(), seasonTags = emptyList(),
                    originalImagePath = "clothing/original/30.jpg", cutoutImagePath = null,
                    brand = null, name = "review", note = null, wearCount = 0, deletedAt = null,
                    createdAt = 1, updatedAt = 1,
                ),
            ),
            tags = if (withTag) listOf(TagDictionaryEntity(type = "style", value = "review")) else emptyList(),
        )
        val file = File(root, "backup.zip")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(Json { encodeDefaults = true }.encodeToString(BackupManifest.serializer(), manifest).toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("clothing/original/30.jpg"))
            zip.write(byteArrayOf(1, 2, 3, 4))
            zip.closeEntry()
        }
        return Uri.fromFile(file)
    }

    @Test fun destinationBlockedMustFailAndLeaveNoRows() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, ClothesMateDatabase::class.java).build()
        try {
            val regular = DefaultImageStorage(File(root, "images"), Dispatchers.IO)
            val blocked = File(root, "blocked.jpg").apply { mkdirs() }
            val storage = object : ImageStorage by regular {
                override fun clothingOriginalFile(id: Long): File = blocked
            }
            val result = runCatching { repo(db, storage).import(zip(), "review-password") }
            assertTrue("destination failure was swallowed: $result", result.isFailure)
            assertTrue(db.backupDao().clothing().isEmpty())
        } finally { db.close() }
    }

    @Test fun failedRoomTransactionMustRemoveNewImageFiles() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, ClothesMateDatabase::class.java).build()
        try {
            val images = File(root, "images")
            val storage = DefaultImageStorage(images, Dispatchers.IO)
            db.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER m5_fail_tag BEFORE INSERT ON tag_dictionary BEGIN SELECT RAISE(ABORT, 'synthetic late failure'); END",
            )
            val result = runCatching { repo(db, storage).import(zip(withTag = true), "review-password") }
            assertTrue("injection must fail import", result.isFailure)
            assertTrue("Room must rollback", db.backupDao().clothing().isEmpty())
            val leftovers = images.walkTopDown().filter { it.isFile }.map { it.relativeTo(root).path }.toList()
            assertTrue("rolled-back import leaked final image files: $leftovers", leftovers.isEmpty())
        } finally { db.close() }
    }

    private fun outfitOnlyZip(): Uri {
        val manifest = BackupManifest(
            exportedAt = 1,
            outfits = listOf(
                OutfitEntity(
                    id = 30, name = "only-outfit", photoPaths = listOf("outfit/30/0.jpg"),
                    styleTags = emptyList(), sceneTags = emptyList(), seasonTags = emptyList(),
                    createdAt = 1, updatedAt = 1,
                ),
            ),
        )
        val file = File(root, "outfit-only.zip")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(Json { encodeDefaults = true }.encodeToString(BackupManifest.serializer(), manifest).toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("outfit/30/0.jpg"))
            zip.write(byteArrayOf(9, 8, 7, 6))
            zip.closeEntry()
        }
        return Uri.fromFile(file)
    }

    @Test fun cancelAfterCommitMustKeepOutfitPhoto() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, ClothesMateDatabase::class.java).build()
        val gate = File(context.filesDir, "import-sync-gate")
        try {
            val images = File(root, "images")
            gate.writeText("AFTER_COMMIT")
            val job = async(Dispatchers.IO) {
                runCatching { repo(db, DefaultImageStorage(images, Dispatchers.IO)).import(outfitOnlyZip(), "review-password") }
            }
            val committed = withTimeoutOrNull(10_000) {
                while (db.backupDao().outfits().isEmpty()) delay(50)
                true
            }
            assertTrue("outfit-only import did not reach commit", committed == true)
            job.cancel()
            gate.delete()
            job.join()
            val outfit = db.backupDao().outfits().singleOrNull()
            assertTrue("committed outfit was rolled back", outfit != null)
            val photo = File(images, outfit!!.photoPaths.single())
            assertTrue("committed outfit photo was deleted: $photo", photo.exists())
        } finally {
            gate.delete()
            db.close()
        }
    }

    @Test fun cancelAfterCommitMustKeepRowsAndImages() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, ClothesMateDatabase::class.java).build()
        val gate = File(context.filesDir, "import-sync-gate")
        try {
            val images = File(root, "images")
            gate.writeText("AFTER_COMMIT")
            val job = async(Dispatchers.IO) {
                runCatching { repo(db, DefaultImageStorage(images, Dispatchers.IO)).import(zip(), "review-password") }
            }
            val committed = withTimeoutOrNull(10_000) {
                while (db.backupDao().clothing().isEmpty()) delay(50)
                true
            }
            assertTrue("import did not reach commit", committed == true)
            job.cancel()
            gate.delete()
            job.join()
            assertEquals("committed rows were rolled back by cancellation", 1, db.backupDao().clothing().size)
            val copied = images.walkTopDown().filter { it.isFile }.toList()
            assertTrue("committed image was deleted by cancellation: $copied", copied.isNotEmpty())
        } finally {
            gate.delete()
            db.close()
        }
    }

    @Test fun healthyImportControlStillCopiesImage() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, ClothesMateDatabase::class.java).build()
        try {
            val images = File(root, "images")
            val result = repo(db, DefaultImageStorage(images, Dispatchers.IO)).import(zip(), "review-password")
            assertEquals(1, result.clothing)
            assertEquals(1, result.images)
            assertEquals(0, result.skipped)
            assertEquals(1, db.backupDao().clothing().size)
        } finally { db.close() }
    }
}
