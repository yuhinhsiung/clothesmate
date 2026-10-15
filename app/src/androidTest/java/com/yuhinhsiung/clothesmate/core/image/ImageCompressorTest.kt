package com.yuhinhsiung.clothesmate.core.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

/**
 * 图片压缩测试 —— 开发计划 M1-03「单测 + 手动验证压缩产物 ≤500KB」的验收落点。
 *
 * 需要真实 Bitmap 编解码与 ContentResolver，故在 `src/androidTest`（Room 内存库同款定位：
 * 真 Android framework 的断言不放纯 JVM 测试）。真机目视随 M1-14 录入流程交付时补。
 */
@RunWith(AndroidJUnit4::class)
class ImageCompressorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var root: File
    private lateinit var compressor: ImageCompressor
    private lateinit var storage: ImageStorage

    private fun setUp() {
        root = tempFolder.newFolder("images").also { it.mkdirs() }
        val context = androidx.test.core.app.ApplicationProvider
            .getApplicationContext<android.content.Context>()
        compressor = DefaultImageCompressor(context, UnconfinedTestDispatcher())
        storage = DefaultImageStorage(root, UnconfinedTestDispatcher())
    }

    /**
     * 造一张指定尺寸的源 JPEG。
     *
     * ⚠️ 尺寸上限受设备内存约束：ARGB_8888 是每像素 4 字节，12000×9000 需约 432MB，
     * 在任何真机/模拟器上都会 OOM。测试用 **4000×3000（约 48MB）**——已足够覆盖
     * `inSampleSize ≥ 2` 的降采样分支（长边 4000 > 1600×2），又不会把测试自己撑死。
     * 真实相机原图的更大尺寸由同一代码路径处理，单测无法也不该复刻其内存压力。
     */
    private fun noisyBitmap(width: Int, height: Int): Bitmap {
        val bmp = androidx.core.graphics.createBitmap(width, height)
        val pixels = IntArray(width * height)
        // 逐像素伪随机：细纹理、高熵，比纯色图更接近相机照片的压缩行为
        var seed = 12345L
        for (i in pixels.indices) {
            seed = seed * 1103515245L + 12345L
            val v = ((seed ushr 16) and 0xFF).toInt()
            pixels[i] = (0xFF shl 24) or (v shl 16) or ((255 - v) shl 8) or ((v * 3) % 256)
        }
        bmp.setPixels(pixels, 0, width, 0, 0, width, height)
        return bmp
    }

    /** 写一张不带 EXIF 的 JPEG */
    private fun writePlainJpeg(bmp: Bitmap, name: String): File =
        tempFolder.newFile(name).apply {
            outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        }

    /** 把源 JPEG 的 SOI 之后插入最小 EXIF APP1 段，写入 orientation 标记 */
    private fun writeJpegWithExifOrientation(source: File, orientation: Int, name: String): File {
        val exifSegment = buildMinimalExif(orientation)
        val bytes = source.readBytes()
        // JPEG 结构：FFD8 (SOI) FFE1 (APP1) <len(2)> "Exif\0\0" <TIFF> ...
        val out = tempFolder.newFile(name)
        out.outputStream().use { os ->
            os.write(bytes, 0, 2) // FFD8
            os.write(byteArrayOf(0xFF.toByte(), 0xE1.toByte()))
            val len = exifSegment.size + 2
            os.write(byteArrayOf((len shr 8).toByte(), (len and 0xFF).toByte()))
            os.write(exifSegment)
            os.write(bytes, 2, bytes.size - 2)
        }
        return out
    }

    /**
     * 构造最小 EXIF APP1 内容（不含长度字段本身）：`Exif\0\0` + TIFF 头 + 一个 IFD0 条目。
     *
     * 手写而不是用框架写入，是因为框架 [android.media.ExifInterface] 只能读不能写属性。
     * payload：
     * - `Exif\0\0` 6 字节标识
     * - TIFF 头：`II`（小端）+ 42 + IFD0 偏移 8
     * - IFD0：entry 数 1 + (tag 0x0112 Orientation, type 3 SHORT, count 1, value 1) + next IFD 0
     */
    private fun buildMinimalExif(orientation: Int): ByteArray {
        val exif = byteArrayOf(
            0x45, 0x78, 0x69, 0x66, 0x00, 0x00, // "Exif\0\0"
        )
        val tiff = ByteArray(8 + 2 + 12 + 4)
        var p = 0
        // TIFF header
        tiff[p++] = 0x49; tiff[p++] = 0x49                    // II little endian
        tiff[p++] = 0x2A; tiff[p++] = 0x00                    // 42
        tiff[p++] = 8;   tiff[p++] = 0; tiff[p++] = 0; tiff[p++] = 0 // IFD0 at offset 8
        // IFD0
        tiff[p++] = 1;   tiff[p++] = 0                        // 1 entry
        tiff[p++] = 0x12; tiff[p++] = 0x01                    // tag 0x0112
        tiff[p++] = 3;   tiff[p++] = 0                        // type SHORT
        tiff[p++] = 1;   tiff[p++] = 0; tiff[p++] = 0; tiff[p++] = 0 // count 1
        tiff[p++] = (orientation and 0xFF).toByte()           // value 低字节
        tiff[p++] = 0; tiff[p++] = 0; tiff[p++] = 0           // value 高 3 字节
        tiff[p++] = 0; tiff[p++] = 0; tiff[p++] = 0; tiff[p++] = 0 // next IFD = 0
        return exif + tiff
    }

    // ── 验收 1：长边 ≤1600px、比例保持 ──────────────────────────

    @Test
    fun `4000x3000大图压缩后长边恰为1600且比例保持`() = runTest {
        setUp()
        val source = writePlainJpeg(noisyBitmap(4000, 3000), "big.jpg")
        val target = storage.clothingOriginalFile(1L).apply { parentFile!!.mkdirs() }

        val size = compressor.compress(Source.Path(source), target)

        val out = BitmapFactory.decodeFile(target.path)
        assertEquals(1600, maxOf(out.width, out.height))
        assertEquals(1200, minOf(out.width, out.height))
        assertTrue(size > 0)
        assertTrue(target.length() > 0)
    }

    @Test
    fun `小图不放大（长边小于1600时保持原尺寸）`() = runTest {
        setUp()
        val source = writePlainJpeg(noisyBitmap(800, 600), "small.jpg")
        val target = storage.clothingOriginalFile(2L).apply { parentFile!!.mkdirs() }

        compressor.compress(Source.Path(source), target)

        val out = BitmapFactory.decodeFile(target.path)
        assertEquals(800, out.width)
        assertEquals(600, out.height)
    }

    // ── 验收 2：压缩产物 ≤500KB（开发文档 §10） ──────────────────

    @Test
    fun `高熵大图压缩后仍小于500KB`() = runTest {
        setUp()
        val source = writePlainJpeg(noisyBitmap(4000, 3000), "noisy.jpg")
        val target = storage.clothingOriginalFile(3L).apply { parentFile!!.mkdirs() }

        compressor.compress(Source.Path(source), target)

        assertTrue(
            "产物 ${target.length()}B 应 < 500KB",
            target.length() < ImageSpec.MAX_BYTES,
        )
    }
    // ── 验收 3：EXIF orientation 归正 ───────────────────────────

    @Test
    fun `方向标记六旋转九十度后横竖互换`() = runTest {
        setUp()
        // 像素是横的 1200x800，但 EXIF 说「顺时针转 90」——输出应为竖的 800x1200
        val plain = writePlainJpeg(noisyBitmap(1200, 800), "landscape.jpg")
        val source = writeJpegWithExifOrientation(plain, 6, "rot6.jpg")
        val target = storage.clothingOriginalFile(4L).apply { parentFile!!.mkdirs() }

        compressor.compress(Source.Path(source), target)

        val out = BitmapFactory.decodeFile(target.path)
        assertEquals(800, out.width)
        assertEquals(1200, out.height)
    }

    @Test
    fun `方向标记八旋转二百七十度后横竖互换`() = runTest {
        setUp()
        val plain = writePlainJpeg(noisyBitmap(1200, 800), "landscape2.jpg")
        val source = writeJpegWithExifOrientation(plain, 8, "rot8.jpg")
        val target = storage.clothingOriginalFile(5L).apply { parentFile!!.mkdirs() }

        compressor.compress(Source.Path(source), target)

        val out = BitmapFactory.decodeFile(target.path)
        assertEquals(800, out.width)
        assertEquals(1200, out.height)
    }

    @Test
    fun `无方向标记的图片不改变朝向`() = runTest {
        setUp()
        val source = writePlainJpeg(noisyBitmap(1000, 500), "wide.jpg")
        val target = storage.clothingOriginalFile(6L).apply { parentFile!!.mkdirs() }

        compressor.compress(Source.Path(source), target)

        val out = BitmapFactory.decodeFile(target.path)
        assertEquals(1000, out.width)
        assertEquals(500, out.height)
    }

    // ── 健壮性 ────────────────────────────────────────────────

    @Test
    fun `非图片输入抛ImageProcessException且不留下半张图`() = runTest {
        setUp()
        val source = tempFolder.newFile("notimage.jpg").apply { writeText("这不是图片") }
        val target = storage.clothingOriginalFile(7L).apply { parentFile!!.mkdirs() }

        val error = runCatching {
            compressor.compress(Source.Path(source), target)
        }.exceptionOrNull()

        assertTrue("应抛 ImageProcessException，实得 $error", error is ImageProcessException)
        assertFalse("目标文件不应出现", target.exists())
        assertFalse(".tmp 残留", File(target.parentFile, target.name + ".tmp").exists())
    }

    @Test
    fun `不存在的源文件抛ImageProcessException而非NPE`() = runTest {
        setUp()
        val missing = File(tempFolder.root, "absent.jpg")
        val target = storage.clothingOriginalFile(8L).apply { parentFile!!.mkdirs() }

        val error = runCatching {
            compressor.compress(Source.Path(missing), target)
        }.exceptionOrNull()

        assertTrue(error is ImageProcessException)
    }

    @Test
    fun `重复压缩同一目标可覆盖写入`() = runTest {
        setUp()
        val source = writePlainJpeg(noisyBitmap(3000, 2000), "rep.jpg")
        val target = storage.clothingOriginalFile(9L).apply { parentFile!!.mkdirs() }

        compressor.compress(Source.Path(source), target)
        val first = target.length()
        compressor.compress(Source.Path(source), target)
        val second = target.length()

        assertEquals(first, second)
        assertEquals(1600, BitmapFactory.decodeFile(target.path).width)
    }
}
