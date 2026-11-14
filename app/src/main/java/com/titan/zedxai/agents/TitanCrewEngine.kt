package com.titan.zedxai.agents

import com.titan.zedxai.ai.AiRequestConfig
import com.titan.zedxai.ai.AiStreamEvent
import com.titan.zedxai.ai.MultiProviderAiEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.UUID

data class TitanAgent(
    val id: String,
    val name: String,
    val roleTitle: String,
    val instructions: String,
    val capabilities: List<String>,
    val requiresConfirmationForSensitiveOps: Boolean = true,
    val isBuiltIn: Boolean = true
)

enum class AgentRunStatus {
    QUEUED,
    RUNNING,
    AWAITING_CONFIRMATION,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class AgentTaskRun(
    val runId: String,
    val agentId: String,
    val agentName: String,
    val taskPrompt: String,
    val status: AgentRunStatus,
    val progress: Float,
    val output: String = "",
    val logs: List<String> = emptyList(),
    val requiresApproval: Boolean = false
)

object BuiltInTitanCrew {
    val agents: List<TitanAgent> = listOf(
        TitanAgent(
            id = "titan_core",
            name = "TITAN CORE",
            roleTitle = "Strategic Orchestrator",
            instructions = "You are TITAN CORE inside ZEDX AI by The Titan AI. Coordinate multi-step plans, synthesize clear executive answers, and respect user safety and privacy.",
            capabilities = listOf("Planning", "Synthesis", "Memory Context", "Routing")
        ),
        TitanAgent(
            id = "titan_coding",
            name = "CODING",
            roleTitle = "Principal Software Architect",
            instructions = "You are TITAN CODING. Write clean, production-grade Kotlin, Compose, Python, TypeScript, HTML/CSS, and debug stacktraces accurately with fenced code blocks.",
            capabilities = listOf("Code Gen", "Debugging", "Architecture", "Tests")
        ),
        TitanAgent(
            id = "titan_research",
            name = "RESEARCH",
            roleTitle = "Deep Analyst & Fact Verifier",
            instructions = "You are TITAN RESEARCH. Break down complex questions, compare trade-offs rigorously, clearly separate verified facts from assumptions.",
            capabilities = listOf("Analysis", "Comparison", "Summaries", "OCR Q&A")
        ),
        TitanAgent(
            id = "titan_automation",
            name = "AUTOMATION",
            roleTitle = "Workflow & Device Operator",
            instructions = "You are TITAN AUTOMATION. Design Android-safe automation workflows, draft messages/emails/reminders, and ALWAYS request explicit user confirmation before external actions.",
            capabilities = listOf("Workflows", "Intents", "Reminders", "Drafting"),
            requiresConfirmationForSensitiveOps = true
        ),
        TitanAgent(
            id = "titan_creative",
            name = "CREATIVE",
            roleTitle = "Design, Copy & Web Studio",
            instructions = "You are TITAN CREATIVE. Craft original UI concepts, clean HTML/CSS/JS websites, storytelling, and crisp product copy.",
            capabilities = listOf("Web Builder", "Copywriting", "UI Concepts", "Multilingual")
        )
    )
}

class TitanCrewOrchestrator(
    private val aiEngine: MultiProviderAiEngine
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val concurrencySemaphore = Semaphore(MAX_CONCURRENT_AGENTS)
    private val activeJobs = mutableMapOf<String, Job>()

    private val _agents = MutableStateFlow(BuiltInTitanCrew.agents)
    val agents: StateFlow<List<TitanAgent>> = _agents.asStateFlow()

    private val _runs = MutableStateFlow<List<AgentTaskRun>>(emptyList())
    val runs: StateFlow<List<AgentTaskRun>> = _runs.asStateFlow()

    fun addCustomAgent(agent: TitanAgent) {
        _agents.update { current -> current + agent }
    }

    fun deleteCustomAgent(agentId: String) {
        _agents.update { current -> current.filterNot { it.id == agentId && !it.isBuiltIn } }
    }

    fun dispatchTask(
        agent: TitanAgent,
        taskPrompt: String,
        providerId: String,
        modelId: String,
        baseUrl: String,
        apiKey: String?,
        memoryContext: String
    ): String {
        val runId = "run_${UUID.randomUUID().toString().take(8)}"
        val sensitive = agent.requiresConfirmationForSensitiveOps && containsSensitiveAction(taskPrompt)

        val initial = AgentTaskRun(
            runId = runId,
            agentId = agent.id,
            agentName = agent.name,
            taskPrompt = taskPrompt,
            status = if (sensitive) AgentRunStatus.AWAITING_CONFIRMATION else AgentRunStatus.QUEUED,
            progress = if (sensitive) 0.1f else 0.05f,
            logs = listOf(
                if (sensitive) "Paused: explicit user confirmation required for sensitive operation."
                else "Queued on Titan Crew scheduler (max $MAX_CONCURRENT_AGENTS concurrent)."
            ),
            requiresApproval = sensitive
        )
        _runs.update { listOf(initial) + it }

        if (!sensitive) {
            launchRunJob(runId, agent, taskPrompt, providerId, modelId, baseUrl, apiKey, memoryContext)
        }
        return runId
    }

    fun approveSensitiveRun(
        runId: String,
        providerId: String,
        modelId: String,
        baseUrl: String,
        apiKey: String?,
        memoryContext: String
    ) {
        val run = _runs.value.firstOrNull { it.runId == runId } ?: return
        val agent = _agents.value.firstOrNull { it.id == run.agentId } ?: return
        updateRun(runId) {
            it.copy(
                status = AgentRunStatus.QUEUED,
                requiresApproval = false,
                logs = it.logs + "User approved sensitive execution."
            )
        }
        launchRunJob(runId, agent, run.taskPrompt, providerId, modelId, baseUrl, apiKey, memoryContext)
    }

    fun cancelRun(runId: String) {
        activeJobs.remove(runId)?.cancel()
        updateRun(runId) {
            it.copy(
                status = AgentRunStatus.CANCELLED,
                logs = it.logs + "Cancelled by user."
            )
        }
    }

    fun cancelAll() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        _runs.update { list ->
            list.map {
                if (it.status == AgentRunStatus.RUNNING || it.status == AgentRunStatus.QUEUED) {
                    it.copy(status = AgentRunStatus.CANCELLED, logs = it.logs + "Stopped by user.")
                } else it
            }
        }
    }

