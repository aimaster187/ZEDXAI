# ZEDX AI — BY THE TITAN AI (v1.0.0)

Complete multi-provider Android AI ecosystem built with Kotlin, Jetpack Compose, Material 3 (`White Crystal`, `Dark Space`, `System Adaptive`), Firebase Auth, Google Credential Manager, Firebase App Check, Firebase AI Logic (`Gemini`), Firestore UID-isolated cloud sync, Room offline persistence, and a separate responsive Web Admin Console.

## Project Structure
- `app/src/main/java/com/titan/zedxai/` — Core Android architecture:
  - `ai/` — Multi-provider streaming engine & catalog (Gemini AI Logic, OpenAI, OpenRouter, Claude, Groq, DeepSeek, Mistral, Together, Fireworks, Ollama, LM Studio, Custom OpenAI-compatible) + Smart Task Router (no silent paid-provider fallback).
  - `security/` — `KeyVaultManager` (AndroidKeyStore AES-256-GCM encryption + masking) & `SecretRedactor`.
  - `firebase/` — `AppCheckConfig`, `GoogleAuthHelper` (Credential Manager), `ZedxFirestoreRepository` (explicit custom DB ID), error handler.
  - `data/local/` — Room database (`ZedxDatabase`), DAOs, DataStore `AppPreferencesRepository`.
  - `agents/` — Five-Agent Titan Crew orchestrator (`TITAN CORE`, `CODING`, `RESEARCH`, `AUTOMATION`, `CREATIVE`) + Custom Agent Creator + up to 5 concurrent runs + explicit confirmation gate for sensitive actions.
  - `voice/` — `TitanVoiceController` (`SpeechRecognizer` + `TextToSpeech` in EN/HI/Hinglish) & `ZedxVoiceForegroundService`.
  - `assistant/` — `PhoneAssistantController` (WhatsApp, Gmail OAuth/Intent, Calendar, Dialer) & optional `ZedxAccessibilityService` (non-password screen context only).
  - `tools/` — `WorkspaceToolsEngine` (Crystal Browser safety, Safe Terminal, SAF document & `PdfRenderer`, Android Compose ZIP Project Builder).
  - `ui/` — `ZedxRootApp`, `ZedxViewModel`, animated `NeuralZOrb`, Markdown + syntax-highlighted `CodeBlockCard`, and all 27 screens.
- `admin-web/` — Separate responsive HTML/CSS/JS Admin Website (`pranavji628@gmail.com` owner governance via Firebase custom claims).
- `firestore.rules`, `firebase-blueprint.json`, `firestore.test.js` — Zero-trust Firestore security schema, rules, and automated tests.

## External Configuration & Commands

### 1. Firebase & App Check (Provisioned)
- Project ID: `gen-lang-client-0800058431`
- Firestore Database ID: `ai-studio-android-zedxai-8ef47d02-6b3b-4e87-be23-acd8a14f183a`
- Debug signing SHA-1 is registered in `app/google-services.json`.
- For release Play Store builds, configure App Check Debug/Play Integrity or reCAPTCHA Enterprise in Firebase Console.

### 2. Admin Website & Owner Claims (`pranavji628@gmail.com`)
- See `admin-web/README.md` for adding your Firebase Web App `apiKey`/`appId` and running the `firebase-admin` custom claim script (`{ admin: true, role: "admin" }`).

### 3. Gmail API Separate OAuth Consent
- Sending email directly via Gmail REST API requires separate user OAuth consent (`https://www.googleapis.com/auth/gmail.send`) in Google Cloud Console. Without it, the app safely opens the system Gmail/Email compose intent after user confirmation.

### 4. Third-Party AI Provider Keys
- Users add and encrypt their own OpenAI / OpenRouter / Claude / Groq / DeepSeek / Mistral / Together / Fireworks / Custom keys inside **Provider Manager** (stored locally with AndroidKeyStore AES-256-GCM). No shared or hardcoded keys exist in source code.

### 5. Build & Test Commands
```bash
# Run unit & Robolectric tests
gradle :app:testDebugUnitTest

# Build Debug APK
gradle :app:assembleDebug
```
Debug APK output: `app/build/outputs/apk/debug/app-debug.apk`
