package com.aetheris.chat.data.model

import kotlinx.serialization.Serializable

/**
 * Represents the type of LLM API provider.
 * OPENAI_COMPATIBLE: Any provider using the /v1/chat/completions format
 * ANTHROPIC: Anthropic's /v1/messages format
 */
enum class ProviderType {
    OPENAI_COMPATIBLE,
    ANTHROPIC
}

@Serializable
data class Provider(
    val id: String,
    val name: String,
    val type: ProviderType,
    val baseUrl: String,
    val models: List<AIModel>,
    val requiresApiKey: Boolean = true,
    val extraHeaders: Map<String, String> = emptyMap()
)

@Serializable
data class AIModel(
    val id: String,
    val name: String,
    val providerId: String,
    val maxTokens: Int = 4096,
    val supportsStreaming: Boolean = true
)

/**
 * Pre-configured providers with their available models.
 */
object DefaultProviders {

    val openAI = Provider(
        id = "openai",
        name = "OpenAI",
        type = ProviderType.OPENAI_COMPATIBLE,
        baseUrl = "https://api.openai.com/",
        models = listOf(
            AIModel("gpt-4o", "GPT-4o", "openai", 4096),
            AIModel("gpt-4o-mini", "GPT-4o Mini", "openai", 4096),
            AIModel("gpt-4.1", "GPT-4.1", "openai", 8192),
            AIModel("gpt-4.1-mini", "GPT-4.1 Mini", "openai", 8192),
            AIModel("gpt-4.1-nano", "GPT-4.1 Nano", "openai", 8192),
            AIModel("o4-mini", "o4-mini", "openai", 16384),
        )
    )

    val anthropic = Provider(
        id = "anthropic",
        name = "Anthropic",
        type = ProviderType.ANTHROPIC,
        baseUrl = "https://api.anthropic.com/",
        models = listOf(
            AIModel("claude-sonnet-4-20250514", "Claude Sonnet 4", "anthropic", 8192),
            AIModel("claude-3-5-sonnet-20241022", "Claude 3.5 Sonnet", "anthropic", 8192),
            AIModel("claude-3-5-haiku-20241022", "Claude 3.5 Haiku", "anthropic", 8192),
        )
    )

    val groq = Provider(
        id = "groq",
        name = "Groq",
        type = ProviderType.OPENAI_COMPATIBLE,
        baseUrl = "https://api.groq.com/openai/",
        models = listOf(
            AIModel("llama-3.3-70b-versatile", "Llama 3.3 70B", "groq", 4096),
            AIModel("mixtral-8x7b-32768", "Mixtral 8x7B", "groq", 4096),
            AIModel("gemma2-9b-it", "Gemma 2 9B", "groq", 4096),
        )
    )

    val openRouter = Provider(
        id = "openrouter",
        name = "OpenRouter",
        type = ProviderType.OPENAI_COMPATIBLE,
        baseUrl = "https://openrouter.ai/api/",
        models = listOf(
            AIModel("openai/gpt-4o", "GPT-4o (via OR)", "openrouter", 4096),
            AIModel("anthropic/claude-sonnet-4", "Claude Sonnet 4 (via OR)", "openrouter", 8192),
            AIModel("google/gemini-2.5-pro", "Gemini 2.5 Pro (via OR)", "openrouter", 8192),
            AIModel("meta-llama/llama-3.3-70b-instruct", "Llama 3.3 70B (via OR)", "openrouter", 4096),
        )
    )

    val custom = Provider(
        id = "custom",
        name = "Custom (legacy)",
        type = ProviderType.OPENAI_COMPATIBLE,
        baseUrl = "",
        models = listOf(
            AIModel("custom-model", "Custom Model", "custom", 4096),
        )
    )

    /** Includes the legacy "custom" placeholder for backwards compat in older callers. */
    val allProviders = listOf(openAI, anthropic, groq, openRouter, custom)

    /** Built-in providers excluding the legacy "custom" placeholder. */
    val builtIn = listOf(openAI, anthropic, groq, openRouter)
}

/**
 * Domain representation of a user-defined custom provider, mirroring
 * [com.aetheris.chat.data.local.entity.CustomProviderEntity].
 */
data class CustomProvider(
    val id: Long,
    val name: String,
    val baseUrl: String,
    val type: ProviderType
) {
    /** Stable key used in encrypted prefs and the cached_models table. */
    val providerKey: String get() = "custom:$id"
}
