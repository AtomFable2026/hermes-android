<div align="center">

# ✦ Aetheris AI

### *One app. Every great AI model.*

A beautiful, fast, fully native **Android chat client** for OpenAI, Anthropic, Groq, OpenRouter, and any OpenAI-compatible endpoint — with streaming responses, encrypted on-device API keys, and a polished Jetpack Compose UI.

[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-2024.12-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Material 3](https://img.shields.io/badge/Material%203-Design-757575?logo=materialdesign&logoColor=white)](https://m3.material.io)
[![License](https://img.shields.io/badge/License-MIT-F59E0B)](#license)

</div>

---

## ✨ Why Aetheris

Most chat apps lock you into one provider. **Aetheris** lets you talk to *every* major LLM — switch models mid-conversation, bring your own keys, and keep everything encrypted on your device. No accounts, no servers, no telemetry.

> ⚡ Streaming-first · 🔐 Encrypted keys · 🎨 Designed, not assembled · 🪶 Single 100% Compose module

## 🚀 Features

- 🔌 **Multi-provider** — OpenAI, Anthropic (Claude), Groq, OpenRouter, plus **unlimited user-defined custom providers** (Ollama, Together, LM Studio, vLLM, your own gateway, …) — both OpenAI-compatible and Anthropic-compatible API styles
- 🔭 **Live model discovery** — fetch the model catalog from any provider via `GET /v1/models`, cached in Room; pin / hide / refresh per provider
- ➕ **Add your own models** — manually add any model id to any provider, in case its catalog endpoint is unavailable
- 💬 **Real streaming** — proper SSE framing for both OpenAI `[DONE]`-style and Anthropic typed events
- 🔐 **Encrypted API keys** — `EncryptedSharedPreferences` with `MasterKey` (AES‑256‑GCM); separate key per provider (built-in *or* custom); never backed up to the cloud, never sent anywhere except the provider you chose
- 💾 **Persistent conversations** — Room database, auto-titled from your first message
- 🧠 **Per-conversation model + system prompt** — change provider / model on the fly from the chat header
- 📝 **Markdown rendering** — code blocks, lists, links, strikethrough via Markwon
- 🎨 **Aetheris design system** — custom Material 3 theme (Electric Violet / Cyan / Amber), edge-to-edge, light + dark, follows your system or your preference
- ✋ **Stop generation**, ⏎ multi-line input, 📋 one-tap copy, ⏱ live typing indicator
- 🛡 **Secure by default** — release builds disable HTTP body logging, HTTPS-only network policy, `allowBackup="false"`, data-extraction rules exclude the encrypted store and the chat DB

## 📸 Screens

| Conversations | Chat (streaming) | Settings |
| :-: | :-: | :-: |
| _Add screenshot_ | _Add screenshot_ | _Add screenshot_ |

> Drop screenshots into `docs/` and reference them here once you have a build.

## 🧱 Architecture

A clean **MVVM + repository** split, single Gradle module, package root `com.aetheris.chat`.

```
ui/               ← Compose screens (chat, conversations, settings) + ViewModels
ui/components     ← ChatBubble, MessageInput, ModelSelector, TypingIndicator
ui/theme          ← Aetheris Material 3 palette, typography, theme
data/local        ← Room: AppDatabase, ConversationDao, MessageDao
data/remote       ← LlmClient (OkHttp + SSE) + OpenAI / Anthropic DTOs
data/repository   ← ChatRepository, SettingsRepository, ProvidersRepository, BackupRepository
di/               ← Hilt modules (AppModule, DatabaseModule)
```

```
┌──────────────┐   StateFlow   ┌────────────────┐
│  Compose UI  │ ◄──────────── │   ViewModels   │
└──────────────┘               └───────┬────────┘
                                       │
           ┌───────────┬───────────────┼───────────────┬───────────┐
           ▼           ▼               ▼               ▼           ▼
  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
  │    Chat      │ │   Settings   │ │  Providers   │ │   Backup     │
  │  Repository  │ │  Repository  │ │  Repository  │ │  Repository  │
  └──────┬───────┘ └──────┬───────┘ └──────┬───────┘ └──────┬───────┘
         │                │                │                │
         ▼                ▼                ▼                ▼
   ┌──────────┐   ┌──────────────┐  ┌──────────┐    ┌──────────┐
   │  Room DB │   │  DataStore + │  │  Room DB │    │  File I/O│
   │ Conv/Msg │   │  Encrypted   │  │  Models  │    │  AES-256 │
   └──────────┘   │  SharedPrefs │  └──────────┘    └──────────┘
                  └──────┬───────┘
                         │
                         ▼
                  ┌──────────────┐
                  │   LlmClient  │
                  │  OkHttp+SSE  │
                  └──────────────┘
```

## 🛠 Tech Stack

| Layer | Tools |
| --- | --- |
| **Language / Build** | Kotlin 2.1, AGP 8.7.3, Gradle 9.5, JDK 17 |
| **UI** | Jetpack Compose (BOM 2024.12), Material 3, Navigation-Compose, Material Icons Extended |
| **DI** | Hilt 2.53 + `hilt-navigation-compose` |
| **Persistence** | Room 2.6, DataStore Preferences, `EncryptedSharedPreferences` (security-crypto 1.1.0) |
| **Networking** | OkHttp 4.12 (raw SSE), `kotlinx-serialization-json` |
| **Markdown** | Markwon 4.6 (core + strikethrough) |
| **Async** | Kotlin Coroutines + Flow |
| **Min / Target SDK** | 26 / 35 |

## 📦 Supported providers

| Provider | Type | Example models |
| --- | --- | --- |
| **OpenAI** | OpenAI-compatible | GPT‑4o, GPT‑4o mini, GPT‑4.1 family, o4‑mini |
| **Anthropic** | Native `/v1/messages` | Claude Sonnet 4, Claude 3.5 Sonnet, Claude 3.5 Haiku |
| **Groq** | OpenAI-compatible | Llama 3.3 70B, Mixtral 8x7B, Gemma 2 9B |
| **OpenRouter** | OpenAI-compatible | GPT‑4o, Claude, Gemini 2.5 Pro, Llama 3.3 (via OR) |
| **Custom (any number)** | OpenAI- or Anthropic-compatible | Any base URL + model id (Ollama, vLLM, LM Studio, Together, your own gateway, …) |

> Models are seeded from a curated bootstrap list and then **augmented at runtime** by `GET /v1/models` — tap the refresh icon in the model picker (or in Settings) to pull the latest catalog from any provider. Manually-entered models persist alongside discovered ones; pin favorites to the top, hide noisy entries.

## 🏁 Getting Started

### Prerequisites

- **Android Studio** Ladybug (2024.2) or newer
- **JDK 17**
- An Android device or emulator running **API 26+**

### Build & run

```bash
git clone https://github.com/rahulmasal/AetherisAI.git
cd AetherisAI
# Open in Android Studio, then Run ▶ — or:
./gradlew assembleDebug
./gradlew installDebug
```

> If `gradlew` is missing on a fresh checkout, run `gradle wrapper` once or open the project in Android Studio to generate it.

### First launch

1. Open **Settings** → paste your API key for any built-in provider (saved encrypted on-device).
2. Optionally tap **Add custom provider** to register an Ollama / vLLM / LM Studio / Together / private gateway endpoint. Pick the API style (OpenAI- or Anthropic-compatible), set the base URL, and save its API key.
3. Tap **Fetch models** on a provider card to populate the model catalog from `GET /v1/models`. Or use **Add model** to enter ids manually.
4. Pick your provider + model from the chat header (or hit the refresh icon there). Start chatting. Tap ⏹ to stop streaming, ✦ for a new chat.

## 🔐 Privacy & security

- API keys live in `EncryptedSharedPreferences` (AES‑256‑GCM keys / AES‑256‑SIV names) backed by Android's hardware-backed `MasterKey`.
- HTTP request/response bodies are logged **only in debug builds** — release builds never write your keys to logcat.
- `network_security_config` enforces **HTTPS-only** by default.
- `allowBackup="false"` and `data_extraction_rules.xml` keep the encrypted prefs, the Room DB, and DataStore out of cloud backups and device-transfer flows.
- No analytics. No crash reporting. No remote config. Your messages go from your phone straight to the provider you picked.

## 🗺 Roadmap

- [ ] Image input (vision models)
- [ ] Message editing & regeneration
- [ ] Conversation export (Markdown / JSON)
- [ ] Search across conversations
- [ ] Custom theme accents
- [ ] Tablet / foldable layouts
- [ ] Voice input
- [ ] Local model support via `llama.cpp` / MLC-LLM

## 🤝 Contributing

Issues and PRs are very welcome. If you're adding a feature, please:

1. Open an issue first to discuss the approach.
2. Match the existing Compose / MVVM style.
3. Keep dependencies lean — Aetheris ships small on purpose.

## 📜 License

[MIT](LICENSE) © Aetheris AI contributors

---

<div align="center">

Crafted with care for people who love good software and good AI. ✦

</div>

---
Auto-build enabled. Push code to trigger APK release.
