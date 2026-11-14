package com.titan.zedxai.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.titan.zedxai.agents.AgentRunStatus
import com.titan.zedxai.agents.TitanAgent
import com.titan.zedxai.ui.ZedxViewModel
import com.titan.zedxai.ui.components.MarkdownMessageContent
import com.titan.zedxai.ui.components.NeuralZOrb
import com.titan.zedxai.ui.theme.CrystalGlassCard
import com.titan.zedxai.voice.OrbVisualState
import com.titan.zedxai.voice.TitanVoiceController
import com.titan.zedxai.voice.ZedxVoiceForegroundService
import java.util.UUID

@Composable
fun VoiceAssistantScreen(
    viewModel: ZedxViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val chatState by viewModel.chatState.collectAsStateWithLifecycle()
    val messages by viewModel.activeMessages.collectAsStateWithLifecycle()

    var controllerHolder by remember { mutableStateOf<TitanVoiceController?>(null) }

    val controller = remember {
        TitanVoiceController(context) { transcript ->
            viewModel.sendMessage(transcript) { reply ->
                controllerHolder?.speakResponse(reply)
            }
        }.also { controllerHolder = it }
    }

    DisposableEffect(Unit) {
        onDispose { controller.release() }
    }

    LaunchedEffect(prefs.voiceLanguage, prefs.voiceSpeechRate, prefs.voicePitch, prefs.wakeWordEnabled) {
        controller.updateSettings(
            languageTag = prefs.voiceLanguage,
            speechRate = prefs.voiceSpeechRate,
            pitch = prefs.voicePitch,
            wakeWordArmed = prefs.wakeWordEnabled
        )
    }

    val voiceState by controller.state.collectAsStateWithLifecycle()
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted) {
            if (prefs.voiceSessionForegroundEnabled) ZedxVoiceForegroundService.start(context)
            controller.startListening()
        } else {
            viewModel.showBanner("Microphone permission is needed only when you initiate voice capture.")
        }
    }

    val effectiveOrbState = when {
        chatState.isStreaming -> OrbVisualState.THINKING
        else -> voiceState.orbState
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 900.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            CrystalGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 28.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    NeuralZOrb(
                        orbState = effectiveOrbState,
                        amplitude = voiceState.rmsNormalized,
                        size = 188.dp,
                        onClick = {
                            if (hasMicPermission) {
                                if (prefs.voiceSessionForegroundEnabled) ZedxVoiceForegroundService.start(context)
                                controller.startListening()
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(voiceState.statusMessage, style = MaterialTheme.typography.titleMedium)
                    if (voiceState.liveTranscript.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "\"${voiceState.liveTranscript}\"",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (hasMicPermission) {
                                    if (prefs.voiceSessionForegroundEnabled) ZedxVoiceForegroundService.start(context)
                                    controller.startListening()
                                } else {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("voice_ptt_button")
                        ) {
                            Icon(Icons.Rounded.Mic, null)
                            Spacer(Modifier.size(6.dp))
                            Text("Push to Talk")
                        }
                        OutlinedButton(
                            onClick = { controller.toggleMute() },
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(if (voiceState.isMuted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp, null)
                            Spacer(Modifier.size(4.dp))
                            Text(if (voiceState.isMuted) "Muted" else "Audio")
                        }
                        OutlinedButton(
                            onClick = {
                                viewModel.stopGeneration()
                                controller.stopAll()
                            },
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("voice_stop_all_button")
                        ) {
                            Icon(Icons.Rounded.Stop, null)
                            Spacer(Modifier.size(4.dp))
                            Text("Stop All")
                        }
                    }
                }
            }
        }

        // Latest spoken AI reply
        val latestReply = chatState.streamingText.ifBlank {
            messages.lastOrNull { it.role == "assistant" }?.content.orEmpty()
        }
        if (latestReply.isNotBlank()) {
            item {
                CrystalGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Titan Voice Response", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(6.dp))
                        MarkdownMessageContent(latestReply)
                    }
                }
            }
        }

        // Language, Rate, Pitch, Wake-word & Foreground controls
        item {
            CrystalGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Voice & Language Matrix", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("en-IN" to "English", "hi-IN" to "Hindi", "hinglish" to "Hinglish").forEach { (tag, label) ->
                            FilterChip(
                                selected = prefs.voiceLanguage == tag,
                                onClick = {
                                    viewModel.updateVoicePreferences(
                                        tag, prefs.voiceSpeechRate, prefs.voicePitch,
                                        prefs.wakeWordEnabled, prefs.voiceSessionForegroundEnabled
                                    )
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                    Text("Speech Rate: ${"%.2f".format(prefs.voiceSpeechRate)}x", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = prefs.voiceSpeechRate,
                        onValueChange = {
                            viewModel.updateVoicePreferences(
                                prefs.voiceLanguage, it, prefs.voicePitch,
                                prefs.wakeWordEnabled, prefs.voiceSessionForegroundEnabled
                            )
                        },
                        valueRange = 0.6f..1.8f
                    )
                    Text("Pitch: ${"%.2f".format(prefs.voicePitch)}", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = prefs.voicePitch,
                        onValueChange = {
                            viewModel.updateVoicePreferences(
                                prefs.voiceLanguage, prefs.voiceSpeechRate, it,
                                prefs.wakeWordEnabled, prefs.voiceSessionForegroundEnabled
                            )
                        },
                        valueRange = 0.6f..1.6f
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Wake-word Architecture ('Hey Titan')", style = MaterialTheme.typography.titleMedium)
                            Text("Strips wake-phrase when armed. Zero secret background recording.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = prefs.wakeWordEnabled,
                            onCheckedChange = {
                                viewModel.updateVoicePreferences(
                                    prefs.voiceLanguage, prefs.voiceSpeechRate, prefs.voicePitch,
                                    it, prefs.voiceSessionForegroundEnabled
                                )
                            }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Visible Foreground Voice Session", style = MaterialTheme.typography.titleMedium)
                            Text("Shows persistent notification indicator while active.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = prefs.voiceSessionForegroundEnabled,
                            onCheckedChange = {
                                viewModel.updateVoicePreferences(
                                    prefs.voiceLanguage, prefs.voiceSpeechRate, prefs.voicePitch,
                                    prefs.wakeWordEnabled, it
                                )
                                if (!it) ZedxVoiceForegroundService.stop(context)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TitanCrewScreen(
    viewModel: ZedxViewModel,
    modifier: Modifier = Modifier
) {
    val agents by viewModel.crewOrchestrator.agents.collectAsStateWithLifecycle()
    val runs by viewModel.crewOrchestrator.runs.collectAsStateWithLifecycle()
    var selectedAgent by remember(agents) { mutableStateOf(agents.first()) }
    var taskInput by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 900.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Five-Agent Titan Crew", style = MaterialTheme.typography.headlineMedium)
                    Text("Up to 5 concurrent agents • Confirmation for sensitive ops", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = { showCreateDialog = true }, modifier = Modifier.testTag("create_custom_agent_btn")) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.size(4.dp))
                    Text("Custom")
                }
            }
        }

        item {
            CrystalGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Select Agent & Assign Task", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        agents.take(3).forEach { agent ->
                            FilterChip(
                                selected = selectedAgent.id == agent.id,
                                onClick = {
                                    selectedAgent = agent
                                    viewModel.selectActiveAgent(agent.id)
                                },
                                label = { Text(agent.name) }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        agents.drop(3).forEach { agent ->
                            FilterChip(
                                selected = selectedAgent.id == agent.id,
                                onClick = {
                                    selectedAgent = agent
                                    viewModel.selectActiveAgent(agent.id)
                                },
                                label = { Text(agent.name) }
                            )
                        }
                    }
                    Text(
                        "${selectedAgent.roleTitle} • ${selectedAgent.capabilities.joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedTextField(
                        value = taskInput,
                        onValueChange = { taskInput = it },
                        label = { Text("Task for ${selectedAgent.name}") },
                        minLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("agent_task_input")
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (taskInput.isNotBlank()) {
                                    viewModel.dispatchAgentTask(selectedAgent, taskInput)
                                    taskInput = ""
                                }
                            },
                            modifier = Modifier.weight(1f).testTag("dispatch_agent_btn")
                        ) {
                            Icon(Icons.Rounded.PlayArrow, null)
                            Spacer(Modifier.size(4.dp))
                            Text("Dispatch Agent")
                        }
                        OutlinedButton(onClick = { viewModel.crewOrchestrator.cancelAll() }) {
                            Text("Stop All")
                        }
                    }
                }
            }
        }

        items(runs, key = { it.runId }) { run ->
            CrystalGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${run.agentName} • ${run.status.name}", style = MaterialTheme.typography.titleMedium)
                        if (run.status == AgentRunStatus.RUNNING || run.status == AgentRunStatus.QUEUED) {
                            IconButton(onClick = { viewModel.crewOrchestrator.cancelRun(run.runId) }) {
                                Icon(Icons.Rounded.Cancel, contentDescription = "Cancel agent task")
                            }
                        }
                    }
                    Text(run.taskPrompt, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LinearProgressIndicator(progress = { run.progress }, modifier = Modifier.fillMaxWidth())

                    if (run.requiresApproval && run.status == AgentRunStatus.AWAITING_CONFIRMATION) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.approveAgentRun(run.runId) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                            ) {
                                Icon(Icons.Rounded.CheckCircle, null)
                                Spacer(Modifier.size(4.dp))
                                Text("Confirm Sensitive Op")
                            }
                            OutlinedButton(onClick = { viewModel.crewOrchestrator.cancelRun(run.runId) }) {
                                Text("Deny")
                            }
                        }
                    }

                    if (run.output.isNotBlank()) {
                        MarkdownMessageContent(run.output)
                    }
                    run.logs.forEach { logLine ->
                        Text("• $logLine", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        var role by remember { mutableStateOf("") }
        var instructions by remember { mutableStateOf("") }
        var confirmSensitive by remember { mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create Custom Titan Agent") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Agent Name") }, singleLine = true)
                    OutlinedTextField(value = role, onValueChange = { role = it }, label = { Text("Role Title") }, singleLine = true)
                    OutlinedTextField(value = instructions, onValueChange = { instructions = it }, label = { Text("System Instructions") }, minLines = 3)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Confirm sensitive ops", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = confirmSensitive, onCheckedChange = { confirmSensitive = it })
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (name.isNotBlank() && instructions.isNotBlank()) {
                        val custom = TitanAgent(
                            id = "custom_${UUID.randomUUID().toString().take(8)}",
                            name = name.trim().uppercase(),
                            roleTitle = role.trim().ifBlank { "Custom Specialist" },
                            instructions = instructions.trim(),
                            capabilities = listOf("Custom"),
                            requiresConfirmationForSensitiveOps = confirmSensitive,
                            isBuiltIn = false
                        )
                        viewModel.crewOrchestrator.addCustomAgent(custom)
                        selectedAgent = custom
                        showCreateDialog = false
                    }
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            }
        )
    }
}
