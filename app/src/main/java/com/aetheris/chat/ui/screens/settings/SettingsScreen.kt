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
                                "设置",
                                fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
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
            SettingsSection(title = "内置服务商") {
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
            SettingsSection(title = "自定义服务商") {
                if (uiState.customProviders.isEmpty()) {
                    Text(
                        text = "添加任意 OpenAI 兼容或 Anthropic 兼容的服务商 — Ollama, vLLM, LM Studio, 你自己的网关等。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                uiState.customProviders.forEach { custom ->
                    val merged = uiState.mergedProviders.find { it.id == custom.providerKey }
                    ProviderCard(
                        title = custom.name,
                        subtitle = custom.baseUrl.ifBlank { "(无地址)" } + " · ${custom.type.displayName()}",
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
                    Text("添加自定义服务商")
                }
            }

            // ── Chat Settings ──
            SettingsSection(title = "聊天") {
                OutlinedTextField(
                    value = uiState.systemPrompt,
                    onValueChange = viewModel::setSystemPrompt,
                    label = { Text("系统提示词") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 3,
                    maxLines = 6
                )

                Spacer(modifier = Modifier.height(16.dp))

                ToggleRow(
                    title = "流式响应",
                    subtitle = "逐字显示回复内容",
                    checked = uiState.streamingEnabled,
                    onCheckedChange = viewModel::setStreamingEnabled
                )

                Spacer(modifier = Modifier.height(16.dp))

                ToggleRow(
                    title = "深色主题",
                    subtitle = "覆盖系统设置",
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
                        "温度",
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
                            "精确",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "创意",
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
                    label = { Text("最大 Token 数") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            // ── About ──
            SettingsSection(title = "关于") {
                Text(
                    text = "Hermes AI v1.0.0",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "你的智能伙伴，由全球最先进的 AI 模型驱动。支持 OpenAI、Anthropic、Groq、OpenRouter 以及任意数量的 OpenAI 或 Anthropic 兼容的自定义服务商。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── Data Management ──
            SettingsSection(title = "数据管理") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showBackupDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Upload, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("导出")
                    }
                    OutlinedButton(
                        onClick = { showBackupDialog = false },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Download, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("导入")
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
    ProviderType.OPENAI_COMPATIBLE -> "OpenAI 兼容"
    ProviderType.ANTHROPIC -> "Anthropic 兼容"
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
                        text = "$models 个模型已缓存",
                        style = MaterialTheme.typography.labelSmall,
                        color = AetherisPrimary
                    )
                }
                if (onEdit != null) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "编辑")
                    }
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "删除", tint = ErrorRed)
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
                        Text("获取模型")
                    }
                }
                OutlinedButton(
                    onClick = onAddModel,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("添加模型")
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
        label = { Text("$providerName API 密钥") },
        placeholder = {
            Text(if (hasKey) "••••••••••••" else "输入 API 密钥")
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
                        contentDescription = "切换可见性"
                    )
                }
                if (key != currentKey && key.isNotBlank()) {
                    IconButton(onClick = { onSave(key) }) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "保存",
                            tint = SuccessGreen
                        )
                    }
                } else if (hasKey && onClear != null) {
                    IconButton(onClick = onClear) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = "移除密钥",
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
        title = { Text(if (isEdit) "编辑自定义服务商" else "添加自定义服务商") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    placeholder = { Text("我的 Ollama, 本地 LLM 等") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("API 地址") },
                    placeholder = { Text("https://api.example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Text("API 类型", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == ProviderType.OPENAI_COMPATIBLE,
                        onClick = { type = ProviderType.OPENAI_COMPATIBLE },
                        label = { Text("OpenAI 兼容") }
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
                    label = { Text("API 密钥（可选）") },
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
                Text(if (isEdit) "保存" else "添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
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
        title = { Text("添加模型 · $providerName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = modelId,
                    onValueChange = { modelId = it },
                    label = { Text("模型 ID") },
                    placeholder = { Text("gpt-4o, claude-sonnet-4-20250514, llama3:70b…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("显示名称（可选）") },
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
            ) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
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
        title = { Text(if (isExport) "设置备份密码" else "输入备份密码") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (isExport)
                        "你的数据将使用 AES-256 加密。请记住这个密码；没有它你将无法恢复备份。"
                    else
                        "请输入用于加密备份文件的密码。",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码") },
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
                        label = { Text("确认密码") },
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
            ) { Text(if (isExport) "导出" else "导入") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
