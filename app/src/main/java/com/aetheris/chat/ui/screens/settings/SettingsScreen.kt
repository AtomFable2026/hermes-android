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
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aetheris.chat.data.model.CustomProvider
import com.aetheris.chat.data.model.DefaultProviders
import com.aetheris.chat.data.model.Provider
import com.aetheris.chat.data.model.ProviderType
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
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearErrorMessage()
        }
    }

    var showAddCustom by remember { mutableStateOf(false) }
    var editingProvider by remember { mutableStateOf<CustomProvider?>(null) }
    var addingModelFor by remember { mutableStateOf<Provider?>(null) }
    var showBackupDialog by remember { mutableStateOf<Boolean?>(null) } // true = export, false = import
    var backupPassword by remember { mutableStateOf("") }

    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let {
            val file = File(context.cacheDir, "temp_backup.bin")
            viewModel.exportBackup(backupPassword, file)
            file.inputStream().use { input ->
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    input.copyTo(output)
                }
            }
            file.delete()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val file = File(context.cacheDir, "temp_import.bin")
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            viewModel.importBackup(backupPassword, file)
            file.delete()
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
            // ── Built-in Providers ──
            SettingsSection(title = "Built-in providers") {
                DefaultProviders.builtIn.forEach { provider ->
                    val merged = uiState.mergedProviders.find { it.id == provider.id } ?: provider
                    ProviderCard(
                        title = provider.name,
                        subtitle = provider.baseUrl,
                        models = merged.models.size,
                        apiKey = uiState.apiKeys[provider.id] ?: "",
                        isRefreshing = uiState.refreshingProviderId == provider.id,
                        onSaveKey = { viewModel.saveApiKey(provider.id, it) },
                        onDeleteKey = { viewModel.deleteApiKey(provider.id) },
                        onRefresh = { viewModel.refreshModels(provider.id) },
                        onAddModel = { addingModelFor = merged }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // ── Custom Providers ──
            SettingsSection(title = "Custom providers") {
                if (uiState.customProviders.isEmpty()) {
                    Text(
                        text = "Add any OpenAI-compatible or Anthropic-compatible endpoint — Ollama, vLLM, LM Studio, your own gateway, etc.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                uiState.customProviders.forEach { custom ->
                    val merged = uiState.mergedProviders.find { it.id == custom.providerKey }
                    ProviderCard(
                        title = custom.name,
                        subtitle = custom.baseUrl.ifBlank { "(no base URL)" } + " · ${custom.type.displayName()}",
                        models = merged?.models?.size ?: 0,
                        apiKey = uiState.apiKeys[custom.providerKey] ?: "",
                        isRefreshing = uiState.refreshingProviderId == custom.providerKey,
                        onSaveKey = { viewModel.saveApiKey(custom.providerKey, it) },
                        onDeleteKey = { viewModel.deleteApiKey(custom.providerKey) },
                        onRefresh = { viewModel.refreshModels(custom.providerKey) },
                        onAddModel = { merged?.let { addingModelFor = it } },
                        onEdit = { editingProvider = custom },
                        onDelete = { viewModel.deleteCustomProvider(custom.id) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Button(
                    onClick = { showAddCustom = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AetherisPrimary)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add custom provider")
                }
            }

            // ── Chat Settings ──
            SettingsSection(title = "Chat") {
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

                ToggleRow(
                    title = "Streaming responses",
                    subtitle = "Show tokens as they arrive",
                    checked = uiState.streamingEnabled,
                    onCheckedChange = viewModel::setStreamingEnabled
                )

                Spacer(modifier = Modifier.height(16.dp))

                ToggleRow(
                    title = "Dark theme",
                    subtitle = "Override the system setting",
                    checked = uiState.darkMode,
                    onCheckedChange = viewModel::setDarkMode
                )

                Spacer(modifier = Modifier.height(16.dp))

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
            SettingsSection(title = "About") {
                Text(
                    text = "Aetheris AI v1.0.0",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Your intelligent companion powered by the world's best AI models. Supports OpenAI, Anthropic, Groq, OpenRouter, and any number of custom OpenAI- or Anthropic-compatible endpoints.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── Data Management ──
            SettingsSection(title = "Data Management") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showBackupDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Upload, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export")
                    }
                    OutlinedButton(
                        onClick = { showBackupDialog = false },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Download, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Import")
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showBackupDialog != null) {
        BackupPasswordDialog(
            isExport = showBackupDialog!!,
            onDismiss = { showBackupDialog = null },
            onConfirm = { password ->
                backupPassword = password
                if (showBackupDialog == true) {
                    exportLauncher.launch("aetheris_backup_${System.currentTimeMillis()}.bin")
                } else {
                    importLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                }
                showBackupDialog = null
            }
        )
    }

    if (showAddCustom) {
        CustomProviderDialog(
            initialName = "",
            initialBaseUrl = "",
            initialType = ProviderType.OPENAI_COMPATIBLE,
            initialApiKey = "",
            onDismiss = { showAddCustom = false },
            onConfirm = { name, baseUrl, type, key ->
                viewModel.addCustomProvider(name, baseUrl, type, key)
                showAddCustom = false
            }
        )
    }

    editingProvider?.let { provider ->
        CustomProviderDialog(
            initialName = provider.name,
            initialBaseUrl = provider.baseUrl,
            initialType = provider.type,
            initialApiKey = uiState.apiKeys[provider.providerKey] ?: "",
            isEdit = true,
            onDismiss = { editingProvider = null },
            onConfirm = { name, baseUrl, type, key ->
                viewModel.updateCustomProvider(provider.id, name, baseUrl, type)
                if (key.isNotBlank()) viewModel.saveApiKey(provider.providerKey, key)
                editingProvider = null
            }
        )
    }

    addingModelFor?.let { provider ->
        AddModelDialog(
            providerName = provider.name,
            onDismiss = { addingModelFor = null },
            onConfirm = { modelId, displayName ->
                viewModel.addCustomModel(provider.id, modelId, displayName)
                addingModelFor = null
            }
        )
    }
}

private fun ProviderType.displayName(): String = when (this) {
    ProviderType.OPENAI_COMPATIBLE -> "OpenAI compatible"
    ProviderType.ANTHROPIC -> "Anthropic compatible"
}

@Composable
private fun ProviderCard(
    title: String,
    subtitle: String,
    models: Int,
    apiKey: String,
    isRefreshing: Boolean,
    onSaveKey: (String) -> Unit,
    onDeleteKey: () -> Unit,
    onRefresh: () -> Unit,
    onAddModel: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                    Text(
                        text = "$models model${if (models == 1) "" else "s"} cached",
                        style = MaterialTheme.typography.labelSmall,
                        color = AetherisPrimary
                    )
                }
                if (onEdit != null) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ErrorRed)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            ApiKeyInput(
                providerName = title,
                currentKey = apiKey,
                onSave = onSaveKey,
                onClear = onDeleteKey
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onRefresh,
                    enabled = !isRefreshing,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = AetherisPrimary
                        )
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Fetch models")
                    }
                }
                OutlinedButton(
                    onClick = onAddModel,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add model")
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = AetherisPrimary)
        )
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
    onSave: (String) -> Unit,
    onClear: (() -> Unit)? = null
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
                } else if (hasKey && onClear != null) {
                    IconButton(onClick = onClear) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = "Remove key",
                            tint = ErrorRed
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

@Composable
private fun CustomProviderDialog(
    initialName: String,
    initialBaseUrl: String,
    initialType: ProviderType,
    initialApiKey: String,
    isEdit: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (name: String, baseUrl: String, type: ProviderType, apiKey: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var baseUrl by remember { mutableStateOf(initialBaseUrl) }
    var type by remember { mutableStateOf(initialType) }
    var key by remember { mutableStateOf(initialApiKey) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Edit custom provider" else "Add custom provider") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    placeholder = { Text("My Ollama, Local LLM, etc.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    placeholder = { Text("https://api.example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Text("API style", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == ProviderType.OPENAI_COMPATIBLE,
                        onClick = { type = ProviderType.OPENAI_COMPATIBLE },
                        label = { Text("OpenAI compatible") }
                    )
                    FilterChip(
                        selected = type == ProviderType.ANTHROPIC,
                        onClick = { type = ProviderType.ANTHROPIC },
                        label = { Text("Anthropic") }
                    )
                }
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("API key (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = PasswordVisualTransformation()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, baseUrl, type, key) },
                enabled = baseUrl.isNotBlank()
            ) {
                Text(if (isEdit) "Save" else "Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun AddModelDialog(
    providerName: String,
    onDismiss: () -> Unit,
    onConfirm: (modelId: String, displayName: String?) -> Unit
) {
    var modelId by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add model · $providerName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = modelId,
                    onValueChange = { modelId = it },
                    label = { Text("Model ID") },
                    placeholder = { Text("gpt-4o, claude-sonnet-4-20250514, llama3:70b…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Display name (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(modelId, displayName.ifBlank { null }) },
                enabled = modelId.isNotBlank()
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun BackupPasswordDialog(
    isExport: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isExport) "Set Backup Password" else "Enter Backup Password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (isExport)
                        "Your data will be encrypted with AES-256. Please remember this password; without it, you cannot restore your backup."
                    else
                        "Enter the password used to encrypt the backup file.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                if (isExport) {
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirm Password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(password) },
                enabled = password.isNotBlank() && (!isExport || password == confirmPassword)
            ) { Text(if (isExport) "Export" else "Import") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
