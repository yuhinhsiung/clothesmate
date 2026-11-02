package com.yuhinhsiung.clothesmate.core.platform

import androidx.camera.core.CameraSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MainCameraSelectorTest {

    private fun candidate(
        id: String,
        facing: Int = CameraSelector.LENS_FACING_BACK,
        flash: Boolean,
        area: Int,
    ) = CameraCandidate(id, facing, flash, area)

    @Test
    fun prefersFlashAndLargestSensorOverUltraWide() {
        val main = candidate("0", flash = true, area = 12_000_000)
        val wide = candidate("20", flash = false, area = 8_000_000)
        val macro = candidate("21", flash = false, area = 2_000_000)
        assertEquals(main, selectMainBackCamera(listOf(wide, macro, main)))
    }

    @Test
    fun ignoresFrontCamera() {
        val front = candidate("1", CameraSelector.LENS_FACING_FRONT, flash = true, area = 20_000_000)
        val main = candidate("0", flash = true, area = 12_000_000)
        assertEquals(main, selectMainBackCamera(listOf(front, main)))
    }

    @Test
    fun withoutFlashPicksLargestBackSensor() {
        val main = candidate("2", flash = false, area = 12_000_000)
        val wide = candidate("3", flash = false, area = 8_000_000)
        assertEquals(main, selectMainBackCamera(listOf(wide, main)))
    }

    @Test
    fun equalSensorPrefersLowestNumericId() {
        val logical = candidate("61", flash = true, area = 12_000_000)
        val physical = candidate("0", flash = true, area = 12_000_000)
        assertEquals(physical, selectMainBackCamera(listOf(logical, physical)))
    }

    @Test
    fun noBackCameraReturnsNull() {
        val front = candidate("1", CameraSelector.LENS_FACING_FRONT, flash = true, area = 20_000_000)
        assertNull(selectMainBackCamera(listOf(front)))
    }
}
