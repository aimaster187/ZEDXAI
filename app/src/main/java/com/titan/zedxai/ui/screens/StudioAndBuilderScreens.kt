package com.titan.zedxai.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.titan.zedxai.tools.WorkspaceToolsEngine
import com.titan.zedxai.ui.ZedxViewModel
import com.titan.zedxai.ui.components.CodeBlockCard
import com.titan.zedxai.ui.components.MarkdownMessageContent
import com.titan.zedxai.ui.theme.CrystalGlassCard
import com.titan.zedxai.ui.theme.JetBrainsMonoFontFamily

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    viewModel: ZedxViewModel,
    modifier: Modifier = Modifier
) {
    val tabs by viewModel.browserTabs.collectAsStateWithLifecycle()
    val activeIdx by viewModel.activeTabIndex.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val toolOutput by viewModel.toolOutput.collectAsStateWithLifecycle()
    val toolBusy by viewModel.toolBusy.collectAsStateWithLifecycle()

    val currentTab = tabs.getOrElse(activeIdx) { tabs.first() }
    var urlInput by remember(currentTab.id, currentTab.url) { mutableStateOf(currentTab.url) }
    var showBookmarks by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 980.dp)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Tabs strip
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            itemsIndexed(tabs) { index, tab ->
                FilterChip(
                    selected = index == activeIdx,
                    onClick = { viewModel.selectBrowserTab(index) },
                    label = { Text(tab.title.take(16)) },
                    trailingIcon = if (tabs.size > 1) {
                        {
                            IconButton(onClick = { viewModel.closeBrowserTab(index) }, modifier = Modifier.size(18.dp)) {
                                Icon(Icons.Rounded.Close, contentDescription = "Close tab", modifier = Modifier.size(14.dp))
                            }
                        }
                    } else null
                )
            }
            item {
                IconButton(onClick = { viewModel.addBrowserTab() }) {
                    Icon(Icons.Rounded.Add, contentDescription = "New tab")
                }
            }
        }

        // URL Bar
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                singleLine = true,
                placeholder = { Text("Enter URL or search...") },
                modifier = Modifier.weight(1f).testTag("browser_url_input")
            )
            Button(onClick = {
                val norm = WorkspaceToolsEngine.normalizeBrowserUrl(urlInput)
                val (safe, msg) = WorkspaceToolsEngine.isSafeBrowserUrl(norm)
                if (!safe) {
                    viewModel.showBanner(msg)
                } else {
                    viewModel.updateActiveBrowserTab(norm, norm)
                }
            }) { Text("Go") }
            IconButton(onClick = { viewModel.bookmarkPage(currentTab.title, currentTab.url) }) {
                Icon(Icons.Rounded.BookmarkAdd, contentDescription = "Bookmark page")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = { showBookmarks = !showBookmarks }, label = { Text("Bookmarks (${bookmarks.size})") })
            AssistChip(
                onClick = {
                    viewModel.runAiToolPrompt(
                        "You are ZEDX Research Assistant. Summarize the web resource topic and key takeaways clearly.",
                        "Provide a structured overview and safety checklist for: ${currentTab.url}"
                    )
                },
                label = { Text("AI Page Brief") },
                leadingIcon = { Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(16.dp)) }
            )
        }

        if (showBookmarks && bookmarks.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(bookmarks, key = { it.id }) { bm ->
                    AssistChip(
                        onClick = {
                            viewModel.updateActiveBrowserTab(bm.title, bm.url)
                            showBookmarks = false
                        },
                        label = { Text(bm.title.take(24)) }
                    )
                }
            }
        }

        if (toolBusy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        if (toolOutput.isNotBlank()) {
            CrystalGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    MarkdownMessageContent(toolOutput)
                }
            }
        }

        // Real WebView
        CrystalGlassCard(modifier = Modifier.weight(1f).fillMaxWidth(), cornerRadius = 18.dp) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val nextUrl = request?.url?.toString().orEmpty()
                                val (safe, _) = WorkspaceToolsEngine.isSafeBrowserUrl(nextUrl)
                                return !safe
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                if (!url.isNullOrBlank()) {
                                    viewModel.updateActiveBrowserTab(view?.title ?: url, url)
                                }
                            }
                        }
                        loadUrl(currentTab.url)
                    }
                },
                update = { webView ->
                    if (webView.url != currentTab.url) {
                        webView.loadUrl(currentTab.url)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun CodingStudioScreen(
    viewModel: ZedxViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val projects by viewModel.codeProjects.collectAsStateWithLifecycle()
    val toolOutput by viewModel.toolOutput.collectAsStateWithLifecycle()
    val toolBusy by viewModel.toolBusy.collectAsStateWithLifecycle()

    var activeId by remember { mutableStateOf<String?>(null) }
    var fileName by remember { mutableStateOf("TitanSample.kt") }
    var language by remember { mutableStateOf("kotlin") }
    var codeText by remember { mutableStateOf(projects.firstOrNull()?.code ?: "fun main() {\n    println(\"ZEDX AI Coding Studio\")\n}") }
    var showPreview by remember { mutableStateOf(false) }

    val exportCodeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { it.write(codeText.toByteArray()) }
            viewModel.showBanner("Exported $fileName")
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 940.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(projects, key = { it.id }) { proj ->
                    FilterChip(
                        selected = activeId == proj.id,
                        onClick = {
                            activeId = proj.id
                            fileName = proj.name
                            language = proj.language
                            codeText = proj.code
                        },
                        label = { Text(proj.name) }
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = fileName, onValueChange = { fileName = it }, label = { Text("File Name") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = language, onValueChange = { language = it }, label = { Text("Lang") }, singleLine = true, modifier = Modifier.weight(0.6f))
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.saveCodeProject(activeId, fileName, language, codeText) }) {
                    Icon(Icons.Rounded.Save, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Save")
                }
                OutlinedButton(onClick = { showPreview = !showPreview }) {
                    Text(if (showPreview) "Edit Mode" else "Syntax Preview")
                }
                OutlinedButton(onClick = { exportCodeLauncher.launch(fileName) }) {
                    Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Export")
                }
            }
        }

        item {
            if (showPreview) {
                CodeBlockCard(language = language, code = codeText)
            } else {
                OutlinedTextField(
                    value = codeText,
                    onValueChange = { codeText = it },
                    label = { Text("Code Editor") },
                    minLines = 10,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                    modifier = Modifier.fillMaxWidth().testTag("coding_studio_editor")
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    viewModel.runAiToolPrompt(
                        "You are TITAN CODING Debugger. Inspect code for bugs, security risks, and performance improvements.",
                        "Debug & improve this $language file ($fileName):\n```$language\n$codeText\n```"
                    )
                }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.BugReport, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("AI Debug")
                }
                OutlinedButton(onClick = {
                    viewModel.runAiToolPrompt(
                        "You are TITAN CODING. Refactor cleanly and explain changes concisely.",
                        "Refactor this $language file cleanly:\n```$language\n$codeText\n```"
                    )
                }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("AI Refactor")
                }
            }
        }

        if (toolBusy) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        if (toolOutput.isNotBlank()) {
            item {
                CrystalGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        MarkdownMessageContent(toolOutput)
                    }
                }
            }
        }
    }
}

