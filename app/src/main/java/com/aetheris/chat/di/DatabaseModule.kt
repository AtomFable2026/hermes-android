package com.aetheris.chat.di

import android.content.Context
import androidx.room.Room
import com.aetheris.chat.data.local.AppDatabase
import com.aetheris.chat.data.local.dao.CachedModelDao
import com.aetheris.chat.data.local.dao.ConversationDao
import com.aetheris.chat.data.local.dao.CustomProviderDao
import com.aetheris.chat.data.local.dao.MessageDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "aetheris_database"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideConversationDao(database: AppDatabase): ConversationDao {
        return database.conversationDao()
    }

    @Provides
    @Singleton
    fun provideMessageDao(database: AppDatabase): MessageDao {
        return database.messageDao()
    }

    @Provides
    @Singleton
    fun provideCustomProviderDao(database: AppDatabase): CustomProviderDao {
        return database.customProviderDao()
    }

    @Provides
    @Singleton
    fun provideCachedModelDao(database: AppDatabase): CachedModelDao {
        return database.cachedModelDao()
    }
}
