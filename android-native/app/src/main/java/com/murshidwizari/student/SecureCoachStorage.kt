package com.murshidwizari.student

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts graded answers at rest. The key cannot be exported from AndroidKeyStore.
 * Old plaintext notebooks are migrated only after encrypted persistence succeeds.
 * Never falls back to writing plaintext on crypto failures.
 */
internal class SecureCoachStorage(private val prefs: SharedPreferences) {
    private val alias = "murshid_coach_encryption_v1"
    private val encryptedPrefix = "coach_notebook_secure_v1_"
    private val legacyPrefix = "coach_notebook_v1_"

    private fun secret(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setKeySize(256)
                .build())
        }.generateKey()
    }

    private fun encrypt(raw: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secret())
        val encrypted = cipher.doFinal(raw.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(raw: String): String {
        val all = Base64.decode(raw, Base64.NO_WRAP)
        require(all.size > 12 + 16)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secret(), GCMParameterSpec(128, all.copyOfRange(0, 12)))
        return String(cipher.doFinal(all.copyOfRange(12, all.size)), StandardCharsets.UTF_8)
    }

    fun read(userId: Int): String? {
        if (userId <= 0) return null
        val encryptedKey = encryptedPrefix + userId
        val encrypted = prefs.getString(encryptedKey, null)
        if (encrypted != null) return runCatching { decrypt(encrypted) }.getOrNull()
        val legacyKey = legacyPrefix + userId
        val legacy = prefs.getString(legacyKey, null) ?: return null
        // No delete until the encrypted value has been committed.
        return if (write(userId, legacy)) legacy else null
    }

    fun write(userId: Int, raw: String): Boolean {
        if (userId <= 0 || raw.length > 1_500_000) return false
        return runCatching {
            val encrypted = encrypt(raw)
            prefs.edit()
                .putString(encryptedPrefix + userId, encrypted)
                .remove(legacyPrefix + userId)
                .commit()
        }.getOrDefault(false)
    }

    fun delete(userId: Int) {
        if (userId <= 0) return
        prefs.edit().remove(encryptedPrefix + userId).remove(legacyPrefix + userId).apply()
    }
}
