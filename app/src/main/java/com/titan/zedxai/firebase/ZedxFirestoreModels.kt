package com.titan.zedxai.firebase

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfileDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val displayName: String = "",
    val photoUrl: String? = null,
    val role: String = "user",
    val status: String = "active",
    val preferredLanguage: String = "en",
    val preferredTheme: String = "white_crystal",
    val dailyLimit: Long = 200L,
    val monthlyBudgetUsd: Double = 25.0,
    val totalRequests: Long = 0L,
    val totalTokens: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SyncedConversationDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val agentId: String = "titan_core",
    val providerId: String = "gemini",
    val modelId: String = "gemini-3.8-flash",
    val messageCount: Long = 0L,
    val lastMessagePreview: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SyncedMessageDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val role: String = "user",
    val content: String = "",
    val providerId: String = "gemini",
    val modelId: String = "gemini-3.8-flash",
    val agentId: String? = null,
    val tokensUsed: Long = 0L,
    val attachmentSummary: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class UserMemoryDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val content: String = "",
    val category: String = "preference",
    val enabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AutomationWorkflowDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val triggerType: String = "manual",
    val actionType: String = "ai_briefing",
    val promptTemplate: String = "",
    val requiresConfirmation: Boolean = true,
    val enabled: Boolean = true,
    val lastRunStatus: String? = null,
    val runCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class UsageMetricDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val providerId: String = "",
    val modelId: String = "",
    val tokensUsed: Long = 0L,
    val estimatedCostUsd: Double = 0.0,
    val latencyMs: Long = 0L,
    val status: String = "success",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SystemConfigDoc(
    @DocumentId val id: String = "",
    val maintenanceMode: Boolean = false,
    val defaultProvider: String = "gemini",
    val defaultModel: String = "gemini-3.8-flash",
    val allowedProviders: List<String> = emptyList(),
    val announcement: String? = null,
    val updatedBy: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
)
