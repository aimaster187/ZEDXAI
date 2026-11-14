package com.titan.zedxai.assistant

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Optional, user-enabled Accessibility helper.
 * Never reads password fields, OTP inputs, or performs silent background automation.
 */
class ZedxAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString().orEmpty()
        if (pkg.isNotBlank()) {
            latestPackage = pkg
        }
    }

    override fun onInterrupt() {
        // No-op
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    fun captureSafeVisibleSummary(): String {
        val root = rootInActiveWindow ?: return "No active window available."
        val collected = mutableListOf<String>()
        collectNonSensitiveText(root, collected, 0)
        return if (collected.isEmpty()) {
            "Active app: $latestPackage (no non-sensitive text found)"
        } else {
            "Active app: $latestPackage\n" + collected.take(15).joinToString("\n")
        }
    }

    private fun collectNonSensitiveText(
        node: AccessibilityNodeInfo,
        out: MutableList<String>,
        depth: Int
    ) {
        if (depth > 8 || out.size >= 20) return
        if (node.isPassword) return
        val text = node.text?.toString()?.trim().orEmpty()
        val lower = text.lowercase()
        val looksSensitive = "otp" in lower || "password" in lower || "cvv" in lower || "pin" in lower
        if (text.isNotBlank() && !looksSensitive) {
            out.add(text.take(140))
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectNonSensitiveText(it, out, depth + 1) }
        }
    }

    companion object {
        @Volatile
        var instance: ZedxAccessibilityService? = null
            private set

        @Volatile
        var latestPackage: String = ""
            private set

        fun isEnabled(): Boolean = instance != null
    }
}
