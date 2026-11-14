package com.titan.zedxai

import com.titan.zedxai.agents.AgentRunStatus
import com.titan.zedxai.agents.BuiltInTitanCrew
import com.titan.zedxai.agents.TitanCrewOrchestrator
import com.titan.zedxai.ai.MultiProviderAiEngine
import com.titan.zedxai.voice.TitanVoiceController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceAndAgentStateTest {

    @Test
    fun wakeWordStripping_removesArmedPrefixSafely() {
        assertEquals(
            "open coding studio",
            TitanVoiceController.stripWakeWordIfNeeded("Hey Titan, open coding studio", wakeWordEnabled = true)
        )
        assertEquals(
            "Hey Titan, open coding studio",
            TitanVoiceController.stripWakeWordIfNeeded("Hey Titan, open coding studio", wakeWordEnabled = false)
        )
    }

    @Test
    fun titanCrew_hasFiveBuiltInAgentsAndPausesSensitiveOperations() {
        assertEquals(5, BuiltInTitanCrew.agents.size)
        assertEquals(
            listOf("TITAN CORE", "CODING", "RESEARCH", "AUTOMATION", "CREATIVE"),
            BuiltInTitanCrew.agents.map { it.name }
        )
        assertTrue(TitanCrewOrchestrator.containsSensitiveAction("Send email to manager via Gmail"))
        assertFalse(TitanCrewOrchestrator.containsSensitiveAction("Summarize Jetpack Compose state hoisting"))

        val orchestrator = TitanCrewOrchestrator(MultiProviderAiEngine())
        val automationAgent = BuiltInTitanCrew.agents.first { it.id == "titan_automation" }
        val runId = orchestrator.dispatchTask(
            agent = automationAgent,
            taskPrompt = "Send WhatsApp message to Alice",
            providerId = "gemini",
            modelId = "gemini-3.8-flash",
            baseUrl = "firebase://ai-logic",
            apiKey = null,
            memoryContext = ""
        )
        val pausedRun = orchestrator.runs.value.first { it.runId == runId }
        assertEquals(AgentRunStatus.AWAITING_CONFIRMATION, pausedRun.status)
        assertTrue(pausedRun.requiresApproval)

        orchestrator.cancelRun(runId)
        assertEquals(AgentRunStatus.CANCELLED, orchestrator.runs.value.first { it.runId == runId }.status)
    }
}
