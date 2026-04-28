package com.aetheris.chat.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.aetheris.chat.data.local.dao.CachedModelDao
import com.aetheris.chat.data.local.dao.ConversationDao
import com.aetheris.chat.data.local.dao.CustomProviderDao
import com.aetheris.chat.data.local.dao.MessageDao
import com.aetheris.chat.data.local.entity.CachedModelEntity
import com.aetheris.chat.data.local.entity.ConversationEntity
import com.aetheris.chat.data.local.entity.CustomProviderEntity
import com.aetheris.chat.data.local.entity.MessageEntity

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        CustomProviderEntity::class,
        CachedModelEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun customProviderDao(): CustomProviderDao
    abstract fun cachedModelDao(): CachedModelDao
}
