package com.yuhinhsiung.clothesmate.feature.today.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yuhinhsiung.clothesmate.ui.theme.LocalReducedMotion
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** M5-06：减弱动态效果时粒子庆祝必须直接结束，不进入 600ms 动画。 */
@RunWith(AndroidJUnit4::class)
class ReducedMotionCelebrationTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun reducedMotionFinishesCelebrationWithoutAnimating() {
        val finished = AtomicBoolean(false)
        rule.setContent {
            CompositionLocalProvider(LocalReducedMotion provides true) {
                WearCelebration(onFinished = { finished.set(true) })
            }
        }
        rule.waitUntil(timeoutMillis = 1_000) { finished.get() }
        assertTrue(finished.get())
    }
}
