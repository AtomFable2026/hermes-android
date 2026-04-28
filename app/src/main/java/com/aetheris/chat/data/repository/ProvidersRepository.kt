package com.aetheris.chat.data.repository

import com.aetheris.chat.data.local.dao.CachedModelDao
import com.aetheris.chat.data.local.dao.CustomProviderDao
import com.aetheris.chat.data.local.entity.CachedModelEntity
import com.aetheris.chat.data.local.entity.CustomProviderEntity
import com.aetheris.chat.data.model.AIModel
import com.aetheris.chat.data.model.CustomProvider
import com.aetheris.chat.data.model.DefaultProviders
import com.aetheris.chat.data.model.Provider
import com.aetheris.chat.data.model.ProviderType
import com.aetheris.chat.data.remote.LlmClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for the providers + models the user can pick from.
 *
 * - Built-in providers (OpenAI, Anthropic, Groq, OpenRouter) are bootstrapped
 *   from [DefaultProviders] but augmented at runtime with whatever
 *   [LlmClient.listModels] discovered from each provider's `/v1/models`
 *   endpoint and cached in Room.
 * - User-defined custom providers live in the `custom_providers` table; each
 *   resolves to its own [Provider] (with provider key `custom:{id}`) and its
 *   own list of cached / discovered models.
 */
