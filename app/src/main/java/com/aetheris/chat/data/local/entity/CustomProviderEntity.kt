package com.aetheris.chat.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * A user-defined custom LLM provider. Multiple custom providers can coexist;
 * each gets its own row, its own base URL, type, and (via SettingsRepository)
 * its own encrypted API key keyed by `custom:{id}`.
 */
@Entity(
    tableName = "custom_providers",
    indices = [Index(value = ["name"], unique = false)]
)
@Serializable
data class CustomProviderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val baseUrl: String,
    /** Stored as the [com.aetheris.chat.data.model.ProviderType] enum name. */
    val type: String,
    val createdAt: Long = System.currentTimeMillis()
)
