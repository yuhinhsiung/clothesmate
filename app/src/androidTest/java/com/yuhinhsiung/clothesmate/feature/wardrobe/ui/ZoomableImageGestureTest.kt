package com.yuhinhsiung.clothesmate.feature.wardrobe.ui

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ZoomableImageGestureTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun pinchZoomAndPanUpdateTheSameState() {
        var last = ZoomState()
        rule.setContent {
            ZoomableImage(
                path = "",
                contentDescription = null,
                clothingId = 0L,
                modifier = Modifier.size(400.dp).testTag("zoomable"),
                shared = false,
                onZoomChanged = { last = it },
            )
        }

        rule.onNodeWithTag("zoomable").performTouchInput {
            pinch(
                start0 = Offset(120f, 220f),
                end0 = Offset(60f, 220f),
                start1 = Offset(300f, 220f),
                end1 = Offset(360f, 220f),
                durationMillis = 400,
            )
        }
        rule.runOnIdle {
            assertTrue("pinch must zoom in, got ${last.scale}", last.scale > 1f)
        }

        rule.onNodeWithTag("zoomable").performTouchInput {
            swipe(start = Offset(220f, 240f), end = Offset(340f, 240f), durationMillis = 300)
        }
        rule.runOnIdle {
            assertTrue("pan while zoomed must move the image, got ${last.offset}", last.offset.x > 0f)
        }
    }
}
