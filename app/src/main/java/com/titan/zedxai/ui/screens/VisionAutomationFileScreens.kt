package com.titan.zedxai.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.titan.zedxai.assistant.PhoneAssistantController
import com.titan.zedxai.assistant.ZedxAccessibilityService
import com.titan.zedxai.tools.WorkspaceToolsEngine
import com.titan.zedxai.ui.ZedxViewModel
import com.titan.zedxai.ui.components.MarkdownMessageContent
import com.titan.zedxai.ui.theme.CrystalGlassCard

@Composable
fun VisionScannerScreen(
    viewModel: ZedxViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val toolOutput by viewModel.toolOutput.collectAsStateWithLifecycle()
    val toolBusy by viewModel.toolBusy.collectAsStateWithLifecycle()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var visionPrompt by remember { mutableStateOf("Extract all visible text (OCR) and summarize key insights.") }
    val imageCapture = remember { ImageCapture.Builder().build() }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

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
                capturedBitmap = bmp
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 900.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            CrystalGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("CameraX Vision & OCR Scanner", style = MaterialTheme.typography.titleLarge)
                    if (hasCameraPermission && capturedBitmap == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp)
                                .clip(RoundedCornerShape(18.dp))
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    val previewView = PreviewView(ctx)
                                    val providerFuture = ProcessCameraProvider.getInstance(ctx)
                                    providerFuture.addListener({
                                        val cameraProvider = providerFuture.get()
                                        val preview = Preview.Builder().build().also {
                                            it.surfaceProvider = previewView.surfaceProvider
                                        }
                                        runCatching {
                                            cameraProvider.unbindAll()
                                            cameraProvider.bindToLifecycle(
                                                lifecycleOwner,
                                                CameraSelector.DEFAULT_BACK_CAMERA,
                                                preview,
                                                imageCapture
                                            )
                                        }
                                    }, ContextCompat.getMainExecutor(ctx))
                                    previewView
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else if (capturedBitmap != null) {
                        Image(
                            bitmap = capturedBitmap!!.asImageBitmap(),
                            contentDescription = "Captured image for analysis",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp)
                                .clip(RoundedCornerShape(18.dp))
                        )
                    } else {
                        Text(
                            "Grant Camera permission only when scanning live, or pick any photo/screenshot directly using Android Photo Picker.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (!hasCameraPermission) {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                } else if (capturedBitmap != null) {
                                    capturedBitmap = null
                                } else {
                                    imageCapture.takePicture(
                                        ContextCompat.getMainExecutor(context),
                                        object : ImageCapture.OnImageCapturedCallback() {
                                            override fun onCaptureSuccess(image: ImageProxy) {
                                                val buffer = image.planes[0].buffer
                                                val bytes = ByteArray(buffer.remaining())
                                                buffer.get(bytes)
                                                capturedBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                                image.close()
                                            }
                                            override fun onError(exception: ImageCaptureException) {
                                                viewModel.showBanner("Capture error: ${exception.message}")
                                            }
                                        }
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.CameraAlt, null)
                            Spacer(Modifier.size(4.dp))
                            Text(if (!hasCameraPermission) "Enable Camera" else if (capturedBitmap != null) "Retake" else "Capture")
                        }

                        OutlinedButton(
                            onClick = {
                                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Image, null)
                            Spacer(Modifier.size(4.dp))
                            Text("Gallery / Screenshot")
                        }
                    }

                    OutlinedTextField(
                        value = visionPrompt,
                        onValueChange = { visionPrompt = it },
                        label = { Text("Vision / OCR Instruction") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        enabled = capturedBitmap != null && !toolBusy,
                        onClick = {
                            capturedBitmap?.let { bmp ->
                                viewModel.runAiToolPrompt(
                                    "You are ZEDX Vision & OCR Engine. Extract text accurately and answer user instructions.",
                                    visionPrompt,
                                    bitmap = bmp
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("analyze_vision_btn")
                    ) {
                        Text("Analyze Image / Extract OCR")
                    }
                }
            }
        }

        if (toolBusy) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        if (toolOutput.isNotBlank()) {
            item {
                CrystalGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) { MarkdownMessageContent(toolOutput) }
                }
            }
        }
    }
}

