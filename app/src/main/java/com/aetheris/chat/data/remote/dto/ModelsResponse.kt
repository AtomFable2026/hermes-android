package com.aetheris.chat.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ============================================================
// /v1/models response DTOs
// Covers the OpenAI shape (and providers that mimic it: Groq,
// OpenRouter, Together, Ollama, vLLM, LM Studio, etc.) plus
// Anthropic's slightly different /v1/models response.
// ============================================================

@Serializable
data class OpenAIModelsResponse(
    @SerialName("data") val data: List<OpenAIModelItem> = emptyList(),
    @SerialName("models") val modelsAlt: List<OpenAIModelItem> = emptyList()
)

@Serializable
data class OpenAIModelItem(
    val id: String,
    val name: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    val owned_by: String? = null,
    @SerialName("context_length") val contextLength: Int? = null
)

@Serializable
data class AnthropicModelsResponse(
    val data: List<AnthropicModelItem> = emptyList()
)

@Serializable
data class AnthropicModelItem(
    val id: String,
    @SerialName("display_name") val displayName: String? = null,
    val type: String? = null
)
