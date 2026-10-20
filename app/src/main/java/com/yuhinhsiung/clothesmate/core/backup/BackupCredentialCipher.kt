package com.yuhinhsiung.clothesmate.core.backup

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 备份凭据的可移植加密（M4-05）。
 *
 * Android Keystore 密钥不能随备份迁移，因此这里用**用户口令**派生 AES-256-GCM：
 * PBKDF2-HMAC-SHA256（120k 次，随机 16 字节 salt）+ 12 字节 IV，输出
 * `Base64([1 byte version][16 salt][12 iv][cipher+tag])`。口令不落盘；
 * 解不开时抛 [BackupException]，不会把密文当明文。
 */
object BackupCredentialCipher {

    private const val VERSION = 1
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val MIN_PASSWORD_LENGTH = 6

    fun validatePassword(password: String) {
        if (password.length < MIN_PASSWORD_LENGTH) {
            throw BackupException("备份密码至少 $MIN_PASSWORD_LENGTH 位")
        }
    }

    fun encrypt(plainText: String, password: String): String {
        validatePassword(password)
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val key = derive(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val payload = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val packed = ByteArray(1 + salt.size + iv.size + payload.size)
        packed[0] = VERSION.toByte()
        salt.copyInto(packed, 1)
        iv.copyInto(packed, 1 + salt.size)
        payload.copyInto(packed, 1 + salt.size + iv.size)
        return Base64.getEncoder().encodeToString(packed)
    }

    fun decrypt(cipherText: String, password: String): String {
        val packed = runCatching { Base64.getDecoder().decode(cipherText) }
            .getOrElse { throw BackupException("备份凭据损坏") }
        require(packed.size > 1 + SALT_BYTES + IV_BYTES) { throw BackupException("备份凭据损坏") }
        if (packed[0].toInt() != VERSION) throw BackupException("备份凭据版本不受支持")
        val salt = packed.copyOfRange(1, 1 + SALT_BYTES)
        val iv = packed.copyOfRange(1 + SALT_BYTES, 1 + SALT_BYTES + IV_BYTES)
        val body = packed.copyOfRange(1 + SALT_BYTES + IV_BYTES, packed.size)
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, derive(password, salt), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(body), Charsets.UTF_8)
        }.getOrElse { throw BackupException("备份密码不正确，无法解密凭据") }
    }

    private fun derive(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec)
            .encoded
        spec.clearPassword()
        return SecretKeySpec(bytes, "AES")
    }
}
