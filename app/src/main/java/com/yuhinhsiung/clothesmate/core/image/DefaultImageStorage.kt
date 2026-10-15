package com.yuhinhsiung.clothesmate.core.image

import com.yuhinhsiung.clothesmate.core.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ImageStorage] 默认实现 —— 开发文档 §3.3 / 开发计划 M1-03。
 *
 * @param imagesRoot images 根目录（生产中为 `context.filesDir/images/`，
 *                   测试中注入临时目录，故本类无 Android 依赖、可在 `src/test` 直跑）。
 * @param ioDispatcher 文件删除走 IO 线程
 */
@Singleton
class DefaultImageStorage @Inject constructor(
    private val imagesRoot: File,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ImageStorage {

    override fun clothingOriginalFile(id: Long): File =
        File(clothingOriginalDir(), "$id.jpg")

    override fun clothingCutoutFile(id: Long): File =
        File(clothingCutoutDir(), "$id.png")

    override fun outfitPhotoFile(outfitId: Long, index: Int): File =
        File(File(imagesRoot, "$OUTFIT_DIR/$outfitId"), "$index.jpg")

    override fun wardrobeCoverFile(wardrobeId: Long): File =
        File(wardrobeCoverDir(), "$wardrobeId.jpg")

    override fun toRelative(file: File): String? {
        val root = imagesRoot.canonicalFile
        val target = file.canonicalFile
        if (target == root) return ""
        val relative = runCatching { target.relativeTo(root) }.getOrNull() ?: return null
        // relativeTo 对「不在其下」的路径会产生 ../ 段，必须挡掉
        if (relative.path.startsWith("..")) return null
        return relative.path.replace(File.separatorChar, '/')
    }

    /**
     * 相对路径 → File。
     *
     * 这是本模块唯一的路径注入面，三道校验：
     * ① 拒绝绝对路径（DB 里本就不该有绝对路径，出现即数据已损坏或外部输入恶意）；
     * ② `canonicalFile` 归一化，把 `..` 段与软链接展开后再判归属；
     * ③ 结果必须仍在 images 根之内（否则 `..` 可逃逸成任意文件读取）。
     *
     * 用 canonical 而非字符串拼接判前缀：`root=/a/b` 时 `/a/bc/evil.jpg` 会被
     * `startsWith("/a/b")` 误判为合法，必须比对「root + 分隔符」。
     */
    override fun toFile(relativePath: String?): File? {
        val raw = relativePath?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val looksAbsolute = raw.startsWith("/") ||
            raw.startsWith("\\") ||
            raw.startsWith("file:", ignoreCase = true) ||
            (raw.length >= 2 && raw[1] == ':')
        if (looksAbsolute) return null
        val root = imagesRoot.canonicalFile
        val candidate = runCatching { File(imagesRoot, raw).canonicalFile }.getOrNull() ?: return null
        if (candidate != root && !candidate.path.startsWith(root.path + File.separator)) return null
        return candidate
    }

    /**
     * 删单件衣物的原图 + 抠图。
     *
     * 定向删两个文件而**不是**删 `clothing/` 整目录——§3.3 的衣物目录是按「original /
     * cutout」类型分的（`clothing/original/{id}.jpg`），整目录删会连带清掉其他衣物的图。
     */
    override suspend fun deleteClothingImages(id: Long): Unit = withContext(ioDispatcher) {
        clothingOriginalFile(id).delete()
        clothingCutoutFile(id).delete()
    }

    /**
     * 删某穿搭的照片目录（`outfit/{id}/` 整棵）—— M1-20 删除穿搭时调用。
     *
     * 这里可以整目录删：§3.3 的穿搭照片恰恰是**按 id 分目录**的（`outfit/{id}/{index}.jpg`），
     * 与衣物的「按类型分目录」相反。删除中间某一张照片（M1-18）不使用本方法。
     */
    override suspend fun deleteOutfitImages(outfitId: Long): Unit = withContext(ioDispatcher) {
        File(imagesRoot, "outfit/$outfitId").deleteRecursively()
    }

    override suspend fun deleteWardrobeCover(wardrobeId: Long): Unit = withContext(ioDispatcher) {
        wardrobeCoverFile(wardrobeId).delete()
    }

    override suspend fun deleteOutfitPhoto(outfitId: Long, index: Int): Unit =
        withContext(ioDispatcher) {
            outfitPhotoFile(outfitId, index).delete()
        }

    /**
     * 只删原图（保留抠图）—— M4-06。
     *
     * 抠图不存在时**不删**：没有抠图的衣物被清掉原图就成了裸体条目（列表、详情、搜索全裂），
     * 这是「只能清有抠图的」那条规则，收在这里而不是让每个调用点重复判断。
     */
    override suspend fun deleteClothingOriginalOnly(id: Long): Unit = withContext(ioDispatcher) {
        val cutout = clothingCutoutFile(id)
        if (cutout.exists() && cutout.length() > 0) {
            clothingOriginalFile(id).delete()
        }
    }

    private fun clothingOriginalDir(): File = File(imagesRoot, CLOTHING_ORIGINAL_DIR)

    private fun clothingCutoutDir(): File = File(imagesRoot, CLOTHING_CUTOUT_DIR)

    private fun wardrobeCoverDir(): File = File(imagesRoot, WARDROBE_COVER_DIR)

    /**
     * 目录名一律写 `/`：Android 的 [java.io.File] 接受 `/` 作分隔符（底层就是 Linux VFS），
     * 而 [File.separator] 不是编译期常量、无法用于 `const`。反过来说，这里用 `\` 反而
     * 会在 Android 上出错——只在 Windows 上「碰巧对」没有意义。
     */
    private companion object {
        const val CLOTHING_ORIGINAL_DIR = "clothing/original"
        const val CLOTHING_CUTOUT_DIR = "clothing/cutout"
        const val WARDROBE_COVER_DIR = "wardrobe_cover"
        const val OUTFIT_DIR = "outfit"
    }
}
