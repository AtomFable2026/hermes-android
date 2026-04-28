package com.aetheris.chat.data.model

/**
 * Domain model for a conversation (chat session).
 */
data class Conversation(
    val id: Long = 0,
    val title: String = "New Chat",
    val providerId: String = "openai",
    val modelId: String = "gpt-4o",
    val systemPrompt: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val messageCount: Int = 0,
    val lastMessage: String? = null
)