@Composable
fun SafeTerminalScreen(
    viewModel: ZedxViewModel,
    modifier: Modifier = Modifier
) {
    val lines by viewModel.terminalLines.collectAsStateWithLifecycle()
    var cmdInput by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 920.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf("help", "ls", "sysinfo", "whoami", "date", "ai hello world", "clear")) { preset ->
                AssistChip(
                    onClick = { viewModel.runTerminalCommand(preset) },
                    label = { Text(preset) }
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF071022))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(lines) { line ->
                Text(
                    text = line.text,
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (line.isCommand) Color(0xFF38E8FF) else Color(0xFFE2EEFF)
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = cmdInput,
                onValueChange = { cmdInput = it },
                placeholder = { Text("Enter safe command (e.g. help, ls, ai <prompt>)") },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("terminal_input")
            )
            Button(onClick = {
                if (cmdInput.isNotBlank()) {
                    viewModel.runTerminalCommand(cmdInput)
                    cmdInput = ""
                }
            }, modifier = Modifier.testTag("terminal_run_btn")) {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Run command")
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebsiteBuilderScreen(
    viewModel: ZedxViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val html by viewModel.websiteHtml.collectAsStateWithLifecycle()
    val busy by viewModel.toolBusy.collectAsStateWithLifecycle()
    var prompt by remember { mutableStateOf("") }
    var previewMode by remember { mutableStateOf(true) }

    val exportHtml = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/html")
    ) { uri ->
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { it.write(html.toByteArray()) }
            viewModel.showBanner("Website exported as HTML.")
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 940.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                label = { Text("Describe website to generate (HTML/CSS/JS)") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = {
                if (prompt.isNotBlank()) {
                    viewModel.runAiToolPrompt(
                        "Return ONLY a complete single-file HTML5 document with embedded CSS and JS. Do not wrap in markdown fences.",
                        prompt
                    ) { out ->
                        val cleaned = out.removePrefix("```html").removePrefix("```").removeSuffix("```").trim()
                        if (cleaned.contains("<html", ignoreCase = true)) {
                            viewModel.setWebsiteHtml(cleaned)
                        }
                    }
                }
            }) { Text("Generate") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = previewMode, onClick = { previewMode = true }, label = { Text("Live Preview") })
            FilterChip(selected = !previewMode, onClick = { previewMode = false }, label = { Text("HTML/CSS/JS Code") })
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = { exportHtml.launch("zedx_site.html") }) {
                Icon(Icons.Rounded.Download, null, Modifier.size(16.dp))
                Spacer(Modifier.size(4.dp))
                Text("Export HTML")
            }
        }

        if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

        CrystalGlassCard(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (previewMode) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                        }
                    },
                    update = { webView ->
                        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                OutlinedTextField(
                    value = html,
                    onValueChange = { viewModel.setWebsiteHtml(it) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                    modifier = Modifier.fillMaxSize().padding(8.dp)
                )
            }
        }
    }
}

