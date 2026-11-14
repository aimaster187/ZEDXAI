package com.titan.zedxai.ai

enum class ProviderType(
    val id: String,
    val displayName: String,
    val defaultBaseUrl: String,
    val requiresApiKey: Boolean,
    val isPaidThirdParty: Boolean,
    val supportsVision: Boolean,
    val supportsTools: Boolean,
    val isLocalEndpoint: Boolean = false
) {
    GEMINI_AI_LOGIC(
        id = "gemini",
        displayName = "Gemini (Firebase AI Logic)",
        defaultBaseUrl = "firebase://ai-logic",
        requiresApiKey = false,
        isPaidThirdParty = false,
        supportsVision = true,
        supportsTools = true
    ),
    OPENAI(
        id = "openai",
        displayName = "OpenAI",
        defaultBaseUrl = "https://api.openai.com/v1",
        requiresApiKey = true,
        isPaidThirdParty = true,
        supportsVision = true,
        supportsTools = true
    ),
    OPENROUTER(
        id = "openrouter",
        displayName = "OpenRouter",
        defaultBaseUrl = "https://openrouter.ai/api/v1",
        requiresApiKey = true,
        isPaidThirdParty = true,
        supportsVision = true,
        supportsTools = true
    ),
    CLAUDE(
        id = "claude",
        displayName = "Anthropic Claude",
        defaultBaseUrl = "https://api.anthropic.com/v1",
        requiresApiKey = true,
        isPaidThirdParty = true,
        supportsVision = true,
        supportsTools = true
    ),
    GROQ(
        id = "groq",
        displayName = "Groq",
        defaultBaseUrl = "https://api.groq.com/openai/v1",
        requiresApiKey = true,
        isPaidThirdParty = true,
        supportsVision = false,
        supportsTools = true
    ),
    DEEPSEEK(
        id = "deepseek",
        displayName = "DeepSeek",
        defaultBaseUrl = "https://api.deepseek.com/v1",
        requiresApiKey = true,
        isPaidThirdParty = true,
        supportsVision = false,
        supportsTools = true
    ),
    MISTRAL(
        id = "mistral",
        displayName = "Mistral AI",
        defaultBaseUrl = "https://api.mistral.ai/v1",
        requiresApiKey = true,
        isPaidThirdParty = true,
        supportsVision = true,
        supportsTools = true
    ),
    TOGETHER(
        id = "together",
        displayName = "Together AI",
        defaultBaseUrl = "https://api.together.xyz/v1",
        requiresApiKey = true,
        isPaidThirdParty = true,
        supportsVision = false,
        supportsTools = true
    ),
    FIREWORKS(
        id = "fireworks",
        displayName = "Fireworks AI",
        defaultBaseUrl = "https://api.fireworks.ai/inference/v1",
        requiresApiKey = true,
        isPaidThirdParty = true,
        supportsVision = false,
        supportsTools = true
    ),
    OLLAMA(
        id = "ollama",
        displayName = "Ollama (Local)",
        defaultBaseUrl = "http://127.0.0.1:11434/v1",
        requiresApiKey = false,
        isPaidThirdParty = false,
        supportsVision = true,
        supportsTools = false,
        isLocalEndpoint = true
    ),
    LM_STUDIO(
        id = "lm_studio",
        displayName = "LM Studio (Local)",
        defaultBaseUrl = "http://127.0.0.1:1234/v1",
        requiresApiKey = false,
        isPaidThirdParty = false,
        supportsVision = false,
        supportsTools = false,
        isLocalEndpoint = true
    ),
    CUSTOM_OPENAI(
        id = "custom_openai",
        displayName = "Custom OpenAI-Compatible",
        defaultBaseUrl = "https://api.example.com/v1",
        requiresApiKey = true,
        isPaidThirdParty = true,
        supportsVision = false,
        supportsTools = false
    );

    companion object {
        fun fromId(id: String): ProviderType =
            entries.firstOrNull { it.id == id } ?: GEMINI_AI_LOGIC
    }
}

data class AiModelSpec(
    val id: String,
    val displayName: String,
    val providerId: String,
    val contextWindow: String,
    val supportsVision: Boolean,
    val supportsTools: Boolean,
    val estimatedCostPer1kTokensUsd: Double,
    val bestFor: String
)

enum class TaskIntent(val label: String) {
    GENERAL_CHAT("General Chat"),
    CODING("Coding & Debug"),
    RESEARCH("Deep Research"),
    VISION("Vision & OCR"),
    CREATIVE("Creative & Web"),
    AUTOMATION("Automation")
}

