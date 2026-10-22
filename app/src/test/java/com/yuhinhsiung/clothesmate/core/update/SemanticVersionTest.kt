package com.yuhinhsiung.clothesmate.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticVersionTest {

    @Test
    fun parsesWithAndWithoutVPrefix() {
        assertEquals(SemanticVersion(1, 2, 3), SemanticVersion.parse("v1.2.3"))
        assertEquals(SemanticVersion(1, 2, 3), SemanticVersion.parse("1.2.3"))
    }

    @Test
    fun rejectsPreReleaseAndMissingParts() {
        assertNull(SemanticVersion.parse("1.2.3-beta"))
        assertNull(SemanticVersion.parse("1.2"))
        assertNull(SemanticVersion.parse("v1.2.3.4"))
        assertNull(SemanticVersion.parse("v1.2.x"))
    }

    @Test
    fun rejectsOutOfRangeMinorAndPatch() {
        assertNull(SemanticVersion.parse("1.0.100"))
        assertNull(SemanticVersion.parse("1.100.0"))
    }

    @Test
    fun comparesNumericallyNotLexicographically() {
        assertTrue(SemanticVersion.parse("1.10.0")!! > SemanticVersion.parse("1.9.9")!!)
        assertTrue(SemanticVersion.parse("2.0.0")!! > SemanticVersion.parse("1.99.99")!!)
        assertEquals(0, SemanticVersion.parse("1.2.3")!!.compareTo(SemanticVersion(1, 2, 3)))
    }
}