@Composable
fun ApkBuilderScreen(
    viewModel: ZedxViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val apkCode by viewModel.apkCode.collectAsStateWithLifecycle()
    val busy by viewModel.toolBusy.collectAsStateWithLifecycle()

    var appName by remember { mutableStateOf("TitanApp") }
    var packageName by remember { mutableStateOf("com.titan.generated") }
    var featurePrompt by remember { mutableStateOf("") }
    var buildStatus by remember { mutableStateOf("Ready to validate & package Android Compose project bundle.") }

    val bundle = remember(appName, packageName, apkCode) {
        WorkspaceToolsEngine.buildAndroidProjectBundle(appName, packageName, apkCode)
    }

    val exportZip = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            val zipBytes = WorkspaceToolsEngine.packageZipBytes(bundle)
            context.contentResolver.openOutputStream(uri)?.use { it.write(zipBytes) }
            buildStatus = "Exported complete Gradle Android project ZIP (${zipBytes.size} bytes). Compile APK with `gradle assembleDebug`."
            viewModel.showBanner("Android Project ZIP exported!")
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 940.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            CrystalGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ZEDX Android Project & APK Builder", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Generates and validates a real Kotlin + Jetpack Compose Gradle project (`AndroidManifest.xml`, `build.gradle.kts`, `MainActivity.kt`) and exports a buildable `.zip` bundle. On-device APK binary compilation requires a full Android SDK/Gradle build environment.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("Status: $buildStatus", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = appName, onValueChange = { appName = it }, label = { Text("App Name") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = packageName, onValueChange = { packageName = it }, label = { Text("Package Name") }, singleLine = true, modifier = Modifier.weight(1f))
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = featurePrompt,
                    onValueChange = { featurePrompt = it },
                    label = { Text("Generate MainActivity.kt screen with AI") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = {
                    if (featurePrompt.isNotBlank()) {
                        viewModel.runAiToolPrompt(
                            "Return ONLY valid Kotlin Jetpack Compose MainActivity.kt code for package $packageName. No markdown fences.",
                            featurePrompt
                        ) { out ->
                            val clean = out.removePrefix("```kotlin").removePrefix("```").removeSuffix("```").trim()
                            if (clean.contains("ComponentActivity")) {
                                viewModel.setApkCode(clean)
                                buildStatus = "AI generated MainActivity.kt (${clean.lines().size} lines)."
                            }
                        }
                    }
                }) { Text("AI Build") }
            }
        }

        if (busy) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val hasPkg = packageName.contains(".") && !packageName.contains(" ")
                    val hasActivity = apkCode.contains("ComponentActivity") && apkCode.contains("setContent")
                    buildStatus = if (hasPkg && hasActivity) {
                        "Validation PASSED: ${bundle.size} Gradle files ready for export."
                    } else {
                        "Validation WARNING: Check package name and ComponentActivity setContent."
                    }
                }) { Text("Validate Project") }

                OutlinedButton(onClick = { exportZip.launch("${appName.lowercase()}_android_src.zip") }) {
                    Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Export ZIP Bundle")
                }
            }
        }

        item {
            CodeBlockCard(language = "kotlin", code = apkCode)
        }
    }
}
