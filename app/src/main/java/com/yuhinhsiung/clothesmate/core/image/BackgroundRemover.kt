package com.yuhinhsiung.clothesmate.core.image

import android.graphics.Bitmap
import android.net.Uri

/**
 * 端上抠图（C-01）—— 开发文档 §2.2.2、开发计划 M2-01。
 *
 * 默认实现是 TFLite U²-Net（u2netp 轻量版，模型打包在 `assets/`），完全离线；
 * 不依赖 GMS、网络或 API Key。失败/超时一律抛 [BackgroundRemovalException]，
 * 由调用方降级为「使用原图」，**绝不阻断录入**。
 */
interface BackgroundRemover {

    /**
     * 对 [uri] 指向的照片抠图。
     *
     * @param timeoutMillis 超时（开发文档 §10「耗时操作可取消、有超时」）。超时抛
     *   [BackgroundRemovalException]；协程取消抛 `CancellationException`（按协程语义向上传播）。
     * @return 透明背景的 ARGB_8888 Bitmap（调用方负责 recycle）
     */
    suspend fun removeBackground(uri: Uri, timeoutMillis: Long = CutoutSpec.TIMEOUT_MILLIS): Bitmap
}

/** 抠图规格 —— 模型与预处理口径集中一处，避免 UI/测试各写一份数字。 */
object CutoutSpec {

    /** 打包在 `app/src/main/assets/` 的模型文件名 */
    const val MODEL_ASSET = "u2netp.tflite"

    /** u2netp 固定输入边长（ONNX 模型输入为 1×3×320×320） */
    const val INPUT_SIZE = 320

    /** 解码上限：模型只要 320，取 1024 保留重采样余量、又不至于内存过大 */
    const val DECODE_MAX_EDGE = 1024

    /**
     * 单次抠图超时。首次推理要加载 4.4MB 模型并初始化 GPU delegate；模拟器冷启动实测约 34s。
     * 15s 会让每次冷启动的第一张必然兜底为原图，故放宽到 60s。超时仍可取消。
     */
    const val TIMEOUT_MILLIS = 60_000L

    /** U²-Net 官方预处理：ImageNet mean / std */
    val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
    val STD = floatArrayOf(0.229f, 0.224f, 0.225f)
}

/** 抠图失败（模型缺失、推理异常、输出为空等）。调用方据此显示「使用原图」兜底。 */
class BackgroundRemovalException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** 抠图结果的可观测信息（测试与日志用；不含 Bitmap 生命周期） */
data class CutoutOutputInfo(
    val width: Int,
    val height: Int,
    val opaqueRatio: Double,
)
