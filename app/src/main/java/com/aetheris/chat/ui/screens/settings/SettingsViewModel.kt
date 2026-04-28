package com.aetheris.chat.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aetheris.chat.data.model.DefaultProviders
import com.aetheris.chat.data.model.Provider
import com.aetheris.chat.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val providers: List<Provider> = DefaultProviders.allProviders,
    val selectedProviderId: String = "openai",
    val selectedModelId: String = "gpt-4o",
    val apiKeys: Map<String, String> = emptyMap(),
    val systemPrompt: String = "",
    val streamingEnabled: Boolean = true,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 4096,
    val darkMode: Boolean = true,
    val customBaseUrl: String = "",
    val customModelId: String = "",
    val saveMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            // Load all API keys
            val keys = mutableMapOf<String, String>()
            DefaultProviders.allProviders.forEach { provider ->
                settingsRepository.getApiKey(provider.id)?.let {
                    keys[provider.id] = it
                }
            }

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
                        apiKeys = keys,
                        systemPrompt = systemPrompt,
                        streamingEnabled = streaming,
                        temperature = temp
                    )
                }
            }.collect()
        }

        viewModelScope.launch {
            combine(
                settingsRepository.maxTokens,
                settingsRepository.darkMode,
                settingsRepository.customBaseUrl,
                settingsRepository.customModelId
            ) { tokens, dark, baseUrl, modelId ->
                _uiState.update {
                    it.copy(
                        maxTokens = tokens,
                        darkMode = dark,
                        customBaseUrl = baseUrl,
                        customModelId = modelId
                    )
                }
            }.collect()
        }
    }

    fun saveApiKey(providerId: String, apiKey: String) {
        settingsRepository.saveApiKey(providerId, apiKey)
        _uiState.update {
            it.copy(
                apiKeys = it.apiKeys.toMutableMap().apply { this[providerId] = apiKey },
                saveMessage = "API key saved securely"
            )
        }
    }

    fun setSelectedProvider(providerId: String) {
        viewModelScope.launch { settingsRepository.setSelectedProvider(providerId) }
    }

    fun setSelectedModel(modelId: String) {
        viewModelScope.launch { settingsRepository.setSelectedModel(modelId) }
    }

    fun setSystemPrompt(prompt: String) {
        viewModelScope.launch { settingsRepository.setSystemPrompt(prompt) }
    }

    fun setStreamingEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setStreamingEnabled(enabled) }
    }

    fun setTemperature(temp: Float) {
        viewModelScope.launch { settingsRepository.setTemperature(temp) }
    }

    fun setMaxTokens(tokens: Int) {
        viewModelScope.launch { settingsRepository.setMaxTokens(tokens) }
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDarkMode(enabled) }
    }

    fun setCustomBaseUrl(url: String) {
        viewModelScope.launch { settingsRepository.setCustomBaseUrl(url) }
    }

    fun setCustomModelId(modelId: String) {
        viewModelScope.launch { settingsRepository.setCustomModelId(modelId) }
    }

    fun clearSaveMessage() {
        _uiState.update { it.copy(saveMessage = null) }
    }
}
