package com.yuhinhsiung.clothesmate.core.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.yuhinhsiung.clothesmate.core.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * TFLite U²-Net 抠图实现 —— M2-01 / 开发文档 §2.2.2 C-01。
 *
 * ## 模型来源与口径
 *
 * - 模型：u2netp（U²-Net 轻量版）TFLite float32（4.36MB），打包进 `assets/u2netp.tflite`；
 *   来源、SHA-256 与许可见 `licenses/u2netp-Apache-2.0.txt`，选型过程见
 *   `docs/m2-model-provenance.md`。
 * - 输入：`[1, 320, 320, 3]` float32，`(pixel/255 - ImageNet mean) / std`；
 * - 输出：7 个单通道显著图 `[1, 320, 320, 1]`（已含 sigmoid）；d0（融合结果）选作蒙版，
 *   其余 d1..d6 为侧输出。软 alpha 直接使用，不做二值化。
 *
 * ## 失败兜底（C-01 / M2-08）
 *
 * 模型缺失、GPU delegate 不可用、推理异常、输出全空、超时——一律抛
 * [BackgroundRemovalException]，由预览页降级为「使用原图」。**不阻断录入**。
 *
 * ## GPU delegate
 *
 * 2026-10-14 用户拍板需要 GPU delegate（低端机 CPU 推理可达数秒）。这里先建 GPU 解释器，
 * 初始化或一次空跑失败就**关闭 delegate 回退 CPU**（模拟器/部分国产 ROM 的 GLES 不支持时
 * 会走到这条路径）。两条路径都在同一把 [inferenceMutex] 下串行，避免 Interpreter 并发。
 */
@Singleton
class TfliteU2NetBackgroundRemover @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bitmapLoader: BitmapLoader,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : BackgroundRemover {

    private val inferenceMutex = Mutex()
    private val initMutex = Mutex()

    /** 懒初始化；失败不缓存异常，下一次调用允许重试（例如内存瞬时不足） */
    @Volatile
    private var interpreter: Interpreter? = null

    @Volatile
    private var gpuDelegate: GpuDelegate? = null

    /** 主输出（d0）的输出槽位；首次解析后缓存 */
    @Volatile
    private var mainOutputSlot: Int? = null

    override suspend fun removeBackground(uri: Uri, timeoutMillis: Long): Bitmap =
        withTimeout(timeoutMillis) {
            withContext(ioDispatcher) {
                val source = bitmapLoader.decode(uri, CutoutSpec.DECODE_MAX_EDGE)
                try {
                    val engine = ensureInterpreter()
                    coroutineContext.ensureActive()
                    val input = buildInput(source, engine)
                    val mask = runInference(engine, input)
                    coroutineContext.ensureActive()
                    applyMask(source, mask)
                } finally {
                    source.recycle()
                }
            }
        }

    /** 懒加载模型 + 尝试 GPU；GPU 建不起来就回退 CPU（不抛错）。 */
    private suspend fun ensureInterpreter(): Interpreter {
        interpreter?.let { return it }
        return initMutex.withLock {
            interpreter?.let { return@withLock it }
            val buffer = readModelAsset()
            var candidate: Interpreter? = null
            val gpu = runCatching {
                val options = Interpreter.Options()
                    .setNumThreads(CPU_THREADS)
                    .addDelegate(
                        GpuDelegate().also { gpuDelegate = it },
                    )
                candidate = Interpreter(buffer, options)
                // 空跑一次验证 delegate 真能执行（建得起来 ≠ 跑得动）
                runInference(candidate!!, zeroInput(candidate!!))
                candidate
            }.getOrElse {
                // 清理半成品：先关解释器再关 delegate，与创建顺序相反
                runCatching { candidate?.close() }
                candidate = null
                runCatching { gpuDelegate?.close() }
                gpuDelegate = null
                null
            }
            if (gpu != null) {
                interpreter = gpu
                return@withLock gpu
            }
            // CPU 回退
            val cpu = Interpreter(
                buffer,
                Interpreter.Options().setNumThreads(CPU_THREADS).setUseXNNPACK(false),
            )
            interpreter = cpu
            cpu
        }
    }

    private fun readModelAsset(): ByteBuffer {
        val bytes = runCatching {
            context.assets.open(CutoutSpec.MODEL_ASSET).use { it.readBytes() }
        }.getOrElse { failure ->
            throw BackgroundRemovalException(
                "抠图模型缺失（assets/${CutoutSpec.MODEL_ASSET}），请使用原图",
                failure,
            )
        }
        if (bytes.isEmpty()) {
            throw BackgroundRemovalException("抠图模型为空，请使用原图")
        }
        return ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply {
            put(bytes)
            rewind()
        }
    }

