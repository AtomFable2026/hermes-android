package com.aetheris.chat.ui.screens.chat

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aetheris.chat.ui.components.*
import com.aetheris.chat.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: Long?,
    onNavigateBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Auto-scroll to bottom on new messages
    LaunchedEffect(uiState.messages.size, uiState.messages.lastOrNull()?.content) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    // Surface errors via Snackbar; ChatViewModel.init already loads the conversation
    // from the SavedStateHandle, so no LaunchedEffect on conversationId is needed here.
    LaunchedEffect(uiState.error) {
        val err = uiState.error
        if (err != null) {
            snackbarHostState.showSnackbar(err)
            viewModel.clearError()
        }
    }
    LaunchedEffect(uiState.info) {
        val info = uiState.info
        if (info != null) {
            snackbarHostState.showSnackbar(info)
            viewModel.clearInfo()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    ModelSelector(
                        providers = uiState.availableProviders,
                        selectedProviderId = uiState.selectedProviderId,
                        selectedModelId = uiState.selectedModelId,
                        onProviderSelected = viewModel::onProviderSelected,
                        onModelSelected = viewModel::onModelSelected,
                        onRefreshModels = viewModel::refreshModels,
                        isRefreshing = uiState.isRefreshingModels
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::newChat) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New Chat",
                            tint = AetherisPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            MessageInput(
                value = uiState.inputText,
                onValueChange = viewModel::onInputChanged,
                onSend = viewModel::sendMessage,
                onStop = viewModel::stopGeneration,
                isLoading = uiState.isLoading
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (uiState.messages.isEmpty()) {
                EmptyChat(onSuggestionClick = viewModel::onInputChanged)
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(
                        items = uiState.messages,
                        key = { it.id }
                    ) { message ->
                        ChatBubble(message = message)
                    }

                    // Show typing indicator when loading and no streaming content yet
                    if (uiState.isLoading && (uiState.messages.lastOrNull()?.content?.isEmpty() == true)) {
                        item {
                            TypingIndicator()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyChat(onSuggestionClick: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "✦",
            fontSize = MaterialTheme.typography.displayLarge.fontSize * 2,
            textAlign = TextAlign.Center,
            color = AetherisPrimary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Aetheris AI",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Ask anything — I'm here to help.\nPowered by the world's best AI models.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Suggestion chips
        val suggestions = listOf(
            "✍️ Write a story",
            "💻 Help me code",
            "📝 Summarize text",
            "🧠 Explain a concept"
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            suggestions.chunked(2).forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    row.forEach { suggestion ->
                        SuggestionChip(
                            onClick = { onSuggestionClick(suggestion.substringAfter(' ')) },
                            label = {
                                Text(
                                    text = suggestion,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            modifier = Modifier.weight(1f),
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                }
            }
        }
    }
}
