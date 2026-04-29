package com.aetheris.chat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aetheris.chat.data.model.AIModel
import com.aetheris.chat.data.model.Provider
import com.aetheris.chat.ui.theme.AetherisPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelector(
    providers: List<Provider>,
    selectedProviderId: String,
    selectedModelId: String,
    onProviderSelected: (Provider) -> Unit,
    onModelSelected: (AIModel) -> Unit,
    onRefreshModels: ((Provider) -> Unit)? = null,
    isRefreshing: Boolean = false,
    modifier: Modifier = Modifier
) {
    var showSheet by remember { mutableStateOf(false) }

    val provider = providers.find { it.id == selectedProviderId }
        ?: providers.firstOrNull()
    val model = provider?.models?.find { it.id == selectedModelId }
        ?: provider?.models?.firstOrNull()

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { showSheet = true },
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = provider?.name ?: "No provider",
                    style = MaterialTheme.typography.labelSmall,
                    color = AetherisPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = model?.name ?: "Select Model",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = "Change model",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            ModelSelectorContent(
                providers = providers,
                selectedProviderId = selectedProviderId,
                selectedModelId = selectedModelId,
                onProviderSelected = onProviderSelected,
                onModelSelected = {
                    onModelSelected(it)
                    showSheet = false
                },
                onRefreshModels = onRefreshModels,
                isRefreshing = isRefreshing
            )
        }
    }
}

@Composable
private fun ModelSelectorContent(
    providers: List<Provider>,
    selectedProviderId: String,
    selectedModelId: String,
    onProviderSelected: (Provider) -> Unit,
    onModelSelected: (AIModel) -> Unit,
    onRefreshModels: ((Provider) -> Unit)?,
    isRefreshing: Boolean
) {
    var searchQuery by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = "Choose a Model",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            val activeProviderObj = providers.find { it.id == selectedProviderId }
            if (onRefreshModels != null && activeProviderObj != null) {
                IconButton(onClick = { onRefreshModels(activeProviderObj) }, enabled = !isRefreshing) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = AetherisPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh model list",
                            tint = AetherisPrimary
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            placeholder = { Text("Search models...", style = MaterialTheme.typography.bodyMedium) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AetherisPrimary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            )
        )

        var activeProvider by remember(selectedProviderId) { mutableStateOf(selectedProviderId) }
        val activeIndex = providers.indexOfFirst { it.id == activeProvider }.coerceAtLeast(0)

        if (providers.isNotEmpty()) {
            ScrollableTabRow(
                selectedTabIndex = activeIndex,
                edgePadding = 0.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = AetherisPrimary,
                divider = {}
            ) {
                providers.forEach { provider ->
                    Tab(
                        selected = provider.id == activeProvider,
                        onClick = {
                            activeProvider = provider.id
                            onProviderSelected(provider)
                        },
                        text = {
                            Text(
                                text = provider.name,
                                fontWeight = if (provider.id == activeProvider)
                                    FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val allModels = providers.find { it.id == activeProvider }?.models ?: emptyList()
        val filteredModels = if (searchQuery.isBlank()) {
            allModels
        } else {
            allModels.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                    it.id.contains(searchQuery, ignoreCase = true)
            }
        }

        if (filteredModels.isEmpty()) {
            val emptyMsg = if (searchQuery.isNotBlank()) {
                "No models match \"$searchQuery\""
            } else {
                "No models yet. Tap the refresh icon above to fetch models from this provider, or add one manually in Settings."
            }
            Text(
                text = emptyMsg,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp)
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                items(filteredModels) { model ->
                    ModelItem(
                        model = model,
                        isSelected = model.id == selectedModelId && activeProvider == selectedProviderId,
                        onClick = { onModelSelected(model) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelItem(
    model: AIModel,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = if (isSelected)
            AetherisPrimary.copy(alpha = 0.1f)
        else
            MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = model.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) AetherisPrimary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = model.id,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = AetherisPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
