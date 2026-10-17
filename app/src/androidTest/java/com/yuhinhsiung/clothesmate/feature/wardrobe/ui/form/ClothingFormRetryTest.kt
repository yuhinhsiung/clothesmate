package com.yuhinhsiung.clothesmate.feature.wardrobe.ui.form

import android.graphics.Bitmap
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuhinhsiung.clothesmate.core.database.ClothesMateDatabase
import com.yuhinhsiung.clothesmate.core.database.entity.WardrobeEntity
import com.yuhinhsiung.clothesmate.core.datastore.CodeCounterStore
import com.yuhinhsiung.clothesmate.core.image.DefaultImageStorage
import com.yuhinhsiung.clothesmate.core.image.ImageStore
import com.yuhinhsiung.clothesmate.core.image.StagedImage
import com.yuhinhsiung.clothesmate.feature.wardrobe.data.DefaultClothingRepository
import com.yuhinhsiung.clothesmate.feature.wardrobe.data.DefaultWardrobeRepository
import com.yuhinhsiung.clothesmate.feature.wardrobe.domain.CodeAllocator
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 建档失败后**同一个表单原地重试** —— review R-14 场景 A。
 *
 * 现场：手动填编号 500 → 第一次保存时图片归位注入 IOException → 补偿把行删了。
 * 但**编号历史仍把 500 记在刚被删掉的那一行名下**，于是重试直接被
 * 「编号 500 已被使用」拦住：用户什么都没得到，号却没了。
 *
 * 修法：补偿改走 `rollbackFailedCreate`，把**本次预约**的编号占用一并撤回
 * （只删 holder 恰好是本行的那一条，别人占用的历史行不受影响 → 不重新引入 R-03）。
 */
@RunWith(AndroidJUnit4::class)
class ClothingFormRetryTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val prefs = object : DataStore<Preferences> {
        override val data: Flow<Preferences> = flowOf(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(emptyPreferences())
    }

    private class FixedCounter(private var value: Long = 1L) : CodeCounterStore {
        override suspend fun currentValue() = value
        override suspend fun setValue(value: Long) {
            this.value = value
        }

        override suspend fun nextCode(existsConflict: suspend (Long) -> Boolean): Long {
            while (existsConflict(value)) value++
            return value++
        }
    }

    private suspend fun newWardrobeId(db: ClothesMateDatabase): Long = db.wardrobeDao().insert(
        WardrobeEntity(name = "retry", note = null, coverImagePath = null, sortOrder = 0, createdAt = 1L),
    )

