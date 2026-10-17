package com.yuhinhsiung.clothesmate.core.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

/**
 * EXIF 方向归正测试 —— 全部 **8 种取值**（review R-15）。
 *
 * ## 为什么必须按像素验
 *
 * 旧实现只映射了 3/6/8 的旋转与 2/7 的「水平镜像」，**4、5 完全没有变换、7 只做了一半**
 * （少了轴交换）。这类错误看不出编译问题、也看不出「代码像是对的」——
 * 只能拿四角不同色的非对称样图跑真实压缩器，再比输出象限。
 *
 * ## 样图与期望
 *
 * 输入 120×80，四角各一色：左上红(0) / 右上绿(1) / 左下蓝(2) / 右下黄(3)。
 * 期望表由各方向的几何语义直接推出（见 `DefaultImageCompressor.orientationMatrix` 的表格）：
 * 2/3/4 不换宽高，5/6/7/8 宽高互换。
 *
 * 样图在测试内生成，**不依赖外部素材**。
 */
@RunWith(AndroidJUnit4::class)
class ExifOrientationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    /** 象限颜色编号：0 红 / 1 绿 / 2 蓝 / 3 黄 */
    private val quadrantColors = listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW)

    private fun sourceBitmap(width: Int = 120, height: Int = 80): Bitmap {
        val bitmap = androidx.core.graphics.createBitmap(width, height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val quadrant = (if (y >= height / 2) 2 else 0) + (if (x >= width / 2) 1 else 0)
                bitmap.setPixel(x, y, quadrantColors[quadrant])
            }
        }
        return bitmap
    }

    private fun compressedOutput(orientation: Int): Bitmap {
        val dir = tempFolder.newFolder("exif-$orientation")
        val input = File(dir, "input.jpg")
        val source = sourceBitmap()
        input.outputStream().use { source.compress(Bitmap.CompressFormat.JPEG, 100, it) }
        source.recycle()
        ExifInterface(input.absolutePath).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
            saveAttributes()
        }
        val target = File(dir, "output.jpg")
        val compressor = DefaultImageCompressor(
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>(),
            UnconfinedTestDispatcher(),
        )
        kotlinx.coroutines.runBlocking { compressor.compress(Source.Path(input), target) }
        return BitmapFactory.decodeFile(target.path)
    }

    /** 输出四角各落在输入的哪个象限（近邻色匹配，JPEG 有损） */
    private fun observedQuadrants(output: Bitmap): List<Int> = listOf(
        0.25f to 0.25f,
        0.75f to 0.25f,
        0.25f to 0.75f,
        0.75f to 0.75f,
    ).map { (fx, fy) ->
        val pixel = output.getPixel((output.width * fx).toInt(), (output.height * fy).toInt())
        quadrantColors.indices.minBy { i ->
            val dr = Color.red(pixel) - Color.red(quadrantColors[i])
            val dg = Color.green(pixel) - Color.green(quadrantColors[i])
            val db = Color.blue(pixel) - Color.blue(quadrantColors[i])
            dr * dr + dg * dg + db * db
        }
    }

    private fun assertOrientation(
        orientation: Int,
        expectedQuadrants: List<Int>,
        expectedWidth: Int,
        expectedHeight: Int,
    ) {
        val output = compressedOutput(orientation)
        try {
            assertEquals(
                "orientation=$orientation 输出宽高",
                expectedWidth to expectedHeight,
                output.width to output.height,
            )
            assertEquals(
                "orientation=$orientation 四角象限（TL,TR,BL,BR）",
                expectedQuadrants,
                observedQuadrants(output),
            )
        } finally {
            output.recycle()
        }
    }

    @Test
    fun orientation1NormalIsIdentity() = assertOrientation(1, listOf(0, 1, 2, 3), 120, 80)

    @Test
    fun orientation2FlipsHorizontally() = assertOrientation(2, listOf(1, 0, 3, 2), 120, 80)

    @Test
    fun orientation3Rotates180() = assertOrientation(3, listOf(3, 2, 1, 0), 120, 80)

    @Test
    fun orientation4FlipsVertically() = assertOrientation(4, listOf(2, 3, 0, 1), 120, 80)

    @Test
    fun orientation5TransposesAxes() = assertOrientation(5, listOf(0, 2, 1, 3), 80, 120)

    @Test
    fun orientation6Rotates90Clockwise() = assertOrientation(6, listOf(2, 0, 3, 1), 80, 120)

    @Test
    fun orientation7TransversesAxes() = assertOrientation(7, listOf(3, 1, 2, 0), 80, 120)

    @Test
    fun orientation8Rotates270() = assertOrientation(8, listOf(1, 3, 0, 2), 80, 120)

    /** 未知/未定义取值按恒等处理，不因元数据缺失而变换或失败 */
    @Test
    fun undefinedOrientationIsIdentity() = assertOrientation(0, listOf(0, 1, 2, 3), 120, 80)
}