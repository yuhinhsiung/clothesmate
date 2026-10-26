package com.yuhinhsiung.clothesmate.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class UpdateFailureTextTest {

    @Test
    fun timeoutBecomesReadableChinese() {
        val text = downloadFailureText("timeout")
        assertEquals("下载超时，请检查网络后重试", text)
    }

    @Test
    fun rawHttp2ResetIsNotShownToUser() {
        val text = downloadFailureText("stream was reset: CANCEL")
        assertFalse(text.contains("stream", ignoreCase = true))
        assertFalse(text.contains("CANCEL", ignoreCase = true))
        assertEquals("下载中断，请重试", text)
    }

    @Test
    fun digestMismatchBecomesVerificationMessage() {
        assertEquals("安装包校验未通过", downloadFailureText("sha256 mismatch"))
    }

    @Test
    fun httpStatusKeepsOnlyCode() {
        assertEquals("下载服务器返回错误（HTTP 503）", downloadFailureText("HTTP 503"))
    }
}
