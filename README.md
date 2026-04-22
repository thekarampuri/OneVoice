# 🎙️ OneVoice: Real-Time Universal Translator

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com/)
[![WebRTC](https://img.shields.io/badge/Transport-WebRTC-orange.svg)](https://webrtc.org/)

**OneVoice** is a powerful, open-source Android application designed to bridge language barriers in real-time. It provides seamless Speech-to-Speech translation using state-of-the-art on-device AI models, ensuring fully offline operation and maximum privacy.

---

## 🚀 Key Translation Modes

### 📱 Remote Conversation (WebRTC)
*   **Global Reach:** Talk to anyone over the internet using a secure WebRTC signaling server.
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

OneVoice uses a **Service-Oriented Architecture** to manage persistent translation sessions independently of the UI.

### High-Level Flow
1.  **Audio Capture:** Microphone captures user speech.
2.  **VAD:** Voice Activity Detection identifies speech intervals.
3.  **STT:** OpenAI Whisper converts audio to text (On-Device).
4.  **NMT:** Meta NLLB translates text to the target language (On-Device).
5.  **Output:** Android TTS synthesizes the translation back to speech.
6.  **P2P:** In conversation mode, data is exchanged via WebRTC (DataChannels).

### Core Components
- **Frontend:** Android (Java/Kotlin) utilizing a Fragment-based UI architecture.
- **Background Services:** `GeneralService` handles foreground notifications and app persistence.
- **Native Layer (C++):** Integrates Whisper and NLLB using **ONNX Runtime** for high-performance inference.
- **Storage:** SQLite (Room) for message history and local settings.

---

## 🧠 On-Device AI Models

We use quantized AI models to provide high accuracy with efficient local resource usage.

| Task | Model | Details |
| :--- | :--- | :--- |
| **Speech-to-Text** | **OpenAI Whisper** | Whisper-Small (Quantized int8) |
| **Translation** | **Meta NLLB-200** | Distilled-600M (Quantized int8) |
| **Synthesis** | **Android TTS** | System-level voice output |
| **Language ID** | **ML Kit** | Lightweight language detector |

---

## 🛠️ Installation & Setup

### Prerequisites
- **Android Studio:** Arctic Fox (2020.3.1) or later.
- **Java:** JDK 11 or later.
- **Hardware:** Android device with 6GB+ RAM recommended for optimal AI performance.

### Steps
1.  **Clone the Repository:**
    ```bash
    git clone https://github.com/thekarampuri/OneVoice.git
    ```
2.  **Open in Android Studio:** Let the IDE sync Gradle and download dependencies.
3.  **SDK Configuration:** Ensure Android SDK API Level 33 and NDK are installed via the SDK Manager.
4.  **Build & Run:** Select your device and click **Run**.
5.  **First Launch:** The app will download ~1.2GB of AI models. Ensure you have a stable internet connection for this one-time step.

---

## 📂 Storage & Privacy

OneVoice is built on a **privacy-first** philosophy:
- **Offline Processing:** No audio data or text is sent to the cloud.
- **Local History:** Conversation logs are stored strictly on your device.
- **No Analytics:** We do not track your usage or collect telemetry.

### Internal Paths
- `/files/models/`: AI model storage.
- `/databases/`: Local SQLite encrypted storage.

---

## 🔧 Troubleshooting

- **Models won't download:** Verify internet connection and ensure 3GB+ free storage.
- **Slow Translation:** Ensure your device isn't in Power Saving mode; AI inference is CPU-intensive.
- **Bluetooth/WebRTC Issues:** Ensure Location permission is granted (required for BLE/P2P discovery on Android).

---

## 🤝 Contribution & License

We welcome contributions to improve translation accuracy and UI!

**License:** Distributed under the **Apache License 2.0**.
