package com.aetheris.chat.data.local.dao

import androidx.room.*
import com.aetheris.chat.data.local.entity.CachedModelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedModelDao {

    @Query("SELECT * FROM cached_models WHERE providerKey = :providerKey ORDER BY isPinned DESC, displayName ASC")
    fun observeForProvider(providerKey: String): Flow<List<CachedModelEntity>>

    @Query("SELECT * FROM cached_models ORDER BY providerKey ASC, displayName ASC")
    fun observeAll(): Flow<List<CachedModelEntity>>

    @Query("SELECT * FROM cached_models WHERE providerKey = :providerKey")
    suspend fun getForProvider(providerKey: String): List<CachedModelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(models: List<CachedModelEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(model: CachedModelEntity)

    @Query("DELETE FROM cached_models WHERE providerKey = :providerKey AND fromApi = 1")
    suspend fun clearApiModelsForProvider(providerKey: String)

    @Query("DELETE FROM cached_models WHERE providerKey = :providerKey")
    suspend fun clearAllForProvider(providerKey: String)

    @Query("UPDATE cached_models SET isPinned = :pinned WHERE providerKey = :providerKey AND modelId = :modelId")
    suspend fun setPinned(providerKey: String, modelId: String, pinned: Boolean)

    @Query("UPDATE cached_models SET isHidden = :hidden WHERE providerKey = :providerKey AND modelId = :modelId")
    suspend fun setHidden(providerKey: String, modelId: String, hidden: Boolean)

    @Query("SELECT MAX(fetchedAt) FROM cached_models WHERE providerKey = :providerKey AND fromApi = 1")
    suspend fun lastFetchedAt(providerKey: String): Long?
}
