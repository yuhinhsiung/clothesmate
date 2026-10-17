package com.yuhinhsiung.clothesmate.core.image

import android.graphics.Bitmap
import android.net.Uri
import android.system.Os
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * 暂存 / 归位 / **归位补偿** 测试 —— review R-14。
 *
 * 锁的是「保存失败后草稿仍然可重试」这条承诺：
 * `promote()` 会 consume 暂存源，多张图批量归位时前面成功的源已经没了；
 * 只删归位产物的话草稿会指向不存在的文件，用户按提示重试仍然失败。
 * [DefaultImageStore.restoreStaged] 必须把状态真正还原（幂等），
 * 而不是删掉目标就算完。
 */
@RunWith(AndroidJUnit4::class)
class ImageStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var root: File
    private lateinit var store: DefaultImageStore
    private val touchUp = mutableListOf<File>()

    private fun setUp() {
        root = tempFolder.newFolder("images").also { it.mkdirs() }
        val context = androidx.test.core.app.ApplicationProvider
            .getApplicationContext<android.content.Context>()
        val dispatcher = UnconfinedTestDispatcher()
        store = DefaultImageStore(
            context = context,
            compressor = DefaultImageCompressor(context, dispatcher),
            storage = DefaultImageStorage(root, dispatcher),
            ioDispatcher = dispatcher,
        )
    }

    @After
    fun tearDown() {
        touchUp.forEach { runCatching { it.delete() } }
    }

    private fun sourceImage(name: String): Uri {
        val file = File(tempFolder.root, name)
        val bitmap = androidx.core.graphics.createBitmap(64, 64)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        bitmap.recycle()
        touchUp += file
        return Uri.fromFile(file)
    }

    @Test
    fun promoteMovesStagedFileToDestination() = runTest {
        setUp()
        val staged = store.stash(sourceImage("a.jpg"))
        val destination = File(root, "clothing/original/1.jpg")

        val relative = store.promote(staged, destination)

        assertEquals("clothing/original/1.jpg", relative)
        assertTrue(destination.exists())
        assertFalse("归位会消费暂存源", staged.file.exists())
    }

    @Test
    fun restoreStagedBringsTheFileBackSoTheDraftIsRetryable() = runTest {
        setUp()
        val staged = store.stash(sourceImage("b.jpg"))
        val destination = File(root, "clothing/original/2.jpg")
        store.promote(staged, destination)
        assertFalse(staged.file.exists())

        store.restoreStaged(staged, destination)

        assertTrue("退回暂存后草稿重新可重试", staged.file.exists())
        assertFalse("不能留下孤儿图片", destination.exists())

        // 重试路径就是再 promote 一次，必须成功
        val relative = store.promote(staged, destination)
        assertEquals("clothing/original/2.jpg", relative)
        assertTrue(destination.exists())
        store.clearStaged(staged)
    }

    /** 幂等：还没归位（源还在）时不能把暂存文件误删/误覆盖 */
    @Test
    fun restoreStagedIsIdempotentWhenSourceIsStillThere() = runTest {
        setUp()
        val staged = store.stash(sourceImage("c.jpg"))
        val destination = File(root, "clothing/original/3.jpg")

        store.restoreStaged(staged, destination)

        assertTrue(staged.file.exists())
        assertFalse(destination.exists())
        store.clearStaged(staged)
    }

    /** 第二张归位的失败场景：删掉目标模拟「归位产物已被清掉」，退回时不应炸 */
    @Test
    fun restoreStagedToleratesMissingDestination() = runTest {
        setUp()
        val staged = store.stash(sourceImage("d.jpg"))
        val missing = File(root, "clothing/original/404.jpg")

        store.restoreStaged(staged, missing)

        assertTrue("源还在，状态本来就是对的", staged.file.exists())
        store.clearStaged(staged)
    }

    // ── review R-14：复制回退不得破坏旧文件 ─────────────────────

    /** 让 `renameTo` 恒定失败，精确把生产代码逼进复制回退分支（不替换 promote 实现） */
    private fun File.renameAlwaysFails(): File = object : File(path) {
        override fun renameTo(dest: File): Boolean = false
    }

    private fun stageWith(content: String, name: String): File =
        File(tempFolder.root, name).apply { writeText(content) }

    /**
     * 复制回退的**正常路径**对照：旧目标被新内容替换、暂存源被消费、不留临时文件。
     */
    @Test
    fun copyFallbackReplacesTargetAndConsumesSource() = runTest {
        setUp()
        val stagedFile = stageWith("new-cover", "fallback-ok.jpg").renameAlwaysFails()
        val destination = File(root, "wardrobe_cover/1.jpg").apply {
            parentFile!!.mkdirs()
            writeText("old-original")
        }

        val relative = store.promote(StagedImage(stagedFile, stagedFile.length()), destination)

        assertEquals("wardrobe_cover/1.jpg", relative)
        assertEquals("new-cover", destination.readText())
        assertFalse("复制成功后暂存源应被消费", stagedFile.exists())
        assertFalse(
            "不得留下临时产物",
            File(destination.parentFile, destination.name + ".tmp").exists(),
        )
    }

    /**
     * 复制回退的**失败路径**（review R-14 的生产故障注入）：源不可读 → 旧目标必须逐字节不变。
     *
     * 旧实现走的是 `copyTo(overwrite = true)`——Kotlin 会**先 `target.delete()` 再读源**，
     * 源一读失败，旧封面就凭空消失，而数据库还指着那条路径。
     */
    @Test
    fun copyFallbackKeepsOldTargetWhenSourceReadFails() = runTest {
        setUp()
        val stagedFile = stageWith("new-cover", "fallback-fail.jpg").renameAlwaysFails()
        val destination = File(root, "wardrobe_cover/2.jpg").apply {
            parentFile!!.mkdirs()
            writeText("old-original")
        }
        // 应用自身 UID 读不了 000 权限的文件 → 真实 EACCES
        Os.chmod(stagedFile.path, 0)

        val error = runCatching {
            store.promote(StagedImage(stagedFile, stagedFile.length()), destination)
        }.exceptionOrNull()

        try {
            assertNotNull("读源失败必须抛出，不能假装成功", error)
            assertTrue("旧封面必须逐字节不变", destination.exists())
            assertEquals("old-original", destination.readText())
            assertTrue("暂存源要留着，草稿才能原地重试", stagedFile.exists())
            assertFalse(
                "本次的临时产物要清掉",
                File(destination.parentFile, destination.name + ".tmp").exists(),
            )
        } finally {
            // 恢复权限，否则 TemporaryFolder 清理时会失败
            Os.chmod(stagedFile.path, 0b110_100_100)
        }
    }

    /** 目标原本不存在 + 复制失败：不能凭空留下一个半截的目标文件 */
    @Test
    fun copyFallbackLeavesNoPartialTargetWhenSourceReadFails() = runTest {
        setUp()
        val stagedFile = stageWith("new-cover", "fallback-fresh.jpg").renameAlwaysFails()
        val destination = File(root, "wardrobe_cover/3.jpg").apply { parentFile!!.mkdirs() }
        Os.chmod(stagedFile.path, 0)

        runCatching { store.promote(StagedImage(stagedFile, stagedFile.length()), destination) }

        try {
            assertFalse("失败后不该出现目标文件", destination.exists())
            assertFalse(
                "也不该留下临时产物",
                File(destination.parentFile, destination.name + ".tmp").exists(),
            )
        } finally {
            Os.chmod(stagedFile.path, 0b110_100_100)
        }
    }

    /**
     * **写入侧**失败（review R-14 关闭条件里的「目标写到一半失败」）：
     * 目标目录不可写 → rename 与临时文件创建都失败 → 旧图必须逐字节不变。
     *
     * 这条不需要替换 promote 实现：目录 0555 会让生产代码的两条路径都真实失败。
     */
    @Test
    fun copyFallbackKeepsOldTargetWhenWriteSideFails() = runTest {
        setUp()
        val staged = stageWith("new-cover", "write-fail.jpg")
        val dir = File(root, "wardrobe_cover").apply { mkdirs() }
        val destination = File(dir, "4.jpg").apply { writeText("old-original") }
        // 0555：谁都别想在这个目录里创建 / 替换文件
        Os.chmod(dir.path, 0b101_101_101)

        val error = runCatching {
            store.promote(StagedImage(staged, staged.length()), destination)
        }.exceptionOrNull()

        try {
            assertNotNull("写不进目标目录必须抛出", error)
            assertTrue("旧封面必须还在", destination.exists())
            assertEquals("旧封面必须逐字节不变", "old-original", destination.readText())
            assertTrue("暂存源留着，草稿可重试", staged.exists())
            assertFalse(
                "临时产物要清掉",
                File(dir, destination.name + ".tmp").exists(),
            )
        } finally {
            Os.chmod(dir.path, 0b111_101_101)
        }
    }
}