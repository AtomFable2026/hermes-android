package com.aetheris.chat.data.repository

import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.aetheris.chat.data.model.DefaultProviders
import com.aetheris.chat.data.model.Provider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for app settings — API keys stored encrypted, preferences in DataStore.
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val encryptedPrefs: SharedPreferences
) {
    companion object {
        // DataStore keys
        val SELECTED_PROVIDER_ID = stringPreferencesKey("selected_provider_id")
        val SELECTED_MODEL_ID = stringPreferencesKey("selected_model_id")
        val SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
        val STREAMING_ENABLED = booleanPreferencesKey("streaming_enabled")
        val TEMPERATURE = floatPreferencesKey("temperature")
        val MAX_TOKENS = intPreferencesKey("max_tokens")
        val DARK_MODE = booleanPreferencesKey("dark_mode")

        // Encrypted prefs key prefix
        private const val API_KEY_PREFIX = "api_key_"
    }

    // --- API Key Management (Encrypted) ---

    fun getApiKey(providerId: String): String? {
        return encryptedPrefs.getString("$API_KEY_PREFIX$providerId", null)
    }

    fun saveApiKey(providerId: String, apiKey: String) {
        encryptedPrefs.edit().putString("$API_KEY_PREFIX$providerId", apiKey).apply()
    }

    fun deleteApiKey(providerId: String) {
        encryptedPrefs.edit().remove("$API_KEY_PREFIX$providerId").apply()
    }

    fun hasApiKey(providerId: String): Boolean {
        return !getApiKey(providerId).isNullOrBlank()
    }

    // --- Selected Provider ---

    val selectedProviderId: Flow<String> = dataStore.data.map { prefs ->
        prefs[SELECTED_PROVIDER_ID] ?: "openai"
    }

    suspend fun setSelectedProvider(providerId: String) {
        dataStore.edit { prefs -> prefs[SELECTED_PROVIDER_ID] = providerId }
    }

    // --- Selected Model ---

    val selectedModelId: Flow<String> = dataStore.data.map { prefs ->
        prefs[SELECTED_MODEL_ID] ?: "gpt-4o"
    }

    suspend fun setSelectedModel(modelId: String) {
        dataStore.edit { prefs -> prefs[SELECTED_MODEL_ID] = modelId }
    }

    // --- System Prompt ---

    val systemPrompt: Flow<String> = dataStore.data.map { prefs ->
        prefs[SYSTEM_PROMPT] ?: "You are Aetheris, a helpful, creative, and intelligent AI assistant."
    }

    suspend fun setSystemPrompt(prompt: String) {
        dataStore.edit { prefs -> prefs[SYSTEM_PROMPT] = prompt }
    }

    // --- Streaming ---

    val streamingEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[STREAMING_ENABLED] ?: true
    }

    suspend fun setStreamingEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[STREAMING_ENABLED] = enabled }
    }

    // --- Temperature ---

    val temperature: Flow<Float> = dataStore.data.map { prefs ->
        prefs[TEMPERATURE] ?: 0.7f
    }

    suspend fun setTemperature(temp: Float) {
        dataStore.edit { prefs -> prefs[TEMPERATURE] = temp }
    }

    // --- Max Tokens ---

    val maxTokens: Flow<Int> = dataStore.data.map { prefs ->
        prefs[MAX_TOKENS] ?: 4096
    }

    suspend fun setMaxTokens(tokens: Int) {
        dataStore.edit { prefs -> prefs[MAX_TOKENS] = tokens }
    }

    // --- Dark Mode ---

    val darkMode: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[DARK_MODE] ?: true
    }

    suspend fun setDarkMode(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[DARK_MODE] = enabled }
    }

    // --- Helpers ---

    /**
     * Static lookup of a built-in provider. Custom providers and runtime model
     * lists are now resolved via [com.aetheris.chat.data.repository.ProvidersRepository].
     */
    fun getBuiltInProvider(providerId: String): Provider {
        return DefaultProviders.builtIn.find { it.id == providerId } ?: DefaultProviders.openAI
    }
}
