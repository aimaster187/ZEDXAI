package com.titan.zedxai.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.titan.zedxai.assistant.PhoneAssistantController
import com.titan.zedxai.assistant.ZedxAccessibilityService
import com.titan.zedxai.ui.ZedxViewModel
import com.titan.zedxai.ui.navigation.ZedxRoute
import com.titan.zedxai.ui.theme.CrystalGlassCard
import com.titan.zedxai.ui.theme.ZedxThemeMode

@Composable
fun NotificationsScreen(
    viewModel: ZedxViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val items by viewModel.notifications.collectAsStateWithLifecycle()
    var reminderTitle by remember { mutableStateOf("") }

    val notifPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && reminderTitle.isNotBlank()) {
            PhoneAssistantController.postReminderNotification(context, "ZEDX Reminder", reminderTitle)
            viewModel.addNotification("Reminder", reminderTitle, "reminder")
            reminderTitle = ""
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().widthIn(max = 900.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            CrystalGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Notification & Reminder Center", style = MaterialTheme.typography.titleLarge)
                        TextButton(onClick = { viewModel.markAllNotificationsRead() }) { Text("Mark All Read") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = reminderTitle,
                            onValueChange = { reminderTitle = it },
                            label = { Text("Quick Reminder") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = {
                            if (reminderTitle.isNotBlank()) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    PhoneAssistantController.postReminderNotification(context, "ZEDX Reminder", reminderTitle)
                                    viewModel.addNotification("Reminder", reminderTitle, "reminder")
                                    reminderTitle = ""
                                }
                            }
                        }) {
                            Icon(Icons.Rounded.NotificationsActive, null)
                        }
                    }
                }
            }
        }
        items(items, key = { it.id }) { notif ->
            CrystalGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(notif.title, style = MaterialTheme.typography.titleMedium)
                    Text(notif.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "${notif.category.uppercase()} • ${if (notif.isRead) "Read" else "Unread"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun ProfileScreen(
    viewModel: ZedxViewModel,
    onSignOut: () -> Unit,
    onNavigate: (ZedxRoute) -> Unit,
    modifier: Modifier = Modifier
) {
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val isTrustedAdmin by viewModel.isTrustedAdmin.collectAsStateWithLifecycle()
    val usageLogs by viewModel.usageLogs.collectAsStateWithLifecycle()
    var editName by remember(profile?.displayName) { mutableStateOf(profile?.displayName ?: viewModel.userName) }

    LazyColumn(
        modifier = modifier.fillMaxSize().widthIn(max = 900.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            CrystalGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(profile?.displayName ?: viewModel.userName, style = MaterialTheme.typography.headlineMedium)
                    Text(viewModel.userEmail, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                    Text(
                        "UID: ${viewModel.currentUserId.take(12)}… • Role: ${if (isTrustedAdmin) "Trusted Admin (Custom Claim)" else (profile?.role ?: "user")}",
                        style = MaterialTheme.typography.labelSmall
                    )
                    if (viewModel.userEmail.equals("pranavji628@gmail.com", ignoreCase = true) || isTrustedAdmin) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.AdminPanelSettings, null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                if (isTrustedAdmin) "Admin claim verified. Manage platform on the separate Admin Website."
                                else "Owner email detected. Admin privileges require backend Firebase custom claims (`admin: true`).",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Display Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.updateProfileDisplayName(editName) }) { Text("Save Profile") }
                        OutlinedButton(onClick = onSignOut, modifier = Modifier.testTag("sign_out_btn")) { Text("Sign Out") }
                    }
                }
            }
        }

        item {
            CrystalGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Usage Statistics", style = MaterialTheme.typography.titleMedium)
                    val totalTokens = usageLogs.sumOf { it.tokensUsed }
                    val totalCost = usageLogs.sumOf { it.estimatedCostUsd }
                    Text("Requests logged: ${usageLogs.size} • Total tokens: $totalTokens • Est. paid spend: $${"%.4f".format(totalCost)}", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { onNavigate(ZedxRoute.PRIVACY) }) { Text("Privacy & Export") }
                        TextButton(onClick = { onNavigate(ZedxRoute.DELETE_ACCOUNT) }) {
                            Text("Delete Account", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    viewModel: ZedxViewModel,
    onNavigate: (ZedxRoute) -> Unit,
    modifier: Modifier = Modifier
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.fillMaxSize().widthIn(max = 900.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            CrystalGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Appearance & Theme", style = MaterialTheme.typography.titleLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ZedxThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = prefs.themeMode == mode,
                                onClick = { viewModel.updateThemeMode(mode) },
                                label = { Text(mode.label) }
                            )
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Firestore Cloud Sync", style = MaterialTheme.typography.titleMedium)
                            Text("Sync conversations & memories to your UID.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = prefs.cloudSyncEnabled, onCheckedChange = { viewModel.updateRoutingAndSync(it, prefs.smartRoutingEnabled) })
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Smart Task Router", style = MaterialTheme.typography.titleMedium)
                            Text("Optimize model selection inside your chosen provider.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = prefs.smartRoutingEnabled, onCheckedChange = { viewModel.updateRoutingAndSync(prefs.cloudSyncEnabled, it) })
                    }
                }
            }
        }

        item {
            CrystalGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Workspace & Governance", style = MaterialTheme.typography.titleLarge)
                    listOf(
                        "Provider Manager & Key Vault" to ZedxRoute.PROVIDERS,
                        "Model Catalog" to ZedxRoute.MODEL_SELECTOR,
                        "Memory Manager" to ZedxRoute.MEMORY,
                        "Voice Settings" to ZedxRoute.VOICE,
                        "Privacy & Data Export" to ZedxRoute.PRIVACY,
                        "Permissions Center" to ZedxRoute.PERMISSIONS,
                        "Help & Documentation" to ZedxRoute.HELP,
                        "About ZEDX AI v1.0.0" to ZedxRoute.ABOUT,
                        "Delete Account" to ZedxRoute.DELETE_ACCOUNT
                    ).forEach { (label, route) ->
                        OutlinedButton(
                            onClick = { onNavigate(route) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(label) }
                    }
                }
            }
        }
    }
}

