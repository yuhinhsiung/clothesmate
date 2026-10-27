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

    /** key=value / "apiKey":"..." / Authorization: ... 等字段（值由下方按引号边界消费）。 */
    private val secretField = Regex(
        """(?i)\b(api[_-]?key|apikey|access[_-]?token|refresh[_-]?token|token|secret|password|passwd|authorization|private[_-]?key)\b(["']?\s*[:=]\s*)(["']?)""",
    )

    private val urlQuery = Regex("""([?&](?:key|api[_-]?key|token|access_token|sig|signature|secret)=)[^&#\s]+""", RegexOption.IGNORE_CASE)

    /** 应用私有目录 / 外置存储 / file:// 绝对路径；保留「路径存在」这个事实，但不保留内容。 */
    private val filePath = Regex("""(?:/data/|/storage/|/sdcard/|file://)[^\s"'\\)\]}]+""")

    private val sqlText = Regex(
        """(?i)\b(SELECT|INSERT\s+INTO|UPDATE|DELETE\s+FROM|PRAGMA)\b[^\n]*""",
    )

    private val httpBodyHeader = Regex(
        """(?i)(["']?)\b(responseBody|response_body|body)\b\1\s*[:=]\s*""",
    )

    fun redact(input: String): String {
        var output = input
        output = pemBlock.replace(output, "[REDACTED_PRIVATE_KEY]")
        // R02：必须先隔离完整 response body，再做字段/Token 替换。否则字段规则会把
        // body 内部的引号/逗号/花括号当成秘密值边界，破坏后续括号扫描的语法。
        output = redactHttpBodies(output)
        output = bearer.replace(output, "Bearer [REDACTED]")
        output = skKey.replace(output, "sk-[REDACTED]")
        output = redactSecretFields(output)
        output = urlQuery.replace(output) { match -> "${match.groupValues[1]}[REDACTED]" }
        output = filePath.replace(output, "[REDACTED_PATH]")
        output = sqlText.replace(output, "[REDACTED_SQL]")
        return output
    }

    /**
     * 按字段值的真实边界消费，而不是在第一个空格/逗号/右括号处截止。
     *
     * - 带引号的值一直消费到未转义的闭引号，内部空格、逗号、&、; 都不泄漏；
     * - 无引号的值仍按空白/分隔符截止，避免吞掉整行普通日志；
     * - 截断的引号值消费到文本末尾（保守）。
     */
    private fun redactSecretFields(input: String): String {
        val output = StringBuilder(input.length)
        var cursor = 0
        while (true) {
            val match = secretField.find(input, cursor) ?: break
            output.append(input, cursor, match.range.first)
            val quote = match.groupValues[3].firstOrNull()
            output.append(match.groupValues[1])
            output.append(match.groupValues[2])
            if (quote != null) output.append(quote)
            output.append("[REDACTED]")

            var value = match.range.last + 1
            if (quote != null) {
                value = consumeQuoted(input, value, quote)
                if (value < input.length && input[value] == quote) {
                    value++
                    output.append(quote)
                }
            } else {
                while (value < input.length) {
                    val ch = input[value]
                    if (ch.isWhitespace() || ch in ",;&}") break
                    value++
                }
            }
            cursor = value
        }
        output.append(input, cursor, input.length)
        return output.toString()
    }

    /**
     * 响应体无法可靠地按最小括号匹配；一旦识别到 body 字段，就整段消费：
     * 结构完整时消费到配对的闭括号/闭引号，截断或解析失败时消费到文本末尾。
     */
    private fun redactHttpBodies(input: String): String {
        val output = StringBuilder(input.length)
        var cursor = 0
        while (true) {
            val match = httpBodyHeader.find(input, cursor) ?: break
            output.append(input, cursor, match.range.first)
            output.append(match.groupValues[2]).append("=[REDACTED_BODY]")
            cursor = consumeBody(input, match.range.last + 1)
        }
        output.append(input, cursor, input.length)
        return output.toString()
    }

    private fun consumeBody(input: String, start: Int): Int {
        var index = start
        while (index < input.length && input[index].isWhitespace()) index++
        if (index >= input.length) return index
        return when (input[index]) {
            '{', '[' -> consumeBalanced(input, index)
            '"', '\'' -> consumeQuoted(input, index + 1, input[index]).let { end ->
                if (end < input.length && input[end] == input[index]) end + 1 else end
            }
            else -> {
                while (index < input.length && input[index] != '\n') index++
                index
            }
        }
    }

    private fun consumeBalanced(input: String, start: Int): Int {
        // R02：用类型栈校验 {} / [] 配对；错配或未闭合一律消费到末尾（保守）。
        val closing = java.util.ArrayDeque<Char>()
        var index = start
        var quote: Char? = null
        while (index < input.length) {
            val ch = input[index]
            if (quote != null) {
                if (ch == '\\') {
                    index += 2
                    continue
                }
                if (ch == quote) quote = null
            } else {
                when (ch) {
                    '{' -> closing.addLast('}')
                    '[' -> closing.addLast(']')
                    '}', ']' -> {
                        if (closing.isEmpty() || closing.removeLast() != ch) return input.length
                        if (closing.isEmpty()) return index + 1
                    }
                    '"', '\'' -> quote = ch
                }
            }
            index++
        }
        return input.length
    }

    private fun consumeQuoted(input: String, start: Int, quote: Char): Int {
        var index = start
        while (index < input.length) {
            val ch = input[index]
            if (ch == '\\') {
                index += 2
                continue
            }
            if (ch == quote) return index
            index++
        }
        return input.length
    }
}
