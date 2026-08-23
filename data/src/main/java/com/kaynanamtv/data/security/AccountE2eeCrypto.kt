package com.kaynanamtv.data.security

import android.util.Log
import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Account-derived E2EE AES-256-GCM cipher for multi-device cross-device IPTV credential synchronization.
 *
 * Payload format: enc:v2:<base64(salt + iv + ciphertext)>
 *
 * This allows Device A to encrypt credentials with the user's account key and Device B (TV, second phone)
 * to decrypt the same payload when signed into the same Firebase account.
 */
@Singleton
class AccountE2eeCrypto @Inject constructor() {
    private val TAG = "AccountE2eeCrypto"
    private val TRANSFORMATION = "AES/GCM/NoPadding"
    private val SALT_SIZE_BYTES = 16
    private val IV_SIZE_BYTES = 12
    private val AUTH_TAG_BITS = 128
    private val ITERATIONS = 10_000
    private val KEY_LENGTH_BITS = 256
    private val PREFIX = "enc:v2:"

    fun encryptForAccount(value: String, userUid: String): String {
        if (value.isBlank() || userUid.isBlank() || value.startsWith(PREFIX)) return value

        return try {
            val random = SecureRandom()
            val salt = ByteArray(SALT_SIZE_BYTES).also(random::nextBytes)
            val iv = ByteArray(IV_SIZE_BYTES).also(random::nextBytes)

            val secretKey = deriveKey(userUid, salt)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(AUTH_TAG_BITS, iv))

            val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            val packed = salt + iv + ciphertext
            PREFIX + java.util.Base64.getEncoder().encodeToString(packed)
        } catch (e: Exception) {
            Log.e(TAG, "Account E2EE encryption failed", e)
            throw SecurityException("Failed to encrypt account credential: ${e.message}", e)
        }
    }

    fun decryptForAccount(value: String, userUid: String): String {
        if (!value.startsWith(PREFIX)) return value
        if (userUid.isBlank()) throw IllegalArgumentException("User UID cannot be blank for account decryption")

        return try {
            val payload = value.removePrefix(PREFIX)
            val bytes = java.util.Base64.getDecoder().decode(payload)
            val minSize = SALT_SIZE_BYTES + IV_SIZE_BYTES + 16
            if (bytes.size < minSize) {
                throw IllegalArgumentException("Encrypted account payload is truncated")
            }

            val salt = bytes.copyOfRange(0, SALT_SIZE_BYTES)
            val iv = bytes.copyOfRange(SALT_SIZE_BYTES, SALT_SIZE_BYTES + IV_SIZE_BYTES)
            val ciphertext = bytes.copyOfRange(SALT_SIZE_BYTES + IV_SIZE_BYTES, bytes.size)

            val secretKey = deriveKey(userUid, salt)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(AUTH_TAG_BITS, iv))

            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Account E2EE decryption failed", e)
            throw SecurityException("Failed to decrypt account credential: ${e.message}", e)
        }
    }

    private fun deriveKey(userUid: String, salt: ByteArray): SecretKey {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec: KeySpec = PBEKeySpec(userUid.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, "AES")
    }
}
