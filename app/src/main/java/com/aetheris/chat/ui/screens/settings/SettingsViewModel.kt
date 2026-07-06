package com.aetheris.chat.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aetheris.chat.data.model.CustomProvider
import com.aetheris.chat.data.model.DefaultProviders
import com.aetheris.chat.data.model.Provider
import com.aetheris.chat.data.model.ProviderType
import com.aetheris.chat.data.repository.BackupRepository
import com.aetheris.chat.data.repository.ProvidersRepository
import com.aetheris.chat.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class SettingsUiState(
    val builtInProviders: List<Provider> = DefaultProviders.builtIn,
    val customProviders: List<CustomProvider> = emptyList(),
    val mergedProviders: List<Provider> = DefaultProviders.builtIn,
    val apiKeys: Map<String, String> = emptyMap(),
    val systemPrompt: String = "",
    val streamingEnabled: Boolean = true,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 4096,
    val darkMode: Boolean = true,
    val refreshingProviderId: String? = null,
    val saveMessage: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val providersRepository: ProvidersRepository,
    private val backupRepository: BackupRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        // Reload encrypted API keys on demand. Keys keyed by built-in id, plus
        // every custom-provider key, are pulled into a single map for the UI.
        viewModelScope.launch {
            providersRepository.observeCustomProviders().collect { customs ->
                val keys = mutableMapOf<String, String>()
                DefaultProviders.builtIn.forEach { p ->
                    settingsRepository.getApiKey(p.id)?.let { keys[p.id] = it }
                }
                customs.forEach { c ->
                    settingsRepository.getApiKey(c.providerKey)?.let { keys[c.providerKey] = it }
                }
                _uiState.update { it.copy(customProviders = customs, apiKeys = keys) }
            }
        }
        viewModelScope.launch {
            providersRepository.observeAllProviders().collect { providers ->
                _uiState.update { it.copy(mergedProviders = providers) }
            }
        }

        viewModelScope.launch {
            combine(
                settingsRepository.systemPrompt,
                settingsRepository.streamingEnabled,
                settingsRepository.temperature,
                settingsRepository.maxTokens,
                settingsRepository.darkMode
            ) { sys, streaming, temp, tokens, dark ->
                _uiState.update {
                    it.copy(
                        systemPrompt = sys,
                        streamingEnabled = streaming,
                        temperature = temp,
                        maxTokens = tokens,
                        darkMode = dark
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
                saveMessage = "API 密钥已安全保存"
            )
        }
    }

    fun deleteApiKey(providerId: String) {
        settingsRepository.deleteApiKey(providerId)
        _uiState.update {
            it.copy(
                apiKeys = it.apiKeys.toMutableMap().apply { remove(providerId) },
                saveMessage = "API 密钥已移除"
            )
        }
    }

    // ---- custom provider CRUD ----

    fun addCustomProvider(name: String, baseUrl: String, type: ProviderType, apiKey: String?) {
        if (baseUrl.isBlank()) {
            _uiState.update { it.copy(errorMessage = "API 地址不能为空") }
            return
        }
        viewModelScope.launch {
            providersRepository.addCustomProvider(name, baseUrl, type, apiKey)
            _uiState.update { it.copy(saveMessage = "已添加服务商 \"$name\"") }
        }
    }

    fun updateCustomProvider(id: Long, name: String, baseUrl: String, type: ProviderType) {
        viewModelScope.launch {
            providersRepository.updateCustomProvider(id, name, baseUrl, type)
            _uiState.update { it.copy(saveMessage = "服务商已更新") }
        }
    }

    fun deleteCustomProvider(id: Long) {
        viewModelScope.launch {
            providersRepository.deleteCustomProvider(id)
            _uiState.update { it.copy(saveMessage = "服务商已删除") }
        }
    }

    fun addCustomModel(providerKey: String, modelId: String, displayName: String?) {
        viewModelScope.launch {
            providersRepository.addCustomModel(providerKey, modelId, displayName)
            _uiState.update { it.copy(saveMessage = "模型已添加") }
        }
    }

    fun setPinned(providerKey: String, modelId: String, pinned: Boolean) {
        viewModelScope.launch { providersRepository.setPinned(providerKey, modelId, pinned) }
    }

    fun setHidden(providerKey: String, modelId: String, hidden: Boolean) {
        viewModelScope.launch { providersRepository.setHidden(providerKey, modelId, hidden) }
    }

    fun refreshModels(providerId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(refreshingProviderId = providerId) }
            val result = providersRepository.refreshModels(providerId)
            _uiState.update {
                it.copy(
                    refreshingProviderId = null,
                    saveMessage = result.getOrNull()?.let { count -> "找到 $count 个模型" },
                    errorMessage = result.exceptionOrNull()?.localizedMessage
                )
            }
        }
    }

    // ---- chat preferences ----

    fun setSystemPrompt(prompt: String) =
        viewModelScope.launch { settingsRepository.setSystemPrompt(prompt) }.let {}

    fun setStreamingEnabled(enabled: Boolean) =
        viewModelScope.launch { settingsRepository.setStreamingEnabled(enabled) }.let {}

    fun setTemperature(temp: Float) =
        viewModelScope.launch { settingsRepository.setTemperature(temp) }.let {}

    fun setMaxTokens(tokens: Int) =
        viewModelScope.launch { settingsRepository.setMaxTokens(tokens) }.let {}

    fun setDarkMode(enabled: Boolean) =
        viewModelScope.launch { settingsRepository.setDarkMode(enabled) }.let {}

    fun exportBackup(password: String, file: File) {
        viewModelScope.launch {
            val result = backupRepository.exportBackup(password, file)
            _uiState.update {
                it.copy(
                    saveMessage = result.fold(
                        onSuccess = { "备份导出成功" },
                        onFailure = { err -> "导出失败: ${err.localizedMessage}" }
                    )
                )
            }
        }
    }

    fun importBackup(password: String, file: File) {
        viewModelScope.launch {
            val result = backupRepository.importBackup(password, file)
            _uiState.update {
                it.copy(
                    saveMessage = result.fold(
                        onSuccess = { "备份导入成功。重启应用查看更改。" },
                        onFailure = { err -> "导入失败: ${err.localizedMessage}" }
                    )
                )
            }
        }
    }

    fun clearSaveMessage() {
        _uiState.update { it.copy(saveMessage = null) }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
