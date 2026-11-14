package com.titan.zedxai.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations WHERE userId = :userId ORDER BY updatedAt DESC")
    fun observeConversations(userId: String): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE userId = :userId AND title LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    fun searchConversations(userId: String, query: String): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getConversation(id: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversation(conversation: ConversationEntity)

    @Query("UPDATE conversations SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun renameConversation(id: String, title: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversation(id: String)

    @Query("DELETE FROM conversations WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun observeMessages(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    suspend fun getMessagesOnce(conversationId: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessage(id: String)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: String)
}

@Dao
interface ProviderProfileDao {
    @Query("SELECT * FROM provider_profiles WHERE userId = :userId ORDER BY updatedAt DESC")
    fun observeProfiles(userId: String): Flow<List<ProviderProfileEntity>>

    @Query("SELECT * FROM provider_profiles WHERE userId = :userId")
    suspend fun getProfilesOnce(userId: String): List<ProviderProfileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: ProviderProfileEntity)

    @Query("DELETE FROM provider_profiles WHERE id = :id")
    suspend fun deleteProfile(id: String)

    @Query("DELETE FROM provider_profiles WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}

@Dao
interface LocalMemoryDao {
    @Query("SELECT * FROM local_memories WHERE userId = :userId ORDER BY updatedAt DESC")
    fun observeMemories(userId: String): Flow<List<LocalMemoryEntity>>

    @Query("SELECT * FROM local_memories WHERE userId = :userId AND enabled = 1 ORDER BY updatedAt DESC")
    suspend fun getEnabledMemoriesOnce(userId: String): List<LocalMemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMemory(memory: LocalMemoryEntity)

    @Query("DELETE FROM local_memories WHERE id = :id")
    suspend fun deleteMemory(id: String)

    @Query("DELETE FROM local_memories WHERE userId = :userId")
    suspend fun clearAllForUser(userId: String)
}

@Dao
interface UsageLogDao {
    @Query("SELECT * FROM usage_logs WHERE userId = :userId ORDER BY createdAt DESC LIMIT 100")
    fun observeUsageLogs(userId: String): Flow<List<UsageLogEntity>>

    @Query("SELECT COUNT(*) FROM usage_logs WHERE userId = :userId AND dayKey = :dayKey AND status = 'success'")
    suspend fun getDailySuccessCount(userId: String, dayKey: String): Int

    @Query("SELECT COALESCE(SUM(estimatedCostUsd), 0.0) FROM usage_logs WHERE userId = :userId AND monthKey = :monthKey")
    suspend fun getMonthlyCostUsd(userId: String, monthKey: String): Double

    @Insert
    suspend fun insertUsageLog(log: UsageLogEntity)

    @Query("DELETE FROM usage_logs WHERE userId = :userId")
    suspend fun clearAllForUser(userId: String)
}

@Dao
interface BrowserDao {
    @Query("SELECT * FROM browser_bookmarks WHERE userId = :userId ORDER BY createdAt DESC")
    fun observeBookmarks(userId: String): Flow<List<BrowserBookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BrowserBookmarkEntity)

    @Query("DELETE FROM browser_bookmarks WHERE id = :id")
    suspend fun deleteBookmark(id: String)

    @Query("SELECT * FROM browser_history WHERE userId = :userId ORDER BY visitedAt DESC LIMIT 50")
    fun observeHistory(userId: String): Flow<List<BrowserHistoryEntity>>

    @Insert
    suspend fun insertHistory(history: BrowserHistoryEntity)

    @Query("DELETE FROM browser_history WHERE userId = :userId")
    suspend fun clearHistory(userId: String)
}

@Dao
interface CodeProjectDao {
    @Query("SELECT * FROM code_projects WHERE userId = :userId ORDER BY updatedAt DESC")
    fun observeProjects(userId: String): Flow<List<CodeProjectEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProject(project: CodeProjectEntity)

    @Query("DELETE FROM code_projects WHERE id = :id")
    suspend fun deleteProject(id: String)
}

@Dao
interface AutomationDao {
    @Query("SELECT * FROM automations WHERE userId = :userId ORDER BY updatedAt DESC")
    fun observeAutomations(userId: String): Flow<List<AutomationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAutomation(automation: AutomationEntity)

    @Query("DELETE FROM automations WHERE id = :id")
    suspend fun deleteAutomation(id: String)

    @Query("DELETE FROM automations WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications WHERE userId = :userId ORDER BY createdAt DESC")
    fun observeNotifications(userId: String): Flow<List<NotificationItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNotification(item: NotificationItemEntity)

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: String)

    @Query("UPDATE notifications SET isRead = 1 WHERE userId = :userId")
    suspend fun markAllRead(userId: String)

    @Query("DELETE FROM notifications WHERE userId = :userId")
    suspend fun clearAllForUser(userId: String)
}
