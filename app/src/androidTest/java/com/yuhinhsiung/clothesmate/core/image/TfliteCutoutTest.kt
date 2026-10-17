package com.yuhinhsiung.clothesmate.core.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * M2-01 / M2-02 的 Android 侧真机验证：
 * - 模型确实打进 APK（assets/u2netp.tflite），端上能跑出透明背景结果；
 * - 输出尺寸与原图一致、alpha 有透明也有不透明；
 * - 本地取色服务能把纯红衣物的颜色识别成 `red`。
 *
 * 说明：合成图只用于验证「整条链路能跑通 + 形状/alpha 合法」，不作为抠图质量的评价。
 */
@RunWith(AndroidJUnit4::class)
class TfliteCutoutTest {

    private lateinit var context: Context
    private lateinit var remover: TfliteU2NetBackgroundRemover
    private lateinit var colors: DefaultLocalColorExtractorService

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val loader = DefaultBitmapLoader(context, Dispatchers.IO)
        remover = TfliteU2NetBackgroundRemover(context, loader, Dispatchers.IO)
        colors = DefaultLocalColorExtractorService(loader, Dispatchers.IO)
    }

    @Test
    fun cutoutProducesTransparentBackgroundWithSameSize() = runBlocking {
        val source = File(context.cacheDir, "cutout_source.png")
        writeRedSquareOnWhite(source, width = 480, height = 600)

        val result = remover.removeBackground(Uri.fromFile(source))

        assertEquals(480, result.width)
        assertEquals(600, result.height)
        val pixels = IntArray(result.width * result.height)
        result.getPixels(pixels, 0, result.width, 0, 0, result.width, result.height)
        val transparent = pixels.count { (it ushr 24) < 64 }
        val opaque = pixels.size - transparent
        assertTrue("应存在透明背景像素，实际透明 $transparent", transparent > 0)
        assertTrue("主体不应被整张抠掉，实际不透明 $opaque", opaque > 0)
        result.recycle()
    }

    @Test
    fun localColorExtractionSeesRed() = runBlocking {
        val source = File(context.cacheDir, "color_source.png")
        // 取色应对「抠图后的透明背景」做统计——白底原图会把背景也算成主色，
        // 真实流程里确认页拿到的是抠图 PNG（M2-02 口径：抠图结果优先）
        writeRedSquareOnWhite(source, width = 320, height = 320, transparentBackground = true)
        val keys = colors.extractKeys(Uri.fromFile(source))
        assertTrue("应识别出红色，实际 $keys", keys.contains("red"))
    }

    private fun writeRedSquareOnWhite(
        file: File,
        width: Int,
        height: Int,
        transparentBackground: Boolean = false,
    ) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (!transparentBackground) canvas.drawColor(Color.WHITE)
        val paint = Paint().apply { color = Color.rgb(200, 30, 40) }
        canvas.drawRect(
            width * 0.15f,
            height * 0.15f,
            width * 0.85f,
            height * 0.85f,
            paint,
        )
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
