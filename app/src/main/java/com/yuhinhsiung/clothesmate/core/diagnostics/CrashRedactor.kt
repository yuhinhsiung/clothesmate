package com.yuhinhsiung.clothesmate.core.diagnostics

/**
 * 崩溃日志脱敏器（M5-11）。
 *
 * 崩溃堆栈会原样携带异常 message / cause、SQLite 的 SQL、OkHttp 的 URL 与
 * 响应片段、以及图片绝对路径；只过滤「已知 Key」不够。这里按**字段形状**
 * 做通用脱敏，宁可多抹掉一段，也不把凭据或用户私有路径带出应用。
 *
 * 纯 Kotlin，无 Android 依赖，单测覆盖（M5-11 验收）。
 */
object CrashRedactor {

    private val pemBlock = Regex(
        """-----BEGIN [A-Z0-9 ]*PRIVATE KEY-----.*?-----END [A-Z0-9 ]*PRIVATE KEY-----""",
        setOf(RegexOption.DOT_MATCHES_ALL),
    )

    private val bearer = Regex("""(?i)\bBearer\s+[A-Za-z0-9._~+/=-]+""")

    private val skKey = Regex("""\bsk-[A-Za-z0-9_-]{8,}\b""")

    /** key=value / "apiKey":"..." / Authorization: ... 等字段。 */
    private val secretField = Regex(
        """(?i)\b(api[_-]?key|apikey|access[_-]?token|refresh[_-]?token|token|secret|password|passwd|authorization|private[_-]?key)\b(\s*[:=]\s*)(["']?)[^"'\s,;&}]+""",
    )

    private val urlQuery = Regex("""([?&](?:key|api[_-]?key|token|access_token|sig|signature|secret)=)[^&#\s]+""", RegexOption.IGNORE_CASE)

    /** 应用私有目录 / 外置存储 / file:// 绝对路径；保留「路径存在」这个事实，但不保留内容。 */
    private val filePath = Regex("""(?:/data/|/storage/|/sdcard/|file://)[^\s"'\\)\]}]+""")

    private val sqlText = Regex(
        """(?i)\b(SELECT|INSERT\s+INTO|UPDATE|DELETE\s+FROM|PRAGMA)\b[^\n]*""",
    )

    private val httpBody = Regex(
        """(?i)\b(body|responseBody|response_body)\s*[=:]\s*[^\n]{4,}""",
    )

    fun redact(input: String): String {
        var output = input
        output = pemBlock.replace(output, "[REDACTED_PRIVATE_KEY]")
        output = bearer.replace(output, "Bearer [REDACTED]")
        output = skKey.replace(output, "sk-[REDACTED]")
        output = secretField.replace(output) { match ->
            "${match.groupValues[1]}${match.groupValues[2]}[REDACTED]"
        }
        output = urlQuery.replace(output) { match -> "${match.groupValues[1]}[REDACTED]" }
        output = httpBody.replace(output) { match -> "${match.groupValues[1]}=[REDACTED_BODY]" }
        output = filePath.replace(output, "[REDACTED_PATH]")
        output = sqlText.replace(output, "[REDACTED_SQL]")
        return output
    }
}
