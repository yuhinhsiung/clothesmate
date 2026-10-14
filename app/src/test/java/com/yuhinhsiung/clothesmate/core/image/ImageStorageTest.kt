package com.yuhinhsiung.clothesmate.core.image

import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 图片存储测试 —— 开发计划 M1-03「只返回相对路径 / 删除后文件从磁盘消失」。
 *
 * 纯 JVM 直跑（无需设备）：[DefaultImageStorage] 的依赖只有 images 根目录与调度器，
 * 把 [TemporaryFolder] 注进构造即可控住全部文件副作用。
 */
class ImageStorageTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var root: File
    private lateinit var storage: ImageStorage

    /** Unconfined 让 suspend 删除体同步跑完，测试无需 advanceUntilIdle */
    private fun createStorage(): ImageStorage =
        DefaultImageStorage(root, UnconfinedTestDispatcher())

    private fun setUpWith(rootDir: File) {
        root = rootDir
        root.mkdirs()
        storage = createStorage()
    }

    // ── 路径规范（开发文档 §3.3 四种文件） ────────────────────────

    @Test
    fun `衣物原图路径符合图片存储规范`() {
        setUpWith(tempFolder.newFolder("images"))
        assertEquals(
            File(root, "clothing/original/42.jpg"),
            storage.clothingOriginalFile(42L),
        )
    }

    @Test
    fun `衣物抠图必须是 png（透明背景，JPEG 不支持 alpha）`() {
        setUpWith(tempFolder.newFolder("images"))
        assertEquals(
            File(root, "clothing/cutout/42.png"),
            storage.clothingCutoutFile(42L),
        )
    }

    @Test
    fun `穿搭照片 index 从 0 起且 0 是封面`() {
        setUpWith(tempFolder.newFolder("images"))
        assertEquals(File(root, "outfit/7/0.jpg"), storage.outfitPhotoFile(7L, 0))
        assertEquals(File(root, "outfit/7/8.jpg"), storage.outfitPhotoFile(7L, 8))
    }

    @Test
    fun `衣柜封面路径符合图片存储规范`() {
        setUpWith(tempFolder.newFolder("images"))
        assertEquals(
            File(root, "wardrobe_cover/99.jpg"),
            storage.wardrobeCoverFile(99L),
        )
    }

    // ── 相对路径往返（红线：DB 只存相对路径） ─────────────────────

    @Test
    fun `相对路径往返一致，且 DB 侧形态无绝对路径`() {
        setUpWith(tempFolder.newFolder("images"))
        val file = storage.clothingOriginalFile(12L)
        val relative = storage.toRelative(file)

        assertNotNull(relative)
        assertEquals("clothing/original/12.jpg", relative)
        // 红线 1：写进 DB 的字符串绝不含文件系统根
        assertFalse(relative!!.contains(":"))
        assertFalse(relative.startsWith("/"))
        // 往返可逆
        assertEquals(file.canonicalFile, storage.toFile(relative)!!.canonicalFile)
    }

    @Test
    fun `root 之外的相对路径返回 null`() {
        setUpWith(tempFolder.newFolder("images"))
        val outside = tempFolder.newFile("outside.jpg")
        assertNull(storage.toRelative(outside))
    }

    // ── 非法路径拒绝（toFile 是唯一路径注入面） ───────────────────

    @Test
    fun `拒绝绝对路径与上层逃逸路径`() {
        setUpWith(tempFolder.newFolder("images"))

        // Android 绝对路径：这是红线 1 明确禁止进 DB 的形态
        assertNull(storage.toFile("/storage/emulated/0/DCIM/x.jpg"))
        assertNull(storage.toFile("/data/data/com.yuhinhsiung.clothesmate/files/images/clothing/original/1.jpg"))
        assertNull(storage.toFile("file:///storage/emulated/0/x.jpg"))
        assertNull(storage.toFile("\\x.jpg"))

        // `..` 逃逸：canonical 后会落到 images 根之外
        assertNull(storage.toFile("../../etc/hosts"))
        assertNull(storage.toFile("clothing/../../../secret.jpg"))
    }

    @Test
    fun `空串与空白返回 null，合法相对路径返回 root 下文件`() {
        setUpWith(tempFolder.newFolder("images"))
        assertNull(storage.toFile(null))
        assertNull(storage.toFile(""))
        assertNull(storage.toFile("   "))

        val resolved = storage.toFile("clothing/original/5.jpg")
        assertNotNull(resolved)
        assertEquals(File(root, "clothing/original/5.jpg").canonicalFile, resolved!!.canonicalFile)
    }

    @Test
    fun `同前缀兄弟目录不会因字符串前缀相同而放行`() {
        // root=/tmp/images 时，/tmp/images-evil/x.jpg 不能因字符串前缀相同而通过
        val parent = tempFolder.newFolder("boundary")
        setUpWith(File(parent, "images"))
        File(parent, "images-evil").mkdirs()
        // 该路径相对 images 根本就是越界的（含 ..），正常输入不应出现；
        // 这里守住的是「前缀比较必须比到分隔符」这条实现细节
        assertNull(storage.toFile("../images-evil/x.jpg"))
    }

    // ── 删除：文件从磁盘消失 ─────────────────────────────────────

    @Test
    fun `删除衣物后原图与抠图都消失`() = runTest {
        setUpWith(tempFolder.newFolder("images"))
        storage.clothingOriginalFile(1L).apply { parentFile!!.mkdirs(); writeText("orig") }
        storage.clothingCutoutFile(1L).apply { parentFile!!.mkdirs(); writeText("cut") }

        storage.deleteClothingImages(1L)

        assertFalse(storage.clothingOriginalFile(1L).exists())
        assertFalse(storage.clothingCutoutFile(1L).exists())
    }

    @Test
    fun `删除穿搭会清掉整棵照片目录`() = runTest {
        setUpWith(tempFolder.newFolder("images"))
        storage.outfitPhotoFile(3L, 0).apply { parentFile!!.mkdirs(); writeText("a") }
        storage.outfitPhotoFile(3L, 1).apply { parentFile!!.mkdirs(); writeText("b") }

        storage.deleteOutfitImages(3L)

        assertFalse(File(root, "outfit/3").exists())
    }

    @Test
    fun `删除衣柜封面不影响其他目录`() = runTest {
        setUpWith(tempFolder.newFolder("images"))
        storage.wardrobeCoverFile(1L).apply { parentFile!!.mkdirs(); writeText("cover") }
        storage.clothingOriginalFile(1L).apply { parentFile!!.mkdirs(); writeText("orig") }

        storage.deleteWardrobeCover(1L)

        assertFalse(storage.wardrobeCoverFile(1L).exists())
        assertTrue(storage.clothingOriginalFile(1L).exists())
    }

    @Test
    fun `删除单张穿搭照片只删目标 index`() = runTest {
        setUpWith(tempFolder.newFolder("images"))
        storage.outfitPhotoFile(3L, 0).apply { parentFile!!.mkdirs(); writeText("a") }
        storage.outfitPhotoFile(3L, 1).apply { parentFile!!.mkdirs(); writeText("b") }

        storage.deleteOutfitPhoto(3L, 0)

        assertFalse(storage.outfitPhotoFile(3L, 0).exists())
        assertTrue(storage.outfitPhotoFile(3L, 1).exists())
    }

    // ── M4-06「清理原图只留抠图」的前置规则 ──────────────────────

    @Test
    fun `没有抠图时拒绝清原图（否则衣物变裸条目）`() = runTest {
        setUpWith(tempFolder.newFolder("images"))
        storage.clothingOriginalFile(9L).apply { parentFile!!.mkdirs(); writeText("orig") }

        storage.deleteClothingOriginalOnly(9L)

        // 无抠图 → 原图必须保留
        assertTrue(storage.clothingOriginalFile(9L).exists())
    }

    @Test
    fun `有抠图时才清原图`() = runTest {
        setUpWith(tempFolder.newFolder("images"))
        storage.clothingOriginalFile(9L).apply { parentFile!!.mkdirs(); writeText("orig") }
        storage.clothingCutoutFile(9L).apply { parentFile!!.mkdirs(); writeText("cut") }

        storage.deleteClothingOriginalOnly(9L)

        assertFalse(storage.clothingOriginalFile(9L).exists())
        assertTrue(storage.clothingCutoutFile(9L).exists())
    }

    // ── 幂等性：删不存在的东西不许抛异常 ────────────────────────

    @Test
    fun `删除不存在的数据是幂等操作`() = runTest {
        setUpWith(tempFolder.newFolder("images"))
        // 目录根本没建过，四次删除都应静默通过
        storage.deleteClothingImages(404L)
        storage.deleteOutfitImages(404L)
        storage.deleteWardrobeCover(404L)
        storage.deleteOutfitPhoto(404L, 0)
        storage.deleteClothingOriginalOnly(404L)
    }
}
