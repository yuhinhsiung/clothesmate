package com.yuhinhsiung.clothesmate.core.image

import android.content.Context
import android.net.Uri
import com.yuhinhsiung.clothesmate.core.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ImageStore] 默认实现 —— 开发计划 M1-14 / M1-15。
 *
 * 暂存区在 `cacheDir/tmp_images/`，见 [ImageStore] 上「为什么不用 filesDir」的三条理由。
 */
@Singleton
class DefaultImageStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val compressor: ImageCompressor,
    private val storage: ImageStorage,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ImageStore {

    /** 暂存序号：单调递增，保证一次会话内多个暂存图不互相覆盖 */
    private val stashSeq = AtomicLong(0)

    private val stashDir: File
        get() = File(context.cacheDir, STASH_DIR)

    override suspend fun stash(uri: Uri): StagedImage = withContext(ioDispatcher) {
        if (!stashDir.exists()) stashDir.mkdirs()
        val target = File(stashDir, "staged_${stashSeq.incrementAndGet()}.jpg")
        val size = compressor.compress(Source.Uri(uri), target)
        StagedImage(file = target, byteSize = size)
    }

    override suspend fun promote(
        staged: StagedImage,
        destination: File,
    ): String = withContext(ioDispatcher) {
        if (!staged.file.exists()) {
            throw ImageProcessException("暂存图已不存在（可能已被清理）：${staged.file.name}")
        }
        destination.parentFile?.let { parent ->
            if (!parent.exists() && !parent.mkdirs() && !parent.exists()) {
                throw ImageProcessException("无法创建目录：${parent.path}")
            }
        }
        // 移动优先：同文件系统内 rename 近乎零成本；跨卷失败时退化为复制后删源
        val moved = staged.file.renameTo(destination)
        if (!moved) {
            staged.file.copyTo(destination, overwrite = true)
            staged.file.delete()
        }
        // 只返回相对路径给调用方写库（红线 1）
        storage.toRelative(destination)
            ?: throw ImageProcessException("归位后的文件不在 images 根目录内：${destination.path}")
    }

    override suspend fun clearStaged(staged: StagedImage) = withContext(ioDispatcher) {
        staged.file.delete()
        Unit
    }

    private companion object {
        const val STASH_DIR = "tmp_images"
    }
}