@Composable
fun PrivacyScreen(
    viewModel: ZedxViewModel,
    onNavigateDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val json = viewModel.exportAllUserDataJson()
            context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            viewModel.showBanner("Exported ZEDX AI user data JSON.")
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().widthIn(max = 900.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            CrystalGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Security, null, tint = MaterialTheme.colorScheme.primary)
                        Text("Privacy & Zero-Trust Guarantees", style = MaterialTheme.typography.titleLarge)
                    }
                    Text("• API keys stay strictly on-device in AndroidKeyStore AES-256-GCM.", style = MaterialTheme.typography.bodyMedium)
                    Text("• Private conversations and memories are UID-isolated and never exposed to admins.", style = MaterialTheme.typography.bodyMedium)
                    Text("• SecretRedactor blocks saving passwords or tokens into AI memories.", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { exportJsonLauncher.launch("zedx_user_export.json") }) {
                        Icon(Icons.Rounded.Download, null)
                        Spacer(Modifier.size(6.dp))
                        Text("Export My Data (JSON)")
                    }
                    OutlinedButton(onClick = onNavigateDelete) {
                        Text("Request Account Deletion", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionsScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var camGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var micGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    var notifGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            else true
        )
    }

    val camLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { camGranted = it }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { micGranted = it }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifGranted = it }

    LazyColumn(
        modifier = modifier.fillMaxSize().widthIn(max = 900.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            PermissionCard(
                title = "Camera (CameraX Vision & OCR)",
                description = "Requested only when scanning live documents or objects.",
                granted = camGranted,
                onRequest = { camLauncher.launch(Manifest.permission.CAMERA) }
            )
        }
        item {
            PermissionCard(
                title = "Microphone (Voice Assistant)",
                description = "Requested only when you tap Push-to-Talk. No secret background recording.",
                granted = micGranted,
                onRequest = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }
            )
        }
        item {
            PermissionCard(
                title = "Notifications (Reminders & Foreground Voice)",
                description = "Used for user-scheduled reminders and active microphone session status.",
                granted = notifGranted,
                onRequest = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            )
        }
        item {
            PermissionCard(
                title = "Optional Accessibility Helper",
                description = "User-enabled in Android Settings. Strictly excludes passwords, OTPs, and payment screens.",
                granted = ZedxAccessibilityService.isEnabled(),
                onRequest = { PhoneAssistantController.openAccessibilitySettings(context) }
            )
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    granted: Boolean,
    onRequest: () -> Unit
) {
    CrystalGlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (granted) "Status: Granted" else "Status: Not Granted", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            if (!granted) {
                Button(onClick = onRequest) { Text("Enable") }
            }
        }
    }
}

