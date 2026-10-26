package com.yuhinhsiung.clothesmate.ui.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** M5-06：系统「减弱动态效果」判定必须覆盖两个动画缩放开关。 */
class ReducedMotionTest {

    @Test
    fun transitionScaleZeroDisablesMotion() {
        assertTrue(isReducedMotion(transitionScale = 0f, animatorScale = 1f))
    }

    @Test
    fun animatorScaleZeroDisablesMotion() {
        // Android 的「移除动画」通常只把 animator_duration_scale 置 0。
        assertTrue(isReducedMotion(transitionScale = 1f, animatorScale = 0f))
    }

    @Test
    fun normalScalesKeepMotion() {
        assertFalse(isReducedMotion(transitionScale = 1f, animatorScale = 1f))
    }
}
