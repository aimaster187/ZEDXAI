package com.titan.zedxai.assistant

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract
import android.provider.Settings
import android.util.Base64
import androidx.core.app.NotificationCompat
import com.example.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object PhoneAssistantController {

    fun openUrl(context: Context, rawUrl: String): Result<String> = runCatching {
        val safeUrl = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) rawUrl else "https://$rawUrl"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        "Opened $safeUrl"
    }

    fun openDialer(context: Context, phoneNumber: String): Result<String> = runCatching {
        val clean = phoneNumber.filter { it.isDigit() || it == '+' }
        require(clean.isNotEmpty()) { "Valid phone number required" }
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$clean")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        "Opened dialer for $clean"
    }

    fun draftSms(context: Context, phoneNumber: String, body: String): Result<String> = runCatching {
        val clean = phoneNumber.filter { it.isDigit() || it == '+' }
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$clean")).apply {
            putExtra("sms_body", body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        "Opened SMS draft"
    }

    fun sendWhatsAppIntent(context: Context, message: String): Result<String> = runCatching {
        val pm = context.packageManager
        val waIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
            setPackage("com.whatsapp")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (waIntent.resolveActivity(pm) != null) {
            context.startActivity(waIntent)
            "Opened WhatsApp composer"
        } else {
            val chooser = Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message)
                },
                "Share message"
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            "WhatsApp not installed; opened system share sheet"
        }
    }

    fun openEmailIntent(context: Context, to: String, subject: String, body: String): Result<String> = runCatching {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:${Uri.encode(to)}")
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        "Opened email app for $to"
    }

    suspend fun sendGmailViaApi(
        oauthAccessToken: String,
        to: String,
        subject: String,
        body: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(oauthAccessToken.isNotBlank()) { "Gmail OAuth token not configured. Authorize Gmail first or use Email Intent." }
            val rfc2822 = "To: $to\r\nSubject: $subject\r\nContent-Type: text/plain; charset=utf-8\r\n\r\n$body"
            val encoded = Base64.encodeToString(
                rfc2822.toByteArray(Charsets.UTF_8),
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )
            val payload = JSONObject().put("raw", encoded).toString()
            val req = Request.Builder()
                .url("https://gmail.googleapis.com/gmail/v1/users/me/messages/send")
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .header("Authorization", "Bearer ${oauthAccessToken.trim()}")
                .build()
            OkHttpClient().newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    "Email sent via Gmail API (HTTP ${resp.code})"
                } else {
                    throw IllegalStateException("Gmail API HTTP ${resp.code}: ${resp.message}")
                }
            }
        }
    }

    fun openCalendarEvent(context: Context, title: String, description: String): Result<String> = runCatching {
        val now = System.currentTimeMillis()
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.Events.DESCRIPTION, description)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, now + 3_600_000L)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, now + 7_200_000L)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        "Opened Calendar event composer"
    }

    fun openSystemSettings(context: Context, action: String): Result<String> = runCatching {
        val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        "Opened system settings"
    }

    fun openAccessibilitySettings(context: Context): Result<String> =
        openSystemSettings(context, Settings.ACTION_ACCESSIBILITY_SETTINGS)

    fun postReminderNotification(context: Context, title: String, body: String): Result<String> = runCatching {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "zedx_reminders"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                context.getString(R.string.reminder_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            nm.createNotificationChannel(channel)
        }
        val notif = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_zedx)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .build()
        nm.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notif)
        "Reminder notification dispatched"
    }
}
