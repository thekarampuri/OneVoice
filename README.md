# 🎙️ OneVoice: Real-Time Universal Translator

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com/)
[![WebRTC](https://img.shields.io/badge/Transport-WebRTC-orange.svg)](https://webrtc.org/)

**OneVoice** is a powerful, open-source Android application designed to bridge language barriers in real-time. Whether you are across the table or across the globe, OneVoice provides seamless Speech-to-Speech translation using state-of-the-art on-device AI.

---

## 🚀 Key Translation Modes

### 📱 Remote Conversation (WebRTC)
*   **Global Reach:** Talk to anyone over the internet using our secure WebRTC signaling server.
*   **Two-Way Sync:** Simultaneous speech recognition and translation on both devices.
*   **Secure Transport:** Encrypted data channels for privacy.

### 📻 Walkie-Talkie Mode
*   **Short Bursts:** Perfect for quick interactions or asking directions.
*   **Auto-Detection:** Automatically identifies which of the two selected languages is being spoken.
*   **Offline Ready:** Works without any internet once models are downloaded.

### 📄 Text Translation
*   **Instant Results:** Fast, lightweight text translator for quick lookups.
*   **Clipboard Support:** Copy/Paste and listen to translations instantly.

---

## 🏗️ Technical Architecture

OneVoice uses a modern **Service-Oriented Architecture** to manage persistent translation sessions independently of the UI.

- **Frontend:** Android (Java/Kotlin) with a Fragment-based UI.
- **Transport:** 
    - **WebRTC:** For internet-based calls (DataChannels + Signaling).
    - **Legacy Bluetooth:** Fallback for local P2P communication.
- **Signaling:** FastAPI-based Python server (Bhasha_Setu) deployed on **Render.com**.
- **Database:** Room Persistence for recent peer history and settings.

---

## 🧠 AI Magic On-Device

We use **ONNX Runtime** to run heavy neural networks directly on your smartphone—ensuring your data never leaves the device.

| Task | Model | Details |
| :--- | :--- | :--- |
| **Speech-to-Text** | **OpenAI Whisper** | Whisper-Small-244M (Quantized int8) |
| **Translation** | **Meta NLLB-200** | Distilled-600M (Quantized int8) |
| **Synthesis** | **Android TTS** | High-quality system-level voice output |
| **Language ID** | **ML Kit** | Google's lightweight language detector |

---

## 🛠️ Getting Started

### 1. Android App Setup
1.  Clone this repository: `git clone https://github.com/thekarampuri/OneVoice.git`
2.  Open the project in **Android Studio**.
3.  Build and install the APK on your device.
4.  **Note:** On first launch, the app will download ~1.2GB of neural models to provide offline functionality.

### 2. Signaling Server Deployment (Signaling/Bhasha_Setu)
OneVoice requires a signaling server to connect peers. We have a production-ready server in the `Bhasha_Setu/signaling-server` directory.

- **Fastest Way:** Deploy to **Render.com** as a Web Service.
- **Root Directory:** `Bhasha_Setu/signaling-server`
- **Link:** Use the provided `render.yaml` for one-click blueprint deployment.

---

## 🌍 Supported Locales

**High Quality (Whisper + NLLB):**
English, Hindi, Marathi, Tamil, Kannada, Assamese, Bengali, Gujarati, Kashmiri, Urdu, Japanese, Korean, French, Arabic, Spanish, German, Italian, and many more (100+ total).

---

## 🤝 Contribution & License

We welcome contributions! Feel free to open issues or submit pull requests to improve the translation accuracy or UI.

**License:** Distributed under the **Apache License 2.0**. See `LICENSE` for more information.

---
*Built with ❤️ for a world without language barriers.*
