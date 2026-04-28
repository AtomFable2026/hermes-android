package com.aetheris.chat.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ============================================================
// OpenAI-compatible API DTOs
// Works with: OpenAI, Groq, OpenRouter, Together, Ollama, etc.
// ============================================================

@Serializable
data class OpenAIRequest(
    val model: String,
    val messages: List<OpenAIMessage>,
    val stream: Boolean = true,
    val temperature: Double = 0.7,
    @SerialName("max_tokens")
    val maxTokens: Int = 4096,
    @SerialName("stream_options")
    val streamOptions: StreamOptions? = if (stream) StreamOptions() else null
)

@Serializable
data class StreamOptions(
    @SerialName("include_usage")
    val includeUsage: Boolean = true
)

@Serializable
data class OpenAIMessage(
    val role: String, // "system", "user", "assistant"
    val content: String
)

@Serializable
data class OpenAIResponse(
    val id: String? = null,
    val choices: List<OpenAIChoice> = emptyList(),
    val usage: OpenAIUsage? = null,
    val error: OpenAIError? = null
)

@Serializable
data class OpenAIChoice(
    val index: Int = 0,
    val message: OpenAIMessage? = null,
    val delta: OpenAIDelta? = null,
    @SerialName("finish_reason")
    val finishReason: String? = null
)

@Serializable
data class OpenAIDelta(
    val role: String? = null,
    val content: String? = null
)

@Serializable
data class OpenAIUsage(
    @SerialName("prompt_tokens")
    val promptTokens: Int = 0,
    @SerialName("completion_tokens")
    val completionTokens: Int = 0,
    @SerialName("total_tokens")
    val totalTokens: Int = 0
)

@Serializable
data class OpenAIError(
    val message: String = "Unknown error",
    val type: String? = null,
    val code: String? = null
)