@Singleton
class ProvidersRepository @Inject constructor(
    private val customProviderDao: CustomProviderDao,
    private val cachedModelDao: CachedModelDao,
    private val llmClient: LlmClient,
    private val settingsRepository: SettingsRepository
) {

    /** Domain stream of every user-defined custom provider. */
    fun observeCustomProviders(): Flow<List<CustomProvider>> =
        customProviderDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    /**
     * Stream of every selectable provider — built-ins first, then user-defined
     * customs. Each provider's `models` list is merged from the curated
     * bootstrap list (for built-ins) plus any models discovered from the
     * provider's `/v1/models` endpoint and cached in `cached_models`.
     */
    fun observeAllProviders(): Flow<List<Provider>> =
        combine(
            customProviderDao.observeAll(),
            cachedModelDao.observeAll()
        ) { customs, cached ->
            val cachedByKey = cached.groupBy { it.providerKey }
            val builtIns = DefaultProviders.builtIn.map { p ->
                p.copy(models = mergeModels(p.id, p.models, cachedByKey[p.id].orEmpty()))
            }
            val customProviders = customs.map { entity ->
                val custom = entity.toDomain()
                Provider(
                    id = custom.providerKey,
                    name = custom.name,
                    type = custom.type,
                    baseUrl = custom.baseUrl,
                    models = cachedToAi(cachedByKey[custom.providerKey].orEmpty(), custom.providerKey)
                )
            }
            builtIns + customProviders
        }

    /** Resolve a provider id (`openai`, `anthropic`, ..., `custom:{id}`) into a [Provider]. */
    suspend fun getProvider(providerId: String): Provider {
        // Built-in providers
        DefaultProviders.builtIn.find { it.id == providerId }?.let { base ->
            val cached = cachedModelDao.getForProvider(providerId)
            return base.copy(models = mergeModels(providerId, base.models, cached))
        }

        // Custom providers
        if (providerId.startsWith("custom:")) {
            val rowId = providerId.removePrefix("custom:").toLongOrNull()
                ?: return fallbackOpenAI()
            val entity = customProviderDao.getById(rowId) ?: return fallbackOpenAI()
            val cached = cachedModelDao.getForProvider(providerId)
            val custom = entity.toDomain()
            return Provider(
                id = custom.providerKey,
                name = custom.name,
                type = custom.type,
                baseUrl = custom.baseUrl,
                models = cachedToAi(cached, providerId)
            )
        }

        // Legacy fallback for older "custom" id from v1 — fall through to OpenAI.
        return fallbackOpenAI()
    }

    private fun fallbackOpenAI() = DefaultProviders.openAI

    // ----------------------------------------------------------------
    // Custom provider CRUD
    // ----------------------------------------------------------------

    suspend fun addCustomProvider(
        name: String,
        baseUrl: String,
        type: ProviderType,
        apiKey: String?
    ): Long {
        val id = customProviderDao.upsert(
            CustomProviderEntity(
                name = name.ifBlank { "Custom" },
                baseUrl = baseUrl.trim().trimEnd('/'),
                type = type.name
            )
        )
        if (!apiKey.isNullOrBlank()) {
            settingsRepository.saveApiKey("custom:$id", apiKey)
        }
        return id
    }

    suspend fun updateCustomProvider(
        id: Long,
        name: String,
        baseUrl: String,
        type: ProviderType
    ) {
        customProviderDao.upsert(
            CustomProviderEntity(
                id = id,
                name = name.ifBlank { "Custom" },
                baseUrl = baseUrl.trim().trimEnd('/'),
                type = type.name,
                createdAt = customProviderDao.getById(id)?.createdAt ?: System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteCustomProvider(id: Long) {
        val key = "custom:$id"
        customProviderDao.delete(id)
        cachedModelDao.clearAllForProvider(key)
        settingsRepository.deleteApiKey(key)
    }

    // ----------------------------------------------------------------
    // Custom-provider model CRUD (manual entry)
    // ----------------------------------------------------------------

    suspend fun addCustomModel(providerKey: String, modelId: String, displayName: String?, contextWindow: Int = 4096) {
        if (modelId.isBlank()) return
        cachedModelDao.upsert(
            CachedModelEntity(
                providerKey = providerKey,
                modelId = modelId.trim(),
                displayName = displayName?.ifBlank { null } ?: modelId.trim(),
                contextWindow = contextWindow,
                fromApi = false
            )
        )
    }

    suspend fun removeModel(providerKey: String, modelId: String) {
        // Implemented via clearAllForProvider would be too coarse; do a targeted upsert
        // with isHidden = true so user-curated entries can be re-enabled later. For
        // truly removing manual entries, the user can delete the provider.
        cachedModelDao.setHidden(providerKey, modelId, true)
    }

    suspend fun setPinned(providerKey: String, modelId: String, pinned: Boolean) =
        cachedModelDao.setPinned(providerKey, modelId, pinned)

    suspend fun setHidden(providerKey: String, modelId: String, hidden: Boolean) =
        cachedModelDao.setHidden(providerKey, modelId, hidden)

    // ----------------------------------------------------------------
    // Model discovery
    // ----------------------------------------------------------------

    /**
     * Refresh the model list for a provider by calling its /v1/models endpoint.
     * Returns the number of models discovered, or a failure if the call fails.
     */
    suspend fun refreshModels(providerId: String): Result<Int> {
        val provider = getProvider(providerId)
        if (provider.baseUrl.isBlank()) {
            return Result.failure(IllegalStateException("Set a base URL before refreshing"))
        }
        val apiKey = settingsRepository.getApiKey(providerId)
            ?: return Result.failure(IllegalStateException("No API key saved for ${provider.name}"))

        val result = llmClient.listModels(provider, apiKey)
        return result.map { discovered ->
            // Replace the previously discovered API rows for this provider while
            // preserving any user-pinned/hidden state the user already set.
            val existing = cachedModelDao.getForProvider(providerId).associateBy { it.modelId }
            cachedModelDao.clearApiModelsForProvider(providerId)
            val rows = discovered.map { d ->
                val prev = existing[d.id]
                CachedModelEntity(
                    providerKey = providerId,
                    modelId = d.id,
                    displayName = d.displayName,
                    contextWindow = d.contextWindow,
                    fromApi = true,
                    isPinned = prev?.isPinned ?: false,
                    isHidden = prev?.isHidden ?: false,
                    fetchedAt = System.currentTimeMillis()
                )
            }
            cachedModelDao.upsertAll(rows)
            rows.size
        }
    }

    suspend fun lastFetchedAt(providerId: String): Long? =
        cachedModelDao.lastFetchedAt(providerId)

    // ----------------------------------------------------------------
    // Mappers / merging
    // ----------------------------------------------------------------

    private fun CustomProviderEntity.toDomain() = CustomProvider(
        id = id,
        name = name,
        baseUrl = baseUrl,
        type = runCatching { ProviderType.valueOf(type) }.getOrDefault(ProviderType.OPENAI_COMPATIBLE)
    )

    private fun mergeModels(
        providerKey: String,
        bootstrap: List<AIModel>,
        cached: List<CachedModelEntity>
    ): List<AIModel> {
        val visible = cached.filterNot { it.isHidden }
        val cachedAi = visible.map { c ->
            AIModel(
                id = c.modelId,
                name = c.displayName,
                providerId = providerKey,
                maxTokens = c.contextWindow.coerceAtLeast(4096)
            )
        }
        val cachedIds = cachedAi.map { it.id }.toSet()
        // Bootstrap entries that the API discovery didn't return are kept too
        // so the user always has a non-empty selector for known built-ins.
        val extras = bootstrap.filter { it.id !in cachedIds }
        val pinnedFirst = visible.filter { it.isPinned }.map { it.modelId }.toSet()
        return (cachedAi + extras).sortedWith(
            compareByDescending<AIModel> { it.id in pinnedFirst }
                .thenBy { it.name.lowercase() }
        )
    }

    private fun cachedToAi(cached: List<CachedModelEntity>, providerKey: String): List<AIModel> {
        val visible = cached.filterNot { it.isHidden }
        if (visible.isEmpty()) return emptyList()
        val pinned = visible.filter { it.isPinned }.map { it.modelId }.toSet()
        return visible.map {
            AIModel(
                id = it.modelId,
                name = it.displayName,
                providerId = providerKey,
                maxTokens = it.contextWindow.coerceAtLeast(4096)
            )
        }.sortedWith(
            compareByDescending<AIModel> { it.id in pinned }
                .thenBy { it.name.lowercase() }
        )
    }
}
