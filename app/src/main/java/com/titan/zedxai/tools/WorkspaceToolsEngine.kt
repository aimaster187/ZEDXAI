package com.titan.zedxai.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class WorkspaceFile(
    val name: String,
    val content: String
)

object WorkspaceToolsEngine {

    fun isSafeBrowserUrl(url: String): Pair<Boolean, String> {
        val trimmed = url.trim()
        val lower = trimmed.lowercase()
        if (lower.startsWith("javascript:") || lower.startsWith("file:") || lower.startsWith("content:")) {
            return false to "Blocked unsafe URI scheme for security."
        }
        if (lower.startsWith("http://")) {
            return true to "Warning: Unencrypted HTTP connection. Prefer HTTPS."
        }
        return true to "Safe HTTPS browsing"
    }

    fun normalizeBrowserUrl(input: String): String {
        val t = input.trim()
        if (t.isEmpty()) return "https://developer.android.com"
        if (t.startsWith("https://") || t.startsWith("http://")) return t
        if (t.contains(".") && !t.contains(" ")) return "https://$t"
        return "https://www.google.com/search?q=${Uri.encode(t)}"
    }

    fun evaluateSafeTerminalCommand(
        rawCommand: String,
        virtualFiles: Map<String, String>,
        userEmail: String,
        activeProvider: String,
        activeModel: String
    ): Pair<String, Boolean> {
        val cmd = rawCommand.trim()
        if (cmd.isEmpty()) return "" to false
        val parts = cmd.split(Regex("\\s+"), limit = 2)
        val verb = parts[0].lowercase()
        val arg = parts.getOrNull(1)?.trim().orEmpty()

        return when (verb) {
            "help" -> (
                """
                ZEDX Safe Terminal (Sandboxed Workspace)
                Commands:
                  help             Show available commands
                  pwd              Print current virtual workspace
                  ls               List workspace files
                  cat <file>       Display workspace file content
                  echo <text>      Print text
                  date             Show local timestamp
                  whoami           Show signed-in user identity
                  sysinfo          Show Android device & active AI router info
                  ai <prompt>      Send prompt directly to active AI model
                  clear            Clear terminal buffer
                """.trimIndent() to false
            )
            "clear" -> "__CLEAR__" to false
            "pwd" -> "/zedx/workspace" to false
            "ls" -> (if (virtualFiles.isEmpty()) "(workspace empty)" else virtualFiles.keys.joinToString("\n")) to false
            "cat" -> {
                if (arg.isBlank()) "Usage: cat <filename>" to false
                else (virtualFiles[arg] ?: "File not found in safe workspace: $arg") to false
            }
            "echo" -> arg to false
            "date" -> SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.getDefault()).format(Date()) to false
            "whoami" -> userEmail.ifBlank { "authenticated-user" } to false
            "sysinfo" -> (
                """
                App: ZEDX AI v1.0.0 (BY THE TITAN AI)
                Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})
                Active AI: $activeProvider / $activeModel
                Sandbox: Restricted Safe Terminal (no arbitrary shell or DCL)
                """.trimIndent() to false
            )
            "ai" -> {
                if (arg.isBlank()) "Usage: ai <prompt>" to false
                else arg to true
            }
            else -> "Command '$verb' is restricted or unrecognized. Type 'help' for permitted sandbox commands." to false
        }
    }

    fun readDocumentText(context: Context, uri: Uri, maxChars: Int = 24000): Result<Pair<String, String>> = runCatching {
        val name = resolveDisplayName(context, uri)
        val mime = context.contentResolver.getType(uri).orEmpty()
        if (mime == "application/pdf" || name.endsWith(".pdf", ignoreCase = true)) {
            val pages = renderPdfPages(context, uri, maxPages = 3).getOrDefault(emptyList())
            val info = "PDF Document: $name (${pages.size} preview page(s) rendered for Vision/Q&A)"
            name to info
        } else {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                val buf = CharArray(maxChars)
                val read = reader.read(buf)
                if (read > 0) String(buf, 0, read) else ""
            }.orEmpty()
            name to text.ifBlank { "(Binary or empty file: $name)" }
        }
    }

    fun renderPdfPages(context: Context, uri: Uri, maxPages: Int = 5): Result<List<Bitmap>> = runCatching {
        val result = mutableListOf<Bitmap>()
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val count = minOf(renderer.pageCount, maxPages)
                for (i in 0 until count) {
                    renderer.openPage(i).use { page ->
                        val bmp = Bitmap.createBitmap(
                            (page.width * 1.5f).toInt().coerceAtMost(1400),
                            (page.height * 1.5f).toInt().coerceAtMost(1800),
                            Bitmap.Config.ARGB_8888
                        )
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        result.add(bmp)
                    }
                }
            }
        }
        result
    }

    fun resolveDisplayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return cursor.getString(idx) ?: "document"
            }
        }
        return uri.lastPathSegment ?: "document"
    }

    fun buildAndroidProjectBundle(
        appName: String,
        packageName: String,
        mainComposeCode: String
    ): Map<String, String> {
        val cleanPkg = packageName.trim().ifBlank { "com.titan.generated" }
        val cleanName = appName.trim().ifBlank { "TitanGeneratedApp" }
        val pkgPath = cleanPkg.replace('.', '/')

        return linkedMapOf(
            "settings.gradle.kts" to """
                rootProject.name = "$cleanName"
                include(":app")
            """.trimIndent(),
            "build.gradle.kts" to """
                plugins {
                    id("com.android.application") version "8.9.0" apply false
                    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
                    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
                }
            """.trimIndent(),
            "app/build.gradle.kts" to """
                plugins {
                    id("com.android.application")
                    id("org.jetbrains.kotlin.android")
                    id("org.jetbrains.kotlin.plugin.compose")
                }
                android {
                    namespace = "$cleanPkg"
                    compileSdk = 36
                    defaultConfig {
                        applicationId = "$cleanPkg"
                        minSdk = 24
                        targetSdk = 36
                        versionCode = 1
                        versionName = "1.0.0"
                    }
                    buildFeatures { compose = true }
                }
                dependencies {
                    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
                    implementation("androidx.activity:activity-compose:1.10.1")
                    implementation("androidx.compose.material3:material3")
                }
            """.trimIndent(),
            "app/src/main/AndroidManifest.xml" to """
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                    <application
                        android:label="$cleanName"
                        android:theme="@android:style/Theme.Material.Light.NoActionBar">
                        <activity android:name=".MainActivity" android:exported="true">
                            <intent-filter>
                                <action android:name="android.intent.action.MAIN" />
                                <category android:name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                        </activity>
                    </application>
                </manifest>
            """.trimIndent(),
            "app/src/main/java/$pkgPath/MainActivity.kt" to mainComposeCode
        )
    }

    fun packageZipBytes(files: Map<String, String>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            files.forEach { (path, content) ->
                zos.putNextEntry(ZipEntry(path))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }
}
