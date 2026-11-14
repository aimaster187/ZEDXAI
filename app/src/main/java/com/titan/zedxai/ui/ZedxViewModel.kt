package com.titan.zedxai.ui

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestoreException
import com.titan.zedxai.agents.TitanAgent
import com.titan.zedxai.agents.TitanCrewOrchestrator
import com.titan.zedxai.ai.AiRequestConfig
import com.titan.zedxai.ai.AiStreamEvent
import com.titan.zedxai.ai.MultiProviderAiEngine
import com.titan.zedxai.ai.ProviderCatalog
import com.titan.zedxai.ai.ProviderType
import com.titan.zedxai.ai.SmartTaskRouter
import com.titan.zedxai.data.local.AppPreferencesRepository
import com.titan.zedxai.data.local.AppPreferencesState
import com.titan.zedxai.data.local.AutomationEntity
import com.titan.zedxai.data.local.BrowserBookmarkEntity
import com.titan.zedxai.data.local.BrowserHistoryEntity
import com.titan.zedxai.data.local.CodeProjectEntity
import com.titan.zedxai.data.local.ConversationEntity
import com.titan.zedxai.data.local.LocalMemoryEntity
import com.titan.zedxai.data.local.MessageEntity
import com.titan.zedxai.data.local.NotificationItemEntity
import com.titan.zedxai.data.local.ProviderProfileEntity
import com.titan.zedxai.data.local.UsageLogEntity
import com.titan.zedxai.data.local.ZedxDatabase
import com.titan.zedxai.firebase.SystemConfigDoc
import com.titan.zedxai.firebase.UserProfileDoc
import com.titan.zedxai.firebase.ZedxFirestoreRepository
import com.titan.zedxai.security.KeyVaultManager
import com.titan.zedxai.security.SecretRedactor
import com.titan.zedxai.tools.WorkspaceToolsEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class BrowserTabState(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Android Developers",
    val url: String = "https://developer.android.com"
)

data class TerminalLine(
    val text: String,
    val isCommand: Boolean = false
)

data class ChatUiState(
    val activeConversationId: String? = null,
    val isStreaming: Boolean = false,
    val streamingText: String = "",
    val lastError: String? = null,
    val searchQuery: String = "",
    val pendingAttachmentSummary: String? = null
)

