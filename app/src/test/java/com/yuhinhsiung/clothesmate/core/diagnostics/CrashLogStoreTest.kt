package com.yuhinhsiung.clothesmate.core.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CrashLogStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `writes readable crash report and rotates to three files`() {
        val store = CrashLogStore(folder.newFolder("diagnostics"), maxFiles = 3)
        repeat(4) { index ->
            val file = store.record(
                Thread.currentThread(),
                IllegalStateException("boom $index /data/user/0/com.yuhinhsiung.clothesmate/files/x.jpg"),
            )
            assertTrue(file != null && file.isFile)
        }
        val logs = store.logs()
        assertEquals(3, logs.size)
        val report = store.buildReport()
        assertTrue(report.contains("IllegalStateException"))
        assertTrue(report.contains("time="))
        assertFalse(report.contains("/files/x.jpg"))
        assertTrue(report.contains("[REDACTED_PATH]"))
    }

    @Test
    fun `truncates a single oversized log`() {
        val store = CrashLogStore(folder.newFolder("diagnostics"), maxFileBytes = 256)
        val error = IllegalStateException("x".repeat(10_000))
        val file = store.record(Thread.currentThread(), error)
        assertTrue(file != null && file!!.length() <= 256)
    }

    @Test
    fun `empty store exports explain no logs`() {
        val store = CrashLogStore(folder.newFolder("diagnostics"))
        val report = store.buildReport()
        assertTrue(report.contains("暂无崩溃日志"))
    }
}
