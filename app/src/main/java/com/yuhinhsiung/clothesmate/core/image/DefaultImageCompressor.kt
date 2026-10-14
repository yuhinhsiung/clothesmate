package com.yuhinhsiung.clothesmate.core.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ImageCompressor] 默认实现 —— 开发文档 §3.3 / §10、开发计划 M1-03。
 *
 * ## 为什么用框架 API 而不是图片库
 *
 * 需求只有「缩放 + 转 JPEG + 存盘」，装箱引入裁剪库属过度设计。解码走
 * [BitmapFactory]（框架类，minSdk 26 可用），方向读 [ExifInterface]（框架类，
 * API 24 起在 android.jar 内，**不引入** androidx.exifinterface——本项目不新增依赖）。
 *
 * ## 内存安全
 *
 * 相机原图可达 4000×3000 以上，直接解码在低端机上必 OOM。故走两级解码：
 * ① `inJustDecodeBounds` 只读尺寸不解像素；② 算好 `inSampleSize`（2 的幂）再真正解码。
 *
 * ## EXIF 方向
 *
 * 相机竖拍时文件里的像素是横的，靠 EXIF orientation 标记告知显示端旋转。不处理这个标记，
 * 列表里的衣物照片就会躺着——故解码后必须按标记用 [Matrix] 归正。
 *
 * 读 EXIF 用**框架** `android.media.ExifInterface`（API 24 起在 android.jar 内），lint 会提示
 * 「改用 androidx.exifinterface」（[ExifInterface] 警告，已论证后保留）：
 *
 * - androidx.exifinterface:1.4.2 确实已在 runtime classpath（coil 的传递依赖），
 *   但**不在 compile classpath**，要用它就得在 build.gradle.kts 显式新增一条依赖；
 * - CLAUDE.md 技术栈是定稿冻结的，明确「不得引入文档未列的库」——为消一条 lint 警告而
 *   破这条红线，代价不成比例；
 * - 本模块只读 `TAG_ORIENTATION` 一个属性，框架版在 minSdk 26 上已稳定支持，
 *   用不到 androidx 版相对框架版多修的 bug（多在 WebP/HEIF 元数据场景，本 App 不产这类文件）。
 *
 * 若将来 M2/M4 需要处理更完整的元数据（或引入裁剪库顺带带来该依赖），再切 androidx 版。
 * 这条取舍登记在开发计划 M1-03 完成记录。
 */
