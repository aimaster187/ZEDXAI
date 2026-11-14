package com.titan.zedxai.ui.screens

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.titan.zedxai.data.local.ConversationEntity
import com.titan.zedxai.data.local.MessageEntity
import com.titan.zedxai.tools.WorkspaceToolsEngine
import com.titan.zedxai.ui.ZedxViewModel
import com.titan.zedxai.ui.components.MarkdownMessageContent
import com.titan.zedxai.ui.navigation.ZedxRoute
import com.titan.zedxai.ui.theme.CrystalGlassCard

@Composable
fun TitanChatScreen(
    viewModel: ZedxViewModel,
    onNavigate: (ZedxRoute) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val chatState by viewModel.chatState.collectAsStateWithLifecycle()
    val messages by viewModel.activeMessages.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    var inputText by remember { mutableStateOf("") }
    var attachedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var attachedDocText by remember { mutableStateOf<String?>(null) }
    var attachedDocName by remember { mutableStateOf<String?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                attachedBitmap = bmp
                viewModel.setPendingAttachmentSummary("Image attached")
            }
        }
    }

    val docPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            WorkspaceToolsEngine.readDocumentText(context, uri).onSuccess { (name, text) ->
                attachedDocName = name
                attachedDocText = text
                viewModel.setPendingAttachmentSummary("Document: $name")
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        val convId = chatState.activeConversationId
        if (uri != null && convId != null) {
            val md = viewModel.exportConversationMarkdown(convId)
            context.contentResolver.openOutputStream(uri)?.use { it.write(md.toByteArray()) }
            viewModel.showBanner("Conversation exported as Markdown.")
        }
    }

    LaunchedEffect(messages.size, chatState.streamingText) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 900.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Top model & agent control strip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AssistChip(
                onClick = { onNavigate(ZedxRoute.MODEL_SELECTOR) },
                label = { Text(prefs.activeModelId, maxLines = 1) },
                leadingIcon = { Icon(Icons.Rounded.Hub, null, Modifier.size(16.dp)) },
                modifier = Modifier.weight(1f, fill = false)
            )
            AssistChip(
                onClick = { onNavigate(ZedxRoute.AGENTS) },
                label = { Text(prefs.activeAgentId.removePrefix("titan_").uppercase()) },
                leadingIcon = { Icon(Icons.Rounded.Psychology, null, Modifier.size(16.dp)) }
            )
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = { viewModel.startNewChat() },
                modifier = Modifier.testTag("chat_new_button")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "New Chat")
            }
            if (chatState.activeConversationId != null) {
                IconButton(
                    onClick = { exportLauncher.launch("zedx_chat.md") },
                    modifier = Modifier.testTag("chat_export_button")
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = "Export Chat")
                }
            }
        }

        // Messages area
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty() && !chatState.isStreaming) {
                item {
                    CrystalGlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("ZEDX Titan Chat", style = MaterialTheme.typography.headlineMedium)
                            Text(
                                "Ask anything, attach photos or PDFs/code files, switch between 12 AI providers, or collaborate with the 5-Agent Titan Crew.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AssistChip(
                                    onClick = { inputText = "Write a clean Kotlin Compose glassmorphism card component" },
                                    label = { Text("Compose Code") }
                                )
                                AssistChip(
                                    onClick = { inputText = "Create a 5-point productivity workflow for today" },
                                    label = { Text("Plan Today") }
                                )
                            }
                        }
                    }
                }
            }

            items(messages, key = { it.id }) { msg ->
                ChatBubble(
                    message = msg,
                    isLastAssistant = msg == messages.lastOrNull() && msg.role == "assistant",
                    onRegenerate = { viewModel.regenerateLastResponse() }
                )
            }

            if (chatState.isStreaming) {
                item(key = "streaming_bubble") {
                    CrystalGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 18.dp
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                "ZEDX AI • Streaming (${prefs.activeModelId})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(6.dp))
                            MarkdownMessageContent(
                                markdown = chatState.streamingText.ifBlank { "Thinking..." }
                            )
                        }
                    }
                }
            }
        }

        // Attachment preview chip
        if (attachedBitmap != null || attachedDocName != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                attachedBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Attached image",
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }
                Text(
                    text = attachedDocName ?: "Image ready",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    attachedBitmap = null
                    attachedDocName = null
                    attachedDocText = null
                    viewModel.setPendingAttachmentSummary(null)
                }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Remove attachment")
                }
            }
        }

        // Composer bar
        CrystalGlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 22.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.testTag("chat_attach_image")
                ) {
                    Icon(Icons.Rounded.Image, contentDescription = "Attach image")
                }
                IconButton(
                    onClick = { docPicker.launch(arrayOf("application/pdf", "text/*", "application/json")) },
                    modifier = Modifier.testTag("chat_attach_doc")
                ) {
                    Icon(Icons.Rounded.AttachFile, contentDescription = "Attach PDF or document")
                }
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Message ZEDX AI...") },
                    maxLines = 4,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input_field")
                )
                if (chatState.isStreaming) {
                    FilledIconButton(
                        onClick = { viewModel.stopGeneration() },
                        modifier = Modifier.testTag("chat_stop_button")
                    ) {
                        Icon(Icons.Rounded.Stop, contentDescription = "Stop generation")
                    }
                } else if (inputText.isBlank() && attachedBitmap == null && attachedDocText == null) {
                    FilledIconButton(
                        onClick = { onNavigate(ZedxRoute.VOICE) },
                        modifier = Modifier.testTag("chat_mic_button")
                    ) {
                        Icon(Icons.Rounded.Mic, contentDescription = "Open Voice Assistant")
                    }
                } else {
                    FilledIconButton(
                        onClick = {
                            val prompt = inputText
                            val bmp = attachedBitmap
                            val doc = attachedDocText
                            inputText = ""
                            attachedBitmap = null
                            attachedDocText = null
                            attachedDocName = null
                            viewModel.sendMessage(prompt, bmp, doc)
                        },
                        modifier = Modifier.testTag("chat_send_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send message")
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(
    message: MessageEntity,
    isLastAssistant: Boolean,
    onRegenerate: () -> Unit
) {
    val isUser = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = when {
                message.isError -> MaterialTheme.colorScheme.errorContainer
                isUser -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surface
            },
            shape = RoundedCornerShape(18.dp),
            tonalElevation = if (isUser) 2.dp else 4.dp,
            modifier = Modifier.widthIn(max = 680.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (!message.attachmentSummary.isNullOrBlank()) {
                    Text(
                        text = message.attachmentSummary,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                }
                if (isUser) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else {
                    MarkdownMessageContent(markdown = message.content)
                    if (isLastAssistant) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = onRegenerate) {
                                Icon(Icons.Rounded.Refresh, null, Modifier.size(16.dp))
                                Spacer(Modifier.size(4.dp))
                                Text(if (message.isError) "Retry" else "Regenerate")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatHistoryScreen(
    viewModel: ZedxViewModel,
    onOpenChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var renamingItem by remember { mutableStateOf<ConversationEntity?>(null) }
    var renameText by remember { mutableStateOf("") }

    val filtered = remember(conversations, query) {
        if (query.isBlank()) conversations
        else conversations.filter { it.title.contains(query, ignoreCase = true) }
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
            placeholder = { Text("Search conversations...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (filtered.isEmpty()) {
            CrystalGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No saved conversations yet", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        viewModel.startNewChat()
                        onOpenChat()
                    }) {
                        Text("Start First Chat")
                    }
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(filtered, key = { it.id }) { item ->
                    CrystalGlassCard(
                        onClick = {
                            viewModel.selectConversation(item.id)
                            onOpenChat()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${item.providerId} • ${item.modelId} • ${item.agentId}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = {
                                renamingItem = item
                                renameText = item.title
                            }) {
                                Icon(Icons.Rounded.Edit, contentDescription = "Rename chat")
                            }
                            IconButton(onClick = { viewModel.deleteConversation(item.id) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Delete chat")
                            }
                        }
                    }
                }
            }
        }
    }

    renamingItem?.let { target ->
        AlertDialog(
            onDismissRequest = { renamingItem = null },
            title = { Text("Rename Conversation") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.renameConversation(target.id, renameText)
                    renamingItem = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renamingItem = null }) { Text("Cancel") }
            }
        )
    }
}
