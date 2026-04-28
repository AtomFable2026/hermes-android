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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aetheris.chat.data.model.AIModel
import com.aetheris.chat.data.model.DefaultProviders
import com.aetheris.chat.data.model.Provider
import com.aetheris.chat.ui.theme.AetherisPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelector(
    selectedProviderId: String,
    selectedModelId: String,
    onProviderSelected: (Provider) -> Unit,
    onModelSelected: (AIModel) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSheet by remember { mutableStateOf(false) }

    val provider = DefaultProviders.allProviders.find { it.id == selectedProviderId }
        ?: DefaultProviders.openAI
    val model = provider.models.find { it.id == selectedModelId }
        ?: provider.models.firstOrNull()

    // Compact selector chip
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
                    text = provider.name,
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
                selectedProviderId = selectedProviderId,
                selectedModelId = selectedModelId,
                onProviderSelected = onProviderSelected,
                onModelSelected = {
                    onModelSelected(it)
                    showSheet = false
                }
            )
        }
    }
}

@Composable
private fun ModelSelectorContent(
    selectedProviderId: String,
    selectedModelId: String,
    onProviderSelected: (Provider) -> Unit,
    onModelSelected: (AIModel) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp)
    ) {
        Text(
            text = "Choose a Model",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Provider tabs
        var activeProvider by remember { mutableStateOf(selectedProviderId) }

        ScrollableTabRow(
            selectedTabIndex = DefaultProviders.allProviders.indexOfFirst { it.id == activeProvider }
                .coerceAtLeast(0),
            edgePadding = 0.dp,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = AetherisPrimary,
            divider = {}
        ) {
            DefaultProviders.allProviders.forEach { provider ->
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

        Spacer(modifier = Modifier.height(12.dp))

        // Model list for active provider
        val models = DefaultProviders.allProviders
            .find { it.id == activeProvider }?.models ?: emptyList()

        LazyColumn {
            items(models) { model ->
                ModelItem(
                    model = model,
                    isSelected = model.id == selectedModelId && activeProvider == selectedProviderId,
                    onClick = { onModelSelected(model) }
                )
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
