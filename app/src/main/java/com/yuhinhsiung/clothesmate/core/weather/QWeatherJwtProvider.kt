package com.yuhinhsiung.clothesmate.core.weather

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.i2p.crypto.eddsa.EdDSAEngine
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import net.i2p.crypto.eddsa.EdDSAPrivateKey
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Clock
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

class QWeatherJwtException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * 和风天气 JWT 生成器（Ed25519 / EdDSA）。
 *
 * 按官方要求：
 * - Header 只有 `alg=EdDSA` 与 `kid`；不加 `typ`；
 * - Payload 只有 `iss`（开发者 ID）、`sub`（项目 ID）、`iat`（当前时间 -30s）、`exp`；
 * - 有效期 900 秒，最长不超过 24 小时；
 * - Base64URL 无 padding；签名使用 Ed25519。
 *
 * Token 在内存中缓存到过期前 60 秒，避免每次刷新天气都重新签名。
 */
@Singleton
class QWeatherJwtProvider @Inject constructor() {

    private val json = Json { encodeDefaults = false }
    private val cache = ConcurrentHashMap<CacheKey, CachedToken>()
    private var clock: Clock = Clock.systemUTC()

    internal constructor(clock: Clock) : this() {
        this.clock = clock
    }

    /**
     * 生成（或复用）JWT。私钥格式错误抛 [QWeatherJwtException]，调用方应在设置页
     * 保存前先调用 [validatePrivateKey] 给出可读提示。
     */
    fun token(config: WeatherAuthConfig, nowEpochSeconds: Long = clock.instant().epochSecond): String {
        val key = CacheKey(
            apiHost = config.apiHost,
            developerId = config.developerId,
            projectId = config.projectId,
            keyId = config.keyId,
            privateKeyHash = config.privateKeyPem.hashCode(),
        )
        val cached = cache[key]
        if (cached != null && cached.expiresAt - 60 > nowEpochSeconds) return cached.value

        val token = build(config, nowEpochSeconds)
        cache[key] = CachedToken(token, nowEpochSeconds + TOKEN_TTL_SECONDS)
        return token
    }

    /** 只做解析和一次签名校验，不泄露私钥内容；供设置页保存前校验。 */
    fun validatePrivateKey(pem: String): Boolean = runCatching {
        sign("validate".toByteArray(Charsets.UTF_8), parsePrivateKey(pem))
        true
    }.getOrDefault(false)

    internal fun build(config: WeatherAuthConfig, nowEpochSeconds: Long): String {
        if (!config.isConfigured) throw QWeatherJwtException("和风天气认证信息不完整")
        val iat = nowEpochSeconds - 30
        val exp = iat + TOKEN_TTL_SECONDS
        val header = json.encodeToString(JwtHeader(alg = "EdDSA", kid = config.keyId))
        val payload = json.encodeToString(
            JwtPayload(
                iss = config.developerId,
                sub = config.projectId,
                iat = iat,
                exp = exp,
            ),
        )
        val encodedHeader = base64Url(header.toByteArray(Charsets.UTF_8))
        val encodedPayload = base64Url(payload.toByteArray(Charsets.UTF_8))
        val signingInput = "$encodedHeader.$encodedPayload"
        val signature = runCatching {
            sign(signingInput.toByteArray(Charsets.UTF_8), parsePrivateKey(config.privateKeyPem))
        }.getOrElse { error ->
            throw QWeatherJwtException("和风天气私钥无法解析或签名失败", error)
        }
        return "$signingInput.${base64Url(signature)}"
    }

    private fun sign(data: ByteArray, privateKey: PrivateKey): ByteArray {
        val spec = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519)
        val engine = EdDSAEngine(MessageDigest.getInstance(spec.hashAlgorithm))
        engine.initSign(privateKey)
        engine.update(data)
        return engine.sign()
    }

    private fun parsePrivateKey(pem: String): PrivateKey {
        val stripped = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")
        if (stripped.isEmpty()) throw QWeatherJwtException("私钥为空")
        val bytes = Base64.getDecoder().decode(stripped)
        return EdDSAPrivateKey(PKCS8EncodedKeySpec(bytes))
    }

    private fun base64Url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    @Serializable
    private data class JwtHeader(val alg: String, val kid: String)

    @Serializable
    private data class JwtPayload(val iss: String, val sub: String, val iat: Long, val exp: Long)

    private data class CacheKey(
        val apiHost: String,
        val developerId: String,
        val projectId: String,
        val keyId: String,
        val privateKeyHash: Int,
    )

    private data class CachedToken(val value: String, val expiresAt: Long)

    private companion object {
        const val TOKEN_TTL_SECONDS = 900L
    }
}
