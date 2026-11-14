package com.titan.zedxai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.titan.zedxai.ui.theme.JetBrainsMonoFontFamily

private sealed interface MarkdownSegment {
    data class TextBlock(val text: String) : MarkdownSegment
    data class CodeBlock(val language: String, val code: String) : MarkdownSegment
}

@Composable
fun MarkdownMessageContent(
    markdown: String,
    modifier: Modifier = Modifier
) {
    val segments = remember(markdown) { parseMarkdownSegments(markdown) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        segments.forEach { segment ->
            when (segment) {
                is MarkdownSegment.TextBlock -> {
                    Text(
                        text = styleInlineMarkdown(segment.text, MaterialTheme.colorScheme.primary),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                is MarkdownSegment.CodeBlock -> {
                    CodeBlockCard(language = segment.language, code = segment.code)
                }
            }
        }
    }
}

@Composable
fun CodeBlockCard(
    language: String,
    code: String,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0A1428))
            .border(1.dp, Color(0xFF1F3864), RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF10203D))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = language.ifBlank { "code" }.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF56E2FF)
            )
            IconButton(
                onClick = {
                    clipboard.setText(AnnotatedString(code))
                    copied = true
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                    contentDescription = "Copy code",
                    tint = if (copied) Color(0xFF34D399) else Color(0xFFBFD7FF),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = highlightSyntax(code),
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 12.5.sp,
            lineHeight = 18.sp,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(12.dp)
        )
    }
}

fun highlightSyntax(code: String): AnnotatedString {
    val keywords = setOf(
        "fun", "val", "var", "class", "object", "interface", "data", "return", "if", "else", "when",
        "for", "while", "import", "package", "suspend", "private", "public", "override", "def", "async",
        "await", "const", "let", "function", "export", "import", "true", "false", "null"
    )
    return buildAnnotatedString {
        val lines = code.lines()
        lines.forEachIndexed { idx, line ->
            val trimmed = line.trimStart()
            if (trimmed.startsWith("//") || trimmed.startsWith("#")) {
                withStyle(SpanStyle(color = Color(0xFF7D95B8))) { append(line) }
            } else {
                var i = 0
                while (i < line.length) {
                    val ch = line[i]
                    when {
                        ch == '"' || ch == '\'' -> {
                            val quote = ch
                            var j = i + 1
                            while (j < line.length && line[j] != quote) {
                                if (line[j] == '\\' && j + 1 < line.length) j++
                                j++
                            }
                            if (j < line.length) j++
                            withStyle(SpanStyle(color = Color(0xFF6EE7B7))) {
                                append(line.substring(i, j))
                            }
                            i = j
                        }
                        ch.isLetter() || ch == '_' -> {
                            var j = i + 1
                            while (j < line.length && (line[j].isLetterOrDigit() || line[j] == '_')) j++
                            val word = line.substring(i, j)
                            if (word in keywords) {
                                withStyle(SpanStyle(color = Color(0xFF7DD3FC), fontWeight = FontWeight.SemiBold)) {
                                    append(word)
                                }
                            } else if (word.first().isUpperCase()) {
                                withStyle(SpanStyle(color = Color(0xFFC4B5FD))) { append(word) }
                            } else {
                                withStyle(SpanStyle(color = Color(0xFFE2EEFF))) { append(word) }
                            }
                            i = j
                        }
                        ch.isDigit() -> {
                            var j = i + 1
                            while (j < line.length && (line[j].isDigit() || line[j] == '.')) j++
                            withStyle(SpanStyle(color = Color(0xFFFBBF24))) { append(line.substring(i, j)) }
                            i = j
                        }
                        else -> {
                            withStyle(SpanStyle(color = Color(0xFFCBD5E1))) { append(ch) }
                            i++
                        }
                    }
                }
            }
            if (idx < lines.lastIndex) append("\n")
        }
    }
}

private fun parseMarkdownSegments(input: String): List<MarkdownSegment> {
    val result = mutableListOf<MarkdownSegment>()
    val regex = Regex("```([a-zA-Z0-9_\\-+]*)\\n([\\s\\S]*?)```")
    var lastIndex = 0
    for (match in regex.findAll(input)) {
        val before = input.substring(lastIndex, match.range.first).trim()
        if (before.isNotEmpty()) result.add(MarkdownSegment.TextBlock(before))
        val lang = match.groupValues[1].trim()
        val code = match.groupValues[2].trimEnd()
        result.add(MarkdownSegment.CodeBlock(lang, code))
        lastIndex = match.range.last + 1
    }
    val tail = input.substring(lastIndex).trim()
    if (tail.isNotEmpty() || result.isEmpty()) {
        result.add(MarkdownSegment.TextBlock(tail.ifEmpty { input }))
    }
    return result
}

private fun styleInlineMarkdown(text: String, accentColor: Color): AnnotatedString {
    return buildAnnotatedString {
        val lines = text.lines()
        lines.forEachIndexed { idx, rawLine ->
            val line = when {
                rawLine.startsWith("### ") -> rawLine.removePrefix("### ")
                rawLine.startsWith("## ") -> rawLine.removePrefix("## ")
                rawLine.startsWith("# ") -> rawLine.removePrefix("# ")
                rawLine.startsWith("- ") -> "• " + rawLine.removePrefix("- ")
                else -> rawLine
            }
            val isHeading = rawLine.startsWith("#")
            if (isHeading) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = accentColor)) {
                    append(line)
                }
            } else {
                var i = 0
                while (i < line.length) {
                    if (line.startsWith("**", i)) {
                        val end = line.indexOf("**", i + 2)
                        if (end > i + 2) {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                append(line.substring(i + 2, end))
                            }
                            i = end + 2
                            continue
                        }
                    }
                    if (line[i] == '`') {
                        val end = line.indexOf('`', i + 1)
                        if (end > i + 1) {
                            withStyle(SpanStyle(fontFamily = JetBrainsMonoFontFamily, color = accentColor)) {
                                append(line.substring(i + 1, end))
                            }
                            i = end + 1
                            continue
                        }
                    }
                    append(line[i])
                    i++
                }
            }
            if (idx < lines.lastIndex) append("\n")
        }
    }
}