object ProviderCatalog {
    val models: List<AiModelSpec> = listOf(
        // Gemini via Firebase AI Logic
        AiModelSpec("gemini-3.8-flash", "Gemini 3.8 Flash", "gemini", "1M", true, true, 0.0, "Default multimodal & fast reasoning"),
        AiModelSpec("gemini-3.5-flash-lite", "Gemini 3.5 Flash-Lite", "gemini", "1M", true, true, 0.0, "Ultra-fast lightweight tasks"),
        AiModelSpec("gemini-4-argon", "Gemini 4 Argon", "gemini", "2M", true, true, 0.002, "Complex STEM & architecture"),
        AiModelSpec("gemini-nano-banana-2.1", "Gemini Nano Banana 2.1", "gemini", "32K", true, false, 0.004, "Image generation & editing"),

        // OpenAI
        AiModelSpec("gpt-4.1", "GPT-4.1", "openai", "1M", true, true, 0.005, "Coding & instruction precision"),
        AiModelSpec("gpt-4o", "GPT-4o", "openai", "128K", true, true, 0.005, "Multimodal & voice"),
        AiModelSpec("o4-mini", "o4-mini", "openai", "200K", true, true, 0.003, "Fast reasoning & math"),

        // OpenRouter
        AiModelSpec("anthropic/claude-sonnet-4", "Claude Sonnet 4 (OR)", "openrouter", "200K", true, true, 0.006, "Coding & synthesis"),
        AiModelSpec("openai/gpt-4o", "GPT-4o (OR)", "openrouter", "128K", true, true, 0.005, "General intelligence"),
        AiModelSpec("deepseek/deepseek-r1", "DeepSeek R1 (OR)", "openrouter", "128K", false, false, 0.001, "Chain-of-thought reasoning"),

        // Anthropic Claude
        AiModelSpec("claude-sonnet-4-20250514", "Claude Sonnet 4", "claude", "200K", true, true, 0.006, "Engineering & prose"),
        AiModelSpec("claude-3-5-haiku-latest", "Claude 3.5 Haiku", "claude", "200K", true, true, 0.001, "Fast summarization"),

        // Groq
        AiModelSpec("llama-3.3-70b-versatile", "Llama 3.3 70B", "groq", "128K", false, true, 0.0008, "Ultra-low-latency chat"),
        AiModelSpec("mixtral-8x7b-32768", "Mixtral 8x7B", "groq", "32K", false, true, 0.0005, "Fast structured tasks"),

        // DeepSeek
        AiModelSpec("deepseek-chat", "DeepSeek V3 Chat", "deepseek", "64K", false, true, 0.0004, "Cost-efficient coding"),
        AiModelSpec("deepseek-reasoner", "DeepSeek R1 Reasoner", "deepseek", "64K", false, false, 0.001, "Math & logic"),

        // Mistral
        AiModelSpec("mistral-large-latest", "Mistral Large", "mistral", "128K", true, true, 0.003, "Multilingual & enterprise"),
        AiModelSpec("codestral-latest", "Codestral", "mistral", "256K", false, true, 0.001, "Code completion"),

        // Together
        AiModelSpec("meta-llama/Llama-3.3-70B-Instruct-Turbo", "Llama 3.3 70B Turbo", "together", "128K", false, true, 0.0009, "Open weights inference"),

        // Fireworks
        AiModelSpec("accounts/fireworks/models/llama-v3p3-70b-instruct", "Llama 3.3 70B FW", "fireworks", "128K", false, true, 0.0009, "Fast throughput"),

        // Local Ollama & LM Studio
        AiModelSpec("llama3.2", "Llama 3.2 (Ollama)", "ollama", "128K", false, false, 0.0, "Offline local execution"),
        AiModelSpec("qwen2.5-coder", "Qwen 2.5 Coder (Ollama)", "ollama", "32K", false, false, 0.0, "Offline local coding"),
        AiModelSpec("local-model", "Loaded Local Model", "lm_studio", "32K", false, false, 0.0, "LM Studio active model"),

        // Custom OpenAI-compatible
        AiModelSpec("custom-default", "Custom Model ID", "custom_openai", "128K", false, false, 0.002, "User-defined endpoint")
    )

    fun modelsForProvider(providerId: String): List<AiModelSpec> =
        models.filter { it.providerId == providerId }

    fun findModel(modelId: String): AiModelSpec? =
        models.firstOrNull { it.id == modelId }
}

object SmartTaskRouter {
    fun detectTaskIntent(prompt: String, hasImage: Boolean): TaskIntent {
        if (hasImage) return TaskIntent.VISION
        val lower = prompt.lowercase()
        return when {
            listOf("code", "kotlin", "python", "html", "css", "bug", "stacktrace", "compile", "function", "sql").any { it in lower } ->
                TaskIntent.CODING
            listOf("research", "compare", "cite", "analyze", "study", "paper", "deep dive").any { it in lower } ->
                TaskIntent.RESEARCH
            listOf("automate", "workflow", "trigger", "schedule", "remind", "email", "whatsapp").any { it in lower } ->
                TaskIntent.AUTOMATION
            listOf("story", "poem", "script", "design", "ui", "website", "brand").any { it in lower } ->
                TaskIntent.CREATIVE
            else -> TaskIntent.GENERAL_CHAT
        }
    }

    /**
     * Recommends a model within the user's selected provider (never silently switching to a paid third-party provider).
     */
    fun routeWithinProvider(
        selectedProviderId: String,
        selectedModelId: String,
        intent: TaskIntent,
        smartRoutingEnabled: Boolean
    ): String {
        if (!smartRoutingEnabled) return selectedModelId
        if (selectedProviderId == ProviderType.GEMINI_AI_LOGIC.id) {
            return when (intent) {
                TaskIntent.VISION, TaskIntent.GENERAL_CHAT, TaskIntent.AUTOMATION, TaskIntent.CREATIVE -> "gemini-3.8-flash"
                TaskIntent.CODING, TaskIntent.RESEARCH ->
                    if (selectedModelId == "gemini-4-argon") "gemini-4-argon" else "gemini-3.8-flash"
            }
        }
        return selectedModelId
    }
}
