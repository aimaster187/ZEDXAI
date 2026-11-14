package com.titan.zedxai.firebase

import android.content.Context
import com.example.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class ZedxFirestoreRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        auth = FirebaseAuth.getInstance()
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Google Sign-In required before accessing Firestore.")
    }

    suspend fun ensureUserProfile(
        email: String,
        displayName: String,
        photoUrl: String?
    ): Result<UserProfileDoc> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        val snap = try {
            docRef.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
        if (snap.exists()) {
            val existing = snap.toObject(UserProfileDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            if (existing != null) return@runCatching existing
        }
        val safeName = displayName.trim().ifBlank { email.substringBefore("@").ifBlank { "Titan User" } }.take(100)
        val payload = mutableMapOf<String, Any>(
            "userId" to uid,
            "email" to email.trim().take(200),
            "displayName" to safeName,
            "role" to "user",
            "status" to "active",
            "preferredLanguage" to "en",
            "preferredTheme" to "white_crystal",
            "dailyLimit" to 200L,
            "monthlyBudgetUsd" to 25.0,
            "totalRequests" to 0L,
            "totalTokens" to 0L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (!photoUrl.isNullOrBlank()) {
            payload["photoUrl"] = photoUrl.take(500)
        }
        try {
            docRef.set(payload).await()
            val created = docRef.get().await()
            created.toObject(UserProfileDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: UserProfileDoc(id = uid, userId = uid, email = email, displayName = safeName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    fun observeUserProfile(userId: String): Flow<UserProfileDoc?> = flow {
        val docRef = db.collection("users").document(userId)
        emitAll(
            docRef.snapshots()
                .map { it.toObject(UserProfileDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == userId) {
                        handleFirestoreError(fsErr ?: error, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun updateUserPreferences(
        displayName: String,
        preferredLanguage: String,
        preferredTheme: String,
        dailyLimit: Long,
        monthlyBudgetUsd: Double
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        val updates = mapOf(
            "displayName" to displayName.trim().ifBlank { "Titan User" }.take(100),
            "preferredLanguage" to preferredLanguage,
            "preferredTheme" to preferredTheme,
            "dailyLimit" to dailyLimit.coerceIn(1L, 100000L),
            "monthlyBudgetUsd" to monthlyBudgetUsd.coerceIn(0.0, 10000.0),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun isTrustedAdmin(): Boolean {
        val user = auth.currentUser ?: return false
        return try {
            val tokenResult = user.getIdToken(false).await()
            val claims = tokenResult.claims
            val hasClaim = (claims["admin"] == true) || (claims["role"] == "admin")
            if (hasClaim) return true
            val adminDoc = db.collection("admins").document(user.uid).get().await()
            adminDoc.exists()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    fun observeMemories(userId: String): Flow<List<UserMemoryDoc>> = flow {
        val col = db.collection("users").document(userId).collection("memories")
        emitAll(
            col.snapshots()
                .map { snap -> snap.toObjects(UserMemoryDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == userId) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, col.path)
                    }
                    throw error
                }
        )
    }

    suspend fun upsertMemory(
        memoryId: String = UUID.randomUUID().toString(),
        title: String,
        content: String,
        category: String,
        enabled: Boolean,
        isUpdate: Boolean = false
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val safeId = memoryId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        val docRef = db.collection("users").document(uid).collection("memories").document(safeId)
        try {
            if (isUpdate) {
                docRef.update(
                    mapOf(
                        "title" to title.trim().take(120),
                        "content" to content.trim().take(2000),
                        "category" to category.trim().take(64),
                        "enabled" to enabled,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "title" to title.trim().take(120),
                        "content" to content.trim().take(2000),
                        "category" to category.trim().take(64),
                        "enabled" to enabled,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            safeId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (isUpdate) OperationType.UPDATE else OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteMemory(memoryId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("memories").document(memoryId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    suspend fun syncConversationWithMessage(
        conversationId: String,
        title: String,
        agentId: String,
        providerId: String,
        modelId: String,
        messageCount: Long,
        lastPreview: String,
        role: String,
        content: String,
        tokensUsed: Long
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val safeConvId = conversationId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        val convRef = db.collection("users").document(uid).collection("conversations").document(safeConvId)
        val existing = convRef.get().await()
        try {
            if (existing.exists()) {
                convRef.update(
                    mapOf(
                        "title" to title.take(200),
                        "agentId" to agentId.take(64),
                        "providerId" to providerId.take(64),
                        "modelId" to modelId.take(128),
                        "messageCount" to messageCount.coerceIn(0L, 5000L),
                        "lastMessagePreview" to lastPreview.take(300),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                convRef.set(
                    mapOf(
                        "userId" to uid,
                        "title" to title.take(200),
                        "agentId" to agentId.take(64),
                        "providerId" to providerId.take(64),
                        "modelId" to modelId.take(128),
                        "messageCount" to messageCount.coerceIn(0L, 5000L),
                        "lastMessagePreview" to lastPreview.take(300),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            val msgId = "msg_${UUID.randomUUID().toString().take(16)}"
            val msgRef = convRef.collection("messages").document(msgId)
            msgRef.set(
                mapOf(
                    "userId" to uid,
                    "role" to role,
                    "content" to content.take(50000),
                    "providerId" to providerId.take(64),
                    "modelId" to modelId.take(128),
                    "agentId" to agentId.take(64),
                    "tokensUsed" to tokensUsed.coerceIn(0L, 1000000L),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, convRef.path)
            throw e
        }
    }

    suspend fun upsertAutomation(
        automationId: String,
        name: String,
        triggerType: String,
        actionType: String,
        promptTemplate: String,
        requiresConfirmation: Boolean,
        enabled: Boolean,
        lastRunStatus: String?,
        runCount: Long,
        isUpdate: Boolean
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val safeId = automationId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        val docRef = db.collection("users").document(uid).collection("automations").document(safeId)
        try {
            if (isUpdate) {
                val map = mutableMapOf<String, Any>(
                    "name" to name.take(120),
                    "triggerType" to triggerType.take(64),
                    "actionType" to actionType.take(64),
                    "promptTemplate" to promptTemplate.take(2000),
                    "requiresConfirmation" to requiresConfirmation,
                    "enabled" to enabled,
                    "runCount" to runCount.coerceAtLeast(0L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (lastRunStatus != null) map["lastRunStatus"] = lastRunStatus.take(200)
                docRef.update(map).await()
            } else {
                val map = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "name" to name.take(120),
                    "triggerType" to triggerType.take(64),
                    "actionType" to actionType.take(64),
                    "promptTemplate" to promptTemplate.take(2000),
                    "requiresConfirmation" to requiresConfirmation,
                    "enabled" to enabled,
                    "runCount" to runCount.coerceAtLeast(0L),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (lastRunStatus != null) map["lastRunStatus"] = lastRunStatus.take(200)
                docRef.set(map).await()
            }
            safeId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (isUpdate) OperationType.UPDATE else OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun recordUsageMetric(
        providerId: String,
        modelId: String,
        tokensUsed: Long,
        estimatedCostUsd: Double,
        latencyMs: Long,
        status: String
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val metricId = "m_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val docRef = db.collection("usage_metrics").document(metricId)
        val payload = mapOf(
            "userId" to uid,
            "providerId" to providerId.take(64),
            "modelId" to modelId.take(128),
            "tokensUsed" to tokensUsed.coerceIn(0L, 2000000L),
            "estimatedCostUsd" to estimatedCostUsd.coerceIn(0.0, 1000.0),
            "latencyMs" to latencyMs.coerceIn(0L, 600000L),
            "status" to status,
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            metricId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    fun observeUserUsageMetrics(userId: String): Flow<List<UsageMetricDoc>> = flow {
        val query = db.collection("usage_metrics")
            .whereEqualTo("userId", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
        emitAll(
            query.snapshots()
                .map { snap -> snap.toObjects(UsageMetricDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == userId) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, "usage_metrics")
                    }
                    throw error
                }
        )
    }

    fun observeSystemConfig(): Flow<SystemConfigDoc?> = flow {
        val docRef = db.collection("system_config").document("global")
        emitAll(
            docRef.snapshots()
                .map { it.toObject(SystemConfigDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    emit(null)
                }
        )
    }

    suspend fun deleteAccountAndUserData(): Result<Unit> = runCatching {
        val user = auth.currentUser ?: throw IllegalStateException("No signed-in user.")
        val uid = user.uid
        val userDoc = db.collection("users").document(uid)
        try {
            val memories = userDoc.collection("memories").get().await()
            memories.documents.forEach { it.reference.delete().await() }

            val automations = userDoc.collection("automations").get().await()
            automations.documents.forEach { it.reference.delete().await() }

            val convs = userDoc.collection("conversations").get().await()
            for (conv in convs.documents) {
                val msgs = conv.reference.collection("messages").get().await()
                msgs.documents.forEach { it.reference.delete().await() }
                conv.reference.delete().await()
            }

            userDoc.delete().await()
            user.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, userDoc.path)
            throw e
        }
    }
}
