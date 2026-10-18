package com.yuhinhsiung.clothesmate.core.datastore

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 小段敏感文本的加解密（M2-05，用户 2026-10-18 拍板「Key 还是加密安全一点」）。
 *
 * 抽象成接口有两个原因：
 * 1. DataStore 的读写逻辑可用假实现做 JVM 测试，不必把 Keystore 拖进单测；
 * 2. 将来若要换 EncryptedSharedPreferences / 硬件绑定策略，只改实现。
 */
interface SecretCipher {

    /** 加密；失败抛异常（调用方不得回退到明文落盘） */
    fun encrypt(plainText: String): String

    /** 解密；密文损坏 / Keystore 密钥丢失 / 旧明文数据 → null */
    fun decrypt(cipherText: String): String?
}

/**
 * Android Keystore AES-256-GCM 实现（**不新增第三方依赖**，用框架 API）。
 *
 * - 密钥由 Keystore 生成并保管，应用进程拿不到原始密钥材料；
 * - 每次加密随机 IV（GCM 要求），格式：`[4 字节 IV 长度][IV][密文+Tag]` → Base64；
 * - 不设置用户认证要求：App 启动后无交互即可读 Key（与「打开 App 就能识别」一致）；
 * - 卸载重装 / 备份恢复到新机后 Keystore 密钥不在，解密返回 null，表现为「Key 未配置」，
 *   由用户重新填写——不会崩溃，也不会把失败当明文读出来。
 */
@Singleton
class AndroidKeystoreSecretCipher @Inject constructor() : SecretCipher {

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }

    private fun secretKey(): SecretKey {
        val existing = (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    override fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val payload = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        val packed = ByteBuffer.allocate(Int.SIZE_BYTES + iv.size + payload.size)
            .putInt(iv.size)
            .put(iv)
            .put(payload)
            .array()
        return Base64.encodeToString(packed, Base64.NO_WRAP)
    }

    override fun decrypt(cipherText: String): String? = runCatching {
        val packed = Base64.decode(cipherText, Base64.NO_WRAP)
        val buffer = ByteBuffer.wrap(packed)
        val ivSize = buffer.int
        require(ivSize in 1..MAX_IV_BYTES && buffer.remaining() > ivSize)
        val iv = ByteArray(ivSize).also(buffer::get)
        val payload = ByteArray(buffer.remaining()).also(buffer::get)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        String(cipher.doFinal(payload), StandardCharsets.UTF_8)
    }.getOrNull()

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "clothesmate.ai.api_key.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SIZE_BITS = 256
        const val GCM_TAG_BITS = 128
        const val MAX_IV_BYTES = 32
    }
}

/**
 * 测试 / 预览用的假实现：**不做任何加密**，只在内存里前缀标记。
 *
 * ⚠️ 仅用于单元测试与无 Android 环境的预览；生产绑定必须是
 * [AndroidKeystoreSecretCipher]（见 [UserSettingsModule]）。
 */
class FakeSecretCipher : SecretCipher {
    override fun encrypt(plainText: String): String = PREFIX + plainText.reversed()
    override fun decrypt(cipherText: String): String? =
        cipherText.takeIf { it.startsWith(PREFIX) }
            ?.removePrefix(PREFIX)
            ?.reversed()

    private companion object {
        const val PREFIX = "fake-enc:"
    }
}
