package com.aetheris.chat.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One model row — either fetched from a provider's /v1/models endpoint or
 * a curated entry from DefaultProviders. The (providerKey, modelId) pair is
 * unique. `providerKey` is "openai", "anthropic", "groq", "openrouter", or
 * "custom:{id}" for user-defined providers.
 */
@Entity(
    tableName = "cached_models",
    primaryKeys = ["providerKey", "modelId"],
    indices = [Index(value = ["providerKey"])]
)
data class CachedModelEntity(
    val providerKey: String,
    val modelId: String,
    val displayName: String,
    val contextWindow: Int = 4096,
    val supportsStreaming: Boolean = true,
    /** True if discovered from /v1/models, false if from the curated bootstrap list. */
    val fromApi: Boolean = false,
    /** True if user pinned/favourited this model. */
    val isPinned: Boolean = false,
    /** True if user hid this model from the selector. */
    val isHidden: Boolean = false,
    val fetchedAt: Long = System.currentTimeMillis()
)
