package com.yuhinhsiung.clothesmate.core.image

import android.net.Uri
import com.yuhinhsiung.clothesmate.core.model.ClothingColor
import kotlinx.coroutines.withContext
import com.yuhinhsiung.clothesmate.core.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 衣物照片 → 预设颜色 key（M2-02）。
 *
 * 这是「Android Bitmap → 纯 Kotlin 算法」的薄壳：解码、取像素、调 [LocalColorExtractor]。
 * 规则本身在纯类里，JVM 可测；这里只负责把照片变成 `IntArray`。
 */
interface LocalColorExtractorService {

    /**
     * @return 可写库的颜色 key 列表（纯色 1 个，花色为 `["multi"]`）；
     *   照片无法识别（全透明/解码失败）时返回空列表，调用方保持字段留空。
     */
    suspend fun extractKeys(uri: Uri): List<String>
}

@Singleton
class DefaultLocalColorExtractorService @Inject constructor(
    private val bitmapLoader: BitmapLoader,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : LocalColorExtractorService {

    override suspend fun extractKeys(uri: Uri): List<String> = withContext(ioDispatcher) {
        val bitmap = runCatching { bitmapLoader.decode(uri, SAMPLE_MAX_EDGE) }
            .getOrNull() ?: return@withContext emptyList()
        try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            LocalColorExtractor.extract(pixels, bitmap.width, bitmap.height)
                ?.colors
                ?.map(ClothingColor::key)
                .orEmpty()
        } finally {
            bitmap.recycle()
        }
    }

    private companion object {
        /** 取色只需统计分布，512 长边足够且快 */
        const val SAMPLE_MAX_EDGE = 512
    }
}
