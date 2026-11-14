package com.titan.zedxai.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        ProviderProfileEntity::class,
        LocalMemoryEntity::class,
        UsageLogEntity::class,
        BrowserBookmarkEntity::class,
        BrowserHistoryEntity::class,
        CodeProjectEntity::class,
        AutomationEntity::class,
        NotificationItemEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ZedxDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun providerProfileDao(): ProviderProfileDao
    abstract fun localMemoryDao(): LocalMemoryDao
    abstract fun usageLogDao(): UsageLogDao
    abstract fun browserDao(): BrowserDao
    abstract fun codeProjectDao(): CodeProjectDao
    abstract fun automationDao(): AutomationDao
    abstract fun notificationDao(): NotificationDao

    companion object {
        @Volatile
        private var INSTANCE: ZedxDatabase? = null

        fun getInstance(context: Context): ZedxDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ZedxDatabase::class.java,
                    "zedx_ai_local.db"
                )
                    .fallbackToDestructiveMigration(false)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
