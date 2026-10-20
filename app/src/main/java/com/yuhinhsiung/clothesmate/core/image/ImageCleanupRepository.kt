package com.yuhinhsiung.clothesmate.core.image

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
            val file = imageStorage.toFile(item.originalImagePath) ?: return@forEach
            if (file.isFile) {
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
            val file = imageStorage.toFile(item.originalImagePath) ?: return@forEach
            val size = if (file.isFile) file.length() else 0L
            // 先断开 DB 引用，再删文件：即使删文件失败也只会留下可清理的孤儿文件，
            // 不会让详情页引用一个已不存在的原图路径。
            dao.clearOriginalImagePath(item.id, now)
            if (file.isFile && file.delete()) {
                count++
                bytes += size
            }
        }
        CleanupResult(count = count, bytes = bytes)
    }
}