    /**
     * 按模型真实输入布局装填：[1,H,W,3]（NHWC）与 [1,3,H,W]（NCHW）都支持——
     * 转换工具的选择不该让运行期炸掉。
     */
    private fun buildInput(source: Bitmap, engine: Interpreter): ByteBuffer {
        val shape = engine.getInputTensor(0).shape()
        val size = CutoutSpec.INPUT_SIZE
        val scaled = source.scale(size, size)
        val pixels = IntArray(size * size)
        scaled.getPixels(pixels, 0, size, 0, 0, size, size)
        if (scaled !== source) scaled.recycle()

        val isNchw = shape.size == 4 && shape[1] == 3
        val buffer = ByteBuffer.allocateDirect(size * size * 3 * 4)
            .order(ByteOrder.nativeOrder())
        if (isNchw) {
            for (channel in 0..2) {
                for (pixel in pixels) {
                    buffer.putFloat(normalize(channel(pixel, channel), channel))
                }
            }
        } else {
            for (pixel in pixels) {
                for (channel in 0..2) {
                    buffer.putFloat(normalize(channel(pixel, channel), channel))
                }
            }
        }
        buffer.rewind()
        return buffer
    }

    private fun channel(pixel: Int, channel: Int): Int = when (channel) {
        0 -> (pixel shr 16) and 0xFF
        1 -> (pixel shr 8) and 0xFF
        else -> pixel and 0xFF
    }

    private fun normalize(value: Int, channel: Int): Float =
        ((value / 255f) - CutoutSpec.MEAN[channel]) / CutoutSpec.STD[channel]

    private fun zeroInput(engine: Interpreter): ByteBuffer {
        val shape = engine.getInputTensor(0).shape()
        val count = shape.fold(1) { acc, dim -> acc * dim }
        return ByteBuffer.allocateDirect(count * 4)
            .order(ByteOrder.nativeOrder())
            .apply { rewind() }
    }

    /**
     * 主输出 = 融合后的 d0（其余 6 个是 U²-Net 的侧输出 d1..d6）。
     *
     * 该 TFLite 模型把 7 个侧输出全部导出为图输出，顺序不保证；d0 的张量名以
     * `PartitionedCall:0` 结尾，据此定位。名字缺失时回退到「均值最大的输出」——
     * d0 是融合结果，置信度整体高于各侧输出（该回退只会在极端情况下走一次）。
     */
    private fun mainOutputIndex(engine: Interpreter): Int {
        mainOutputSlot?.let { return it }
        val byName = (0 until engine.outputTensorCount).firstOrNull { index ->
            engine.getOutputTensor(index).name()?.endsWith(OUTPUT_D0_SUFFIX) == true
        }
        val resolved = byName ?: runInferenceAllOutputs(engine, zeroInput(engine))
        mainOutputSlot = resolved
        return resolved
    }

    /** 名称缺失时的兜底：跑一遍全输出，取均值最大者（d0 融合后整体最高）。 */
    private fun runInferenceAllOutputs(engine: Interpreter, input: ByteBuffer): Int {
        var bestIndex = 0
        var bestMean = -Double.MAX_VALUE
        for (index in 0 until engine.outputTensorCount) {
            val output = allocateOutput(engine, index) ?: continue
            engine.runForMultipleInputsOutputs(arrayOf(input), mutableMapOf(index to output))
            val mean = flatten(engine, index, output).average()
            if (mean > bestMean) {
                bestMean = mean
                bestIndex = index
            }
        }
        return bestIndex
    }

    /** 按真实输出形状组装 Java 数组；形状不受支持时返回 null（由调用方报错）。 */
    private fun allocateOutput(engine: Interpreter, index: Int): Any? {
        val shape = engine.getOutputTensor(index).shape().toList()
        return when (shape) {
            listOf(1, CutoutSpec.INPUT_SIZE, CutoutSpec.INPUT_SIZE, 1) -> Array(1) {
                Array(CutoutSpec.INPUT_SIZE) {
                    Array(CutoutSpec.INPUT_SIZE) { FloatArray(1) }
                }
            }

            listOf(1, 1, CutoutSpec.INPUT_SIZE, CutoutSpec.INPUT_SIZE) -> Array(1) {
                Array(1) {
                    Array(CutoutSpec.INPUT_SIZE) { FloatArray(CutoutSpec.INPUT_SIZE) }
                }
            }

            listOf(1, CutoutSpec.INPUT_SIZE, CutoutSpec.INPUT_SIZE) -> Array(1) {
                Array(CutoutSpec.INPUT_SIZE) { FloatArray(CutoutSpec.INPUT_SIZE) }
            }

            else -> null
        }
    }

