package com.titan.zedxai.ai

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import com.titan.zedxai.security.SecretRedactor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.net.URI
import java.util.concurrent.TimeUnit

sealed interface AiStreamEvent {
    data class Delta(val chunk: String, val accumulated: String) : AiStreamEvent
    data class Completed(
        val fullText: String,
        val tokensUsed: Int,
        val estimatedCostUsd: Double,
        val latencyMs: Long
    ) : AiStreamEvent
    data class Error(val message: String) : AiStreamEvent
}

data class AiRequestConfig(
    val providerId: String,
    val modelId: String,
    val baseUrl: String,
    val apiKey: String?,
    val systemPrompt: String,
    val history: List<Pair<String, String>>, // role ("user" | "assistant") to text
    val userPrompt: String,
    val imageBitmap: Bitmap? = null,
    val dailyRequestsUsed: Int = 0,
    val dailyRequestLimit: Int = 200,
    val monthlySpendUsd: Double = 0.0,
    val monthlyBudgetUsd: Double = 25.0
)

class MultiProviderAiEngine(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    private val tag = "MultiProviderAiEngine"

    fun streamCompletion(config: AiRequestConfig): Flow<AiStreamEvent> = flow {
        val startMs = System.currentTimeMillis()
        val provider = ProviderType.fromId(config.providerId)

        // Enforce daily limit and monthly budget before calling paid/external APIs
        if (config.dailyRequestsUsed >= config.dailyRequestLimit) {
            emit(AiStreamEvent.Error("Daily request limit (${config.dailyRequestLimit}) reached. Adjust quota in Settings or wait until tomorrow."))
            return@flow
        }
        if (provider.isPaidThirdParty && config.monthlySpendUsd >= config.monthlyBudgetUsd) {
            emit(AiStreamEvent.Error("Monthly paid-provider budget ($${"%.2f".format(config.monthlyBudgetUsd)}) reached. No silent fallback will occur."))
            return@flow
        }

        if (provider.requiresApiKey && config.apiKey.isNullOrBlank()) {
            emit(AiStreamEvent.Error("${provider.displayName} requires your own API key in Provider Manager. No silent paid fallback is enabled."))
            return@flow
        }

        if (provider != ProviderType.GEMINI_AI_LOGIC) {
            val urlValidation = validateEndpointUrl(config.baseUrl, provider.isLocalEndpoint)
            if (urlValidation != null) {
                emit(AiStreamEvent.Error(urlValidation))
                return@flow
            }
        }

        if (provider == ProviderType.GEMINI_AI_LOGIC) {
            streamFirebaseGemini(config, startMs).collect { emit(it) }
        } else if (provider == ProviderType.CLAUDE) {
            streamAnthropic(config, startMs).collect { emit(it) }
        } else {
            streamOpenAiCompatible(config, startMs).collect { emit(it) }
        }
    }

    private fun streamFirebaseGemini(config: AiRequestConfig, startMs: Long): Flow<AiStreamEvent> = flow {
        val modelName = config.modelId.ifBlank { "gemini-3.8-flash" }
        val model = Firebase.ai.generativeModel(
            modelName = modelName,
            systemInstruction = content { text(config.systemPrompt) }
        )
        val accumulated = StringBuilder()
        try {
            withTimeout(60_000L) {
                val promptContent = content("user") {
                    if (config.imageBitmap != null) {
                        image(scaleBitmapForPayload(config.imageBitmap))
                    }
                    val contextPrefix = if (config.history.isNotEmpty()) {
                        buildString {
                            append("Conversation context:\n")
                            config.history.takeLast(8).forEach { (r, t) ->
                                append(if (r == "user") "User: " else "Assistant: ")
                                append(t.take(1200)).append("\n")
                            }
                            append("\nCurrent user request:\n")
                        }
                    } else ""
                    text(contextPrefix + config.userPrompt)
                }
                model.generateContentStream(promptContent).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        accumulated.append(piece)
                        emit(AiStreamEvent.Delta(piece, accumulated.toString()))
                    }
                }
            }
            val full = accumulated.toString().trim()
            if (full.isEmpty()) {
                emit(AiStreamEvent.Error("Gemini returned an empty response."))
            } else {
                val estTokens = estimateTokens(config.userPrompt + full)
                val costPer1k = ProviderCatalog.findModel(modelName)?.estimatedCostPer1kTokensUsd ?: 0.0
                emit(
                    AiStreamEvent.Completed(
                        fullText = full,
                        tokensUsed = estTokens,
                        estimatedCostUsd = (estTokens / 1000.0) * costPer1k,
                        latencyMs = System.currentTimeMillis() - startMs
                    )
                )
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(tag, "Gemini stream timed out", e)
            emit(AiStreamEvent.Error("Gemini generation timed out after 60 seconds. Please retry."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val safeMsg = SecretRedactor.redact(e.localizedMessage ?: "Firebase AI generation failed.")
            Log.e(tag, "Gemini stream error: $safeMsg", e)
            emit(AiStreamEvent.Error(safeMsg))
        }
    }

    private fun streamOpenAiCompatible(config: AiRequestConfig, startMs: Long): Flow<AiStreamEvent> = callbackFlow {
        val base = config.baseUrl.trimEnd('/')
        val url = if (base.endsWith("/chat/completions")) base else "$base/chat/completions"

        val messagesArray = JSONArray()
        if (config.systemPrompt.isNotBlank()) {
            messagesArray.put(JSONObject().put("role", "system").put("content", config.systemPrompt))
        }
        config.history.takeLast(10).forEach { (role, text) ->
            messagesArray.put(JSONObject().put("role", role).put("content", text))
        }

        if (config.imageBitmap != null) {
            val b64 = bitmapToBase64Jpeg(scaleBitmapForPayload(config.imageBitmap))
            val parts = JSONArray()
                .put(JSONObject().put("type", "text").put("text", config.userPrompt))
                .put(
                    JSONObject()
                        .put("type", "image_url")
                        .put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$b64"))
                )
            messagesArray.put(JSONObject().put("role", "user").put("content", parts))
        } else {
            messagesArray.put(JSONObject().put("role", "user").put("content", config.userPrompt))
        }

        val bodyJson = JSONObject()
            .put("model", config.modelId)
            .put("messages", messagesArray)
            .put("stream", true)

        val reqBuilder = Request.Builder()
            .url(url)
            .post(bodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")

        if (!config.apiKey.isNullOrBlank()) {
            reqBuilder.header("Authorization", "Bearer ${config.apiKey.trim()}")
        }
        if (config.providerId == ProviderType.OPENROUTER.id) {
            reqBuilder.header("HTTP-Referer", "https://zedx.titan.ai")
            reqBuilder.header("X-Title", "ZEDX AI")
        }

        val call = httpClient.newCall(reqBuilder.build())
        val accumulated = StringBuilder()

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string().orEmpty().take(300)
                    trySend(AiStreamEvent.Error("HTTP ${response.code}: ${SecretRedactor.redact(errBody.ifBlank { response.message })}"))
                    close()
                    return@callbackFlow
                }
                val body = response.body ?: run {
                    trySend(AiStreamEvent.Error("Empty response body from ${config.providerId}"))
                    close()
                    return@callbackFlow
                }
                BufferedReader(InputStreamReader(body.byteStream())).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.startsWith("data:")) {
                            val data = line.removePrefix("data:").trim()
                            if (data == "[DONE]") break
                            runCatching {
                                val obj = JSONObject(data)
                                val deltaText = obj.optJSONArray("choices")
                                    ?.optJSONObject(0)
                                    ?.optJSONObject("delta")
                                    ?.optString("content", "")
                                    .orEmpty()
                                if (deltaText.isNotEmpty()) {
                                    accumulated.append(deltaText)
                                    trySend(AiStreamEvent.Delta(deltaText, accumulated.toString()))
                                }
                            }
                        }
                    }
                }
                val full = accumulated.toString().trim()
                if (full.isEmpty()) {
                    trySend(AiStreamEvent.Error("Provider returned an empty stream."))
                } else {
                    val estTokens = estimateTokens(config.userPrompt + full)
                    val costPer1k = ProviderCatalog.findModel(config.modelId)?.estimatedCostPer1kTokensUsd ?: 0.002
                    trySend(
                        AiStreamEvent.Completed(
                            fullText = full,
                            tokensUsed = estTokens,
                            estimatedCostUsd = (estTokens / 1000.0) * costPer1k,
                            latencyMs = System.currentTimeMillis() - startMs
                        )
                    )
                }
            }
        } catch (e: CancellationException) {
            call.cancel()
            throw e
        } catch (e: Exception) {
            val safeMsg = SecretRedactor.redact(e.localizedMessage ?: "Provider request failed.")
            Log.e(tag, "OpenAI-compatible stream failed: $safeMsg")
            trySend(AiStreamEvent.Error(safeMsg))
        }
        close()
        awaitClose { call.cancel() }
    }.flowOn(Dispatchers.IO)

    private fun streamAnthropic(config: AiRequestConfig, startMs: Long): Flow<AiStreamEvent> = callbackFlow {
        val base = config.baseUrl.trimEnd('/')
        val url = if (base.endsWith("/messages")) base else "$base/messages"
        val messagesArray = JSONArray()
        config.history.takeLast(10).forEach { (role, text) ->
            messagesArray.put(JSONObject().put("role", if (role == "assistant") "assistant" else "user").put("content", text))
        }
        messagesArray.put(JSONObject().put("role", "user").put("content", config.userPrompt))

        val bodyJson = JSONObject()
            .put("model", config.modelId)
            .put("max_tokens", 2048)
            .put("stream", true)
            .put("messages", messagesArray)
        if (config.systemPrompt.isNotBlank()) {
            bodyJson.put("system", config.systemPrompt)
        }

        val request = Request.Builder()
            .url(url)
            .post(bodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("x-api-key", config.apiKey.orEmpty().trim())
            .header("anthropic-version", "2023-06-01")
            .header("Content-Type", "application/json")
            .build()

        val call = httpClient.newCall(request)
        val accumulated = StringBuilder()
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string().orEmpty().take(300)
                    trySend(AiStreamEvent.Error("Claude HTTP ${response.code}: ${SecretRedactor.redact(err)}"))
                    close()
                    return@callbackFlow
                }
                val body = response.body ?: run {
                    trySend(AiStreamEvent.Error("Empty response from Claude."))
                    close()
                    return@callbackFlow
                }
                BufferedReader(InputStreamReader(body.byteStream())).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.startsWith("data:")) {
                            val data = line.removePrefix("data:").trim()
                            runCatching {
                                val obj = JSONObject(data)
                                if (obj.optString("type") == "content_block_delta") {
                                    val piece = obj.optJSONObject("delta")?.optString("text", "").orEmpty()
                                    if (piece.isNotEmpty()) {
                                        accumulated.append(piece)
                                        trySend(AiStreamEvent.Delta(piece, accumulated.toString()))
                                    }
                                }
                            }
                        }
                    }
                }
                val full = accumulated.toString().trim()
                if (full.isEmpty()) {
                    trySend(AiStreamEvent.Error("Claude returned empty text."))
                } else {
                    val estTokens = estimateTokens(config.userPrompt + full)
                    val costPer1k = ProviderCatalog.findModel(config.modelId)?.estimatedCostPer1kTokensUsd ?: 0.005
                    trySend(
                        AiStreamEvent.Completed(
                            fullText = full,
                            tokensUsed = estTokens,
                            estimatedCostUsd = (estTokens / 1000.0) * costPer1k,
                            latencyMs = System.currentTimeMillis() - startMs
                        )
                    )
                }
            }
        } catch (e: CancellationException) {
            call.cancel()
            throw e
        } catch (e: Exception) {
            val safeMsg = SecretRedactor.redact(e.localizedMessage ?: "Claude stream failed")
            trySend(AiStreamEvent.Error(safeMsg))
        }
        close()
        awaitClose { call.cancel() }
    }.flowOn(Dispatchers.IO)

    suspend fun testConnection(providerId: String, baseUrl: String, modelId: String, apiKey: String?): Result<String> =
        withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            val provider = ProviderType.fromId(providerId)
            if (provider == ProviderType.GEMINI_AI_LOGIC) {
                return@withContext try {
                    withTimeout(25_000L) {
                        val res = Firebase.ai.generativeModel(modelId.ifBlank { "gemini-3.8-flash" })
                            .generateContent("Reply with OK")
                        val latency = System.currentTimeMillis() - start
                        val txt = res.text?.take(40).orEmpty()
                        Result.success("Connected (${latency}ms): $txt")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(Exception(SecretRedactor.redact(e.localizedMessage ?: "Gemini connection failed")))
                }
            }

            val urlErr = validateEndpointUrl(baseUrl, provider.isLocalEndpoint)
            if (urlErr != null) return@withContext Result.failure(IllegalArgumentException(urlErr))
            if (provider.requiresApiKey && apiKey.isNullOrBlank()) {
                return@withContext Result.failure(IllegalStateException("Missing API key for ${provider.displayName}"))
            }

            return@withContext try {
                val cleanBase = baseUrl.trimEnd('/')
                val testUrl = when (provider) {
                    ProviderType.CLAUDE -> "$cleanBase/models"
                    else -> "$cleanBase/models"
                }
                val reqBuilder = Request.Builder().url(testUrl).get()
                if (provider == ProviderType.CLAUDE) {
                    reqBuilder.header("x-api-key", apiKey.orEmpty().trim())
                    reqBuilder.header("anthropic-version", "2023-06-01")
                } else if (!apiKey.isNullOrBlank()) {
                    reqBuilder.header("Authorization", "Bearer ${apiKey.trim()}")
                }
                httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                    val latency = System.currentTimeMillis() - start
                    if (resp.isSuccessful) {
                        Result.success("Connected (${latency}ms, HTTP ${resp.code})")
                    } else {
                        Result.failure(Exception("HTTP ${resp.code}: ${resp.message}"))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(Exception(SecretRedactor.redact(e.localizedMessage ?: "Connection test failed")))
            }
        }

    companion object {
        fun validateEndpointUrl(rawUrl: String, allowLocalHttp: Boolean): String? {
            val parsed = runCatching { URI(rawUrl.trim()) }.getOrNull()
                ?: return "Invalid endpoint URL."
            val scheme = parsed.scheme?.lowercase() ?: return "URL scheme required."
            val host = parsed.host?.lowercase() ?: return "URL host required."
            if (scheme == "https") return null
            val isLoopback = host == "127.0.0.1" || host == "localhost" || host == "10.0.2.2" ||
                host.startsWith("192.168.") || host.startsWith("10.")
            if (scheme == "http" && allowLocalHttp && isLoopback) return null
            return "Remote AI endpoints must use HTTPS for security."
        }

        fun estimateTokens(text: String): Int = (text.length / 4).coerceAtLeast(1)

        private fun scaleBitmapForPayload(bitmap: Bitmap): Bitmap {
            val maxDim = 1280
            val w = bitmap.width
            val h = bitmap.height
            if (w <= maxDim && h <= maxDim) return bitmap
            val ratio = minOf(maxDim.toFloat() / w, maxDim.toFloat() / h)
            return Bitmap.createScaledBitmap(bitmap, (w * ratio).toInt(), (h * ratio).toInt(), true)
        }

        private fun bitmapToBase64Jpeg(bitmap: Bitmap): String {
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 82, baos)
            return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        }
    }
}
