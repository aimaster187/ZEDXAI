package com.titan.zedxai.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.vaultDataStore: DataStore<Preferences> by preferencesDataStore(name = "zedx_key_vault")

interface KeyCipher {
    fun encrypt(plainText: String): String
    fun decrypt(cipherPayload: String): String
}

/**
 * Hardware-backed AndroidKeyStore AES-256-GCM cipher for user API keys.
 */
class AndroidKeyStoreCipher(
    private val keyAlias: String = "zedx_ai_vault_master_key_v1"
) : KeyCipher {

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    override fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        val dataB64 = Base64.encodeToString(encrypted, Base64.NO_WRAP)
        return "$ivB64:$dataB64"
    }

    override fun decrypt(cipherPayload: String): String {
        val parts = cipherPayload.split(":")
        require(parts.size == 2) { "Malformed encrypted key payload" }
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val cipherBytes = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(128, iv))
        val plainBytes = cipher.doFinal(cipherBytes)
        return String(plainBytes, StandardCharsets.UTF_8)
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

class KeyVaultManager(
    private val context: Context,
    private val cipher: KeyCipher = AndroidKeyStoreCipher()
) {
    suspend fun saveApiKey(profileId: String, rawKey: String) {
        val trimmed = rawKey.trim()
        require(trimmed.isNotEmpty()) { "API key cannot be blank" }
        val encrypted = cipher.encrypt(trimmed)
        val prefKey = stringPreferencesKey("key_$profileId")
        context.vaultDataStore.edit { prefs ->
            prefs[prefKey] = encrypted
        }
    }

    suspend fun getApiKey(profileId: String): String? {
        val prefKey = stringPreferencesKey("key_$profileId")
        val payload = context.vaultDataStore.data.first()[prefKey] ?: return null
        return runCatching { cipher.decrypt(payload) }.getOrNull()
    }

    suspend fun getMaskedKey(profileId: String): String {
        val raw = getApiKey(profileId) ?: return "Not configured"
        return maskSecret(raw)
    }

    suspend fun deleteApiKey(profileId: String) {
        val prefKey = stringPreferencesKey("key_$profileId")
        context.vaultDataStore.edit { prefs ->
            prefs.remove(prefKey)
        }
    }

    suspend fun clearAllKeys() {
        context.vaultDataStore.edit { it.clear() }
    }

    companion object {
        fun maskSecret(secret: String): String {
            val s = secret.trim()
            if (s.length <= 8) return "••••" + s.takeLast(2)
            return "${s.take(4)}••••••••${s.takeLast(4)}"
        }
    }
}

object SecretRedactor {
    private val secretPatterns = listOf(
        Regex("sk-[A-Za-z0-9_\\-]{16,}"),
        Regex("AIza[0-9A-Za-z_\\-]{20,}"),
        Regex("ghp_[A-Za-z0-9]{20,}"),
        Regex("xox[baprs]-[A-Za-z0-9\\-]{10,}"),
        Regex("Bearer\\s+[A-Za-z0-9._\\-]{16,}", RegexOption.IGNORE_CASE),
        Regex("(password|passwd|api[_-]?key|secret[_-]?key|access[_-]?token)\\s*[:=]\\s*\\S+", RegexOption.IGNORE_CASE)
    )

    fun redact(input: String): String {
        var out = input
        secretPatterns.forEach { regex ->
            out = out.replace(regex) { "[REDACTED_SECRET]" }
        }
        return out
    }

    fun containsSensitiveSecret(input: String): Boolean {
        return secretPatterns.any { it.containsMatchIn(input) }
    }
}
