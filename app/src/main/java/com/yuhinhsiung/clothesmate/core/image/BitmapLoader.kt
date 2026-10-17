package com.yuhinhsiung.clothesmate.core.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.media.ExifInterface
import com.yuhinhsiung.clothesmate.core.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 按需取图（M2 新增）—— 抠图模型与 AI 识别都需要「一串像素 / 一段 JPEG」，
 * 而不是 Compose 里的显示控件。
 *
 * 与 [ImageCompressor] 的分工：压缩器负责**落盘**（JPEG q85、长边 1600、铺白底），
 * 这里只负责**读进内存**给算法用，不写文件。两者共用 [ExifOrientation] 的方向表，
 * 保证「同一张竖拍照片，列表、抠图、AI 看到的朝向一致」。
 *
 * 内存口径：先读整份字节（相机 JPEG 典型 3–6MB），再按 [maxEdge] 两段式降采样解码；
 * 抠图取 1024 长边（模型输入 320，留足重采样余量），AI 取 1024 长边（base64 体积可控）。
 */
interface BitmapLoader {

    /** 解码并按 EXIF 归正；调用方负责 `recycle()`。 */
    suspend fun decode(uri: Uri, maxEdge: Int = ImageSpec.MAX_EDGE): Bitmap

    /** 同上，源为本地文件。 */
    suspend fun decode(file: File, maxEdge: Int = ImageSpec.MAX_EDGE): Bitmap

    /** 解码 → 等比缩到长边 ≤ [maxEdge] → 编码成 JPEG 字节（AI 请求用）。 */
    suspend fun jpegBytes(
        uri: Uri,
        maxEdge: Int,
        quality: Int = ImageSpec.JPEG_QUALITY,
    ): ByteArray

    /** 同上，源为本地文件。 */
    suspend fun jpegBytes(
        file: File,
        maxEdge: Int,
        quality: Int = ImageSpec.JPEG_QUALITY,
    ): ByteArray
}

@Singleton
class DefaultBitmapLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : BitmapLoader {

    override suspend fun decode(uri: Uri, maxEdge: Int): Bitmap = withContext(ioDispatcher) {
        decodeBytes(readBytes { context.contentResolver.openInputStream(uri) }, maxEdge)
    }

    override suspend fun decode(file: File, maxEdge: Int): Bitmap = withContext(ioDispatcher) {
        decodeBytes(readBytes { file.inputStream() }, maxEdge)
    }

    override suspend fun jpegBytes(uri: Uri, maxEdge: Int, quality: Int): ByteArray =
        withContext(ioDispatcher) {
            encode(decodeBytes(readBytes { context.contentResolver.openInputStream(uri) }, maxEdge), quality)
        }

    override suspend fun jpegBytes(file: File, maxEdge: Int, quality: Int): ByteArray =
        withContext(ioDispatcher) {
            encode(decodeBytes(readBytes { file.inputStream() }, maxEdge), quality)
        }

    private fun readBytes(open: () -> InputStream?): ByteArray = runCatching {
        open()?.use { it.readBytes() }
            ?: throw ImageProcessException("无法读取图片（可能已被移动或权限已失效）")
    }.getOrElse { failure ->
        if (failure is ImageProcessException) throw failure
        throw ImageProcessException("无法读取图片：${failure.message}", failure)
    }

    /**
     * 字节 → Bitmap：先读尺寸算 `inSampleSize`，再解码，最后按 EXIF 归正并精确缩放。
     *
     * `inSampleSize` 保证降后长边仍 ≥ [maxEdge]（宁大勿小），精确缩放放到 Matrix 一步完成，
     * 与 [DefaultImageCompressor] 的兩段式同一个思路（那边按 1600，这边按调用方上限）。
     */
    private fun decodeBytes(bytes: ByteArray, maxEdge: Int): Bitmap {
        val safeEdge = maxEdge.coerceAtLeast(1)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw ImageProcessException("无法解码图片")
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, safeEdge)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: throw ImageProcessException("无法解码图片")
        val orientation = runCatching {
            ExifInterface(bytes.inputStream()).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_UNDEFINED,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_UNDEFINED)

        val longEdge = maxOf(decoded.width, decoded.height)
        val scale = if (longEdge > safeEdge) safeEdge.toFloat() / longEdge else 1f
        val needsOrientation = orientation != ExifInterface.ORIENTATION_NORMAL &&
            orientation != ExifInterface.ORIENTATION_UNDEFINED
        if (scale == 1f && !needsOrientation) return decoded

        val matrix = ExifOrientation.matrix(orientation).apply {
            if (scale != 1f) postScale(scale, scale)
        }
        val transformed = Bitmap.createBitmap(
            decoded,
            0,
            0,
            decoded.width,
            decoded.height,
            matrix,
            true,
        )
        if (transformed !== decoded) decoded.recycle()
        return transformed
    }

    private fun calculateSampleSize(width: Int, height: Int, maxEdge: Int): Int {
        var sample = 1
        var longEdge = maxOf(width, height)
        while (longEdge / 2 >= maxEdge) {
            longEdge /= 2
            sample *= 2
        }
        return sample
    }

    private fun encode(bitmap: Bitmap, quality: Int): ByteArray {
        try {
            val output = ByteArrayOutputStream()
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) {
                throw ImageProcessException("JPEG 编码失败")
            }
            return output.toByteArray()
        } finally {
            bitmap.recycle()
        }
    }
}
