package com.yuhinhsiung.clothesmate.core.weather

/**
 * 和风天气 JWT 认证配置（M3 修订）。
 *
 * 2026 年起和风天气的公共 API Host 逐步停止服务，且 JWT 是官方推荐认证方式；
 * 用户需要在控制台创建 Ed25519 凭据并填写：
 * - API Host：控制台「设置」中分配的独立域名（不含 https://）
 * - 开发者 ID：控制台「设置」中的 `iss`（Q 开头）
 * - 项目 ID：项目详情中的 `sub`
 * - 凭据 ID：添加 JWT 凭据后显示的 `kid`
 * - 私钥：控制台只保存公钥，本地保存 PKCS#8 PEM 私钥
 *
 * 私钥只在本机使用，设置页通过 [com.yuhinhsiung.clothesmate.core.datastore.SecretCipher]
 * 加密后落 DataStore；不会进入日志、缓存或任何自有服务器。
 */
data class WeatherAuthConfig(
    val apiHost: String = "",
    val developerId: String = "",
    val projectId: String = "",
    val keyId: String = "",
    val privateKeyPem: String = "",
) {
    val isConfigured: Boolean
        get() = apiHost.isNotBlank() &&
            developerId.isNotBlank() &&
            projectId.isNotBlank() &&
            keyId.isNotBlank() &&
            privateKeyPem.isNotBlank()
}

/** 把用户粘贴的 API Host 统一成主机名；允许误粘贴 https:// 前缀和末尾斜杠。 */
fun normalizeWeatherApiHost(raw: String): String? {
    val value = raw.trim()
        .removePrefix("https://")
        .removePrefix("http://")
        .trimEnd('/')
    if (value.isEmpty()) return null
    if (value.any { it.isWhitespace() }) return null
    if (value.contains('/')) return null
    if (!value.contains('.')) return null
    return value
}
