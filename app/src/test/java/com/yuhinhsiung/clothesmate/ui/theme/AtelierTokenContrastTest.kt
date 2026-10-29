package com.yuhinhsiung.clothesmate.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M6 对比度门禁：文本（含 disabled）≥4.5:1，控件边界 ≥3:1。
 * 使用与 Compose 主题相同的 token 常量，避免另抄一份色值。
 */
class AtelierTokenContrastTest {

    private fun contrast(a: Color, b: Color): Double {
        val l1 = a.luminance().toDouble()
        val l2 = b.luminance().toDouble()
        val hi = maxOf(l1, l2)
        val lo = minOf(l1, l2)
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun assertText(name: String, fg: Color, bg: Color) {
        val ratio = contrast(fg, bg)
        assertTrue("$name 对比度 $ratio < 4.5", ratio >= 4.5)
    }

    private fun assertControl(name: String, fg: Color, bg: Color) {
        val ratio = contrast(fg, bg)
        assertTrue("$name 对比度 $ratio < 3.0", ratio >= 3.0)
    }

    @Test
    fun lightTextTokensMeetContrast() {
        assertText("primary/canvas", LightTextPrimary, LightCanvas)
        assertText("secondary/canvas", LightTextSecondary, LightCanvas)
        assertText("disabled/surface", LightTextDisabled, LightSurface)
        assertText("onPrimary/primary", LightOnPrimary, LightPrimary)
        assertText("aiText/aiContainer", LightAiText, LightAiContainer)
        assertText("successText/successContainer", LightSuccessText, LightSuccessContainer)
        assertText("errorText/errorContainer", LightError, LightErrorContainer)
    }

    @Test
    fun darkTextTokensMeetContrast() {
        assertText("primary/canvas", DarkTextPrimary, DarkCanvas)
        assertText("secondary/canvas", DarkTextSecondary, DarkCanvas)
        assertText("disabled/surface", DarkTextDisabled, DarkSurface)
        assertText("onPrimary/primary", DarkOnPrimary, DarkPrimary)
        assertText("aiText/aiContainer", DarkAiText, DarkAiContainer)
        assertText("successText/successContainer", DarkSuccessText, DarkSuccessContainer)
        assertText("errorText/errorContainer", DarkError, DarkErrorContainer)
    }

    @Test
    fun controlOutlinesMeetContrast() {
        assertControl("light control/surface", LightOutlineControl, LightSurface)
        assertControl("dark control/surface", DarkOutlineControl, DarkSurface)
    }
}