class ZedxViewModel(
    private val db: ZedxDatabase,
    private val firestoreRepo: ZedxFirestoreRepository,
    private val prefsRepo: AppPreferencesRepository,
    private val keyVault: KeyVaultManager,
    private val aiEngine: MultiProviderAiEngine,
    val crewOrchestrator: TitanCrewOrchestrator,
    val currentUserId: String,
    val userEmail: String,
    val userName: String,
    val photoUrl: String?
) : ViewModel() {

    val preferences: StateFlow<AppPreferencesState> = prefsRepo.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AppPreferencesState())

    val userProfile: StateFlow<UserProfileDoc?> = firestoreRepo.observeUserProfile(currentUserId)
        .catch { e ->
            val fs = generateSequence(e) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (fs == null && e is CancellationException) throw e
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val systemConfig: StateFlow<SystemConfigDoc?> = firestoreRepo.observeSystemConfig()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val conversations: StateFlow<List<ConversationEntity>> = db.conversationDao()
        .observeConversations(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val providerProfiles: StateFlow<List<ProviderProfileEntity>> = db.providerProfileDao()
        .observeProfiles(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val memories: StateFlow<List<LocalMemoryEntity>> = db.localMemoryDao()
        .observeMemories(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val usageLogs: StateFlow<List<UsageLogEntity>> = db.usageLogDao()
        .observeUsageLogs(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val bookmarks: StateFlow<List<BrowserBookmarkEntity>> = db.browserDao()
        .observeBookmarks(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val browserHistory: StateFlow<List<BrowserHistoryEntity>> = db.browserDao()
        .observeHistory(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val codeProjects: StateFlow<List<CodeProjectEntity>> = db.codeProjectDao()
        .observeProjects(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val automations: StateFlow<List<AutomationEntity>> = db.automationDao()
        .observeAutomations(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val notifications: StateFlow<List<NotificationItemEntity>> = db.notificationDao()
        .observeNotifications(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _chatState = MutableStateFlow(ChatUiState())
    val chatState: StateFlow<ChatUiState> = _chatState.asStateFlow()

    private val _activeMessages = MutableStateFlow<List<MessageEntity>>(emptyList())
    val activeMessages: StateFlow<List<MessageEntity>> = _activeMessages.asStateFlow()

    private val _isTrustedAdmin = MutableStateFlow(false)
    val isTrustedAdmin: StateFlow<Boolean> = _isTrustedAdmin.asStateFlow()

    private val _maskedKeys = MutableStateFlow<Map<String, String>>(emptyMap())
    val maskedKeys: StateFlow<Map<String, String>> = _maskedKeys.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _browserTabs = MutableStateFlow(listOf(BrowserTabState()))
    val browserTabs: StateFlow<List<BrowserTabState>> = _browserTabs.asStateFlow()

    private val _activeTabIndex = MutableStateFlow(0)
    val activeTabIndex: StateFlow<Int> = _activeTabIndex.asStateFlow()

    private val _terminalLines = MutableStateFlow(
        listOf(TerminalLine("ZEDX AI Safe Terminal v1.0.0 — Type 'help' for permitted sandbox commands."))
    )
    val terminalLines: StateFlow<List<TerminalLine>> = _terminalLines.asStateFlow()

    private val _websiteHtml = MutableStateFlow(DEFAULT_WEBSITE_HTML)
    val websiteHtml: StateFlow<String> = _websiteHtml.asStateFlow()

    private val _apkCode = MutableStateFlow(DEFAULT_ANDROID_COMPOSE_APP)
    val apkCode: StateFlow<String> = _apkCode.asStateFlow()

    private val _toolOutput = MutableStateFlow("")
    val toolOutput: StateFlow<String> = _toolOutput.asStateFlow()

    private val _toolBusy = MutableStateFlow(false)
    val toolBusy: StateFlow<Boolean> = _toolBusy.asStateFlow()

    private var activeChatJob: Job? = null
    private var messageObserverJob: Job? = null

    init {
        bootstrapUserSession()
    }

    private fun bootstrapUserSession() {
        viewModelScope.launch {
            firestoreRepo.ensureUserProfile(userEmail, userName, photoUrl)
            _isTrustedAdmin.value = firestoreRepo.isTrustedAdmin()
            seedDefaultProvidersIfEmpty()
            seedDefaultCodeProjectsIfEmpty()
            seedDefaultAutomationsIfEmpty()
            seedWelcomeNotificationIfEmpty()
            refreshMaskedKeys()
        }
    }

    private suspend fun seedDefaultProvidersIfEmpty() {
        val existing = db.providerProfileDao().getProfilesOnce(currentUserId)
        if (existing.isEmpty()) {
            ProviderType.entries.forEach { type ->
                db.providerProfileDao().upsertProfile(
                    ProviderProfileEntity(
                        id = "${currentUserId}_${type.id}",
                        userId = currentUserId,
                        providerId = type.id,
                        displayName = type.displayName,
                        baseUrl = type.defaultBaseUrl,
                        selectedModel = ProviderCatalog.modelsForProvider(type.id).firstOrNull()?.id ?: "gemini-3.8-flash",
                        enabled = type == ProviderType.GEMINI_AI_LOGIC,
                        lastTestStatus = if (type == ProviderType.GEMINI_AI_LOGIC) "App Check Ready" else "Needs API key"
                    )
                )
            }
        }
    }

    private suspend fun seedDefaultCodeProjectsIfEmpty() {
        val current = codeProjects.value
        if (current.isEmpty()) {
            db.codeProjectDao().upsertProject(
                CodeProjectEntity(
                    id = "${currentUserId}_proj_kotlin",
                    userId = currentUserId,
                    name = "NeuralOrbSample.kt",
                    language = "kotlin",
                    code = DEFAULT_ANDROID_COMPOSE_APP
                )
            )
        }
    }

    private suspend fun seedDefaultAutomationsIfEmpty() {
        val current = automations.value
        if (current.isEmpty()) {
            val defaults = listOf(
                AutomationEntity(
                    id = "${currentUserId}_auto_briefing",
                    userId = currentUserId,
                    name = "Morning Titan Briefing",
                    triggerType = "manual",
                    actionType = "ai_briefing",
                    promptTemplate = "Give me a crisp 3-bullet productivity and tech focus plan for today.",
                    requiresConfirmation = false,
                    enabled = true
                ),
                AutomationEntity(
                    id = "${currentUserId}_auto_email_draft",
                    userId = currentUserId,
                    name = "Executive Email Drafter",
                    triggerType = "manual",
                    actionType = "gmail_draft",
                    promptTemplate = "Draft a polite follow-up email summarizing project milestones.",
                    requiresConfirmation = true,
                    enabled = true
                )
            )
            defaults.forEach { db.automationDao().upsertAutomation(it) }
        }
    }

    private suspend fun seedWelcomeNotificationIfEmpty() {
        if (notifications.value.isEmpty()) {
            db.notificationDao().upsertNotification(
                NotificationItemEntity(
                    id = "${currentUserId}_welcome",
                    userId = currentUserId,
                    title = "Welcome to ZEDX AI v1.0.0",
                    body = "BY THE TITAN AI — Gemini AI Logic, 5-Agent Titan Crew, Encrypted Key Vault, and Voice Assistant are ready.",
                    category = "system"
                )
            )
        }
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun showBanner(msg: String) {
        _statusBanner.value = msg
    }

    fun selectConversation(conversationId: String?) {
        _chatState.update { it.copy(activeConversationId = conversationId, lastError = null) }
        messageObserverJob?.cancel()
        if (conversationId == null) {
            _activeMessages.value = emptyList()
            return
        }
        messageObserverJob = viewModelScope.launch {
            db.messageDao().observeMessages(conversationId).collect { list ->
                _activeMessages.value = list
            }
        }
    }

    fun startNewChat() {
        stopGeneration()
        selectConversation(null)
    }

    fun setPendingAttachmentSummary(summary: String?) {
        _chatState.update { it.copy(pendingAttachmentSummary = summary) }
    }

    fun sendMessage(
        rawPrompt: String,
        attachedBitmap: Bitmap? = null,
        attachedDocumentText: String? = null,
        onCompletedResponse: ((String) -> Unit)? = null
    ) {
        val prompt = rawPrompt.trim()
        if (prompt.isEmpty() && attachedBitmap == null && attachedDocumentText.isNullOrBlank()) return

        activeChatJob?.cancel()
        activeChatJob = viewModelScope.launch {
            val prefs = preferences.value
            val convId = _chatState.value.activeConversationId ?: "conv_${UUID.randomUUID().toString().take(12)}"
            val existingConv = db.conversationDao().getConversation(convId)

            val taskIntent = SmartTaskRouter.detectTaskIntent(prompt, attachedBitmap != null)
            val resolvedModel = SmartTaskRouter.routeWithinProvider(
                selectedProviderId = prefs.activeProviderId,
                selectedModelId = prefs.activeModelId,
                intent = taskIntent,
                smartRoutingEnabled = prefs.smartRoutingEnabled
            )

            val title = existingConv?.title ?: prompt.ifBlank { "Multimodal Analysis" }.take(44)
            db.conversationDao().upsertConversation(
                ConversationEntity(
                    id = convId,
                    userId = currentUserId,
                    title = title,
                    agentId = prefs.activeAgentId,
                    providerId = prefs.activeProviderId,
                    modelId = resolvedModel,
                    cloudSyncEnabled = prefs.cloudSyncEnabled,
                    createdAt = existingConv?.createdAt ?: System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            )
            if (_chatState.value.activeConversationId != convId) {
                selectConversation(convId)
            }

            val fullPrompt = buildString {
                if (!attachedDocumentText.isNullOrBlank()) {
                    append("[Attached Document Content]\n")
                    append(attachedDocumentText.take(12000))
                    append("\n\n")
                }
                append(prompt.ifBlank { "Analyze the attached visual/document carefully." })
            }

            val userMsg = MessageEntity(
                id = "msg_${UUID.randomUUID().toString().take(12)}",
                conversationId = convId,
                role = "user",
                content = prompt.ifBlank { "Analyze attachment" },
                providerId = prefs.activeProviderId,
                modelId = resolvedModel,
                agentId = prefs.activeAgentId,
                attachmentSummary = _chatState.value.pendingAttachmentSummary
            )
            db.messageDao().insertMessage(userMsg)
            _chatState.update {
                it.copy(isStreaming = true, streamingText = "", lastError = null, pendingAttachmentSummary = null)
            }

            val history = db.messageDao().getMessagesOnce(convId)
                .dropLast(1)
                .takeLast(10)
                .map { it.role to it.content }

            val agent = crewOrchestrator.agents.value.firstOrNull { it.id == prefs.activeAgentId }
                ?: crewOrchestrator.agents.value.first()
            val enabledMemories = db.localMemoryDao().getEnabledMemoriesOnce(currentUserId)
            val memoryBlock = if (enabledMemories.isEmpty()) "" else {
                enabledMemories.joinToString("\n") { "- ${it.title}: ${it.content}" }
            }
            val langInstruction = when (prefs.voiceLanguage) {
                "hi-IN" -> "\nRespond naturally in Hindi (Devanagari or user script) unless code is requested."
                "hinglish" -> "\nRespond naturally in clear Hinglish when conversing, and clean English/code for technical code."
                else -> ""
            }
            val systemPrompt = buildString {
                append(agent.instructions)
                append(langInstruction)
                if (memoryBlock.isNotBlank()) {
                    append("\n\nUser Preferences & Memory:\n").append(memoryBlock)
                }
            }

            val profile = db.providerProfileDao().getProfilesOnce(currentUserId)
                .firstOrNull { it.providerId == prefs.activeProviderId }
            val baseUrl = profile?.baseUrl ?: ProviderType.fromId(prefs.activeProviderId).defaultBaseUrl
            val apiKey = keyVault.getApiKey("${currentUserId}_${prefs.activeProviderId}")

            val dayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val monthKey = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
            val dailyUsed = db.usageLogDao().getDailySuccessCount(currentUserId, dayKey)
            val monthlyCost = db.usageLogDao().getMonthlyCostUsd(currentUserId, monthKey)

            aiEngine.streamCompletion(
                AiRequestConfig(
                    providerId = prefs.activeProviderId,
                    modelId = resolvedModel,
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    systemPrompt = systemPrompt,
                    history = history,
                    userPrompt = fullPrompt,
                    imageBitmap = attachedBitmap,
                    dailyRequestsUsed = dailyUsed,
                    dailyRequestLimit = prefs.dailyRequestLimit,
                    monthlySpendUsd = monthlyCost,
                    monthlyBudgetUsd = prefs.monthlyBudgetUsd.toDouble()
                )
            ).collect { event ->
                when (event) {
                    is AiStreamEvent.Delta -> {
                        _chatState.update { it.copy(streamingText = event.accumulated) }
                    }
                    is AiStreamEvent.Completed -> {
                        val assistantMsg = MessageEntity(
                            id = "msg_${UUID.randomUUID().toString().take(12)}",
                            conversationId = convId,
                            role = "assistant",
                            content = event.fullText,
                            providerId = prefs.activeProviderId,
                            modelId = resolvedModel,
                            agentId = prefs.activeAgentId,
                            tokensUsed = event.tokensUsed
                        )
                        db.messageDao().insertMessage(assistantMsg)
                        db.usageLogDao().insertUsageLog(
                            UsageLogEntity(
                                userId = currentUserId,
                                providerId = prefs.activeProviderId,
                                modelId = resolvedModel,
                                tokensUsed = event.tokensUsed,
                                estimatedCostUsd = event.estimatedCostUsd,
                                latencyMs = event.latencyMs,
                                status = "success",
                                dayKey = dayKey,
                                monthKey = monthKey
                            )
                        )
                        firestoreRepo.recordUsageMetric(
                            providerId = prefs.activeProviderId,
                            modelId = resolvedModel,
                            tokensUsed = event.tokensUsed.toLong(),
                            estimatedCostUsd = event.estimatedCostUsd,
                            latencyMs = event.latencyMs,
                            status = "success"
                        )
                        if (prefs.cloudSyncEnabled) {
                            val totalCount = db.messageDao().getMessagesOnce(convId).size.toLong()
                            firestoreRepo.syncConversationWithMessage(
                                conversationId = convId,
                                title = title,
                                agentId = prefs.activeAgentId,
                                providerId = prefs.activeProviderId,
                                modelId = resolvedModel,
                                messageCount = totalCount,
                                lastPreview = event.fullText.take(200),
                                role = "assistant",
                                content = event.fullText,
                                tokensUsed = event.tokensUsed.toLong()
                            )
                        }
                        _chatState.update { it.copy(isStreaming = false, streamingText = "") }
                        onCompletedResponse?.invoke(event.fullText)
                    }
                    is AiStreamEvent.Error -> {
                        val errMsg = MessageEntity(
                            id = "msg_${UUID.randomUUID().toString().take(12)}",
                            conversationId = convId,
                            role = "assistant",
                            content = event.message,
                            providerId = prefs.activeProviderId,
                            modelId = resolvedModel,
                            agentId = prefs.activeAgentId,
                            isError = true
                        )
                        db.messageDao().insertMessage(errMsg)
                        db.usageLogDao().insertUsageLog(
                            UsageLogEntity(
                                userId = currentUserId,
                                providerId = prefs.activeProviderId,
                                modelId = resolvedModel,
                                tokensUsed = 0,
                                estimatedCostUsd = 0.0,
                                latencyMs = 0L,
                                status = "error",
                                dayKey = dayKey,
                                monthKey = monthKey
                            )
                        )
                        _chatState.update { it.copy(isStreaming = false, streamingText = "", lastError = event.message) }
                    }
                }
            }
        }
    }

    fun stopGeneration() {
        activeChatJob?.cancel()
        activeChatJob = null
        val partial = _chatState.value.streamingText.trim()
        val convId = _chatState.value.activeConversationId
        if (partial.isNotEmpty() && convId != null) {
            viewModelScope.launch {
                val prefs = preferences.value
                db.messageDao().insertMessage(
                    MessageEntity(
                        id = "msg_${UUID.randomUUID().toString().take(12)}",
                        conversationId = convId,
                        role = "assistant",
                        content = "$partial\n\n_[Stopped by user]_",
                        providerId = prefs.activeProviderId,
                        modelId = prefs.activeModelId,
                        agentId = prefs.activeAgentId
                    )
                )
            }
        }
        _chatState.update { it.copy(isStreaming = false, streamingText = "") }
    }

    fun regenerateLastResponse() {
        val messages = _activeMessages.value
        val lastUser = messages.lastOrNull { it.role == "user" } ?: return
        val lastAssistant = messages.lastOrNull()
        viewModelScope.launch {
            if (lastAssistant != null && lastAssistant.role == "assistant") {
                db.messageDao().deleteMessage(lastAssistant.id)
            }
            sendMessage(lastUser.content)
        }
    }

    fun renameConversation(id: String, newTitle: String) {
        if (newTitle.isBlank()) return
        viewModelScope.launch {
            db.conversationDao().renameConversation(id, newTitle.trim())
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            db.messageDao().deleteMessagesForConversation(id)
            db.conversationDao().deleteConversation(id)
            if (_chatState.value.activeConversationId == id) {
                selectConversation(null)
            }
        }
    }

    fun exportConversationMarkdown(convId: String): String {
        val conv = conversations.value.firstOrNull { it.id == convId }
        val msgs = _activeMessages.value
        return buildString {
            append("# ${conv?.title ?: "ZEDX AI Conversation"}\n")
            append("_Exported from ZEDX AI v1.0.0 BY THE TITAN AI_\n\n")
            msgs.forEach { m ->
                append("## ${m.role.uppercase()} (${m.modelId})\n")
                append(m.content).append("\n\n")
            }
        }
    }

    fun exportAllUserDataJson(): String {
        val root = JSONObject()
        root.put("app", "ZEDX AI v1.0.0")
        root.put("userEmail", userEmail)
        val memArr = JSONArray()
        memories.value.forEach {
            memArr.put(JSONObject().put("title", it.title).put("category", it.category).put("content", it.content))
        }
        root.put("memories", memArr)
        val convArr = JSONArray()
        conversations.value.forEach {
            convArr.put(JSONObject().put("id", it.id).put("title", it.title).put("model", it.modelId))
        }
        root.put("conversations", convArr)
        return root.toString(2)
    }

    // Provider & Key Vault management
    fun refreshMaskedKeys() {
        viewModelScope.launch {
            val map = mutableMapOf<String, String>()
            ProviderType.entries.forEach { type ->
                map[type.id] = keyVault.getMaskedKey("${currentUserId}_${type.id}")
            }
            _maskedKeys.value = map
        }
    }

    fun saveProviderConfig(
        providerId: String,
        baseUrl: String,
        selectedModel: String,
        rawApiKey: String,
        enabled: Boolean
    ) {
        viewModelScope.launch {
            val type = ProviderType.fromId(providerId)
            val profileId = "${currentUserId}_$providerId"
            if (rawApiKey.isNotBlank()) {
                keyVault.saveApiKey(profileId, rawApiKey)
            }
            db.providerProfileDao().upsertProfile(
                ProviderProfileEntity(
                    id = profileId,
                    userId = currentUserId,
                    providerId = providerId,
                    displayName = type.displayName,
                    baseUrl = baseUrl.trim().ifBlank { type.defaultBaseUrl },
                    selectedModel = selectedModel.trim(),
                    enabled = enabled,
                    lastTestStatus = "Saved (untested)"
                )
            )
            refreshMaskedKeys()
            showBanner("${type.displayName} saved securely with AES-256-GCM.")
        }
    }

    fun testProviderConnection(providerId: String, baseUrl: String, modelId: String) {
        viewModelScope.launch {
            val profileId = "${currentUserId}_$providerId"
            val apiKey = keyVault.getApiKey(profileId)
            val result = aiEngine.testConnection(providerId, baseUrl, modelId, apiKey)
            val status = result.fold(onSuccess = { it }, onFailure = { "Failed: ${it.message}" })
            val existing = db.providerProfileDao().getProfilesOnce(currentUserId).firstOrNull { it.providerId == providerId }
            if (existing != null) {
                db.providerProfileDao().upsertProfile(existing.copy(lastTestStatus = status, updatedAt = System.currentTimeMillis()))
            }
            showBanner(status)
        }
    }

    fun deleteProviderKey(providerId: String) {
        viewModelScope.launch {
            keyVault.deleteApiKey("${currentUserId}_$providerId")
            refreshMaskedKeys()
            showBanner("Encrypted API key removed from AndroidKeyStore vault.")
        }
    }

    fun selectActiveModel(providerId: String, modelId: String) {
        viewModelScope.launch {
            prefsRepo.setActiveModel(providerId, modelId)
            showBanner("Active model set to $modelId")
        }
    }

    fun selectActiveAgent(agentId: String) {
        viewModelScope.launch {
            prefsRepo.setActiveAgent(agentId)
        }
    }

    // Memory Manager (blocks passwords/API secrets/tokens)
    fun saveMemory(id: String? = null, title: String, content: String, category: String, enabled: Boolean = true): Boolean {
        if (SecretRedactor.containsSensitiveSecret(title) || SecretRedactor.containsSensitiveSecret(content)) {
            showBanner("Blocked: Passwords, API keys, and authentication tokens cannot be stored as AI memories.")
            return false
        }
        viewModelScope.launch {
            val memId = id ?: "mem_${UUID.randomUUID().toString().take(12)}"
            db.localMemoryDao().upsertMemory(
                LocalMemoryEntity(
                    id = memId,
                    userId = currentUserId,
                    title = title.trim(),
                    content = content.trim(),
                    category = category.trim().ifBlank { "preference" },
                    enabled = enabled,
                    updatedAt = System.currentTimeMillis()
                )
            )
            if (preferences.value.cloudSyncEnabled) {
                firestoreRepo.upsertMemory(memId, title, content, category, enabled, isUpdate = id != null)
            }
        }
        return true
    }

    fun toggleMemory(item: LocalMemoryEntity) {
        saveMemory(item.id, item.title, item.content, item.category, !item.enabled)
    }

    fun deleteMemory(id: String) {
        viewModelScope.launch {
            db.localMemoryDao().deleteMemory(id)
            firestoreRepo.deleteMemory(id)
        }
    }

    fun clearAllMemories() {
        viewModelScope.launch {
            val all = memories.value
            db.localMemoryDao().clearAllForUser(currentUserId)
            all.forEach { firestoreRepo.deleteMemory(it.id) }
            showBanner("All AI memories cleared.")
        }
    }

    // Titan Crew Agent Dispatch
    fun dispatchAgentTask(agent: TitanAgent, taskPrompt: String) {
        viewModelScope.launch {
            val prefs = preferences.value
            val profile = db.providerProfileDao().getProfilesOnce(currentUserId).firstOrNull { it.providerId == prefs.activeProviderId }
            val baseUrl = profile?.baseUrl ?: ProviderType.fromId(prefs.activeProviderId).defaultBaseUrl
            val apiKey = keyVault.getApiKey("${currentUserId}_${prefs.activeProviderId}")
            val mems = db.localMemoryDao().getEnabledMemoriesOnce(currentUserId).joinToString("\n") { "${it.title}: ${it.content}" }
            crewOrchestrator.dispatchTask(
                agent = agent,
                taskPrompt = taskPrompt,
                providerId = prefs.activeProviderId,
                modelId = prefs.activeModelId,
                baseUrl = baseUrl,
                apiKey = apiKey,
                memoryContext = mems
            )
        }
    }

    fun approveAgentRun(runId: String) {
        viewModelScope.launch {
            val prefs = preferences.value
            val profile = db.providerProfileDao().getProfilesOnce(currentUserId).firstOrNull { it.providerId == prefs.activeProviderId }
            val baseUrl = profile?.baseUrl ?: ProviderType.fromId(prefs.activeProviderId).defaultBaseUrl
            val apiKey = keyVault.getApiKey("${currentUserId}_${prefs.activeProviderId}")
            val mems = db.localMemoryDao().getEnabledMemoriesOnce(currentUserId).joinToString("\n") { "${it.title}: ${it.content}" }
            crewOrchestrator.approveSensitiveRun(runId, prefs.activeProviderId, prefs.activeModelId, baseUrl, apiKey, mems)
        }
    }

    // Browser
    fun addBrowserTab(url: String = "https://developer.android.com") {
        val normalized = WorkspaceToolsEngine.normalizeBrowserUrl(url)
        _browserTabs.update { it + BrowserTabState(title = normalized, url = normalized) }
        _activeTabIndex.value = _browserTabs.value.lastIndex
    }

    fun selectBrowserTab(index: Int) {
        if (index in _browserTabs.value.indices) _activeTabIndex.value = index
    }

    fun closeBrowserTab(index: Int) {
        if (_browserTabs.value.size <= 1) return
        _browserTabs.update { it.toMutableList().apply { removeAt(index) } }
        _activeTabIndex.value = _activeTabIndex.value.coerceIn(0, _browserTabs.value.lastIndex)
    }

    fun updateActiveBrowserTab(title: String, url: String) {
        val idx = _activeTabIndex.value
        _browserTabs.update { list ->
            list.mapIndexed { i, tab -> if (i == idx) tab.copy(title = title.ifBlank { url }, url = url) else tab }
        }
        viewModelScope.launch {
            db.browserDao().insertHistory(BrowserHistoryEntity(userId = currentUserId, title = title.ifBlank { url }, url = url))
        }
    }

    fun bookmarkPage(title: String, url: String) {
        viewModelScope.launch {
            db.browserDao().insertBookmark(
                BrowserBookmarkEntity(
                    id = "bm_${UUID.randomUUID().toString().take(10)}",
                    userId = currentUserId,
                    title = title.ifBlank { url },
                    url = url
                )
            )
            showBanner("Bookmarked $url")
        }
    }

    fun deleteBookmark(id: String) {
        viewModelScope.launch { db.browserDao().deleteBookmark(id) }
    }

    // Coding Studio, Website Builder, APK Builder, Vision/PDF Helper
    fun saveCodeProject(id: String?, name: String, language: String, code: String) {
        viewModelScope.launch {
            db.codeProjectDao().upsertProject(
                CodeProjectEntity(
                    id = id ?: "code_${UUID.randomUUID().toString().take(10)}",
                    userId = currentUserId,
                    name = name.trim().ifBlank { "Untitled.kt" },
                    language = language,
                    code = code,
                    updatedAt = System.currentTimeMillis()
                )
            )
            showBanner("Saved $name to Coding Studio")
        }
    }

    fun setWebsiteHtml(html: String) {
        _websiteHtml.value = html
    }

    fun setApkCode(code: String) {
        _apkCode.value = code
    }

    fun runAiToolPrompt(
        systemPrompt: String,
        userPrompt: String,
        bitmap: Bitmap? = null,
        onComplete: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            _toolBusy.value = true
            _toolOutput.value = ""
            val prefs = preferences.value
            val profile = db.providerProfileDao().getProfilesOnce(currentUserId).firstOrNull { it.providerId == prefs.activeProviderId }
            val baseUrl = profile?.baseUrl ?: ProviderType.fromId(prefs.activeProviderId).defaultBaseUrl
            val apiKey = keyVault.getApiKey("${currentUserId}_${prefs.activeProviderId}")
            aiEngine.streamCompletion(
                AiRequestConfig(
                    providerId = prefs.activeProviderId,
                    modelId = prefs.activeModelId,
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    systemPrompt = systemPrompt,
                    history = emptyList(),
                    userPrompt = userPrompt,
                    imageBitmap = bitmap
                )
            ).collect { ev ->
                when (ev) {
                    is AiStreamEvent.Delta -> _toolOutput.value = ev.accumulated
                    is AiStreamEvent.Completed -> {
                        _toolOutput.value = ev.fullText
                        _toolBusy.value = false
                        onComplete(ev.fullText)
                    }
                    is AiStreamEvent.Error -> {
                        _toolOutput.value = "Error: ${ev.message}"
                        _toolBusy.value = false
                    }
                }
            }
        }
    }

    fun runTerminalCommand(rawCmd: String) {
        val prefs = preferences.value
        _terminalLines.update { it + TerminalLine("$ $rawCmd", isCommand = true) }
        val workspaceFiles = codeProjects.value.associate { it.name to it.code }
        val (out, invokeAi) = WorkspaceToolsEngine.evaluateSafeTerminalCommand(
            rawCommand = rawCmd,
            virtualFiles = workspaceFiles,
            userEmail = userEmail,
            activeProvider = prefs.activeProviderId,
            activeModel = prefs.activeModelId
        )
        if (out == "__CLEAR__") {
            _terminalLines.value = emptyList()
            return
        }
        if (invokeAi) {
            runAiToolPrompt("You are ZEDX Terminal Assistant. Reply concisely.", out) { reply ->
                _terminalLines.update { it + TerminalLine(reply) }
            }
        } else {
            _terminalLines.update { it + TerminalLine(out) }
        }
    }

    // Automations
    fun saveAutomation(name: String, trigger: String, action: String, prompt: String, requiresConfirmation: Boolean) {
        viewModelScope.launch {
            val id = "auto_${UUID.randomUUID().toString().take(10)}"
            val entity = AutomationEntity(
                id = id,
                userId = currentUserId,
                name = name.trim(),
                triggerType = trigger,
                actionType = action,
                promptTemplate = prompt.trim(),
                requiresConfirmation = requiresConfirmation,
                enabled = true
            )
            db.automationDao().upsertAutomation(entity)
            firestoreRepo.upsertAutomation(id, entity.name, trigger, action, prompt, requiresConfirmation, true, null, 0L, false)
            showBanner("Automation '${entity.name}' saved.")
        }
    }

    fun executeAutomation(item: AutomationEntity) {
        runAiToolPrompt("Execute workflow '${item.name}' (${item.actionType}) cleanly:", item.promptTemplate) { result ->
            viewModelScope.launch {
                val updated = item.copy(
                    runCount = item.runCount + 1,
                    lastRunStatus = "Success at ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())}",
                    updatedAt = System.currentTimeMillis()
                )
                db.automationDao().upsertAutomation(updated)
                db.notificationDao().upsertNotification(
                    NotificationItemEntity(
                        id = "notif_${UUID.randomUUID().toString().take(10)}",
                        userId = currentUserId,
                        title = "Automation: ${item.name}",
                        body = result.take(180),
                        category = "automation"
                    )
                )
            }
        }
    }

    fun markAllNotificationsRead() {
        viewModelScope.launch { db.notificationDao().markAllRead(currentUserId) }
    }

    fun addNotification(title: String, body: String, category: String = "reminder") {
        viewModelScope.launch {
            db.notificationDao().upsertNotification(
                NotificationItemEntity(
                    id = "notif_${UUID.randomUUID().toString().take(10)}",
                    userId = currentUserId,
                    title = title,
                    body = body,
                    category = category
                )
            )
        }
    }

    // Settings & Preferences
    fun updateThemeMode(mode: com.titan.zedxai.ui.theme.ZedxThemeMode) {
        viewModelScope.launch {
            prefsRepo.setThemeMode(mode)
            val p = preferences.value
            firestoreRepo.updateUserPreferences(userName, p.voiceLanguage, mode.id, p.dailyRequestLimit.toLong(), p.monthlyBudgetUsd.toDouble())
        }
    }

    fun updateVoicePreferences(lang: String, rate: Float, pitch: Float, wakeWord: Boolean, foreground: Boolean) {
        viewModelScope.launch {
            prefsRepo.setVoiceSettings(lang, rate, pitch, wakeWord, foreground)
        }
    }

    fun updateRoutingAndSync(cloudSync: Boolean, smartRouting: Boolean) {
        viewModelScope.launch { prefsRepo.setRoutingAndSync(cloudSync, smartRouting) }
    }

    fun updateQuotas(dailyLimit: Int, monthlyBudgetUsd: Float) {
        viewModelScope.launch {
            prefsRepo.setQuotas(dailyLimit, monthlyBudgetUsd)
            val p = preferences.value
            firestoreRepo.updateUserPreferences(userName, p.voiceLanguage, p.themeMode.id, dailyLimit.toLong(), monthlyBudgetUsd.toDouble())
            showBanner("Quotas updated.")
        }
    }

    fun updateProfileDisplayName(newName: String) {
        viewModelScope.launch {
            val p = preferences.value
            firestoreRepo.updateUserPreferences(newName, p.voiceLanguage, p.themeMode.id, p.dailyRequestLimit.toLong(), p.monthlyBudgetUsd.toDouble())
                .onSuccess { showBanner("Profile updated.") }
                .onFailure { showBanner("Profile update failed: ${it.message}") }
        }
    }

    fun saveGmailToken(token: String) {
        viewModelScope.launch {
            prefsRepo.setGmailOAuthToken(token.trim())
            showBanner(if (token.isBlank()) "Gmail OAuth token cleared." else "Gmail OAuth token configured.")
        }
    }

    fun deleteAccountCompletely(onComplete: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            db.conversationDao().deleteAllForUser(currentUserId)
            db.localMemoryDao().clearAllForUser(currentUserId)
            db.providerProfileDao().deleteAllForUser(currentUserId)
            db.usageLogDao().clearAllForUser(currentUserId)
            db.automationDao().deleteAllForUser(currentUserId)
            db.notificationDao().clearAllForUser(currentUserId)
            keyVault.clearAllKeys()
            val res = firestoreRepo.deleteAccountAndUserData()
            onComplete(res)
        }
    }

    companion object {
        private val DEFAULT_WEBSITE_HTML = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1.0" />
              <title>ZEDX Crystal Site</title>
              <style>
                body { font-family: system-ui, sans-serif; margin: 0; padding: 32px; background: linear-gradient(135deg, #f5faff, #e3f2ff); color: #0a1629; }
                .card { max-width: 640px; margin: auto; background: rgba(255,255,255,0.86); border: 1px solid #cde2f7; border-radius: 24px; padding: 28px; box-shadow: 0 20px 45px rgba(0,119,230,0.12); }
                h1 { margin-top: 0; color: #0077e6; }
                button { background: linear-gradient(90deg, #0077e6, #00b8d9); color: #fff; border: none; padding: 12px 20px; border-radius: 12px; font-weight: 600; cursor: pointer; }
              </style>
            </head>
            <body>
              <div class="card">
                <h1>ZEDX AI Crystal Web</h1>
                <p>Generated with ZEDX AI Website Builder BY THE TITAN AI.</p>
                <button onclick="document.getElementById('out').innerText='Interactive JS Ready!'">Test Interaction</button>
                <p id="out"></p>
              </div>
            </body>
            </html>
        """.trimIndent()

        private val DEFAULT_ANDROID_COMPOSE_APP = """
            package com.titan.generated

            import android.os.Bundle
            import androidx.activity.ComponentActivity
            import androidx.activity.compose.setContent
            import androidx.compose.foundation.layout.*
            import androidx.compose.material3.*
            import androidx.compose.runtime.*
            import androidx.compose.ui.Alignment
            import androidx.compose.ui.Modifier
            import androidx.compose.ui.unit.dp

            class MainActivity : ComponentActivity() {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    setContent {
                        MaterialTheme {
                            var count by remember { mutableIntStateOf(0) }
                            Surface(modifier = Modifier.fillMaxSize()) {
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(24.dp),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("ZEDX Generated App", style = MaterialTheme.typography.headlineMedium)
                                    Spacer(Modifier.height(16.dp))
                                    Button(onClick = { count++ }) {
                                        Text("Tapped ${'$'}count times")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()
    }
}