@Composable
fun PdfDocumentViewerScreen(
    viewModel: ZedxViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val toolOutput by viewModel.toolOutput.collectAsStateWithLifecycle()
    val toolBusy by viewModel.toolBusy.collectAsStateWithLifecycle()

    var fileName by remember { mutableStateOf("No document loaded") }
    var docText by remember { mutableStateOf("") }
    var pdfPages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var question by remember { mutableStateOf("Summarize the main points and action items.") }

    val openDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            fileName = WorkspaceToolsEngine.resolveDisplayName(context, uri)
            if (fileName.endsWith(".pdf", ignoreCase = true)) {
                WorkspaceToolsEngine.renderPdfPages(context, uri).onSuccess { pages ->
                    pdfPages = pages
                    docText = "PDF loaded with ${pages.size} rendered preview page(s)."
                }
            } else {
                pdfPages = emptyList()
                WorkspaceToolsEngine.readDocumentText(context, uri).onSuccess { (_, txt) ->
                    docText = txt
                }
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 900.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            CrystalGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Document & PDF Intelligence", style = MaterialTheme.typography.titleLarge)
                    Text("Loaded: $fileName", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Button(onClick = { openDocLauncher.launch(arrayOf("application/pdf", "text/*", "application/json")) }) {
                        Icon(Icons.Rounded.FolderOpen, null)
                        Spacer(Modifier.size(6.dp))
                        Text("Open PDF or Document")
                    }
                    if (pdfPages.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(pdfPages) { pageBmp ->
                                Image(
                                    bitmap = pageBmp.asImageBitmap(),
                                    contentDescription = "PDF page preview",
                                    modifier = Modifier
                                        .size(width = 180.dp, height = 240.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                            }
                        }
                    } else if (docText.isNotBlank()) {
                        Text(docText.take(1200), style = MaterialTheme.typography.bodyMedium)
                    }
                    OutlinedTextField(value = question, onValueChange = { question = it }, label = { Text("Ask AI about this document") }, modifier = Modifier.fillMaxWidth())
                    Button(
                        enabled = (pdfPages.isNotEmpty() || docText.isNotBlank()) && !toolBusy,
                        onClick = {
                            viewModel.runAiToolPrompt(
                                "You are ZEDX Document Intelligence. Answer accurately based on the provided document.",
                                "$question\n\nDocument ($fileName):\n$docText",
                                bitmap = pdfPages.firstOrNull()
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Analyze Document") }
                }
            }
        }
        if (toolBusy) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        if (toolOutput.isNotBlank()) {
            item {
                CrystalGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) { MarkdownMessageContent(toolOutput) }
                }
            }
        }
    }
}

