package com.yuhinhsiung.clothesmate.core.update

/**
 * 下载失败原因 → 用户可读文案。
 *
 * [ApkDownloader] 的 reason 混有 HTTP 状态码、OkHttp/HTTP2 异常串和内部校验标记，
 * 这里只保留用户能行动的信息；原始串由 [UpdateManager] 写诊断日志，不直接上屏。
 */
fun downloadFailureText(reason: String): String = when {
    reason.contains("timeout", ignoreCase = true) -> "下载超时，请检查网络后重试"
    reason.startsWith("HTTP ") -> "下载服务器返回错误（$reason）"
    reason.contains("sha256 mismatch", ignoreCase = true) -> "安装包校验未通过"
    reason.contains("size mismatch", ignoreCase = true) -> "安装包大小不符"
    reason.contains("redirect without location", ignoreCase = true) -> "下载跳转异常，请改用浏览器下载"
    reason.contains("empty body", ignoreCase = true) -> "下载内容为空，请稍后重试"
    reason.contains("unreachable", ignoreCase = true) -> "无法连接下载服务器"
    else -> "下载中断，请重试"
}
