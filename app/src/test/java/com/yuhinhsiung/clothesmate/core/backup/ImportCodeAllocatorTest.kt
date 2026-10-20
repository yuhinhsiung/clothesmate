package com.yuhinhsiung.clothesmate.core.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ImportCodeAllocatorTest {

    @Test
    fun conflictFallbackSkipsRetiredBackupCode() {
        val imported = ImportCodeAllocator.allocate(
            preferred = "9",
            oldItemId = 30,
            backupCodesByItem = mapOf(30L to "9"),
            backupHistoryHolder = mapOf("1" to 999L, "9" to 30L),
            occupiedCodes = setOf("9"),
            assignedCodes = emptySet(),
        )
        assertEquals("2", imported)
        assertNotEquals("1", imported)
    }

    @Test
    fun freePreferredCodeIsPreserved() {
        assertEquals(
            "42",
            ImportCodeAllocator.allocate("42", 30, mapOf(30L to "42"), emptyMap(), setOf("9"), emptySet()),
        )
    }

    @Test
    fun otherBackupItemCodeIsNotReused() {
        val imported = ImportCodeAllocator.allocate(
            preferred = "9",
            oldItemId = 30,
            backupCodesByItem = mapOf(30L to "9", 31L to "1"),
            backupHistoryHolder = emptyMap(),
            occupiedCodes = setOf("9"),
            assignedCodes = emptySet(),
        )
        assertEquals("2", imported)
    }

    @Test
    fun assignedCodesAreClosedOverWithinOneImport() {
        val imported = ImportCodeAllocator.allocate(
            preferred = "1",
            oldItemId = 32,
            backupCodesByItem = emptyMap(),
            backupHistoryHolder = emptyMap(),
            occupiedCodes = emptySet(),
            assignedCodes = setOf("1", "2"),
        )
        assertEquals("3", imported)
    }
}
