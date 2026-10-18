package com.yuhinhsiung.clothesmate.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * U01：小字号**文字**色的 WCAG 对比度回归（正文 4.5:1）。
 *
 * 图标 / 描边仍可用 `tertiary`（图形 3:1），但 AI 角标文字、状态文字
 * 必须使用 `accentText` / `statusOkText` 的深色变体。
 */
class ContrastTextTest {

    @Test
    fun lightAccentTextPassesOnWhiteAndBackground() {
        assertAtLeast4_5(LightAccentText, LightSurface)
        assertAtLeast4_5(LightAccentText, LightBackground)
    }

    @Test
    fun lightStatusOkTextPassesOnWhiteAndBackground() {
        assertAtLeast4_5(LightStatusOkText, LightSurface)
        assertAtLeast4_5(LightStatusOkText, LightBackground)
    }

    @Test
    fun darkAccentTextPassesOnDarkSurfaceAndBackground() {
        assertAtLeast4_5(DarkAccentText, DarkSurface)
        assertAtLeast4_5(DarkAccentText, DarkBackground)
    }

    @Test
    fun darkStatusOkTextPassesOnDarkSurfaceAndBackground() {
        assertAtLeast4_5(DarkStatusOkText, DarkSurface)
        assertAtLeast4_5(DarkStatusOkText, DarkBackground)
    }

    private fun assertAtLeast4_5(foreground: Color, background: Color) {
        val ratio = contrast(foreground, background)
        assertTrue(
            "对比度不足 4.5:1：fg=#${foreground.toHex()} bg=#${background.toHex()} ratio=$ratio",
            ratio >= 4.5,
        )
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun luminance(color: Color): Double =
        0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)

    private fun linear(channel: Float): Double {
        val s = channel.toDouble()
        return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }

    private fun Color.toHex(): String =
        "%02X%02X%02X".format((red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())
}
