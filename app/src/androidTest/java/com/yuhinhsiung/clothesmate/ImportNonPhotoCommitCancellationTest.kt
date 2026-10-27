package com.yuhinhsiung.clothesmate

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yuhinhsiung.clothesmate.core.backup.DefaultBackupRepository
import com.yuhinhsiung.clothesmate.core.backup.ImportResult
import com.yuhinhsiung.clothesmate.core.database.ClothesMateDatabase
import com.yuhinhsiung.clothesmate.core.database.entity.OutfitCategoryEntity
import com.yuhinhsiung.clothesmate.core.database.entity.TagDictionaryEntity
import com.yuhinhsiung.clothesmate.core.datastore.CodeCounterStore
import com.yuhinhsiung.clothesmate.core.datastore.InMemoryUserSettingsStore
import com.yuhinhsiung.clothesmate.core.image.DefaultImageStorage
import com.yuhinhsiung.clothesmate.feature.wardrobe.data.DefaultClothingRepository
import com.yuhinhsiung.clothesmate.feature.wardrobe.domain.CodeAllocator
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real exporter + real SQLite commit. No production prefs/DB/images, no ImportSyncGate. */
@RunWith(AndroidJUnit4::class)
class ImportNonPhotoCommitCancellationTest {
    private class Counter : CodeCounterStore {
        private var value = 1L
        override suspend fun currentValue() = value
        override suspend fun setValue(value: Long) { this.value = value }
        override suspend fun nextCode(existsConflict: suspend (Long) -> Boolean): Long {
            while (existsConflict(value)) value++
            return value++
        }
    }
    private class Hook(val withCategory: Boolean, val cancel: Boolean) {
        val armed = AtomicBoolean(false)
        val job = AtomicReference<Job?>(null)
        val fired = AtomicInteger()
        val wrapperFailure = AtomicReference<Throwable?>(null)
        fun afterEnd(db: SupportSQLiteDatabase) {
            if (!armed.get() || db.inTransaction()) return
            try {
                val tags = db.query("SELECT COUNT(*) FROM tag_dictionary WHERE value='review-synthetic-tag'")
                    .use { it.moveToFirst(); it.getInt(0) }
                val categories = db.query("SELECT COUNT(*) FROM outfit_category")
                    .use { it.moveToFirst(); it.getInt(0) }
                if (tags != 1 || categories != (if (withCategory) 1 else 0)) return
                if (!armed.compareAndSet(true, false)) return
                fired.incrementAndGet()
                if (cancel) checkNotNull(job.get()).cancel(CancellationException("review after real non-photo commit"))
            } catch (failure: Throwable) { wrapperFailure.set(failure) }
        }
    }
    private fun factory(hook: Hook) = object : SupportSQLiteOpenHelper.Factory {
        override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
            val actual = FrameworkSQLiteOpenHelperFactory().create(configuration)
            fun wrap(db: SupportSQLiteDatabase): SupportSQLiteDatabase = object : SupportSQLiteDatabase by db {
                override fun endTransaction() { db.endTransaction(); hook.afterEnd(db) }
            }
            return object : SupportSQLiteOpenHelper by actual {
                override val writableDatabase get() = wrap(actual.writableDatabase)
                override val readableDatabase get() = wrap(actual.readableDatabase)
            }
        }
    }
    private suspend fun scenario(withCategory: Boolean, cancel: Boolean) = coroutineScope {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val root = File(app.cacheDir, "m5-review-r4-nonphoto-${System.nanoTime()}")
        check(root.mkdirs())
        val files = File(root, "files").apply { check(mkdirs()) }
        val context = object : ContextWrapper(app) {
            override fun getCacheDir(): File = root
            override fun getFilesDir(): File = files
        }
        val hook = Hook(withCategory, cancel)
        val db = Room.inMemoryDatabaseBuilder(context, ClothesMateDatabase::class.java)
            .openHelperFactory(factory(hook)).build()
        var importJob: Job? = null
        try {
            val settings = InMemoryUserSettingsStore()
            val clothing = DefaultClothingRepository(db, db.clothingItemDao(), db.outfitDao(), db.codeHistoryDao())
            val repository = DefaultBackupRepository(
                context, db, db.backupDao(), db.wardrobeDao(), db.clothingItemDao(), db.codeHistoryDao(),
                db.outfitDao(), db.outfitCategoryDao(), db.wearRecordDao(), db.tagDictionaryDao(),
                DefaultImageStorage(File(root, "images"), Dispatchers.IO), CodeAllocator(Counter(), clothing), settings,
            )
            if (withCategory) db.outfitCategoryDao().insert(OutfitCategoryEntity(id=30, name="review category", sortOrder=0, createdAt=1))
            db.tagDictionaryDao().insert(TagDictionaryEntity(type="style", value="review-synthetic-tag"))
            settings.setWeatherCity("review-imported-city")
            settings.setStylePreference("review-imported-style")
            val uri = Uri.fromFile(File(root, "real-export.zip"))
            val summary = repository.export(uri, "review-password")
            assertTrue("export must contain data", summary.total > 0)
            assertEquals(0, summary.wardrobes)
            assertEquals(0, summary.clothing)
            assertEquals(0, summary.outfits)
            assertEquals(1, summary.tags)
            // Clear only this isolated test DB, before arming the transaction hook.
            if (withCategory) db.outfitCategoryDao().deleteById(30)
            db.openHelper.writableDatabase.execSQL("DELETE FROM tag_dictionary")
            settings.setWeatherCity("review-old-city")
            settings.setStylePreference("review-old-style")
            val error = AtomicReference<Throwable?>(null)
            val result = AtomicReference<ImportResult?>(null)
            val child = launch(Dispatchers.Default, start=CoroutineStart.LAZY) {
                try { result.set(repository.import(uri, "review-password")) }
                catch (failure: Throwable) { error.set(failure); if (failure is CancellationException) throw failure }
            }
            importJob = child
            hook.job.set(child)
            hook.armed.set(true)
            child.start()
            withTimeout(10_000) { child.join() }
            assertNull("wrapper failed: ${hook.wrapperFailure.get()}", hook.wrapperFailure.get())
            assertEquals("must hit exactly one populated real outer commit", 1, hook.fired.get())
            val categories = db.outfitCategoryDao().getAll()
            val tags = db.tagDictionaryDao().getByType("style")
            val after = settings.settings.first()
            val detail = "category=$withCategory cancel=$cancel childCancelled=${child.isCancelled} failure=${error.get()?.javaClass?.simpleName} categories=${categories.size} tags=${tags.size} city=${after.weatherCity} style=${after.stylePreference}"
            assertEquals("committed categories lost; $detail", if (withCategory) 1 else 0, categories.size)
            assertEquals("committed tag lost; $detail", 1, tags.size)
            if (cancel) {
                assertTrue("child not cancelled; $detail", child.isCancelled)
                assertTrue("caller did not receive cancellation; $detail", error.get() is CancellationException)
            } else {
                assertNull("normal control failed; $detail", error.get())
                assertNotNull("normal control returned no result; $detail", result.get())
            }
            assertEquals("committed import reverted city; $detail", "review-imported-city", after.weatherCity)
            assertEquals("committed import reverted style; $detail", "review-imported-style", after.stylePreference)
        } finally {
            importJob?.cancelAndJoin()
            db.close()
            check(root.canonicalFile.parentFile == app.cacheDir.canonicalFile)
            check(root.name.startsWith("m5-review-r4-nonphoto-"))
            root.deleteRecursively()
        }
        Unit
    }
    @Test fun categoryAndTagCommitCancellationMustKeepImportedSettings() = runBlocking { scenario(true, true) }
    @Test fun tagOnlyCommitCancellationMustKeepImportedSettings() = runBlocking { scenario(false, true) }
    @Test fun categoryAndTagNormalCommitControl() = runBlocking { scenario(true, false) }
}
