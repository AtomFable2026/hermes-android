package com.aetheris.chat.data.remote

import com.aetheris.chat.data.model.MessageRole
import com.aetheris.chat.data.model.Provider
import com.aetheris.chat.data.model.ProviderType
import com.aetheris.chat.data.remote.dto.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unified LLM client that handles both OpenAI-compatible and Anthropic APIs.
 * Supports streaming (SSE) and non-streaming responses.
 */
@Singleton
class LlmClient @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    /**
     * Send a streaming chat request. Emits content tokens as they arrive.
     */
    fun streamChat(
        provider: Provider,
        apiKey: String,
        modelId: String,
        messages: List<Pair<MessageRole, String>>,
        systemPrompt: String? = null,
        temperature: Double = 0.7,
        maxTokens: Int = 4096
    ): Flow<StreamEvent> = callbackFlow {
        val request = when (provider.type) {
            ProviderType.OPENAI_COMPATIBLE -> buildOpenAIRequest(
                provider, apiKey, modelId, messages, systemPrompt, temperature, maxTokens, stream = true
            )
            ProviderType.ANTHROPIC -> buildAnthropicRequest(
                provider, apiKey, modelId, messages, systemPrompt, temperature, maxTokens, stream = true
            )
        }

        val call = okHttpClient.newCall(request)

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                trySend(StreamEvent.Error("Network error: ${e.localizedMessage}"))
                close()
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    val errorMsg = parseErrorMessage(errorBody, provider.type)
                    trySend(StreamEvent.Error("API error (${response.code}): $errorMsg"))
                    close()
                    return
                }

                try {
                    val reader = BufferedReader(
                        InputStreamReader(response.body!!.byteStream())
                    )

                    // Proper SSE framing: events are separated by blank lines and
                    // may contain multiple `data:` continuation lines plus a
                    // leading `event:` type. Buffer one full event before parsing.
                    val dataBuf = StringBuilder()
                    var eventType: String? = null
                    var done = false

                    fun dispatch() {
                        if (dataBuf.isEmpty() && eventType == null) return
                        val data = dataBuf.toString()
                        val event = when (provider.type) {
                            ProviderType.OPENAI_COMPATIBLE -> parseOpenAIEvent(data)
                            ProviderType.ANTHROPIC -> parseAnthropicEvent(eventType, data)
                        }
                        if (event != null) {
                            trySend(event)
                            if (event is StreamEvent.Done) done = true
                        }
                        dataBuf.setLength(0)
                        eventType = null
                    }

                    var line: String? = null
                    while (!done && reader.readLine().also { line = it } != null) {
                        val l = line ?: continue
                        when {
                            l.isEmpty() -> dispatch()
                            l.startsWith(":") -> { /* SSE comment */ }
                            l.startsWith("event:") -> eventType = l.removePrefix("event:").trim()
                            l.startsWith("data:") -> {
                                val chunk = l.removePrefix("data:").trimStart()
                                if (dataBuf.isNotEmpty()) dataBuf.append('\n')
                                dataBuf.append(chunk)
                            }
                        }
                    }
                    if (!done) dispatch()
                    if (!done) trySend(StreamEvent.Done)
                } catch (e: Exception) {
                    trySend(StreamEvent.Error("Stream error: ${e.localizedMessage}"))
                } finally {
                    response.close()
                    close()
                }
            }
        })

        awaitClose { call.cancel() }
    }.flowOn(Dispatchers.IO)

    /**
     * Send a non-streaming chat request. Returns the full response.
     */
    suspend fun chat(
        provider: Provider,
        apiKey: String,
        modelId: String,
        messages: List<Pair<MessageRole, String>>,
        systemPrompt: String? = null,
        temperature: Double = 0.7,
        maxTokens: Int = 4096
    ): Result<String> {
        val request = when (provider.type) {
            ProviderType.OPENAI_COMPATIBLE -> buildOpenAIRequest(
                provider, apiKey, modelId, messages, systemPrompt, temperature, maxTokens, stream = false
            )
            ProviderType.ANTHROPIC -> buildAnthropicRequest(
                provider, apiKey, modelId, messages, systemPrompt, temperature, maxTokens, stream = false
            )
        }

        return try {
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                return Result.failure(Exception(parseErrorMessage(errorBody, provider.type)))
            }

            val body = response.body?.string() ?: return Result.failure(Exception("Empty response"))

            val content = when (provider.type) {
                ProviderType.OPENAI_COMPATIBLE -> {
                    val parsed = json.decodeFromString<OpenAIResponse>(body)
                    parsed.choices.firstOrNull()?.message?.content ?: ""
                }
                ProviderType.ANTHROPIC -> {
                    val parsed = json.decodeFromString<AnthropicResponse>(body)
                    parsed.content.joinToString("") { it.text }
                }
            }

            Result.success(content)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =====================================================
    // Request builders
    // =====================================================

    /**
     * Discover models from the provider's catalog endpoint.
     *
     * - OpenAI-compatible: `GET {baseUrl}/v1/models` with `Authorization: Bearer`.
     *   This works for OpenAI, Groq, OpenRouter, Together, Ollama (`/api/tags`
     *   is also supported indirectly via OpenAI-compat /v1/models on recent
     *   versions), LM Studio, vLLM, etc.
     * - Anthropic: `GET {baseUrl}/v1/models` with `x-api-key` + `anthropic-version`.
     */
    suspend fun listModels(
        provider: Provider,
        apiKey: String
    ): Result<List<DiscoveredModel>> {
        val baseUrl = provider.baseUrl.trimEnd('/')
        if (baseUrl.isBlank()) return Result.failure(IllegalArgumentException("Base URL is empty"))

        val request = when (provider.type) {
            ProviderType.OPENAI_COMPATIBLE -> Request.Builder()
                .url("$baseUrl/v1/models")
                .get()
                .addHeader("Authorization", "Bearer $apiKey")
                .apply {
                    if (provider.id == "openrouter") {
                        addHeader("HTTP-Referer", "https://aetheris.chat")
                        addHeader("X-Title", "Aetheris AI")
                    }
                    provider.extraHeaders.forEach { (k, v) -> addHeader(k, v) }
                }
                .build()
            ProviderType.ANTHROPIC -> Request.Builder()
                .url("$baseUrl/v1/models")
                .get()
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .build()
        }

        return try {
            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return Result.failure(
                    Exception("Models API error (${response.code}): ${parseErrorMessage(body, provider.type)}")
                )
            }

            val items: List<DiscoveredModel> = when (provider.type) {
                ProviderType.OPENAI_COMPATIBLE -> {
                    val parsed = json.decodeFromString<OpenAIModelsResponse>(body)
                    val list = if (parsed.data.isNotEmpty()) parsed.data else parsed.modelsAlt
                    list.map {
                        DiscoveredModel(
                            id = it.id,
                            displayName = it.displayName ?: it.name ?: it.id,
                            contextWindow = it.contextLength ?: 4096
                        )
                    }
                }
                ProviderType.ANTHROPIC -> {
                    val parsed = json.decodeFromString<AnthropicModelsResponse>(body)
                    parsed.data.map {
                        DiscoveredModel(
                            id = it.id,
                            displayName = it.displayName ?: it.id,
                            contextWindow = 200_000
                        )
                    }
                }
            }
            Result.success(items.distinctBy { it.id })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildOpenAIRequest(
        provider: Provider,
        apiKey: String,
        modelId: String,
        messages: List<Pair<MessageRole, String>>,
        systemPrompt: String?,
        temperature: Double,
        maxTokens: Int,
        stream: Boolean
    ): Request {
        val allMessages = mutableListOf<OpenAIMessage>()
        if (!systemPrompt.isNullOrBlank()) {
            allMessages.add(OpenAIMessage("system", systemPrompt))
        }
        messages.forEach { (role, content) ->
            allMessages.add(OpenAIMessage(role.name.lowercase(), content))
        }

        val requestBody = OpenAIRequest(
            model = modelId,
            messages = allMessages,
            stream = stream,
            temperature = temperature,
            maxTokens = maxTokens
        )

        val jsonBody = json.encodeToString(OpenAIRequest.serializer(), requestBody)
        val baseUrl = provider.baseUrl.trimEnd('/')

        return Request.Builder()
            .url("$baseUrl/v1/chat/completions")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .apply {
                // OpenRouter specific headers
                if (provider.id == "openrouter") {
                    addHeader("HTTP-Referer", "https://aetheris.chat")
                    addHeader("X-Title", "Aetheris AI")
                }
                provider.extraHeaders.forEach { (key, value) ->
                    addHeader(key, value)
                }
            }
            .build()
    }

    private fun buildAnthropicRequest(
        provider: Provider,
        apiKey: String,
        modelId: String,
        messages: List<Pair<MessageRole, String>>,
        systemPrompt: String?,
        temperature: Double,
        maxTokens: Int,
        stream: Boolean
    ): Request {
        val anthropicMessages = messages
            .filter { it.first != MessageRole.SYSTEM }
            .map { (role, content) ->
                AnthropicMessage(
                    role = if (role == MessageRole.USER) "user" else "assistant",
                    content = content
                )
            }

        val requestBody = AnthropicRequest(
            model = modelId,
            maxTokens = maxTokens,
            messages = anthropicMessages,
            system = systemPrompt,
            stream = stream,
            temperature = temperature
        )

        val jsonBody = json.encodeToString(AnthropicRequest.serializer(), requestBody)
        val baseUrl = provider.baseUrl.trimEnd('/')

        return Request.Builder()
            .url("$baseUrl/v1/messages")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .build()
    }

    // =====================================================
    // SSE Stream parsers
    // =====================================================

    private fun parseOpenAIEvent(data: String): StreamEvent? {
        val trimmed = data.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed == "[DONE]") return StreamEvent.Done

        return try {
            val response = json.decodeFromString<OpenAIResponse>(trimmed)
            val delta = response.choices.firstOrNull()?.delta
            val finishReason = response.choices.firstOrNull()?.finishReason

            when {
                delta?.content != null -> StreamEvent.Token(delta.content)
                // finishReason is reported in the same event that closes the
                // stream; we don't emit Done here — [DONE] (or end of stream)
                // does that, avoiding duplicate Done events.
                finishReason != null -> null
                else -> null
            }
        } catch (e: Exception) {
            null // Skip unparseable events
        }
    }

    private fun parseAnthropicEvent(eventType: String?, data: String): StreamEvent? {
        val trimmed = data.trim()
        if (trimmed.isEmpty()) return null

        // Anthropic SSE explicitly types every event via `event:` lines. Use
        // that when available; fall back to the JSON `type` field otherwise.
        return try {
            val parsed = json.decodeFromString<AnthropicStreamEvent>(trimmed)
            val type = eventType ?: parsed.type
            when (type) {
                "content_block_delta" -> parsed.delta?.text?.let { StreamEvent.Token(it) }
                "message_stop" -> StreamEvent.Done
                "error" -> StreamEvent.Error("Anthropic error")
                else -> null
            }
        } catch (e: Exception) {
            // If event type alone tells us this is an error, surface it.
            if (eventType == "error") StreamEvent.Error("Anthropic stream error") else null
        }
    }

    private fun parseErrorMessage(errorBody: String, type: ProviderType): String {
        return try {
            when (type) {
                ProviderType.OPENAI_COMPATIBLE -> {
                    val parsed = json.decodeFromString<OpenAIResponse>(errorBody)
                    parsed.error?.message ?: errorBody.take(200)
                }
                ProviderType.ANTHROPIC -> {
                    val parsed = json.decodeFromString<AnthropicResponse>(errorBody)
                    parsed.error?.message ?: errorBody.take(200)
                }
            }
        } catch (e: Exception) {
            errorBody.take(200)
        }
    }
}

/**
 * Sealed class representing streaming events from the LLM.
 */
sealed class StreamEvent {
    data class Token(val text: String) : StreamEvent()
    data class Error(val message: String) : StreamEvent()
    data object Done : StreamEvent()
}

/**
 * Lightweight model entry returned from a provider's /v1/models endpoint.
 */
data class DiscoveredModel(
    val id: String,
    val displayName: String,
    val contextWindow: Int = 4096
)
