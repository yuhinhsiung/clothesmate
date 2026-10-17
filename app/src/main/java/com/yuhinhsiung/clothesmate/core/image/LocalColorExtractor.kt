package com.yuhinhsiung.clothesmate.core.image

import com.yuhinhsiung.clothesmate.core.model.ClothingColor

/**
 * 本地颜色提取 —— M2-02 / 开发文档 §2.2.3、§3.4。
 *
 * 口径（照文档）：
 * 1. 对抠图结果（无抠图则原图）做聚类取主色；
 * 2. 聚类中心在 **Lab 空间**取最近的预设 12 色；
 * 3. **≥2 个主色各占 ≥20% 时判 `multi`（花色）**。
 *
 * 本类是**纯 Kotlin**（入参是 ARGB 像素数组，不是 Android Bitmap），因此规则、阈值、
 * Lab 映射都能用 JVM 单测锁定；Bitmap → 像素的采样见 `BitmapColorSampler`。
 *
 * ## 实现选择（可解释）
 *
 * - 采样上限 2.4 万像素：一张 1600px 的衣物图有百万级像素，全量 k-means 太慢；
 * - k=5 固定、种子由颜色分桶按出现次数贪心挑选（不是随机初始化），**同一张图结果稳定**；
 * - 聚类后先按 ΔE < [MERGE_DELTA_E] 合并相近簇——不合并的话，同一件红衣服的明暗
 *   阴影会被拆成两个 30%+ 的「主色」，直接把纯色误判成花色；
 * - 合并后份额 < [MIN_CLUSTER_SHARE] 的碎簇丢弃（边缘反光、半透明边等）。
 */
object LocalColorExtractor {

    /** 采样上限：够覆盖主色，又不让 k-means 拖慢录入流程 */
    const val MAX_SAMPLES = 24_000

    /** 聚类数 */
    const val CLUSTER_COUNT = 5

    /** 合并相近簇的 ΔE76 阈值（同一色系的明暗差异） */
    const val MERGE_DELTA_E = 12.0

    /** 「主色」门槛：各占 ≥20% 才参与花色判定（开发文档 §3.4） */
    const val DOMINANT_SHARE = 0.20

    /** 碎簇丢弃门槛 */
    const val MIN_CLUSTER_SHARE = 0.02

    /** 抠图 PNG 的透明像素不参与取色 */
    const val ALPHA_THRESHOLD = 32

    private val palette: List<Pair<ClothingColor, Lab>> =
        ClothingColor.paletteForExtraction.map { it to ColorScience.rgbToLab(it.rgb) }

    /** 单次聚类结果（调试与测试用） */
    data class Cluster(
        /** 该簇中心 rgb（0xRRGGBB） */
        val rgb: Int,
        /** 占有效像素的比例 0..1 */
        val share: Double,
        /** 该簇在 Lab 中的位置 */
        val lab: Lab,
        /** 按 Lab 最近色映射到的预设色 */
        val paletteColor: ClothingColor,
    )

    /**
     * 提取结果。
     *
     * @property colors 可写库的颜色 key 列表：纯色 = 1 个 key；花色 = `["multi"]`
     * @property dominantShare 最大簇占比（测试与 UI 提示用）
     * @property clusters 合并后的簇（按占比降序）
     */
    data class Result(
        val colors: List<ClothingColor>,
        val dominantShare: Double,
        val clusters: List<Cluster>,
    )

    /** 像素数组里装不下任何不透明像素时返回 null（例如整张透明） */
    fun extract(pixels: IntArray, width: Int = 0, height: Int = 0): Result? {
        if (pixels.isEmpty()) return null
        val step = (pixels.size / MAX_SAMPLES).coerceAtLeast(1)
        val samples = ArrayList<Sample>(pixels.size / step + 1)
        var index = 0
        while (index < pixels.size) {
            val pixel = pixels[index]
            val alpha = pixel ushr 24
            if (alpha >= ALPHA_THRESHOLD) {
                val rgb = pixel and 0xFFFFFF
                samples += Sample(rgb = rgb, lab = ColorScience.rgbToLab(rgb))
            }
            index += step
        }
        if (samples.isEmpty()) return null

        val seeds = pickSeeds(samples)
        if (seeds.isEmpty()) return null
        val centers = kMeans(samples, seeds)
        val means = mergeSimilar(centers)

        val counts = IntArray(means.size)
        val rgbSums = LongArray(means.size * 3)
        samples.forEach { sample ->
            var best = 0
            var bestDistance = Double.MAX_VALUE
            means.forEachIndexed { i, mean ->
                val distance = ColorScience.deltaE(sample.lab, mean)
                if (distance < bestDistance) {
                    bestDistance = distance
                    best = i
                }
            }
            counts[best]++
            rgbSums[best * 3] += (sample.rgb shr 16) and 0xFF
            rgbSums[best * 3 + 1] += (sample.rgb shr 8) and 0xFF
            rgbSums[best * 3 + 2] += sample.rgb and 0xFF
        }
        val total = samples.size.toDouble()
        val clusters = means.mapIndexedNotNull { i, lab ->
            val count = counts[i]
            val share = count / total
            if (count == 0 || share < MIN_CLUSTER_SHARE) return@mapIndexedNotNull null
            val rgb = ((rgbSums[i * 3] / count).toInt() shl 16) or
                ((rgbSums[i * 3 + 1] / count).toInt() shl 8) or
                (rgbSums[i * 3 + 2] / count).toInt()
            Cluster(
                rgb = rgb,
                share = share,
                lab = lab,
                paletteColor = nearestPaletteColor(lab),
            )
        }.sortedByDescending { it.share }

        if (clusters.isEmpty()) return null
        val dominant = clusters.filter { it.share >= DOMINANT_SHARE }
        val colors = if (dominant.size >= 2) {
            listOf(ClothingColor.Multi)
        } else {
            listOf(clusters.first().paletteColor)
        }
        return Result(
            colors = colors,
            dominantShare = clusters.first().share,
            clusters = clusters,
        )
    }

