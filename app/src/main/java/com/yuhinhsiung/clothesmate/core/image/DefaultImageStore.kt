package com.yuhinhsiung.clothesmate.core.image

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.yuhinhsiung.clothesmate.core.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
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

    /** 抠图暂存序号（与选图暂存分开计数，文件后缀也不同） */
    private val cutoutSeq = AtomicLong(0)

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
        replaceAtomically(source = staged.file, target = destination)
        // 只返回相对路径给调用方写库（红线 1）
        storage.toRelative(destination)
            ?: throw ImageProcessException("归位后的文件不在 images 根目录内：${destination.path}")
    }

    /**
     * 归位补偿（review R-14）：把已归位的文件退回暂存位置。
     *
     * 用 [replaceAtomically] 的反向操作把状态还原成 [promote] 之前的样子，
     * 草稿里的 [StagedImage] 路径重新变得有效，用户可以直接重试保存。
     */
    override suspend fun restoreStaged(staged: StagedImage, destination: File) =
        withContext(ioDispatcher) {
            // 暂存源还在 = 这张压根没归位成功，无事可做
            if (staged.file.exists()) return@withContext
            if (!destination.exists()) return@withContext
            staged.file.parentFile?.let { parent ->
                if (!parent.exists()) parent.mkdirs()
            }
            replaceAtomically(source = destination, target = staged.file)
            Unit
        }

    /**
     * 把 [source] 落到 [target]，**保证失败时 [target] 的原有字节不变**。
     *
     * ① 同盘优先 `rename`——POSIX 的 `rename(2)` 是原子替换，旧目标要么是旧内容、要么是新内容；
     * ② 退化路径**绝不直接往 [target] 写**：先在**目标同目录**建临时文件，完整复制 +
     *    `flush` + `fsync` 之后再 rename 替换。临时文件与目标同目录，所以这一步同样是同盘 rename。
     *
     * 为什么不能直接 `copyTo(target, overwrite = true)`（review R-14 生产故障探针抓出的正是这条）：
     * Kotlin 的实现是 `if (target.exists()) target.delete()` **然后**才打开源读取——
     * 源一旦读失败（EACCES / IO 错误），旧目标已经被删掉，用户的原封面凭空消失，
     * 而数据库还指着那条路径。先写临时文件则最坏也只是本次临时产物作废。
     *
     * 失败时只清理本次的临时产物并抛出；[source] 保持原样，调用方可以原地重试。
     */
    private fun replaceAtomically(source: File, target: File) {
        if (source.renameTo(target)) return
        val parent = target.parentFile
            ?: throw ImageProcessException("目标目录无效：${target.path}")
        val temp = File(parent, target.name + TEMP_SUFFIX)
        // 上一次进程被杀可能留下同名残留（临时名由 target 唯一确定，不会误删别的文件）
        if (temp.exists()) temp.delete()
        try {
            source.inputStream().use { input ->
                temp.outputStream().use { output ->
                    input.copyTo(output)
                    output.flush()
                    // fsync：rename 之后内容必须已经落盘，否则掉电会得到「新的空文件」
                    output.fd.sync()
                }
            }
        } catch (failure: Throwable) {
            temp.delete()
            throw failure
        }
        if (!temp.renameTo(target)) {
            temp.delete()
            throw ImageProcessException("无法替换目标文件（旧文件保持原样）：${target.path}")
        }
        // 复制路径到这里才真正成功，此时才消费源——失败分支里源必须留着给调用方重试
        source.delete()
    }

    override suspend fun clearStaged(staged: StagedImage) = withContext(ioDispatcher) {
        staged.file.delete()
        Unit
    }

    /**
     * 抠图 PNG 暂存（M2-01）：`cacheDir/tmp_cutouts/cutout_{n}.png`。
     *
     * 与选图暂存分开目录：原图暂存被「用原图」分支清掉时，抠图还要留给确认页展示。
     * 写盘沿用「先 `.tmp` + fsync + rename」——预览页可能立刻读这张图，
     * 半截 PNG 会让 Coil 显示成占位色。
     */
    override suspend fun stageCutout(bitmap: Bitmap): StagedImage = withContext(ioDispatcher) {
        val dir = File(context.cacheDir, CUTOUT_DIR)
        if (!dir.exists() && !dir.mkdirs() && !dir.exists()) {
            throw ImageProcessException("无法创建抠图暂存目录：${dir.path}")
        }
        val target = File(dir, "cutout_${cutoutSeq.incrementAndGet()}.png")
        val tmp = File(dir, target.name + TEMP_SUFFIX)
        try {
            FileOutputStream(tmp).use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    throw ImageProcessException("PNG 编码失败")
                }
                output.flush()
                output.fd.sync()
            }
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        } finally {
            if (tmp.exists()) tmp.delete()
        }
        StagedImage(file = target, byteSize = target.length())
    }

    private companion object {
        const val STASH_DIR = "tmp_images"
        const val CUTOUT_DIR = "tmp_cutouts"

        /** 落盘临时后缀；与 `DefaultImageCompressor.writeJpeg` 的 `.tmp` 同款约定 */
        const val TEMP_SUFFIX = ".tmp"
    }
}