    @Test
    fun manualCodeMustRetryInSameFormAfterPromoteFailure() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, ClothesMateDatabase::class.java).build()
        val clothingRepo = DefaultClothingRepository(db, db.clothingItemDao(), db.outfitDao(), db.codeHistoryDao())
        val wardrobeRepo = DefaultWardrobeRepository(db, db.wardrobeDao(), clothingRepo)
        val wid = newWardrobeId(db)
        val dir = File(context.cacheDir, "retry-${System.nanoTime()}").apply { mkdirs() }
        val stagedFile = File(dir, "staged.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val storage = DefaultImageStorage(File(dir, "images"), Dispatchers.IO)
        val failedOnce = AtomicBoolean(false)
        val imageStore = object : ImageStore {
            override suspend fun stash(uri: Uri) = StagedImage(stagedFile, stagedFile.length())
            override suspend fun promote(staged: StagedImage, destination: File): String {
                if (!failedOnce.getAndSet(true)) throw IOException("injected promote failure")
                destination.parentFile!!.mkdirs()
                staged.file.copyTo(destination, overwrite = true)
                return requireNotNull(storage.toRelative(destination))
            }

            override suspend fun restoreStaged(staged: StagedImage, destination: File) = Unit
            override suspend fun clearStaged(staged: StagedImage) {
                staged.file.delete()
            }

            override suspend fun stageCutout(bitmap: Bitmap) =
                StagedImage(stagedFile, stagedFile.length())
        }
        val vm = ClothingFormViewModel(
            clothingRepo,
            wardrobeRepo,
            CodeAllocator(FixedCounter(), clothingRepo),
            imageStore,
            storage,
            db.tagDictionaryDao(),
            prefs,
            com.yuhinhsiung.clothesmate.core.datastore.InMemoryUserSettingsStore(),
            object : com.yuhinhsiung.clothesmate.core.ai.ClothingRecognizer {
                override suspend fun recognize(
                    endpoint: com.yuhinhsiung.clothesmate.core.ai.RecognitionEndpoint,
                    imageJpeg: ByteArray,
                ) = com.yuhinhsiung.clothesmate.core.ai.AiRecognition()

                override suspend fun testConnection(
                    endpoint: com.yuhinhsiung.clothesmate.core.ai.RecognitionEndpoint,
                ) = com.yuhinhsiung.clothesmate.core.ai.ConnectionTestResult(false)
            },
            object : com.yuhinhsiung.clothesmate.core.image.LocalColorExtractorService {
                override suspend fun extractKeys(uri: Uri): List<String> = emptyList()
            },
            object : com.yuhinhsiung.clothesmate.core.image.BitmapLoader {
                override suspend fun decode(uri: Uri, maxEdge: Int) =
                    error("本用例不触发 AI 取图")

                override suspend fun decode(file: File, maxEdge: Int) =
                    error("本用例不触发 AI 取图")

                override suspend fun jpegBytes(
                    uri: Uri,
                    maxEdge: Int,
                    quality: Int,
                ): ByteArray = error("本用例不触发 AI 取图")

                override suspend fun jpegBytes(
                    file: File,
                    maxEdge: Int,
                    quality: Int,
                ): ByteArray = error("本用例不触发 AI 取图")
            },
            SavedStateHandle(
                mapOf("itemId" to null, "imageUri" to "file:///retry.jpg", "wardrobeId" to wid),
            ),
        )
        val store = ViewModelStore().apply { put("form", vm) }
        try {
            withTimeout(5_000) {
                vm.uiState.filter { !it.compressing && it.code.isNotBlank() && it.wardrobeId != null }.first()
            }
            vm.onCodeChange("500")
            // 等编号校验落定：`onCodeChange` 是异步校验，抢在它前面按保存会让
            // 「校验读到写库后的占用」这种竞态污染后续断言
            kotlinx.coroutines.delay(150)
            vm.save { _, _ -> fail("第一次保存应当停在注入的归位失败上") }
            withTimeout(5_000) { vm.uiState.filter { !it.saving && it.error != null }.first() }

            assertEquals("回滚后不该留下衣物行", 0, db.clothingItemDao().observeAll().first().size)
            assertNull(
                "R-14：未完成的建档必须把编号占用一并撤回，否则同一草稿重试会被自己挡住",
                db.codeHistoryDao().holderOf("500"),
            )

            var saved = false
            // 失败后 VM 会拿回滚后的库状态重新对一次编号校验，等它落定再重试
            kotlinx.coroutines.delay(200)
            vm.save { _, _ -> saved = true }
            val deadline = System.currentTimeMillis() + 5_000
            while (!saved && System.currentTimeMillis() < deadline) {
                kotlinx.coroutines.delay(50)
            }
            assertTrue(
                "R-14：同一表单重试必须成功（当前错误：${vm.uiState.value.error}）",
                saved,
            )
            assertEquals("重试只应产生一条记录", 1, db.clothingItemDao().observeAll().first().size)
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() }
            db.close()
            dir.deleteRecursively()
        }
    }

    /**
     * 反例保护：**别人的**编号占用不能被这次回滚清掉（不然 R-03 的洞又开了）。
     */
    @Test
    fun rollbackDoesNotFreeCodesHeldByOthers() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, ClothesMateDatabase::class.java).build()
        val clothingRepo = DefaultClothingRepository(db, db.clothingItemDao(), db.outfitDao(), db.codeHistoryDao())
        val wid = newWardrobeId(db)
        try {
            // 已存在的一件衣物占着 500
            val existing = clothingRepo.createItem(
                com.yuhinhsiung.clothesmate.core.model.ClothingItem(
                    id = 0L,
                    code = "500",
                    wardrobeId = wid,
                    categoryKey = null,
                    colorKeys = emptyList(),
                    styleTags = emptyList(),
                    seasonTags = emptyList(),
                    originalImagePath = null,
                    cutoutImagePath = null,
                    brand = null,
                    name = null,
                    note = null,
                    wearCount = 0,
                    lastWornAt = null,
                    status = com.yuhinhsiung.clothesmate.core.model.ClothingStatus.NORMAL,
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            )

            // 拿一个无关的 id 去「回滚」，不得动摇 500 的占用
            clothingRepo.rollbackFailedCreate(id = existing + 999, code = "500")

            assertEquals(
                "只有持有人吻合时才撤回，别人的占用必须留着",
                existing,
                db.codeHistoryDao().holderOf("500"),
            )
            assertTrue(clothingRepo.isCodeTaken("500"))
        } finally {
            db.close()
        }
    }
}