package com.titan.zedxai

import com.titan.zedxai.ai.MultiProviderAiEngine
import com.titan.zedxai.ai.ProviderCatalog
import com.titan.zedxai.ai.ProviderType
import com.titan.zedxai.ai.SmartTaskRouter
import com.titan.zedxai.ai.TaskIntent
import com.titan.zedxai.tools.WorkspaceToolsEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AiRouterAndToolsTest {

    @Test
    fun smartTaskRouter_neverSilentlySwitchesAwayFromSelectedProvider() {
        val openAiCoding = SmartTaskRouter.routeWithinProvider(
            selectedProviderId = ProviderType.OPENAI.id,
            selectedModelId = "gpt-4.1",
            intent = TaskIntent.CODING,
            smartRoutingEnabled = true
        )
        assertEquals("gpt-4.1", openAiCoding)

        val geminiVision = SmartTaskRouter.routeWithinProvider(
            selectedProviderId = ProviderType.GEMINI_AI_LOGIC.id,
            selectedModelId = "gemini-4-argon",
            intent = TaskIntent.VISION,
            smartRoutingEnabled = true
        )
        assertEquals("gemini-3.8-flash", geminiVision)
    }

    @Test
    fun endpointValidation_requiresHttpsRemotelyAndAllowsLoopbackHttpLocally() {
        assertNull(MultiProviderAiEngine.validateEndpointUrl("https://api.openai.com/v1", allowLocalHttp = false))
        assertNotNull(MultiProviderAiEngine.validateEndpointUrl("http://api.openai.com/v1", allowLocalHttp = false))
        assertNull(MultiProviderAiEngine.validateEndpointUrl("http://127.0.0.1:11434/v1", allowLocalHttp = true))
        assertNotNull(MultiProviderAiEngine.validateEndpointUrl("http://127.0.0.1:11434/v1", allowLocalHttp = false))
    }

    @Test
    fun providerCatalog_coversAllMajorProvidersAndDefaultGemini() {
        val providers = ProviderCatalog.models.map { it.providerId }.toSet()
        assertTrue(providers.containsAll(listOf("gemini", "openai", "openrouter", "claude", "groq", "deepseek", "mistral", "together", "fireworks", "ollama", "lm_studio", "custom_openai")))
        assertNotNull(ProviderCatalog.findModel("gemini-3.8-flash"))
    }

    @Test
    fun browserAndTerminal_enforceSecurityAndSandbox() {
        assertFalse(WorkspaceToolsEngine.isSafeBrowserUrl("javascript:alert(1)").first)
        assertFalse(WorkspaceToolsEngine.isSafeBrowserUrl("file:///etc/passwd").first)
        assertTrue(WorkspaceToolsEngine.isSafeBrowserUrl("https://developer.android.com").first)
        assertEquals("https://kotlinlang.org", WorkspaceToolsEngine.normalizeBrowserUrl("kotlinlang.org"))

        val (lsOut, lsAi) = WorkspaceToolsEngine.evaluateSafeTerminalCommand(
            rawCommand = "ls",
            virtualFiles = mapOf("Main.kt" to "fun main() {}"),
            userEmail = "user@example.com",
            activeProvider = "gemini",
            activeModel = "gemini-3.8-flash"
        )
        assertFalse(lsAi)
        assertTrue(lsOut.contains("Main.kt"))

        val (rmOut, rmAi) = WorkspaceToolsEngine.evaluateSafeTerminalCommand(
            rawCommand = "rm -rf /",
            virtualFiles = emptyMap(),
            userEmail = "user@example.com",
            activeProvider = "gemini",
            activeModel = "gemini-3.8-flash"
        )
        assertFalse(rmAi)
        assertTrue(rmOut.contains("restricted", ignoreCase = true))
    }

    @Test
    fun androidProjectBuilder_createsCompleteZipBundle() {
        val files = WorkspaceToolsEngine.buildAndroidProjectBundle(
            appName = "CrystalNotes",
            packageName = "com.titan.crystalnotes",
            mainComposeCode = "class MainActivity"
        )
        assertTrue(files.containsKey("app/build.gradle.kts"))
        assertTrue(files.containsKey("app/src/main/AndroidManifest.xml"))
        assertTrue(files.containsKey("app/src/main/java/com/titan/crystalnotes/MainActivity.kt"))
        val zipBytes = WorkspaceToolsEngine.packageZipBytes(files)
        assertTrue(zipBytes.size > 100)
    }
}