    private fun flatten(engine: Interpreter, index: Int, output: Any): FloatArray {
        val shape = engine.getOutputTensor(index).shape().toList()
        val flat = FloatArray(shape.fold(1) { acc, dim -> acc * dim })
        when (shape) {
            listOf(1, CutoutSpec.INPUT_SIZE, CutoutSpec.INPUT_SIZE, 1) -> {
                @Suppress("UNCHECKED_CAST")
                val array = output as Array<Array<Array<FloatArray>>>
                var i = 0
                array[0].forEach { row -> row.forEach { cell -> flat[i++] = cell[0] } }
            }

            listOf(1, 1, CutoutSpec.INPUT_SIZE, CutoutSpec.INPUT_SIZE) -> {
                @Suppress("UNCHECKED_CAST")
                val array = output as Array<Array<Array<FloatArray>>>
                var i = 0
                array[0][0].forEach { row -> row.forEach { value -> flat[i++] = value } }
            }

            listOf(1, CutoutSpec.INPUT_SIZE, CutoutSpec.INPUT_SIZE) -> {
                @Suppress("UNCHECKED_CAST")
                val array = output as Array<Array<FloatArray>>
                var i = 0
                array[0].forEach { row -> row.forEach { value -> flat[i++] = value } }
            }

            else -> throw BackgroundRemovalException(
                "抠图模型输出形状不受支持：$shape（期望 320×320 单通道显著图）",
            )
        }
        return flat
    }

    /** 只取主输出执行推理；其余 6 个侧输出不参与，省一次搬运。 */
    private suspend fun runInference(engine: Interpreter, input: ByteBuffer): FloatArray =
        inferenceMutex.withLock {
            val index = mainOutputIndex(engine)
            val output = allocateOutput(engine, index)
                ?: throw BackgroundRemovalException(
                    "抠图模型输出形状不受支持：${engine.getOutputTensor(index).shape().toList()}",
                )
            engine.runForMultipleInputsOutputs(arrayOf(input), mutableMapOf(index to output))
            flatten(engine, index, output)
        }

    /**
     * 显著图 → 透明背景 Bitmap。
     *
     * 先按输出实际 min/max 归一化（不同导出版本的激活尺度不同），再用双线性缩放
     * 到原图尺寸，最后把灰度值写进 alpha 通道。软边保留，不做二值化——衣服边缘的
     * 半透明比硬切更自然。
     */
    private fun applyMask(source: Bitmap, mask: FloatArray): Bitmap {
        var min = Float.MAX_VALUE
        var max = -Float.MAX_VALUE
        mask.forEach { value ->
            if (value.isFinite()) {
                if (value < min) min = value
                if (value > max) max = value
            }
        }
        if (!min.isFinite() || !max.isFinite() || max - min < 1e-6f) {
            throw BackgroundRemovalException("抠图结果为空，请使用原图")
        }
        val size = CutoutSpec.INPUT_SIZE
        val maskPixels = IntArray(mask.size)
        var opaque = 0
        mask.forEachIndexed { index, raw ->
            val normalized = ((raw - min) / (max - min)).coerceIn(0f, 1f)
            val alpha = (normalized * 255).toInt()
            if (alpha > 32) opaque++
            maskPixels[index] = Color.argb(255, alpha, alpha, alpha)
        }
        if (opaque == 0) {
            throw BackgroundRemovalException("抠图结果为空，请使用原图")
        }

        val maskBitmap = createBitmap(size, size)
        maskBitmap.setPixels(maskPixels, 0, size, 0, 0, size, size)
        val scaledMask = maskBitmap.scale(source.width, source.height)
        maskBitmap.recycle()

        val sourcePixels = IntArray(source.width * source.height)
        source.getPixels(sourcePixels, 0, source.width, 0, 0, source.width, source.height)
        val maskAtSource = IntArray(source.width * source.height)
        scaledMask.getPixels(maskAtSource, 0, source.width, 0, 0, source.width, source.height)
        scaledMask.recycle()

        for (i in sourcePixels.indices) {
            val alpha = maskAtSource[i] and 0xFF
            sourcePixels[i] = (sourcePixels[i] and 0x00FFFFFF) or (alpha shl 24)
        }
        val result = createBitmap(source.width, source.height)
        result.setPixels(sourcePixels, 0, source.width, 0, 0, source.width, source.height)
        return result
    }

    private companion object {
        const val CPU_THREADS = 4

        /** d0 的张量名以 PartitionedCall:0 结尾（其余侧输出是 :1..:6） */
        const val OUTPUT_D0_SUFFIX = ":0"
    }
}
