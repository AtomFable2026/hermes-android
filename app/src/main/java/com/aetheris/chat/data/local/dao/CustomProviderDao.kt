package com.aetheris.chat.data.local.dao

import androidx.room.*
import com.aetheris.chat.data.local.entity.CustomProviderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomProviderDao {

    @Query("SELECT * FROM custom_providers ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<CustomProviderEntity>>

    @Query("SELECT * FROM custom_providers ORDER BY createdAt ASC")
    suspend fun getAll(): List<CustomProviderEntity>

    @Query("SELECT * FROM custom_providers WHERE id = :id")
    suspend fun getById(id: Long): CustomProviderEntity?

    @Query("SELECT * FROM custom_providers ORDER BY createdAt ASC")
    suspend fun getAllProvidersSync(): List<CustomProviderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProvider(provider: CustomProviderEntity): Long

    @Query("DELETE FROM custom_providers WHERE id = :id")
    suspend fun delete(id: Long)
}