@Singleton
class DefaultImageCompressor @Inject constructor(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher,
) : ImageCompressor {

    override suspend fun compress(source: Source, target: File): Long =
        withContext(ioDispatcher) {
            val raw = openStream(source).use { it.readBytes() }
            // EXIF 要独立流读（不能与解码共用已消费的流），故从字节数组另开一份流
            val orientation = readOrientation(raw.inputStream())
            val bitmap = decodeSampled(raw) ?: throw ImageProcessException("无法解码图片")
            val scaled = try {
                scaleAndRotate(bitmap, orientation)
            } finally {
                bitmap.recycle()
            }
            try {
                writeJpeg(scaled, target)
            } finally {
                scaled.recycle()
            }
            target.length()
        }

    /** 统一打开源流：Uri 经 ContentResolver（授权即时有效，不缓存），File 直接开。 */
    private fun openStream(source: Source): InputStream = when (source) {
        is Source.Uri -> context.contentResolver.openInputStream(source.uri)
            ?: throw ImageProcessException("无法读取所选图片（可能已被移动或权限已失效）")
        is Source.Path -> source.file.inputStream()
    }

    /**
     * 读 EXIF 方向标签。
     *
     * 读不到 / 格式不支持一律返回 [ExifInterface.ORIENTATION_UNDEFINED]（等同「正常方向」），
     * 不因元数据缺失而失败——相册里大量图片本就没有 EXIF。
     */
    private fun readOrientation(stream: InputStream): Int = runCatching {
        // 框架 ExifInterface 在 API 24 起支持从 InputStream 读（内部走可回退的读取器）
        ExifInterface(stream).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_UNDEFINED,
        )
    }.getOrDefault(ExifInterface.ORIENTATION_UNDEFINED)

    /**
     * 两级解码：先只读尺寸，再按 `inSampleSize` 降采样解码。
     *
     * `inSampleSize` 取 2 的幂且保证降后长边仍 **≥ MAX_EDGE**（宁大勿小）：
     * 取大了会低于 1600 导致细节损失，取小了则内存峰值接近原图。粗降之后的精确缩放到
     * [ImageSpec.MAX_EDGE] 由 [scaleAndRotate] 用 Matrix 完成。
     */
    private fun decodeSampled(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight)
            // ARGB_8888 保证后续旋转与铺白底的质量；相机位图本就是该配置
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    /** inSampleSize = 使降采样后长边仍 ≥ MAX_EDGE 的最大 2 的幂 */
    private fun calculateSampleSize(width: Int, height: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        var longEdge = maxOf(width, height)
        while (longEdge / 2 >= ImageSpec.MAX_EDGE) {
            longEdge /= 2
            sample *= 2
        }
        return sample
    }

    /**
     * 精确缩放（长边 ≤ MAX_EDGE，不放大）+ EXIF 旋转归正，一次 Matrix 完成。
     *
     * 透明输入（PNG/WebP）在转 JPEG 前铺白底，否则透明像素会变黑块。
     */
    private fun scaleAndRotate(source: Bitmap, orientation: Int): Bitmap {
        val longEdge = maxOf(source.width, source.height)
        val scale = if (longEdge > ImageSpec.MAX_EDGE) {
            ImageSpec.MAX_EDGE.toFloat() / longEdge
        } else {
            1f
        }
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val mirrored = orientation == ExifInterface.ORIENTATION_FLIP_HORIZONTAL ||
            orientation == ExifInterface.ORIENTATION_TRANSVERSE

        val matrix = Matrix().apply {
            if (mirrored) postScale(-1f, 1f)
            if (degrees != 0f) postRotate(degrees)
            if (scale != 1f) postScale(scale, scale)
        }

        val needsTransform = scale != 1f || degrees != 0f || mirrored
        // 一切都对、且没有 alpha（JPEG 不接受透明）——可直接压缩原图
        if (!needsTransform && !source.hasAlpha()) return source

        val result = if (needsTransform) {
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        } else {
            source
        }
        // 转 JPEG 前必须去掉 alpha：JPEG 只有 RGB，透明区会被编成黑块
        if (result.hasAlpha()) return flattenToWhite(result)
        return result
    }

    /** 铺白底去掉 alpha —— JPEG 不支持透明，直接转会让透明区变黑。 */
    private fun flattenToWhite(source: Bitmap): Bitmap {
        val flattened = createBitmap(source.width, source.height)
        val canvas = Canvas(flattened)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(source, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        source.recycle()
        return flattened
    }

    /**
     * 原子写盘：写 `.tmp` → flush + fsync → rename 覆盖 [target]。
     *
     * 直接往最终文件写，中途失败（磁盘满 / 进程被杀）会留下半张图，而 DB 里那条路径
     * 看起来是合法的——用户看到的就是裂图。先写临时文件再 rename 保证 target 要么
     * 是完整的旧文件、要么是完整的新文件。
     */
    private fun writeJpeg(bitmap: Bitmap, target: File) {
        val parent = target.parentFile ?: throw ImageProcessException("目标目录无效")
        if (!parent.exists() && !parent.mkdirs() && !parent.exists()) {
            throw ImageProcessException("无法创建目录：${parent.path}")
        }
        val tmp = File(parent, target.name + ".tmp")
        try {
            FileOutputStream(tmp).use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, ImageSpec.JPEG_QUALITY, out)) {
                    throw ImageProcessException("JPEG 编码失败")
                }
                out.flush()
                out.fd.sync()
            }
            if (!tmp.renameTo(target)) {
                // rename 失败时退化为拷贝式覆盖，避免偶发（跨挂载点/目标被占用）直接失败
                tmp.copyTo(target, overwrite = true)
            }
        } finally {
            if (tmp.exists() && tmp != target) tmp.delete()
        }
    }
}
