package com.yuhinhsiung.clothesmate.core.diagnostics

import com.yuhinhsiung.clothesmate.core.diagnostics.CrashRedactor
import org.junit.Assert.*
import org.junit.Test

/** Plain key=value logging: a quote in the middle does not delimit an unquoted value. */
class CrashRedactorUnquotedBoundaryTest {
    private val tail = "review-synthetic-private-tail"
    @Test fun apostropheInsideUnquotedPasswordMustNotLeakSuffix() {
        val output = CrashRedactor.redact("password=prefix'$tail")
        assertFalse("unquoted password leaked suffix: $output", output.contains(tail))
    }
    @Test fun doubleQuoteInsideUnquotedPasswordMustNotLeakSuffix() {
        val output = CrashRedactor.redact("password=prefix\"$tail")
        assertFalse("unquoted password leaked suffix: $output", output.contains(tail))
    }
    @Test fun secondExportRedactionMustNotLeaveTheSameSuffix() {
        val once = CrashRedactor.redact("password=prefix'$tail")
        val twice = CrashRedactor.redact(once)
        assertFalse("second export redaction still leaked suffix: $twice", twice.contains(tail))
    }
    @Test fun legalUriPasswordQueryMustNotLeakSuffixEvenOnSecondExport() {
        val input = "request https://example.invalid/?password=prefix'$tail&attempt=1"
        val once = CrashRedactor.redact(input)
        val twice = CrashRedactor.redact(once)
        assertTrue("ordinary query metadata lost: $twice", twice.contains("attempt=1"))
        assertFalse("URI password suffix leaked, once=$once; twice=$twice", once.contains(tail) || twice.contains(tail))
    }
    @Test fun quotedPasswordAndNormalMetadataControl() {
        val output = CrashRedactor.redact("password=\"prefix'$tail\" status=500")
        assertFalse(output.contains(tail))
        assertTrue("ordinary metadata lost: $output", output.contains("status=500"))
    }
}