@Composable
fun HelpScreen(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize().widthIn(max = 900.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            CrystalGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ZEDX AI Help & Guides", style = MaterialTheme.typography.titleLarge)
                    Text("1. Gemini via Firebase AI Logic works out-of-the-box with App Check (no API key required).", style = MaterialTheme.typography.bodyMedium)
                    Text("2. Add OpenAI, OpenRouter, Claude, Groq, DeepSeek, Mistral, Together, Fireworks, or Ollama keys in Provider Manager.", style = MaterialTheme.typography.bodyMedium)
                    Text("3. Use Titan Crew to run up to 5 concurrent agents; sensitive actions pause for your approval.", style = MaterialTheme.typography.bodyMedium)
                    Text("4. Speak in English, Hindi, or Hinglish inside Voice Assistant.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize().widthIn(max = 900.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            CrystalGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ZEDX AI", style = MaterialTheme.typography.headlineLarge)
                    Text("BY THE TITAN AI • Version 1.0.0", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text("Package: com.titan.zedxai (App ID: com.aistudio.zedxai.tx7k9p)", style = MaterialTheme.typography.labelSmall)
                    Text(
                        "Complete multi-provider Android AI ecosystem featuring White Crystal & Dark Space UI, Firebase Auth & Firestore UID isolation, Gemini AI Logic with App Check, AndroidKeyStore AES-256-GCM vault, Five-Agent Titan Crew, Voice Assistant, and Developer Studio.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text("Update channel: 1.0.0 (Current Production Build)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}

@Composable
fun DeleteAccountScreen(
    viewModel: ZedxViewModel,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    var confirmText by remember { mutableStateOf("") }
    var showConfirm by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier.fillMaxSize().widthIn(max = 700.dp).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CrystalGlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.DeleteForever, null, tint = MaterialTheme.colorScheme.error)
                    Text("Permanent Account Deletion", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error)
                }
                Text(
                    "Deleting your account permanently removes your Room local cache, AES-256-GCM encrypted API keys, Firestore profile, synced conversations, memories, and Firebase Authentication account.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = confirmText,
                    onValueChange = { confirmText = it },
                    label = { Text("Type DELETE to confirm") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (status != null) {
                    Text(status.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                Button(
                    enabled = confirmText.trim().equals("DELETE", ignoreCase = true),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = { showConfirm = true },
                    modifier = Modifier.fillMaxWidth().testTag("confirm_delete_account_btn")
                ) {
                    Text("Permanently Delete My Account")
                }
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Final Confirmation") },
            text = { Text("This action cannot be undone. Proceed with deleting ${viewModel.userEmail}?") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        showConfirm = false
                        viewModel.deleteAccountCompletely { res ->
                            res.onSuccess { onDeleted() }
                                .onFailure { status = "Deletion failed (re-authenticate with Google if recent login required): ${it.message}" }
                        }
                    }
                ) { Text("Delete Forever") }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("Cancel") } }
        )
    }
}
