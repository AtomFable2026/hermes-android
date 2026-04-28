package com.aetheris.chat.ui.screens.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aetheris.chat.data.model.*
import com.aetheris.chat.data.remote.StreamEvent
import com.aetheris.chat.data.repository.ChatRepository
import com.aetheris.chat.data.repository.ProvidersRepository
import com.aetheris.chat.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val conversationId: Long? = null,
    val selectedProviderId: String = "openai",
    val selectedModelId: String = "gpt-4o",
    val systemPrompt: String = "You are Aetheris, a helpful, creative, and intelligent AI assistant.",
    val streamingEnabled: Boolean = true,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 4096,
    val availableProviders: List<Provider> = emptyList(),
    val isRefreshingModels: Boolean = false,
    val error: String? = null,
    val info: String? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val settingsRepository: SettingsRepository,
    private val providersRepository: ProvidersRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var streamJob: Job? = null
    private var messagesJob: Job? = null

    init {
        // Load settings — typed combine, not vararg-array, so reordering can't
        // silently ClassCastException at runtime.
        viewModelScope.launch {
            combine(
                settingsRepository.selectedProviderId,
                settingsRepository.selectedModelId,
                settingsRepository.systemPrompt,
                settingsRepository.streamingEnabled,
                settingsRepository.temperature
            ) { providerId, modelId, systemPrompt, streaming, temp ->
                _uiState.update {
                    it.copy(
                        selectedProviderId = providerId,
                        selectedModelId = modelId,
                        systemPrompt = systemPrompt,
                        streamingEnabled = streaming,
                        temperature = temp
                    )
                }
            }.collect()
        }
        viewModelScope.launch {
            settingsRepository.maxTokens.collect { tokens ->
                _uiState.update { it.copy(maxTokens = tokens) }
            }
        }
        viewModelScope.launch {
            providersRepository.observeAllProviders().collect { providers ->
                _uiState.update { it.copy(availableProviders = providers) }
            }
        }

        // Load conversation if ID passed via navigation
        val conversationId = savedStateHandle.get<Long>("conversationId")
        if (conversationId != null && conversationId > 0) {
            loadConversation(conversationId)
        }
    }

    fun loadConversation(conversationId: Long) {
        messagesJob?.cancel()
        _uiState.update { it.copy(conversationId = conversationId) }

        messagesJob = viewModelScope.launch {
            // Load conversation settings
            chatRepository.getConversation(conversationId)?.let { conv ->
                _uiState.update {
                    it.copy(
                        selectedProviderId = conv.providerId,
                        selectedModelId = conv.modelId,
                        systemPrompt = conv.systemPrompt ?: it.systemPrompt
                    )
                }
            }

            // Observe messages
            chatRepository.getMessages(conversationId).collect { messages ->
                _uiState.update { it.copy(messages = messages) }
            }
        }
    }

    fun onInputChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendMessage() {
        val state = _uiState.value
        val text = state.inputText.trim()
        if (text.isBlank() || state.isLoading) return

        viewModelScope.launch {
            // Create conversation if needed
            val conversationId = state.conversationId ?: run {
                val id = chatRepository.createConversation(
                    providerId = state.selectedProviderId,
                    modelId = state.selectedModelId,
                    systemPrompt = state.systemPrompt
                )
                _uiState.update { it.copy(conversationId = id) }
                loadConversation(id)
                id
            }

            // Save user message
            val userMessage = ChatMessage(
                conversationId = conversationId,
                role = MessageRole.USER,
                content = text
            )
            chatRepository.saveMessage(userMessage)

            _uiState.update {
                it.copy(
                    inputText = "",
                    isLoading = true,
                    error = null
                )
            }

            // Get API key
            val apiKey = settingsRepository.getApiKey(state.selectedProviderId)
            if (apiKey.isNullOrBlank()) {
                val errorMsg = ChatMessage(
                    conversationId = conversationId,
                    role = MessageRole.ASSISTANT,
                    content = "⚠️ Please add your API key in Settings before chatting.",
                    isError = true
                )
                chatRepository.saveMessage(errorMsg)
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }

            val provider = providersRepository.getProvider(state.selectedProviderId)

            // Get all messages for context
            val allMessages = chatRepository.getMessagesSync(conversationId)

            if (state.streamingEnabled) {
                streamResponse(provider, apiKey, state, allMessages, conversationId)
            } else {
                nonStreamResponse(provider, apiKey, state, allMessages, conversationId)
            }
        }
    }

    private fun streamResponse(
        provider: Provider,
        apiKey: String,
        state: ChatUiState,
        allMessages: List<ChatMessage>,
        conversationId: Long
    ) {
        streamJob = viewModelScope.launch {
            // Create placeholder assistant message
            val assistantMsg = ChatMessage(
                conversationId = conversationId,
                role = MessageRole.ASSISTANT,
                content = "",
                model = state.selectedModelId,
                provider = state.selectedProviderId,
                isStreaming = true
            )
            val assistantId = chatRepository.saveMessage(assistantMsg)

            val contentBuilder = StringBuilder()

            chatRepository.streamChat(
                provider = provider,
                apiKey = apiKey,
                modelId = state.selectedModelId,
                messages = allMessages,
                systemPrompt = state.systemPrompt,
                temperature = state.temperature.toDouble(),
                maxTokens = state.maxTokens
            ).collect { event ->
                when (event) {
                    is StreamEvent.Token -> {
                        contentBuilder.append(event.text)
                        chatRepository.updateMessageContent(assistantId, contentBuilder.toString())
                    }
                    is StreamEvent.Done -> {
                        chatRepository.updateMessageContent(assistantId, contentBuilder.toString())
                        _uiState.update { it.copy(isLoading = false) }
                    }
                    is StreamEvent.Error -> {
                        val errorContent = if (contentBuilder.isNotEmpty()) {
                            contentBuilder.toString() + "\n\n⚠️ ${event.message}"
                        } else {
                            "⚠️ ${event.message}"
                        }
                        chatRepository.updateMessageContent(assistantId, errorContent)
                        _uiState.update {
                            it.copy(isLoading = false, error = event.message)
                        }
                    }
                }
            }
        }
    }

    private fun nonStreamResponse(
        provider: Provider,
        apiKey: String,
        state: ChatUiState,
        allMessages: List<ChatMessage>,
        conversationId: Long
    ) {
        streamJob = viewModelScope.launch {
            val assistantMsg = ChatMessage(
                conversationId = conversationId,
                role = MessageRole.ASSISTANT,
                content = "",
                model = state.selectedModelId,
                provider = state.selectedProviderId,
                isStreaming = false
            )
            val assistantId = chatRepository.saveMessage(assistantMsg)

            val result = chatRepository.chat(
                provider = provider,
                apiKey = apiKey,
                modelId = state.selectedModelId,
                messages = allMessages,
                systemPrompt = state.systemPrompt,
                temperature = state.temperature.toDouble(),
                maxTokens = state.maxTokens
            )

            result.fold(
                onSuccess = { content ->
                    chatRepository.updateMessageContent(assistantId, content)
                    _uiState.update { it.copy(isLoading = false) }
                },
                onFailure = { err ->
                    val msg = err.localizedMessage ?: "Unknown error"
                    chatRepository.updateMessageContent(assistantId, "⚠️ $msg")
                    _uiState.update { it.copy(isLoading = false, error = msg) }
                }
            )
        }
    }

    fun stopGeneration() {
        streamJob?.cancel()
        _uiState.update { it.copy(isLoading = false) }
    }

    fun onProviderSelected(provider: Provider) {
        viewModelScope.launch {
            settingsRepository.setSelectedProvider(provider.id)
            // Auto-select first model of the new provider
            provider.models.firstOrNull()?.let { model ->
                settingsRepository.setSelectedModel(model.id)
            }
        }
    }

    fun onModelSelected(model: AIModel) {
        viewModelScope.launch {
            settingsRepository.setSelectedModel(model.id)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearInfo() {
        _uiState.update { it.copy(info = null) }
    }

    fun refreshModels(provider: Provider) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshingModels = true) }
            val result = providersRepository.refreshModels(provider.id)
            _uiState.update {
                it.copy(
                    isRefreshingModels = false,
                    info = result.getOrNull()?.let { count -> "Found $count models for ${provider.name}" },
                    error = result.exceptionOrNull()?.localizedMessage
                )
            }
        }
    }

    fun newChat() {
        messagesJob?.cancel()
        streamJob?.cancel()
        _uiState.update {
            it.copy(
                conversationId = null,
                messages = emptyList(),
                inputText = "",
                isLoading = false,
                error = null
            )
        }
    }
}