@Composable
fun AutomationCenterScreen(
    viewModel: ZedxViewModel,
    modifier: Modifier = Modifier
) {
    val automations by viewModel.automations.collectAsStateWithLifecycle()
    val toolOutput by viewModel.toolOutput.collectAsStateWithLifecycle()
    val toolBusy by viewModel.toolBusy.collectAsStateWithLifecycle()

    var showCreate by remember { mutableStateOf(false) }
    var confirmItem by remember { mutableStateOf<com.titan.zedxai.data.local.AutomationEntity?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 900.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Automation Center", style = MaterialTheme.typography.headlineMedium)
                    Text("Triggers, workflows, execution logs & safety confirmation", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = { showCreate = true }) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.size(4.dp))
                    Text("New")
                }
            }
        }

        items(automations, key = { it.id }) { item ->
            CrystalGlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(item.name, style = MaterialTheme.typography.titleMedium)
                        Text("${item.triggerType} -> ${item.actionType} • Runs: ${item.runCount}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text("Last: ${item.lastRunStatus}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = {
                        if (item.requiresConfirmation) confirmItem = item else viewModel.executeAutomation(item)
                    }) {
                        Icon(Icons.Rounded.PlayArrow, null)
                        Spacer(Modifier.size(4.dp))
                        Text("Run")
                    }
                }
            }
        }

        if (toolBusy) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        if (toolOutput.isNotBlank()) {
            item {
                CrystalGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) { MarkdownMessageContent(toolOutput) }
                }
            }
        }
    }

    confirmItem?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmItem = null },
            title = { Text("Confirm Automation Execution") },
            text = { Text("Run workflow '${target.name}' (${target.actionType})?") },
            confirmButton = {
                Button(onClick = {
                    viewModel.executeAutomation(target)
                    confirmItem = null
                }) { Text("Approve & Run") }
            },
            dismissButton = { TextButton(onClick = { confirmItem = null }) { Text("Cancel") } }
        )
    }

    if (showCreate) {
        var name by remember { mutableStateOf("") }
        var prompt by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("Create Automation Workflow") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Workflow Name") }, singleLine = true)
                    OutlinedTextField(value = prompt, onValueChange = { prompt = it }, label = { Text("AI Action Prompt") }, minLines = 3)
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (name.isNotBlank() && prompt.isNotBlank()) {
                        viewModel.saveAutomation(name, "manual", "ai_briefing", prompt, true)
                        showCreate = false
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun FileManagerScreen(
    viewModel: ZedxViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val toolOutput by viewModel.toolOutput.collectAsStateWithLifecycle()
    val toolBusy by viewModel.toolBusy.collectAsStateWithLifecycle()

    var loadedName by remember { mutableStateOf("No file imported") }
    var loadedText by remember { mutableStateOf("") }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            WorkspaceToolsEngine.readDocumentText(context, uri).onSuccess { (name, txt) ->
                loadedName = name
                loadedText = txt
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { it.write(loadedText.toByteArray()) }
            viewModel.showBanner("Exported $loadedName")
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().widthIn(max = 900.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            CrystalGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("ZEDX Zero-Permission File Manager", style = MaterialTheme.typography.titleLarge)
                    Text("Uses Android Storage Access Framework (SAF) — zero broad storage permissions required.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Current: $loadedName", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { importLauncher.launch(arrayOf("*/*")) }) { Text("Import File") }
                        OutlinedButton(enabled = loadedText.isNotBlank(), onClick = { exportLauncher.launch(loadedName) }) { Text("Export") }
                        OutlinedButton(enabled = loadedText.isNotBlank() && !toolBusy, onClick = {
                            viewModel.runAiToolPrompt("Summarize and inspect this imported file:", "File: $loadedName\n\n$loadedText")
                        }) { Text("AI Summarize") }
                    }
                    if (loadedText.isNotBlank()) {
                        Text(loadedText.take(2000), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        if (toolBusy) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        if (toolOutput.isNotBlank()) {
            item {
                CrystalGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) { MarkdownMessageContent(toolOutput) }
                }
            }
        }
    }
}

@Composable
fun PhoneAssistantScreen(
    viewModel: ZedxViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()

    var recipient by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("ZEDX AI Update") }
    var messageBody by remember { mutableStateOf("") }
    var oauthTokenInput by remember(prefs.gmailOAuthToken) { mutableStateOf(prefs.gmailOAuthToken) }
    var pendingAction by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    val accessibilityActive = remember { ZedxAccessibilityService.isEnabled() }

    LazyColumn(
        modifier = modifier.fillMaxSize().widthIn(max = 900.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            CrystalGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Phone Assistant, WhatsApp, Gmail & Calendar", style = MaterialTheme.typography.titleLarge)
                    Text("All external actions require explicit confirmation and never bypass passwords, OTPs, CAPTCHAs, or payments.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = recipient, onValueChange = { recipient = it }, label = { Text("Phone / Email / URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = subject, onValueChange = { subject = it }, label = { Text("Subject / Event Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = messageBody, onValueChange = { messageBody = it }, label = { Text("Message / Notes") }, minLines = 2, modifier = Modifier.fillMaxWidth())

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            pendingAction = "Open WhatsApp with message?" to {
                                PhoneAssistantController.sendWhatsAppIntent(context, messageBody.ifBlank { "Hello from ZEDX AI" })
                                    .onSuccess { viewModel.showBanner(it) }
                                    .onFailure { viewModel.showBanner(it.message ?: "Error") }
                            }
                        }, modifier = Modifier.weight(1f)) { Text("WhatsApp") }

                        Button(onClick = {
                            pendingAction = "Open Email / Gmail for $recipient?" to {
                                PhoneAssistantController.openEmailIntent(context, recipient, subject, messageBody)
                                    .onSuccess { viewModel.showBanner(it) }
                            }
                        }, modifier = Modifier.weight(1f)) { Text("Gmail Intent") }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            pendingAction = "Open Dialer for $recipient?" to {
                                PhoneAssistantController.openDialer(context, recipient)
                                    .onSuccess { viewModel.showBanner(it) }
                                    .onFailure { viewModel.showBanner(it.message ?: "Enter phone number") }
                            }
                        }, modifier = Modifier.weight(1f)) { Text("Dialer") }

                        OutlinedButton(onClick = {
                            pendingAction = "Open Calendar Event Composer?" to {
                                PhoneAssistantController.openCalendarEvent(context, subject, messageBody)
                                    .onSuccess { viewModel.showBanner(it) }
                            }
                        }, modifier = Modifier.weight(1f)) { Text("Calendar") }

                        OutlinedButton(onClick = {
                            pendingAction = "Open Android Settings?" to {
                                PhoneAssistantController.openSystemSettings(context, Settings.ACTION_SETTINGS)
                            }
                        }, modifier = Modifier.weight(1f)) { Text("Settings") }
                    }
                }
            }
        }

        item {
            CrystalGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Gmail API Separate OAuth Consent", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Gmail API requires separate `gmail.send` OAuth consent. Paste a short-lived user-authorized OAuth access token or use the Gmail Intent above.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = oauthTokenInput,
                        onValueChange = { oauthTokenInput = it },
                        label = { Text("Gmail OAuth Access Token (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(onClick = { viewModel.saveGmailToken(oauthTokenInput) }) {
                        Text("Save Gmail OAuth Token")
                    }
                }
            }
        }

        item {
            CrystalGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = if (accessibilityActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Optional Accessibility Context Helper: ${if (accessibilityActive) "Enabled" else "Off"}", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        "Explicitly user-enabled in Android Settings. Strictly excludes password, OTP, CAPTCHA, and payment fields.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(onClick = { PhoneAssistantController.openAccessibilitySettings(context) }) {
                        Text("Manage Accessibility Settings")
                    }
                }
            }
        }
    }

    pendingAction?.let { (label, action) ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text("Confirm Phone Action") },
            text = { Text(label) },
            confirmButton = {
                Button(onClick = {
                    action()
                    pendingAction = null
                }) { Text("Confirm") }
            },
            dismissButton = { TextButton(onClick = { pendingAction = null }) { Text("Cancel") } }
        )
    }
}
