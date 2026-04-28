package com.aetheris.chat.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ============================================================
// Anthropic Messages API DTOs
// Works with: Anthropic Claude models
// Endpoint: POST /v1/messages
// ============================================================

@Serializable
data class AnthropicRequest(
    val model: String,
    @SerialName("max_tokens")
    val maxTokens: Int = 4096,
    val messages: List<AnthropicMessage>,
    val system: String? = null,
    val stream: Boolean = true,
    val temperature: Double = 0.7
)

@Serializable
data class AnthropicMessage(
    val role: String, // "user" or "assistant"
    val content: String
)

// --- Non-streaming response ---

@Serializable
data class AnthropicResponse(
    val id: String? = null,
    val type: String? = null,
    val role: String? = null,
    val content: List<AnthropicContentBlock> = emptyList(),
    val model: String? = null,
    @SerialName("stop_reason")
    val stopReason: String? = null,
    val usage: AnthropicUsage? = null,
    val error: AnthropicError? = null
)

@Serializable
data class AnthropicContentBlock(
    val type: String = "text",
    val text: String = ""
)

@Serializable
data class AnthropicUsage(
    @SerialName("input_tokens")
    val inputTokens: Int = 0,
    @SerialName("output_tokens")
    val outputTokens: Int = 0
)

@Serializable
data class AnthropicError(
    val type: String? = null,
    val message: String = "Unknown error"
)

// --- Streaming event types ---

@Serializable
data class AnthropicStreamEvent(
    val type: String, // message_start, content_block_start, content_block_delta, content_block_stop, message_delta, message_stop
    val message: AnthropicResponse? = null,
    val index: Int? = null,
    @SerialName("content_block")
    val contentBlock: AnthropicContentBlock? = null,
    val delta: AnthropicStreamDelta? = null,
    val usage: AnthropicUsage? = null
)

@Serializable
data class AnthropicStreamDelta(
    val type: String? = null,
    val text: String? = null,
    @SerialName("stop_reason")
    val stopReason: String? = null
)
