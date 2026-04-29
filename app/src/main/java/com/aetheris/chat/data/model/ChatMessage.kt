package com.aetheris.chat.data.model

/**
 * Domain model for a chat message.
 */
data class ChatMessage(
    val id: Long = 0,
    val conversationId: Long,
    val role: MessageRole,
    val content: String,
    val model: String? = null,
    val provider: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val images: List<String> = emptyList(),
    val reasoning: String? = null,
    val isStreaming: Boolean = false,
    val isError: Boolean = false
)

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}
