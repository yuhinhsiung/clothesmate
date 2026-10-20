package com.yuhinhsiung.clothesmate.core.image

import android.graphics.BitmapFactory
import com.yuhinhsiung.clothesmate.core.database.dao.ClothingItemDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CleanupEstimate(val count: Int, val bytes: Long)

data class CleanupResult(val count: Int, val bytes: Long)

/** 清理原图只留抠图（M4-06）。无抠图的衣物永远不在候选内。 */
interface ImageCleanupRepository {
    suspend fun estimate(): CleanupEstimate
    suspend fun cleanup(): CleanupResult
}

@Singleton
class DefaultImageCleanupRepository @Inject constructor(
    private val dao: ClothingItemDao,
    private val imageStorage: ImageStorage,
) : ImageCleanupRepository {

    override suspend fun estimate(): CleanupEstimate = withContext(Dispatchers.IO) {
        val candidates = dao.cleanupCandidates()
        var bytes = 0L
        var count = 0
        candidates.forEach { item ->
            // 预估和执行必须使用同一套“可用抠图”判定；DB 里有路径不等于文件仍可用。
            if (!hasUsableCutout(item.id)) return@forEach
            val file = imageStorage.toFile(item.originalImagePath) ?: return@forEach
            if (file.isFile && file.length() > 0L) {
                count++
                bytes += file.length()
            }
        }
        CleanupEstimate(count = count, bytes = bytes)
    }

    override suspend fun cleanup(): CleanupResult = withContext(Dispatchers.IO) {
        val candidates = dao.cleanupCandidates()
        var bytes = 0L
        var count = 0
        val now = System.currentTimeMillis()
        candidates.forEach { item ->
            // 没有可解码的抠图就绝不动原图，避免删掉最后一张可用照片。
            if (!hasUsableCutout(item.id)) return@forEach
            val file = imageStorage.toFile(item.originalImagePath) ?: return@forEach
            if (!file.isFile || file.length() <= 0L) return@forEach
            val size = file.length()
            // 先断开 DB 引用，再删文件：即使删文件失败也只会留下可清理的孤儿文件，
            // 不会让详情页引用一个已不存在的原图路径。
            dao.clearOriginalImagePath(item.id, now)
            if (file.delete()) {
                count++
                bytes += size
            }
        }
        CleanupResult(count = count, bytes = bytes)
    }

    /** 抠图实际存在、非空且能读出边界；仅看 DB 路径会误删带缺失/损坏抠图的原图。 */
    private fun hasUsableCutout(id: Long): Boolean {
        val cutout = imageStorage.clothingCutoutFile(id)
        if (!cutout.isFile || cutout.length() <= 0L) return false
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(cutout.absolutePath, options)
        return options.outWidth > 0 && options.outHeight > 0
    }
}
