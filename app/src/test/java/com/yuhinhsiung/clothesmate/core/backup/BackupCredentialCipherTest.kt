package com.yuhinhsiung.clothesmate.core.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCredentialCipherTest {

    private val payload = """{"aiApiKey":"sk-test","weatherPrivateKeyPem":"-----BEGIN PRIVATE KEY-----\nMIIB\n-----END PRIVATE KEY-----"}"""

    @Test
    fun `roundtrip keeps unicode and private key`() {
        val encrypted = BackupCredentialCipher.encrypt(payload, "备份密码123")
        assertEquals(payload, BackupCredentialCipher.decrypt(encrypted, "备份密码123"))
    }

    @Test
    fun `wrong password fails instead of returning plaintext`() {
        val encrypted = BackupCredentialCipher.encrypt(payload, "secret123")
        val failure = runCatching { BackupCredentialCipher.decrypt(encrypted, "secret124") }.exceptionOrNull()
        assertTrue(failure is BackupException)
    }

    @Test
    fun `tampered ciphertext fails`() {
        val encrypted = BackupCredentialCipher.encrypt(payload, "secret123")
        val tampered = encrypted.dropLast(1) + if (encrypted.last() == 'A') 'B' else 'A'
        val failure = runCatching { BackupCredentialCipher.decrypt(tampered, "secret123") }.exceptionOrNull()
        assertTrue(failure is BackupException)
    }

    @Test
    fun `random salt and iv make ciphertexts differ`() {
        val first = BackupCredentialCipher.encrypt(payload, "secret123")
        val second = BackupCredentialCipher.encrypt(payload, "secret123")
        assertNotEquals(first, second)
    }

    @Test
    fun `short password is rejected`() {
        val failure = runCatching { BackupCredentialCipher.encrypt(payload, "123") }.exceptionOrNull()
        assertTrue(failure is BackupException)
    }
}
