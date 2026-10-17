package com.yuhinhsiung.clothesmate.feature.wardrobe.ui

import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuhinhsiung.clothesmate.core.database.ClothesMateDatabase
import com.yuhinhsiung.clothesmate.core.image.DefaultImageStorage
import com.yuhinhsiung.clothesmate.core.image.ImageStore
import com.yuhinhsiung.clothesmate.core.image.StagedImage
import com.yuhinhsiung.clothesmate.feature.wardrobe.data.DefaultClothingRepository
import com.yuhinhsiung.clothesmate.feature.wardrobe.data.DefaultWardrobeRepository
import com.yuhinhsiung.clothesmate.feature.wardrobe.data.WardrobeRepository
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 衣柜弹层的**会话生命周期**与**封面文件保护** —— review A-01 / R-14 场景 B。
 *
 * 两件事放在一个壳里：它们的失败模式都发生在「保存失败 / 配置重建」这两条非正常路径上，
 * 且共用同一套 `WardrobeEditViewModel` + 文件假件。
 */
@RunWith(AndroidJUnit4::class)
class WardrobeFormSessionTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val prefs = object : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            val updated = transform(data.value)
            data.value = updated
            return updated
        }
    }

    private suspend fun withRepo(
        block: suspend (ClothesMateDatabase, WardrobeRepository, ViewModelStore) -> Unit,
    ) {
        val db = Room.inMemoryDatabaseBuilder(context, ClothesMateDatabase::class.java).build()
        val clothing = DefaultClothingRepository(db, db.clothingItemDao(), db.outfitDao(), db.codeHistoryDao())
        val repo = DefaultWardrobeRepository(db, db.wardrobeDao(), clothing)
        val store = ViewModelStore()
        try {
            block(db, repo, store)
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() }
            db.close()
        }
    }

    /** 只认暂存文件本身的假件：promote 真的把暂存文件搬到目标（与生产语义一致） */
    private fun movingImageStore(storage: DefaultImageStorage, staged: File) = object : ImageStore {
        override suspend fun stash(uri: Uri) = StagedImage(staged, staged.length())
        override suspend fun promote(stagedImage: StagedImage, destination: File): String {
            check(stagedImage.file.renameTo(destination))
            return requireNotNull(storage.toRelative(destination))
        }

        override suspend fun restoreStaged(stagedImage: StagedImage, destination: File) = Unit
        override suspend fun clearStaged(stagedImage: StagedImage) = Unit
    }

    // ── A-01：配置重建不能当作用户重开 ──────────────────────────

    @Test
    fun sameSessionIdKeepsDraftAcrossRecomposition() = runBlocking {
        withRepo { _, repo, store ->
            val dir = File(context.cacheDir, "session-${System.nanoTime()}").apply { mkdirs() }
            val storage = DefaultImageStorage(dir, Dispatchers.IO)
            try {
                val vm = WardrobeEditViewModel(repo, movingImageStore(storage, File(dir, "s.jpg")), storage)
                store.put("editor", vm)

                vm.startSession(sessionId = 1, wardrobeId = null)
                vm.onNameChange("CMRotateDraft")
                vm.onNoteChange("备注也要留住")

                // 旋转 / 切字号：同一个会话身份再送一次（LaunchedEffect 会重跑）
                vm.startSession(sessionId = 1, wardrobeId = null)

                assertEquals("A-01：同会话重建不得清空草稿", "CMRotateDraft", vm.uiState.value.name)
                assertEquals("备注也要留住", vm.uiState.value.note)

                // 用户真的又开了一次弹层 → 会话身份递增 → 才复位
                vm.startSession(sessionId = 2, wardrobeId = null)
                assertEquals("", vm.uiState.value.name)
                assertEquals("", vm.uiState.value.note)
                assertNull(vm.uiState.value.editingId)
            } finally {
                dir.deleteRecursively()
            }
        }
    }

    @Test
    fun editDraftSurvivesSameSessionRecomposition() = runBlocking {
        withRepo { _, repo, store ->
            val dir = File(context.cacheDir, "session-edit-${System.nanoTime()}").apply { mkdirs() }
            val storage = DefaultImageStorage(dir, Dispatchers.IO)
            try {
                val a = repo.createWardrobe("A", "old", null)
                val vm = WardrobeEditViewModel(repo, movingImageStore(storage, File(dir, "s.jpg")), storage)
                store.put("editor", vm)

                vm.startSession(sessionId = 7, wardrobeId = a)
                withTimeout(5_000) { vm.uiState.filter { it.editingId == a }.first() }
                vm.onNameChange("改了一半")

                vm.startSession(sessionId = 7, wardrobeId = a)

                assertEquals(a, vm.uiState.value.editingId)
                assertEquals("改了一半", vm.uiState.value.name)
            } finally {
                dir.deleteRecursively()
            }
        }
    }

    /** 关闭 / 保存结束会话后，同一个 id 再进来必须重新初始化（否则会拿旧 id 去新建） */
    @Test
    fun finishSessionMakesFollowingSameIdStartFresh() = runBlocking {
        withRepo { _, repo, store ->
            val dir = File(context.cacheDir, "session-finish-${System.nanoTime()}").apply { mkdirs() }
            val storage = DefaultImageStorage(dir, Dispatchers.IO)
            try {
                val a = repo.createWardrobe("A", null, null)
                val vm = WardrobeEditViewModel(repo, movingImageStore(storage, File(dir, "s.jpg")), storage)
                store.put("editor", vm)

                vm.startSession(sessionId = 3, wardrobeId = a)
                withTimeout(5_000) { vm.uiState.filter { it.editingId == a }.first() }
                vm.onNameChange("改了")
                vm.finishSession()
                assertNull(vm.uiState.value.editingId)

                vm.startSession(sessionId = 4, wardrobeId = null)
                assertNull("新会话必须是新建态", vm.uiState.value.editingId)
                assertEquals("", vm.uiState.value.name)

                // 编辑保存后再新建，不得覆盖旧柜（R-02 的回归点，A-01 改动后仍需成立）
                vm.startSession(sessionId = 5, wardrobeId = a)
                withTimeout(5_000) { vm.uiState.filter { it.editingId == a }.first() }
                vm.onNameChange("Edited A")
                val saved = java.util.concurrent.CompletableFuture<Long>()
                vm.save { id, _ -> saved.complete(id) }
                assertEquals(a, saved.get(5, java.util.concurrent.TimeUnit.SECONDS))

                vm.startSession(sessionId = 6, wardrobeId = null)
                assertEquals("", vm.uiState.value.name)
                vm.onNameChange("B")
                val created = java.util.concurrent.CompletableFuture<Long>()
                vm.save { id, _ -> created.complete(id) }
                val b = created.get(5, java.util.concurrent.TimeUnit.SECONDS)
                assertNotEquals(a, b)
                assertEquals("Edited A", repo.getWardrobe(a)!!.name)
            } finally {
                dir.deleteRecursively()
            }
        }
    }

    // ── R-14 场景 B：编辑封面时数据库失败，旧图必须还在 ──────────

    /**
     * 旧实现的顺序是「先 promote（覆盖同名旧封面、消耗暂存源）→ 再写库」，
     * 于是写库失败时旧封面内容已被换成新图、草稿也失去了可重试的暂存文件。
     * 现在改成**先写库、再动文件**，失败时两边都原样保留。
     */
    @Test
    fun existingCoverSurvivesDatabaseUpdateFailure() = runBlocking {
        withRepo { _, repo, store ->
            val dir = File(context.cacheDir, "cover-failure-${System.nanoTime()}").apply { mkdirs() }
            val storage = DefaultImageStorage(dir, Dispatchers.IO)
            try {
                val id = repo.createWardrobe("A", null, null)
                val original = storage.wardrobeCoverFile(id).apply {
                    parentFile!!.mkdirs()
                    writeText("old-original")
                }
                repo.updateWardrobe(id, "A", null, storage.toRelative(original))

                val staged = File(dir, "staged.jpg").apply { writeText("new-cover") }
                val failingRepo = object : WardrobeRepository by repo {
                    override suspend fun updateWardrobe(
                        id: Long,
                        name: String,
                        note: String?,
                        coverImagePath: String?,
                    ) {
                        throw IOException("injected database update failure")
                    }
                }
                val vm = WardrobeEditViewModel(
                    failingRepo,
                    movingImageStore(storage, staged),
                    storage,
                )
                store.put("cover", vm)

                vm.startSession(sessionId = 1, wardrobeId = id)
                withTimeout(5_000) { vm.uiState.filter { it.editingId == id }.first() }
                vm.onCoverPicked(Uri.parse("file:///fixture.jpg"))
                withTimeout(5_000) { vm.uiState.filter { it.stagedCover != null && !it.compressing }.first() }

                vm.save { _, _ -> fail("updateWardrobe 被注入失败，不应走到成功回调") }
                withTimeout(5_000) { vm.uiState.filter { !it.saving && it.error != null }.first() }

                assertEquals(
                    "R-14：写库失败前不得覆盖旧封面",
                    "old-original",
                    original.readText(),
                )
                assertTrue("R-14：暂存图必须留着，草稿才能原地重试", staged.exists())
            } finally {
                dir.deleteRecursively()
            }
        }
    }

    /** 对照路径：写库与归位都成功时，封面确实换成新图 */
    @Test
    fun successfulCoverEditReplacesFile() = runBlocking {
        withRepo { _, repo, store ->
            val dir = File(context.cacheDir, "cover-ok-${System.nanoTime()}").apply { mkdirs() }
            val storage = DefaultImageStorage(dir, Dispatchers.IO)
            try {
                val id = repo.createWardrobe("A", null, null)
                val original = storage.wardrobeCoverFile(id).apply {
                    parentFile!!.mkdirs()
                    writeText("old-original")
                }
                repo.updateWardrobe(id, "A", null, storage.toRelative(original))

                val staged = File(dir, "staged.jpg").apply { writeText("new-cover") }
                val vm = WardrobeEditViewModel(repo, movingImageStore(storage, staged), storage)
                store.put("cover", vm)

                vm.startSession(sessionId = 1, wardrobeId = id)
                withTimeout(5_000) { vm.uiState.filter { it.editingId == id }.first() }
                vm.onCoverPicked(Uri.parse("file:///fixture.jpg"))
                withTimeout(5_000) { vm.uiState.filter { it.stagedCover != null && !it.compressing }.first() }

                val saved = java.util.concurrent.CompletableFuture<Long>()
                vm.save { savedId, _ -> saved.complete(savedId) }
                assertEquals(id, saved.get(5, java.util.concurrent.TimeUnit.SECONDS))

                assertEquals("new-cover", original.readText())
                assertTrue(original.exists())
            } finally {
                dir.deleteRecursively()
            }
        }
    }
}