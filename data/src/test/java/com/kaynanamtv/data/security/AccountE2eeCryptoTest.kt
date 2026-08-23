package com.kaynanamtv.data.security

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AccountE2eeCryptoTest {

    private val crypto = AccountE2eeCrypto()
    private val uid = "test_user_uid_12345"
    private val otherUid = "other_user_uid_99999"

    @Test
    fun `encrypt and decrypt with same account uid succeeds`() {
        val original = "mySecretXtreamPassword123"
        val encrypted = crypto.encryptForAccount(original, uid)

        assertThat(encrypted).startsWith("enc:v2:")
        assertThat(encrypted).isNotEqualTo(original)

        val decrypted = crypto.decryptForAccount(encrypted, uid)
        assertThat(decrypted).isEqualTo(original)
    }

    @Test(expected = Exception::class)
    fun `decrypt with different account uid fails`() {
        val original = "mySecretXtreamPassword123"
        val encrypted = crypto.encryptForAccount(original, uid)

        crypto.decryptForAccount(encrypted, otherUid)
    }

    @Test
    fun `plain string is returned unchanged if not prefixed`() {
        val plain = "plaintext_password"
        assertThat(crypto.decryptForAccount(plain, uid)).isEqualTo(plain)
    }
}
