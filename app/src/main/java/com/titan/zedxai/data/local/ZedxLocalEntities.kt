package com.titan.zedxai.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val agentId: String,
    val providerId: String,
    val modelId: String,
    val cloudSyncEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String, // user | assistant | system
    val content: String,
    val providerId: String,
    val modelId: String,
    val agentId: String = "titan_core",
    val attachmentSummary: String? = null,
    val tokensUsed: Int = 0,
    val isError: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "provider_profiles")
data class ProviderProfileEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val providerId: String,
    val displayName: String,
    val baseUrl: String,
    val selectedModel: String,
    val enabled: Boolean = true,
    val dailyRequestLimit: Int = 200,
    val monthlyBudgetUsd: Double = 25.0,
    val lastTestStatus: String = "Not tested",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "local_memories")
data class LocalMemoryEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val content: String,
    val category: String,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "usage_logs")
data class UsageLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String,
    val providerId: String,
    val modelId: String,
    val tokensUsed: Int,
    val estimatedCostUsd: Double,
    val latencyMs: Long,
    val status: String,
    val dayKey: String,
    val monthKey: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "browser_bookmarks")
data class BrowserBookmarkEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val url: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "browser_history")
data class BrowserHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String,
    val title: String,
    val url: String,
    val visitedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "code_projects")
data class CodeProjectEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val language: String,
    val code: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "automations")
data class AutomationEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val triggerType: String,
    val actionType: String,
    val promptTemplate: String,
    val requiresConfirmation: Boolean = true,
    val enabled: Boolean = true,
    val lastRunStatus: String = "Never run",
    val runCount: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "notifications")
data class NotificationItemEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val body: String,
    val category: String,
    val isRead: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
