package com.titan.zedxai.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Web
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.titan.zedxai.ui.ZedxViewModel
import com.titan.zedxai.ui.components.NeuralZOrb
import com.titan.zedxai.ui.navigation.ZedxRoute
import com.titan.zedxai.ui.theme.CrystalGlassCard
import com.titan.zedxai.voice.OrbVisualState

private data class QuickToolCard(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val route: ZedxRoute,
    val category: String
)

@Composable
fun HomeDashboardScreen(
    viewModel: ZedxViewModel,
    onNavigate: (ZedxRoute) -> Unit,
    modifier: Modifier = Modifier
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val usageLogs by viewModel.usageLogs.collectAsStateWithLifecycle()
    val sysConfig by viewModel.systemConfig.collectAsStateWithLifecycle()

    val quickActions = listOf(
        QuickToolCard("Titan Chat", "Multi-turn & Markdown", Icons.Rounded.Chat, ZedxRoute.CHAT, "Core"),
        QuickToolCard("Voice Orb", "EN • HI • Hinglish", Icons.Rounded.Mic, ZedxRoute.VOICE, "Core"),
        QuickToolCard("Titan Crew", "5 Concurrent Agents", Icons.Rounded.Psychology, ZedxRoute.AGENTS, "Core"),
        QuickToolCard("Vision OCR", "CameraX & Gallery", Icons.Rounded.CameraAlt, ZedxRoute.VISION_SCANNER, "Tools"),
        QuickToolCard("Coding Studio", "Syntax & Debug", Icons.Rounded.Code, ZedxRoute.CODING_STUDIO, "Studio"),
        QuickToolCard("Web Builder", "Live HTML/CSS/JS", Icons.Rounded.Web, ZedxRoute.WEBSITE_BUILDER, "Studio")
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 900.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (sysConfig?.announcement?.isNotBlank() == true) {
            item {
                CrystalGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = sysConfig?.announcement.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        item {
            CrystalGlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 28.dp,
                elevation = 10.dp
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Image(
                        painter = painterResource(id = R.drawable.img_zedx_hero),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .matchParentSize()
                            .alpha(0.32f)
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        NeuralZOrb(
                            orbState = OrbVisualState.IDLE,
                            size = 136.dp,
                            onClick = { onNavigate(ZedxRoute.VOICE) }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Welcome back, ${viewModel.userName.substringBefore(" ")}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "ZEDX AI • BY THE TITAN AI",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                AssistChip(
                                    onClick = { onNavigate(ZedxRoute.MODEL_SELECTOR) },
                                    label = { Text("${prefs.activeProviderId}: ${prefs.activeModelId}") },
                                    leadingIcon = { Icon(Icons.Rounded.Hub, null, Modifier.size(16.dp)) }
                                )
                            }
                            item {
                                AssistChip(
                                    onClick = { onNavigate(ZedxRoute.AGENTS) },
                                    label = { Text("Agent: ${prefs.activeAgentId.removePrefix("titan_").uppercase()}") },
                                    leadingIcon = { Icon(Icons.Rounded.Psychology, null, Modifier.size(16.dp)) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    viewModel.startNewChat()
                                    onNavigate(ZedxRoute.CHAT)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("home_new_chat_button"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Rounded.Chat, null, Modifier.size(18.dp))
                                Spacer(Modifier.size(8.dp))
                                Text("Start Titan Chat")
                            }
                            Button(
                                onClick = { onNavigate(ZedxRoute.VOICE) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("home_voice_button"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Rounded.Mic, null, Modifier.size(18.dp))
                                Spacer(Modifier.size(8.dp))
                                Text("Voice Orb")
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Command Deck",
                style = MaterialTheme.typography.titleLarge
            )
        }

        items(quickActions.chunked(2)) { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                pair.forEach { card ->
                    CrystalGlassCard(
                        onClick = { onNavigate(card.route) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_card_${card.route.route}")
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = card.icon,
                                contentDescription = card.title,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                            Text(card.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                card.subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        item {
            CrystalGlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Workspace Telemetry", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${conversations.size} chats • ${usageLogs.size} AI runs logged",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(onClick = { onNavigate(ZedxRoute.PROVIDERS) }) {
                        Text("Providers")
                    }
                }
            }
        }
    }
}

@Composable
fun ExploreToolsScreen(
    onNavigate: (ZedxRoute) -> Unit,
    modifier: Modifier = Modifier
) {
    val allTools = listOf(
        QuickToolCard("Titan Chat", "Streaming, Markdown, attachments & export", Icons.Rounded.Chat, ZedxRoute.CHAT, "AI Core"),
        QuickToolCard("Chat History", "Search, rename & manage threads", Icons.Rounded.History, ZedxRoute.HISTORY, "AI Core"),
        QuickToolCard("Titan Crew Agents", "5 concurrent specialized & custom agents", Icons.Rounded.Psychology, ZedxRoute.AGENTS, "AI Core"),
        QuickToolCard("Provider Manager", "12 providers + AES-256-GCM key vault", Icons.Rounded.Key, ZedxRoute.PROVIDERS, "AI Core"),
        QuickToolCard("Model Selector", "Capability catalog & smart router", Icons.Rounded.Hub, ZedxRoute.MODEL_SELECTOR, "AI Core"),
        QuickToolCard("Memory Manager", "Persistent user-controlled context", Icons.Rounded.Memory, ZedxRoute.MEMORY, "AI Core"),
        QuickToolCard("Crystal Browser", "Multi-tab WebView, bookmarks & AI summary", Icons.Rounded.Language, ZedxRoute.BROWSER, "Studio"),
        QuickToolCard("Coding Studio", "Syntax editor, debugging & export", Icons.Rounded.Code, ZedxRoute.CODING_STUDIO, "Studio"),
        QuickToolCard("Safe Terminal", "Restricted sandbox CLI & AI pipe", Icons.Rounded.Terminal, ZedxRoute.TERMINAL, "Studio"),
        QuickToolCard("Website Builder", "AI HTML/CSS/JS + live preview", Icons.Rounded.Web, ZedxRoute.WEBSITE_BUILDER, "Studio"),
        QuickToolCard("APK Builder", "Generate & export Android Compose ZIP", Icons.Rounded.Android, ZedxRoute.APK_BUILDER, "Studio"),
        QuickToolCard("Vision & OCR", "CameraX live scan & multimodal analysis", Icons.Rounded.CameraAlt, ZedxRoute.VISION_SCANNER, "Media"),
        QuickToolCard("Document & PDF", "PdfRenderer pages & document Q&A", Icons.Rounded.PictureAsPdf, ZedxRoute.PDF_VIEWER, "Media"),
        QuickToolCard("File Manager", "Import/export files & AI inspection", Icons.Rounded.FolderOpen, ZedxRoute.FILE_MANAGER, "Media"),
        QuickToolCard("Automation Center", "Triggers, workflows & run logs", Icons.Rounded.AccountTree, ZedxRoute.AUTOMATION, "Device"),
        QuickToolCard("Phone & Comms", "WhatsApp, Gmail API, Calendar, Intents", Icons.Rounded.PhoneAndroid, ZedxRoute.PHONE_ASSISTANT, "Device")
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 900.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Explore ZEDX Ecosystem", style = MaterialTheme.typography.headlineMedium)
            Text(
                "All tools run with real Android APIs, zero-trust Firestore sync, and user confirmation gates.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        items(allTools.chunked(2)) { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                pair.forEach { tool ->
                    CrystalGlassCard(
                        onClick = { onNavigate(tool.route) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("explore_tool_${tool.route.route}")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(tool.icon, contentDescription = tool.title, tint = MaterialTheme.colorScheme.primary)
                                Text(
                                    tool.category,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            Text(tool.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                tool.subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
