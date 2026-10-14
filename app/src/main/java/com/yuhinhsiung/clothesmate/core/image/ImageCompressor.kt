package com.yuhinhsiung.clothesmate.core.image

import android.net.Uri
import java.io.File
import java.io.IOException

/** 图片压缩规格（开发文档 §3.3 / §10）—— 集中一处，避免散落字面量。 */
object ImageSpec {
    /**
     * 长边上限 1600px（§3.3「长边 ≤ 1600px」）。
     *
     * 小于该值的图**不放大**（passthrough）：放大只会徒增体积，肉眼无收益。
     */
    const val MAX_EDGE = 1600

    /** JPEG 质量 85（§3.3「JPEG 质量 85」） */
    const val JPEG_QUALITY = 85

    /**
     * 单件衣物压缩后体积上限 500KB（§10「图片压缩后单件衣物占用 < 500KB」）。
     *
     * 这是**规格目标而非硬断言**：相机照片经 1600px + quality 85 后典型落在 200–400KB，
     * 但高噪声/细纹理画面可能超。故只用于测试与设置页预估展示，压缩过程本身不因超限而
     * 二次降质——那会让质量不可控且慢。真超了由 M4-06 清理策略兜。
     */
    const val MAX_BYTES = 500 * 1024
}

/** 图片处理失败 —— 由 UI 兜底提示，不 crash（§10「崩溃率 < 0.1%」）。 */
class ImageProcessException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * 待压缩的源图。
 *
 * 两种来源覆盖 M1 录入流程（M1-14）的全部入口：
 * - [Uri]：Photo Picker 选图、CameraX 拍照输出（经 FileProvider 拿到 content:// Uri）
 * - [Path]：已在盘上的图（重编码、抠图对比预览缩略）
 *
 * ⚠️ 第二个成员叫 [Path] 而不是 File：`Source.File` 会遮蔽 `java.io.File`，
 * 让本文件与所有调用方的 `File` 引用变得含糊。
 */
sealed interface Source {
    /** content:// / file:// Uri。Photo Picker 与相机的产出形态。 */
    data class Uri(val uri: android.net.Uri) : Source

    /** 盘上文件。 */
    data class Path(val file: File) : Source
}

/**
 * 图片压缩 —— 开发文档 §3.3「长边 ≤ 1600px，JPEG 质量 85」/ §10、开发计划 M1-03。
 *
 * 三类输入（拍照 / 相册 / 衣柜封面）共用同一策略，见 §3.3 括注「穿搭照片、衣柜封面同此策略」。
 */
interface ImageCompressor {

    /**
     * 读 [source] → 缩放（长边 ≤ [ImageSpec.MAX_EDGE]）→ EXIF 方向归正 →
     * JPEG quality [ImageSpec.JPEG_QUALITY] → 原子写入 [target]。
     *
     * @param target 目标文件。父目录不存在时创建；写盘采用「先 .tmp 再 rename」，
     *               失败时清理 .tmp，绝不留下半张图（否则 DB 会记一个读不出的路径）。
     * @return 最终文件字节数
     * @throws ImageProcessException 解码失败、源不可读、磁盘写入失败等。调用方应兜底
     *         （提示用户重试或「使用原图」），不得让异常冒到 UI 造成崩溃。
     */
    suspend fun compress(source: Source, target: File): Long
}
