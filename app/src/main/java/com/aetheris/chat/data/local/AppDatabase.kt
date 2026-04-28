package com.aetheris.chat.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.aetheris.chat.data.local.dao.ConversationDao
import com.aetheris.chat.data.local.dao.MessageDao
import com.aetheris.chat.data.local.entity.ConversationEntity
import com.aetheris.chat.data.local.entity.MessageEntity

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
}