    /**
     * 合并相近簇。返回合并后的 Lab 质心（按样本数加权）。
     */
    private fun mergeSimilar(centers: List<Lab>): List<Lab> {
        val merged = mutableListOf<Lab>()
        centers.forEach { center ->
            val target = merged.indexOfFirst { ColorScience.deltaE(it, center) < MERGE_DELTA_E }
            if (target >= 0) {
                // 简单平均即可：k-means 已经让相近簇的质心非常接近
                val existing = merged[target]
                merged[target] = Lab(
                    l = (existing.l + center.l) / 2,
                    a = (existing.a + center.a) / 2,
                    b = (existing.b + center.b) / 2,
                )
            } else {
                merged += center
            }
        }
        return merged
    }

    /** 贪心挑选初始质心：按颜色分桶出现次数降序，且彼此足够远。 */
    private fun pickSeeds(samples: List<Sample>): List<Lab> {
        val bins = HashMap<Int, IntArray>(1024)
        samples.forEach { sample ->
            val r = (sample.rgb shr 16) and 0xFF
            val g = (sample.rgb shr 8) and 0xFF
            val b = sample.rgb and 0xFF
            // 5 bit/通道 = 32768 个桶；桶值同时累计 rgb，供种子取桶内均值
            val key = ((r shr 3) shl 10) or ((g shr 3) shl 5) or (b shr 3)
            val entry = bins.getOrPut(key) { IntArray(4) }
            entry[0]++
            entry[1] += r
            entry[2] += g
            entry[3] += b
        }
        val ordered = bins.values.sortedByDescending { it[0] }
        val seeds = mutableListOf<Lab>()
        for (entry in ordered) {
            if (seeds.size >= CLUSTER_COUNT) break
            val count = entry[0]
            val rgb = ((entry[1] / count) shl 16) or ((entry[2] / count) shl 8) or (entry[3] / count)
            val lab = ColorScience.rgbToLab(rgb)
            if (seeds.none { ColorScience.deltaE(it, lab) < MERGE_DELTA_E }) {
                seeds += lab
            }
        }
        return seeds
    }

    /** 固定迭代次数、球面外无随机性：同输入同输出（便于回归）。 */
    private fun kMeans(samples: List<Sample>, seeds: List<Lab>): List<Lab> {
        var centers = seeds
        repeat(12) {
            val sums = Array(centers.size) { DoubleArray(3) }
            val counts = IntArray(centers.size)
            samples.forEach { sample ->
                var best = 0
                var bestDistance = Double.MAX_VALUE
                centers.forEachIndexed { i, center ->
                    val distance = ColorScience.deltaE(sample.lab, center)
                    if (distance < bestDistance) {
                        bestDistance = distance
                        best = i
                    }
                }
                counts[best]++
                sums[best][0] += sample.lab.l
                sums[best][1] += sample.lab.a
                sums[best][2] += sample.lab.b
            }
            centers = centers.mapIndexed { i, center ->
                if (counts[i] == 0) {
                    center
                } else {
                    Lab(
                        l = sums[i][0] / counts[i],
                        a = sums[i][1] / counts[i],
                        b = sums[i][2] / counts[i],
                    )
                }
            }
        }
        return centers
    }

    /** Lab → 最近的预设色（排除 `multi`：花色是判定结果，不是距离最近的色）。 */
    fun nearestPaletteColor(lab: Lab): ClothingColor =
        palette.minByOrNull { ColorScience.deltaE(it.second, lab) }?.first ?: ClothingColor.Gray

    /** 便利重载：直接给 rgb */
    fun nearestPaletteColor(rgb: Int): ClothingColor =
        nearestPaletteColor(ColorScience.rgbToLab(rgb))

    private data class Sample(val rgb: Int, val lab: Lab)
}
