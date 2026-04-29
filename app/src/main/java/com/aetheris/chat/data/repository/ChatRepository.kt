package com.aetheris.chat.data.repository

import com.aetheris.chat.data.local.dao.ConversationDao
import com.aetheris.chat.data.local.dao.MessageDao
import com.aetheris.chat.data.local.entity.ConversationEntity
import com.aetheris.chat.data.local.entity.MessageEntity
import com.aetheris.chat.data.model.*
import com.aetheris.chat.data.remote.LlmClient
import com.aetheris.chat.data.remote.StreamEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Main chat repository — bridges local DB, settings, and remote LLM calls.
 */
@Singleton
class ChatRepository @Inject constructor(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val llmClient: LlmClient,
    private val settingsRepository: SettingsRepository
) {
    // =====================================================
    // Conversations
    // =====================================================

    fun getAllConversations(): Flow<List<Conversation>> {
        return conversationDao.getAllConversations().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun getConversation(id: Long): Conversation? {
        return conversationDao.getConversationById(id)?.toDomain()
    }

    suspend fun createConversation(
        providerId: String,
        modelId: String,
        systemPrompt: String? = null
    ): Long {
        val entity = ConversationEntity(
            providerId = providerId,
            modelId = modelId,
            systemPrompt = systemPrompt
        )
        return conversationDao.insertConversation(entity)
    }

    suspend fun updateConversationTitle(id: Long, title: String) {
        conversationDao.updateTitle(id, title)
    }

    suspend fun deleteConversation(id: Long) {
        conversationDao.deleteConversation(id)
    }

    // =====================================================
    // Messages
    // =====================================================

    fun getMessages(conversationId: Long): Flow<List<ChatMessage>> {
        return messageDao.getMessagesForConversation(conversationId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun getMessagesSync(conversationId: Long): List<ChatMessage> {
        return messageDao.getMessagesForConversationSync(conversationId).map { it.toDomain() }
    }

    suspend fun saveMessage(message: ChatMessage): Long {
        val entity = message.toEntity()
        val id = messageDao.insertMessage(entity)

        // Update conversation timestamp and auto-generate title from first user message
        conversationDao.touchConversation(message.conversationId)

        if (message.role == MessageRole.USER) {
            val count = messageDao.getMessageCount(message.conversationId)
            if (count <= 1) {
                // Auto-title from first user message
                val title = message.content.take(50).let {
                    if (message.content.length > 50) "$it…" else it
                }
                conversationDao.updateTitle(message.conversationId, title)
            }
        }

        return id
    }

    suspend fun updateMessageContent(id: Long, content: String) {
        messageDao.updateContent(id, content)
    }

    suspend fun updateMessageReasoning(id: Long, reasoning: String) {
        messageDao.updateReasoning(id, reasoning)
    }

    // =====================================================
    // LLM Chat
    // =====================================================

    fun streamChat(
        provider: Provider,
        apiKey: String,
        modelId: String,
        messages: List<ChatMessage>,
        systemPrompt: String?,
        temperature: Double,
        maxTokens: Int
    ): Flow<StreamEvent> {
        return llmClient.streamChat(
            provider = provider,
            apiKey = apiKey,
            modelId = modelId,
            messages = messages,
            systemPrompt = systemPrompt,
            temperature = temperature,
            maxTokens = maxTokens
        )
    }

    suspend fun chat(
        provider: Provider,
        apiKey: String,
        modelId: String,
        messages: List<ChatMessage>,
        systemPrompt: String?,
        temperature: Double,
        maxTokens: Int
    ): Result<String> {
        return llmClient.chat(
            provider = provider,
            apiKey = apiKey,
            modelId = modelId,
            messages = messages,
            systemPrompt = systemPrompt,
            temperature = temperature,
            maxTokens = maxTokens
        )
    }

    // =====================================================
    // Entity <-> Domain Mappers
    // =====================================================

    private fun ConversationEntity.toDomain() = Conversation(
        id = id,
        title = title,
        providerId = providerId,
        modelId = modelId,
        systemPrompt = systemPrompt,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun MessageEntity.toDomain() = ChatMessage(
        id = id,
        conversationId = conversationId,
        role = MessageRole.valueOf(role.uppercase()),
        content = content,
        model = model,
        provider = provider,
        timestamp = timestamp,
        images = images,
        reasoning = reasoning,
        isError = isError
    )

    private fun ChatMessage.toEntity() = MessageEntity(
        id = if (id == 0L) 0 else id,
        conversationId = conversationId,
        role = role.name.lowercase(),
        content = content,
        model = model,
        provider = provider,
        timestamp = timestamp,
        images = images,
        reasoning = reasoning,
        isError = isError
    )
}
