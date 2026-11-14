package com.titan.zedxai.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.titan.zedxai.ui.theme.ZedxThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "zedx_app_prefs")

data class AppPreferencesState(
    val onboardingCompleted: Boolean = false,
    val themeMode: ZedxThemeMode = ZedxThemeMode.WHITE_CRYSTAL,
    val voiceLanguage: String = "en-IN",
    val voiceSpeechRate: Float = 1.0f,
    val voicePitch: Float = 1.0f,
    val wakeWordEnabled: Boolean = false,
    val voiceSessionForegroundEnabled: Boolean = false,
    val cloudSyncEnabled: Boolean = true,
    val smartRoutingEnabled: Boolean = true,
    val activeProviderId: String = "gemini",
    val activeModelId: String = "gemini-3.8-flash",
    val activeAgentId: String = "titan_core",
    val dailyRequestLimit: Int = 200,
    val monthlyBudgetUsd: Float = 25.0f,
    val gmailOAuthToken: String = ""
)

class AppPreferencesRepository(private val context: Context) {

    val preferencesFlow: Flow<AppPreferencesState> = context.appPrefsDataStore.data.map { prefs ->
        AppPreferencesState(
            onboardingCompleted = prefs[KEY_ONBOARDING_DONE] ?: false,
            themeMode = ZedxThemeMode.fromId(prefs[KEY_THEME_MODE] ?: "white_crystal"),
            voiceLanguage = prefs[KEY_VOICE_LANG] ?: "en-IN",
            voiceSpeechRate = prefs[KEY_VOICE_RATE] ?: 1.0f,
            voicePitch = prefs[KEY_VOICE_PITCH] ?: 1.0f,
            wakeWordEnabled = prefs[KEY_WAKE_WORD] ?: false,
            voiceSessionForegroundEnabled = prefs[KEY_VOICE_FGS] ?: false,
            cloudSyncEnabled = prefs[KEY_CLOUD_SYNC] ?: true,
            smartRoutingEnabled = prefs[KEY_SMART_ROUTING] ?: true,
            activeProviderId = prefs[KEY_ACTIVE_PROVIDER] ?: "gemini",
            activeModelId = prefs[KEY_ACTIVE_MODEL] ?: "gemini-3.8-flash",
            activeAgentId = prefs[KEY_ACTIVE_AGENT] ?: "titan_core",
            dailyRequestLimit = prefs[KEY_DAILY_LIMIT] ?: 200,
            monthlyBudgetUsd = prefs[KEY_MONTHLY_BUDGET] ?: 25.0f,
            gmailOAuthToken = prefs[KEY_GMAIL_TOKEN] ?: ""
        )
    }

    suspend fun setOnboardingCompleted(done: Boolean) {
        context.appPrefsDataStore.edit { it[KEY_ONBOARDING_DONE] = done }
    }

    suspend fun setThemeMode(mode: ZedxThemeMode) {
        context.appPrefsDataStore.edit { it[KEY_THEME_MODE] = mode.id }
    }

    suspend fun setVoiceSettings(
        language: String,
        speechRate: Float,
        pitch: Float,
        wakeWordEnabled: Boolean,
        foregroundEnabled: Boolean
    ) {
        context.appPrefsDataStore.edit {
            it[KEY_VOICE_LANG] = language
            it[KEY_VOICE_RATE] = speechRate.coerceIn(0.5f, 2.0f)
            it[KEY_VOICE_PITCH] = pitch.coerceIn(0.5f, 2.0f)
            it[KEY_WAKE_WORD] = wakeWordEnabled
            it[KEY_VOICE_FGS] = foregroundEnabled
        }
    }

    suspend fun setRoutingAndSync(
        cloudSyncEnabled: Boolean,
        smartRoutingEnabled: Boolean
    ) {
        context.appPrefsDataStore.edit {
            it[KEY_CLOUD_SYNC] = cloudSyncEnabled
            it[KEY_SMART_ROUTING] = smartRoutingEnabled
        }
    }

    suspend fun setActiveModel(providerId: String, modelId: String) {
        context.appPrefsDataStore.edit {
            it[KEY_ACTIVE_PROVIDER] = providerId
            it[KEY_ACTIVE_MODEL] = modelId
        }
    }

    suspend fun setActiveAgent(agentId: String) {
        context.appPrefsDataStore.edit { it[KEY_ACTIVE_AGENT] = agentId }
    }

    suspend fun setQuotas(dailyLimit: Int, monthlyBudgetUsd: Float) {
        context.appPrefsDataStore.edit {
            it[KEY_DAILY_LIMIT] = dailyLimit.coerceIn(1, 100000)
            it[KEY_MONTHLY_BUDGET] = monthlyBudgetUsd.coerceIn(0f, 10000f)
        }
    }

    suspend fun setGmailOAuthToken(token: String) {
        context.appPrefsDataStore.edit { it[KEY_GMAIL_TOKEN] = token }
    }

    private companion object {
        val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_completed")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_VOICE_LANG = stringPreferencesKey("voice_language")
        val KEY_VOICE_RATE = floatPreferencesKey("voice_speech_rate")
        val KEY_VOICE_PITCH = floatPreferencesKey("voice_pitch")
        val KEY_WAKE_WORD = booleanPreferencesKey("wake_word_enabled")
        val KEY_VOICE_FGS = booleanPreferencesKey("voice_foreground_enabled")
        val KEY_CLOUD_SYNC = booleanPreferencesKey("cloud_sync_enabled")
        val KEY_SMART_ROUTING = booleanPreferencesKey("smart_routing_enabled")
        val KEY_ACTIVE_PROVIDER = stringPreferencesKey("active_provider_id")
        val KEY_ACTIVE_MODEL = stringPreferencesKey("active_model_id")
        val KEY_ACTIVE_AGENT = stringPreferencesKey("active_agent_id")
        val KEY_DAILY_LIMIT = intPreferencesKey("daily_request_limit")
        val KEY_MONTHLY_BUDGET = floatPreferencesKey("monthly_budget_usd")
        val KEY_GMAIL_TOKEN = stringPreferencesKey("gmail_oauth_token")
    }
}
