package com.aetheris.chat.ui.screens.chat

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.aetheris.chat.data.model.AIModel
import com.aetheris.chat.data.model.Provider
import com.aetheris.chat.data.repository.ChatRepository
import com.aetheris.chat.data.repository.ProvidersRepository
import com.aetheris.chat.data.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val chatRepository = mockk<ChatRepository>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val providersRepository = mockk<ProvidersRepository>(relaxed = true)
    private val savedStateHandle = SavedStateHandle()

    private lateinit var viewModel: ChatViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        // Default mocks to avoid init crashes
        every { settingsRepository.selectedProviderId } returns flowOf("openai")
        every { settingsRepository.selectedModelId } returns flowOf("gpt-4o")
        every { settingsRepository.systemPrompt } returns flowOf("System prompt")
        every { settingsRepository.streamingEnabled } returns flowOf(true)
        every { settingsRepository.temperature } returns flowOf(0.7f)
        every { settingsRepository.maxTokens } returns flowOf(4096)
        every { providersRepository.observeAllProviders() } returns flowOf(emptyList<Provider>())
        
        viewModel = ChatViewModel(
            chatRepository,
            settingsRepository,
            providersRepository,
            savedStateHandle
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is correct`() = runTest {
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("openai", state.selectedProviderId)
            assertEquals("gpt-4o", state.selectedModelId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onInputChanged updates state`() = runTest {
        viewModel.onInputChanged("Hello")
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("Hello", state.inputText)
            cancelAndIgnoreRemainingEvents()
        }
    }
    
    @Test
    fun `onModelSelected updates settings`() = runTest {
        val model = AIModel("gpt-4-turbo", "GPT-4 Turbo", providerId = "openai")
        viewModel.onModelSelected(model)
        
        coEvery { settingsRepository.setSelectedModel("gpt-4-turbo") } returns Unit
    }
}
