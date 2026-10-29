package com.yuhinhsiung.clothesmate.feature.today

import androidx.compose.ui.unit.dp
import com.yuhinhsiung.clothesmate.feature.today.ui.todayPhotoHeight
import org.junit.Assert.assertEquals
import org.junit.Test

class TodayPhotoHeightTest {
    @Test fun `normal type uses 200`() = assertEquals(200.dp, todayPhotoHeight(1.0f))
    @Test fun `large type uses 220`() = assertEquals(220.dp, todayPhotoHeight(1.4f))
    @Test fun `accessibility type uses 160`() = assertEquals(160.dp, todayPhotoHeight(1.9f))
}