    private fun launchRunJob(
        runId: String,
        agent: TitanAgent,
        taskPrompt: String,
        providerId: String,
        modelId: String,
        baseUrl: String,
        apiKey: String?,
        memoryContext: String
    ) {
        val job = scope.launch {
            concurrencySemaphore.withPermit {
                updateRun(runId) {
                    it.copy(
                        status = AgentRunStatus.RUNNING,
                        progress = 0.25f,
                        logs = it.logs + "Acquired worker slot. Streaming via $providerId ($modelId)..."
                    )
                }
                val systemPrompt = buildString {
                    append(agent.instructions)
                    if (memoryContext.isNotBlank()) {
                        append("\n\nUser personalization context:\n").append(memoryContext)
                    }
                }
                try {
                    aiEngine.streamCompletion(
                        AiRequestConfig(
                            providerId = providerId,
                            modelId = modelId,
                            baseUrl = baseUrl,
                            apiKey = apiKey,
                            systemPrompt = systemPrompt,
                            history = emptyList(),
                            userPrompt = taskPrompt
                        )
                    ).collect { ev ->
                        when (ev) {
                            is AiStreamEvent.Delta -> {
                                updateRun(runId) {
                                    it.copy(
                                        progress = (0.35f + (ev.accumulated.length / 2000f).coerceAtMost(0.55f)),
                                        output = ev.accumulated
                                    )
                                }
                            }
                            is AiStreamEvent.Completed -> {
                                updateRun(runId) {
                                    it.copy(
                                        status = AgentRunStatus.COMPLETED,
                                        progress = 1f,
                                        output = ev.fullText,
                                        logs = it.logs + "Completed in ${ev.latencyMs}ms (~${ev.tokensUsed} tokens)."
                                    )
                                }
                            }
                            is AiStreamEvent.Error -> {
                                updateRun(runId) {
                                    it.copy(
                                        status = AgentRunStatus.FAILED,
                                        progress = 1f,
                                        output = ev.message,
                                        logs = it.logs + "Error: ${ev.message}"
                                    )
                                }
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    updateRun(runId) {
                        it.copy(status = AgentRunStatus.CANCELLED, logs = it.logs + "Execution cancelled.")
                    }
                    throw e
                }
            }
        }
        activeJobs[runId] = job
    }

    private fun updateRun(runId: String, transform: (AgentTaskRun) -> AgentTaskRun) {
        _runs.update { list -> list.map { if (it.runId == runId) transform(it) else it } }
    }

    companion object {
        const val MAX_CONCURRENT_AGENTS = 5

        fun containsSensitiveAction(prompt: String): Boolean {
            val p = prompt.lowercase()
            return listOf("delete", "send email", "gmail", "whatsapp", "call ", "dial", "transfer", "purchase", "payment")
                .any { it in p }
        }
    }
}
