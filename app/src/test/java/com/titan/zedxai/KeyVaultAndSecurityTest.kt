package com.titan.zedxai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.titan.zedxai.security.KeyCipher
import com.titan.zedxai.security.KeyVaultManager
import com.titan.zedxai.security.SecretRedactor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KeyVaultAndSecurityTest {

    private class FakeCipher : KeyCipher {
        override fun encrypt(plainText: String): String = "ENC:${plainText.reversed()}"
        override fun decrypt(cipherPayload: String): String = cipherPayload.removePrefix("ENC:").reversed()
    }

    @Test
    fun keyVault_encryptsMasksAndDeletesApiKey() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vault = KeyVaultManager(context, FakeCipher())

        vault.saveApiKey("uid1_openai", "  sk-test1234567890abcd  ")
        assertEquals("sk-test1234567890abcd", vault.getApiKey("uid1_openai"))
        assertEquals("sk-t••••••••abcd", vault.getMaskedKey("uid1_openai"))

        vault.deleteApiKey("uid1_openai")
        assertNull(vault.getApiKey("uid1_openai"))
        assertEquals("Not configured", vault.getMaskedKey("uid1_openai"))
    }

    @Test
    fun secretRedactor_detectsAndRedactsCredentials() {
        val leak = "Error calling OpenAI with sk-live12345678901234567890 and Bearer abcdefghijklmnopqrstuv"
        assertTrue(SecretRedactor.containsSensitiveSecret(leak))
        val redacted = SecretRedactor.redact(leak)
        assertFalse(redacted.contains("sk-live12345678901234567890"))
        assertFalse(redacted.contains("abcdefghijklmnopqrstuv"))
        assertTrue(redacted.contains("[REDACTED_SECRET]"))
    }

    @Test
    fun secretRedactor_allowsNormalUserPreferencesInMemory() {
        val benign = "Prefer concise Kotlin Compose code with Material 3 tokens and Hinglish voice replies."
        assertFalse(SecretRedactor.containsSensitiveSecret(benign))
        assertEquals(benign, SecretRedactor.redact(benign))
    }
}
