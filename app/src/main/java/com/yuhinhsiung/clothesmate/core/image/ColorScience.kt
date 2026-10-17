package com.yuhinhsiung.clothesmate.core.image

/**
 * 颜色科学小工具（M2-02）—— **纯 Kotlin，不依赖 Android**。
 *
 * 本地颜色提取的口径（开发文档 §2.2.3 / §3.4）：聚类主色在 **Lab 空间**取最近枚举色；
 * 所以这里只做 sRGB → CIE Lab（D65）与 ΔE76 距离，供 [LocalColorExtractor] 直测。
 */
data class Lab(val l: Double, val a: Double, val b: Double)

internal object ColorScience {

    /** D65 参考白 */
    private const val XN = 0.95047
    private const val YN = 1.0
    private const val ZN = 1.08883

    /** sRGB 分量（0..255）→ 线性光（0..1） */
    private fun linearize(channel: Int): Double {
        val c = channel.coerceIn(0, 255) / 255.0
        return if (c <= 0.04045) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
    }

    /** 0xRRGGBB → Lab */
    fun rgbToLab(rgb: Int): Lab {
        val r = linearize((rgb shr 16) and 0xFF)
        val g = linearize((rgb shr 8) and 0xFF)
        val b = linearize(rgb and 0xFF)

        val x = 0.4124564 * r + 0.3575761 * g + 0.1804375 * b
        val y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b
        val z = 0.0193339 * r + 0.1191920 * g + 0.9503041 * b

        fun f(t: Double): Double =
            if (t > 0.008856) Math.cbrt(t) else 7.787 * t + 16.0 / 116.0

        val fx = f(x / XN)
        val fy = f(y / YN)
        val fz = f(z / ZN)
        return Lab(
            l = 116 * fy - 16,
            a = 500 * (fx - fy),
            b = 200 * (fy - fz),
        )
    }

    /** CIE76 色差：同一空间内欧氏距离，够用于「最近的预设色」。 */
    fun deltaE(first: Lab, second: Lab): Double {
        val dl = first.l - second.l
        val da = first.a - second.a
        val db = first.b - second.b
        return Math.sqrt(dl * dl + da * da + db * db)
    }

    /** 由若干 Lab 点求质心 */
    fun centroid(points: List<Lab>): Lab = Lab(
        l = points.sumOf { it.l } / points.size,
        a = points.sumOf { it.a } / points.size,
        b = points.sumOf { it.b } / points.size,
    )
}
