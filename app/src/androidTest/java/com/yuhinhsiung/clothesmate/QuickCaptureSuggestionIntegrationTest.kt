package com.yuhinhsiung.clothesmate

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Data
import androidx.work.WorkerParameters
import com.yuhinhsiung.clothesmate.core.ai.*
import com.yuhinhsiung.clothesmate.core.database.ClothesMateDatabase
import com.yuhinhsiung.clothesmate.core.database.entity.*
import com.yuhinhsiung.clothesmate.core.datastore.*
import com.yuhinhsiung.clothesmate.core.image.*
import com.yuhinhsiung.clothesmate.core.model.ClothingStatus
import com.yuhinhsiung.clothesmate.feature.addclothing.data.*
import com.yuhinhsiung.clothesmate.feature.wardrobe.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.yuhinhsiung.clothesmate.feature.wardrobe.domain.CodeAllocator
import com.yuhinhsiung.clothesmate.feature.wardrobe.ui.form.ClothingFormViewModel
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.Executor
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

/** Original Worker invariants adapted to the new pending-suggestion API; real Worker/DAO. */
@RunWith(AndroidJUnit4::class)
class QuickCaptureSuggestionIntegrationTest {
    private lateinit var context: Context
    private lateinit var db: ClothesMateDatabase
    private lateinit var base: ClothingRepository
    private lateinit var root: File
    private lateinit var storage: ImageStorage
    private lateinit var suggestions: QuickCaptureAiSuggestionStore
    @Before fun before() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, ClothesMateDatabase::class.java).build()
        base = DefaultClothingRepository(db, db.clothingItemDao(), db.outfitDao(), db.codeHistoryDao())
        root = File(context.cacheDir, "m4-ai-acceptance-${System.nanoTime()}").apply { mkdirs() }
        storage = DefaultImageStorage(root, Dispatchers.IO)
        val isolated = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = File(context.cacheDir, "m4-acceptance-suggestions").apply { mkdirs() }
        }
        suggestions = QuickCaptureAiSuggestionStore(isolated)
        suggestions.remove(1)
        db.wardrobeDao().insert(WardrobeEntity(1, "review", null, null, 0, 1))
        db.clothingItemDao().insert(ClothingItemEntity(id = 1, code = "1", wardrobeId = 1,
            category = null, colors = emptyList(), styleTags = listOf("手动正装"), seasonTags = listOf("winter"),
            originalImagePath = "clothing/original/1.jpg", cutoutImagePath = null, brand = null,
            name = null, note = null, wearCount = 4, createdAt = 1, updatedAt = 1))
        Unit
    }
    @After fun after() = runBlocking { suggestions.remove(1); db.close(); root.deleteRecursively(); Unit }
    private suspend fun aiStage(beforeReturn: suspend () -> Unit = {}) {
        val params = WorkerParameters::class.java.constructors.single().newInstance(
            UUID.randomUUID(), Data.EMPTY, emptyList<String>(), WorkerParameters.RuntimeExtras(), 0, 0,
            Executor { it.run() }, Dispatchers.IO, null, null, null, null,
        ) as WorkerParameters
        val worker = QuickCaptureWorker(context, params)
        val entry = object : QuickCaptureWorkerEntryPoint {
            override fun backgroundRemover(): BackgroundRemover = error("unused")
            override fun imageStore(): ImageStore = error("unused")
            override fun imageStorage(): ImageStorage = storage
            override fun clothingRepository(): ClothingRepository = base
            override fun colorExtractor(): LocalColorExtractorService = error("unused")
            override fun aiSuggestionStore() = suggestions
            override fun settingsStore(): UserSettingsStore = InMemoryUserSettingsStore(UserSettings(
                aiConfig = AiConfig("https://example.invalid", "fake-review-key", "model"), aiConsent = AiConsent.Granted))
            override fun bitmapLoader(): BitmapLoader = object : BitmapLoader {
                override suspend fun decode(uri: Uri, maxEdge: Int): Bitmap = error("unused")
                override suspend fun decode(file: File, maxEdge: Int): Bitmap = error("unused")
                override suspend fun jpegBytes(uri: Uri, maxEdge: Int, quality: Int) = byteArrayOf(1)
                override suspend fun jpegBytes(file: File, maxEdge: Int, quality: Int) = byteArrayOf(1)
            }
            override fun recognizer(): ClothingRecognizer = object : ClothingRecognizer {
                override suspend fun recognize(endpoint: RecognitionEndpoint, imageJpeg: ByteArray): AiRecognition {
                    beforeReturn()
                    return AiRecognition("shirt", listOf("休闲"), listOf("summer"))
                }
                override suspend fun testConnection(endpoint: RecognitionEndpoint) = ConnectionTestResult(true)
            }
        }
        worker.javaClass.getDeclaredField("entry\$delegate").apply { isAccessible = true }.set(worker, lazyOf(entry))
        val method = worker.javaClass.declaredMethods.single { it.name == "applyAiSuggestion" }.apply { isAccessible = true }
        suspendCoroutineUninterceptedOrReturn<Any?> { cont -> method.invoke(worker, 1L, cont) }
    }
    @Test fun workerLeavesManualTagsAndCategoryUntouchedUntilAccepted() = runBlocking {
        aiStage()
        val item = base.getItem(1)!!
        assertNull(item.categoryKey)
        assertEquals(listOf("手动正装"), item.styleTags)
        assertEquals(listOf("winter"), item.seasonTags)
        assertEquals("shirt", suggestions.get(1)!!.categoryKey)
    }
    @Test fun workerPreservesEditAndWearCountDuringRecognition() = runBlocking {
        aiStage {
            base.updateItem(base.getItem(1)!!.copy(note = "用户刚写的备注", status = ClothingStatus.STORED, wearCount = 5))
        }
        val item = base.getItem(1)!!
        assertEquals("用户刚写的备注", item.note)
        assertEquals(5, item.wearCount)
        assertEquals(ClothingStatus.STORED, item.status)
    }
    @Test fun acceptedSuggestionOnlyFillsFieldsThatAreStillEmpty() = runBlocking {
        aiStage()
        val pending = suggestions.get(1)!!
        base.updateItem(base.getItem(1)!!.copy(note = "已确认", status = ClothingStatus.STORED, wearCount = 5))
        base.applyAiSuggestionIfEmpty(1, pending.categoryKey, pending.styleTags, pending.seasonTags)
        val item = base.getItem(1)!!
        assertEquals("shirt", item.categoryKey)
        assertEquals(listOf("手动正装"), item.styleTags)
        assertEquals(listOf("winter"), item.seasonTags)
        assertEquals("已确认", item.note)
        assertEquals(5, item.wearCount)
        assertEquals(ClothingStatus.STORED, item.status)
    }
    @Test fun localBackgroundPatchesPreserveOtherFieldsAndManualColors() = runBlocking {
        base.updateItem(base.getItem(1)!!.copy(colorKeys = listOf("red"), note = "用户编辑", status = ClothingStatus.STORED, wearCount = 5))
        base.setColorsIfEmpty(1, listOf("blue"))
        base.setCutoutIfEmpty(1, "clothing/cutout/1.png")
        base.setCutoutIfEmpty(1, "clothing/cutout/other.png")
        val item = base.getItem(1)!!
        assertEquals(listOf("red"), item.colorKeys)
        assertEquals("clothing/cutout/1.png", item.cutoutImagePath)
        assertEquals("用户编辑", item.note)
        assertEquals(5, item.wearCount)
        assertEquals(ClothingStatus.STORED, item.status)
    }
    @Suppress("UNCHECKED_CAST")
    private fun <T> unused(type: Class<T>): T = java.lang.reflect.Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ ->
        error("Unexpected collaborator call: ${method.name}")
    } as T
    @Test fun pendingSuggestionRemainsReviewableWhenReopeningIncompleteItem() = runBlocking {
        aiStage()
        assertNotNull(suggestions.get(1))
        val prefs = object : DataStore<Preferences> {
            override val data = flowOf(emptyPreferences())
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = transform(emptyPreferences())
        }
        val counter = object : CodeCounterStore {
            override suspend fun currentValue() = 2L
            override suspend fun setValue(value: Long) = Unit
            override suspend fun nextCode(existsConflict: suspend (Long) -> Boolean) = 2L
        }
        val wardrobe = DefaultWardrobeRepository(db, db.wardrobeDao(), base)
        val viewModels = ViewModelStore()
        val vm = withContext(Dispatchers.Main) {
            ViewModelProvider(viewModels, object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ClothingFormViewModel(base, wardrobe, CodeAllocator(counter, base), unused(ImageStore::class.java),
                        storage, db.tagDictionaryDao(), prefs, suggestions, InMemoryUserSettingsStore(), unused(ClothingRecognizer::class.java),
                        unused(LocalColorExtractorService::class.java), unused(BitmapLoader::class.java),
                        SavedStateHandle(mapOf("itemId" to 1L))) as T
            })[ClothingFormViewModel::class.java]
        }
        try {
            val state = withTimeout(5000) { vm.uiState.first { it.code == "1" && it.imageReady } }
            assertNotNull("Persisted quick-capture suggestion must be offered on later completion, after leaving the review session", state.aiSuggestions)
        } finally { withContext(Dispatchers.Main) { viewModels.clear() } }
    }
}
