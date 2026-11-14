package com.titan.zedxai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.titan.zedxai.ai.ProviderCatalog
import com.titan.zedxai.ai.ProviderType
import com.titan.zedxai.data.local.LocalMemoryEntity
import com.titan.zedxai.data.local.ProviderProfileEntity
import com.titan.zedxai.ui.ZedxViewModel
import com.titan.zedxai.ui.theme.CrystalGlassCard

@Composable
fun ProviderManagerScreen(
    viewModel: ZedxViewModel,
    modifier: Modifier = Modifier
) {
    val profiles by viewModel.providerProfiles.collectAsStateWithLifecycle()
    val maskedKeys by viewModel.maskedKeys.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()

    var editingProfile by remember { mutableStateOf<ProviderProfileEntity?>(null) }
    var dailyLimit by remember(prefs.dailyRequestLimit) { mutableFloatStateOf(prefs.dailyRequestLimit.toFloat()) }
    var monthlyBudget by remember(prefs.monthlyBudgetUsd) { mutableFloatStateOf(prefs.monthlyBudgetUsd) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 900.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            CrystalGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Encrypted Key Vault & Limits", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Keys are encrypted with AndroidKeyStore AES-256-GCM on-device. No silent paid-provider fallback is ever permitted.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("Daily Request Limit: ${dailyLimit.toInt()} calls/day", style = MaterialTheme.typography.bodyMedium)
                    Slider(value = dailyLimit, onValueChange = { dailyLimit = it }, valueRange = 10f..1000f)
                    Text("Monthly Paid-Provider Budget: $${"%.2f".format(monthlyBudget)}", style = MaterialTheme.typography.bodyMedium)
                    Slider(value = monthlyBudget, onValueChange = { monthlyBudget = it }, valueRange = 0f..500f)
                    Button(
                        onClick = { viewModel.updateQuotas(dailyLimit.toInt(), monthlyBudget) },
                        modifier = Modifier.testTag("save_quotas_btn")
                    ) {
                        Text("Save Quotas")
                    }
                }
            }
        }

        items(profiles, key = { it.id }) { profile ->
            val type = ProviderType.fromId(profile.providerId)
            val masked = maskedKeys[profile.providerId] ?: "Not configured"
            val isActive = prefs.activeProviderId == profile.providerId

            CrystalGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(profile.displayName, style = MaterialTheme.typography.titleMedium)
                            Text("Model: ${profile.selectedModel}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        if (isActive) {
                            AssistChip(
                                onClick = {},
                                label = { Text("Active") },
                                leadingIcon = { Icon(Icons.Rounded.CheckCircle, null, Modifier.size(16.dp)) }
                            )
                        }
                    }
                    Text(
                        text = if (!type.requiresApiKey) "Auth: Firebase App Check / Local Endpoint" else "Encrypted Key: $masked",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text("Endpoint: ${profile.baseUrl}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Status: ${profile.lastTestStatus}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { editingProfile = profile }) {
                            Icon(Icons.Rounded.Key, null, Modifier.size(16.dp))
                            Spacer(Modifier.size(4.dp))
                            Text("Configure")
                        }
                        OutlinedButton(onClick = {
                            viewModel.testProviderConnection(profile.providerId, profile.baseUrl, profile.selectedModel)
                        }) {
                            Text("Test")
                        }
                        Button(onClick = { viewModel.selectActiveModel(profile.providerId, profile.selectedModel) }) {
                            Text("Use")
                        }
                    }
                }
            }
        }
    }

    editingProfile?.let { target ->
        var baseUrl by remember(target.id) { mutableStateOf(target.baseUrl) }
        var modelId by remember(target.id) { mutableStateOf(target.selectedModel) }
        var apiKey by remember(target.id) { mutableStateOf("") }
        var enabled by remember(target.id) { mutableStateOf(target.enabled) }

        AlertDialog(
            onDismissRequest = { editingProfile = null },
            title = { Text("Configure ${target.displayName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it }, label = { Text("Base URL (HTTPS or localhost)") }, singleLine = true)
                    OutlinedTextField(value = modelId, onValueChange = { modelId = it }, label = { Text("Default Model ID") }, singleLine = true)
                    if (ProviderType.fromId(target.providerId).requiresApiKey) {
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text("API Key (leave blank to keep existing)") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Enabled")
                        Switch(checked = enabled, onCheckedChange = { enabled = it })
                    }
                    if (ProviderType.fromId(target.providerId).requiresApiKey) {
                        TextButton(onClick = {
                            viewModel.deleteProviderKey(target.providerId)
                            editingProfile = null
                        }) {
                            Text("Clear Stored Key", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.saveProviderConfig(target.providerId, baseUrl, modelId, apiKey, enabled)
                    editingProfile = null
                }) { Text("Encrypt & Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingProfile = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun ModelSelectorScreen(
    viewModel: ZedxViewModel,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }

    val models = remember(query) {
        if (query.isBlank()) ProviderCatalog.models
        else ProviderCatalog.models.filter {
            it.displayName.contains(query, ignoreCase = true) ||
                it.providerId.contains(query, ignoreCase = true) ||
                it.bestFor.contains(query, ignoreCase = true)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 900.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            placeholder = { Text("Search Gemini, GPT-4.1, Claude, Groq, DeepSeek, Ollama...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        CrystalGlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Smart Task-Based Router", style = MaterialTheme.typography.titleMedium)
                    Text("Automatically optimizes model choice within your selected provider.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = prefs.smartRoutingEnabled,
                    onCheckedChange = { viewModel.updateRoutingAndSync(prefs.cloudSyncEnabled, it) }
                )
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(models, key = { "${it.providerId}_${it.id}" }) { model ->
                val selected = prefs.activeProviderId == model.providerId && prefs.activeModelId == model.id
                CrystalGlassCard(
                    onClick = {
                        viewModel.selectActiveModel(model.providerId, model.id)
                        onDone()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(model.displayName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${model.providerId.uppercase()} • ${model.contextWindow} ctx • ${if (model.supportsVision) "Vision" else "Text"}${if (model.supportsTools) " • Tools" else ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(model.bestFor, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (selected) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MemoryManagerScreen(
    viewModel: ZedxViewModel,
    modifier: Modifier = Modifier
) {
    val memories by viewModel.memories.collectAsStateWithLifecycle()
    var search by remember { mutableStateOf("") }
    var editingMemory by remember { mutableStateOf<LocalMemoryEntity?>(null) }
    var showCreate by remember { mutableStateOf(false) }

    val filtered = remember(memories, search) {
        if (search.isBlank()) memories
        else memories.filter {
            it.title.contains(search, ignoreCase = true) ||
                it.content.contains(search, ignoreCase = true)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 900.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("AI Memory & Personalization", style = MaterialTheme.typography.headlineMedium)
                Text("Per-user isolated. Passwords, API keys & tokens are automatically blocked.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = { showCreate = true }, modifier = Modifier.testTag("add_memory_btn")) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.size(4.dp))
                Text("Add")
            }
        }

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            placeholder = { Text("Search saved memories...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (memories.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { viewModel.clearAllMemories() }) {
                    Text("Clear All Memories", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(filtered, key = { it.id }) { mem ->
                CrystalGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(mem.title, style = MaterialTheme.typography.titleMedium)
                                Text(mem.category.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            Switch(checked = mem.enabled, onCheckedChange = { viewModel.toggleMemory(mem) })
                            IconButton(onClick = { editingMemory = mem }) {
                                Icon(Icons.Rounded.Edit, contentDescription = "Edit memory")
                            }
                            IconButton(onClick = { viewModel.deleteMemory(mem.id) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Delete memory")
                            }
                        }
                        Text(mem.content, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }

    if (showCreate || editingMemory != null) {
        val target = editingMemory
        var title by remember(target) { mutableStateOf(target?.title.orEmpty()) }
        var content by remember(target) { mutableStateOf(target?.content.orEmpty()) }
        var category by remember(target) { mutableStateOf(target?.category ?: "preference") }

        AlertDialog(
            onDismissRequest = {
                showCreate = false
                editingMemory = null
            },
            title = { Text(if (target == null) "Add AI Memory" else "Edit AI Memory") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("preference", "coding", "workflow", "language").forEach { cat ->
                            FilterChip(selected = category == cat, onClick = { category = cat }, label = { Text(cat) })
                        }
                    }
                    OutlinedTextField(value = content, onValueChange = { content = it }, label = { Text("Preference / Context") }, minLines = 3)
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (title.isNotBlank() && content.isNotBlank()) {
                        val ok = viewModel.saveMemory(target?.id, title, content, category, target?.enabled ?: true)
                        if (ok) {
                            showCreate = false
                            editingMemory = null
                        }
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCreate = false
                    editingMemory = null
                }) { Text("Cancel") }
            }
        )
    }
}
