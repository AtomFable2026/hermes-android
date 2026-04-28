package com.aetheris.chat.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aetheris.chat.data.model.DefaultProviders
import com.aetheris.chat.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.saveMessage) {
        uiState.saveMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSaveMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // ── API Keys Section ──
            SettingsSection(title = "🔑 API Keys") {
                DefaultProviders.allProviders
                    .filter { it.id != "custom" }
                    .forEach { provider ->
                        ApiKeyInput(
                            providerName = provider.name,
                            currentKey = uiState.apiKeys[provider.id] ?: "",
                            onSave = { key -> viewModel.saveApiKey(provider.id, key) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
            }

            // ── Custom Provider Section ──
            SettingsSection(title = "🔧 Custom Provider") {
                OutlinedTextField(
                    value = uiState.customBaseUrl,
                    onValueChange = viewModel::setCustomBaseUrl,
                    label = { Text("Base URL") },
                    placeholder = { Text("https://your-server.com/v1") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = uiState.customModelId,
                    onValueChange = viewModel::setCustomModelId,
                    label = { Text("Model ID") },
                    placeholder = { Text("gpt-4o or custom-model") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                ApiKeyInput(
                    providerName = "Custom",
                    currentKey = uiState.apiKeys["custom"] ?: "",
                    onSave = { key -> viewModel.saveApiKey("custom", key) }
                )
            }

            // ── Chat Settings ──
            SettingsSection(title = "💬 Chat Settings") {
                OutlinedTextField(
                    value = uiState.systemPrompt,
                    onValueChange = viewModel::setSystemPrompt,
                    label = { Text("System Prompt") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 3,
                    maxLines = 6
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Streaming toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Streaming Responses",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "Show tokens as they arrive",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.streamingEnabled,
                        onCheckedChange = viewModel::setStreamingEnabled,
                        colors = SwitchDefaults.colors(checkedTrackColor = AetherisPrimary)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Temperature slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Temperature",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "%.1f".format(uiState.temperature),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AetherisPrimary
                        )
                    }
                    Slider(
                        value = uiState.temperature,
                        onValueChange = viewModel::setTemperature,
                        valueRange = 0f..2f,
                        steps = 19,
                        colors = SliderDefaults.colors(
                            thumbColor = AetherisPrimary,
                            activeTrackColor = AetherisPrimary
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Precise",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Creative",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Max tokens
                OutlinedTextField(
                    value = uiState.maxTokens.toString(),
                    onValueChange = { value ->
                        value.toIntOrNull()?.let { viewModel.setMaxTokens(it.coerceIn(1, 32768)) }
                    },
                    label = { Text("Max Tokens") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            // ── About ──
            SettingsSection(title = "ℹ️ About") {
                Text(
                    text = "Aetheris AI v1.0.0",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Your intelligent companion powered by the world's best AI models. Supports OpenAI, Anthropic, Groq, OpenRouter, and custom OpenAI-compatible endpoints.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                content = content
            )
        }
    }
}

@Composable
private fun ApiKeyInput(
    providerName: String,
    currentKey: String,
    onSave: (String) -> Unit
) {
    var key by remember(currentKey) { mutableStateOf(currentKey) }
    var isVisible by remember { mutableStateOf(false) }
    val hasKey = currentKey.isNotBlank()

    OutlinedTextField(
        value = key,
        onValueChange = { key = it },
        label = { Text("$providerName API Key") },
        placeholder = {
            Text(if (hasKey) "••••••••••••" else "Enter API key")
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        singleLine = true,
        visualTransformation = if (isVisible)
            VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            Row {
                IconButton(onClick = { isVisible = !isVisible }) {
                    Icon(
                        if (isVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "Toggle visibility"
                    )
                }
                if (key != currentKey && key.isNotBlank()) {
                    IconButton(onClick = { onSave(key) }) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Save",
                            tint = SuccessGreen
                        )
                    }
                }
            }
        },
        leadingIcon = {
            Icon(
                if (hasKey) Icons.Default.CheckCircle else Icons.Default.Key,
                contentDescription = null,
                tint = if (hasKey) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
}
