# ONEVOICE CODEBASE ANALYSIS REPORT

## 1. PROJECT OVERVIEW

**Purpose:**
OneVoice is a real-time, bi-directional voice and text translation application for Android. It enables users to communicate across language barriers by providing near-instant translation of spoken and written words. The application supports various modes, including remote remote calls (WebRTC), a "Walkie-Talkie" mode (Legacy Bluetooth), and standard text translation.

**Core Functionality:**
- **Voice-to-Voice Translation:** Real-time speech recognition (STT) followed by translation and synthesis (TTS).
- **WebRTC Remote Calling:** Establishing persistent translation sessions between devices over the internet using a signaling server.
- **On-Device Neural Networks:** Uses ONNX Runtime for neural translation (NLLB-200) and speech recognition.
- **Bi-directional Communication:** Both participants can speak in their respective languages and hear/read the translated output.
- **Language Management:** Support for dynamic downloading and selection of language models.

**Tech Stack:**
- **Languages:** Java and Kotlin.
- **Libraries & Frameworks:**
    - **WebRTC (Google):** For peer-to-peer data and communication transport.
    - **ONNX Runtime:** For executing neural network models (rtranslator-neural-networks).
    - **Room Persistence:** For local storage of recent peer data and history.
    - **Dagger/Hilt (Optional):** Some dependency management patterns are visible.
    - **Android Jetpack:** ViewModel, LiveData, and Lifecycle-aware components.
- **APIs:**
    - **Android SpeechRecognizer/TTS:** For local voice processing.
    - **Custom Signaling Server:** For WebRTC handshaking over HTTP.

---

## 2. ARCHITECTURE

**Pattern:**
The app follows a **Service-Oriented Architecture** with elements of **MVVM**. UI components (Fragments) bind to specialized Android Services that manage the persistent communication logic.

**Key Components & Connections:**
- **VoiceTranslationActivity:** The main entry point for calling/translation sessions. It coordinates fragment transitions and manages the UI lifecycle.
- **Global.java:** An `Application` subclass acting as a singleton for critical objects like the `Translator`, `Recognizer`, and shared preferences.
- **WebRtcVoiceTranslationService:** A foreground service that manages the WebRTC call state. It acts as the "Controller" between the UI and the transport layer.
- **WebRtcClient:** Wraps the low-level WebRTC `PeerConnection` logic, handling ICE candidates, SDP offers/answers, and DataChannel management.
- **SignalingClient:** Facilitates the initial "pairing" by communicating with a remote server (currently via HTTP POST) to exchange WebRTC session descriptions.
- **ConversationMainFragment:** The primary UI for active calls, displaying messages from both the local user and the peer in real-time.
- **AppDatabase / MyDao:** Room-based persistence for storing `RecentPeerEntity` (history of connected peers).

---

## 3. WHAT IS FULLY IMPLEMENTED & WORKING

1. **WebRTC remote calls (with recent fixes):**
    - Bi-directional text and translation transmission over WebRTC DataChannels.
    - Call establishment via the SignalingClient.
    - Reliable STT transition (Fixed microphone conflict between WebRTC and Android STT).
    - Functional "End Call" button integration.
2. **Text Translation Mode:**
    - Manual text entry with instant translation and TTS feedback.
    - Language swapping and clipboard support.
3. **Neural Network Integration:**
    - On-device translation using the NLLB model for privacy and speed.
    - Dynamic language detection.
4. **Core UI/UX:**
    - Dark-themed design with smooth animations.
    - Language selection menus with support for over 100+ locales.

---

## 4. WHAT IS PARTIALLY IMPLEMENTED

1. **Walkie-Talkie Mode:**
    - The code exists (`WalkieTalkieService`), but it is heavily architected around the legacy `bluetooth` package. It appears to be separate from the newer WebRTC-based remote call logic.
2. **Recent Peer History:**
    - Room entities (`RecentPeerEntity`) and DAOs exist, but the integration in the UI for re-connecting to past WebRTC peers is less prominent than the Bluetooth counterpart.
3. **Error Feedback:**
    - While STT and Translation errors are tracked, signaling server timeouts or failures are currently logged but don't always provide clear user-facing retry dialogues.

---

## 5. WHAT IS NOT YET IMPLEMENTED

1. **Video Calling:**
    - The WebRTC implementation is currently optimized for Voice/Text. Although WebRTC supports Video, there are no UI components or surface renderers active for video streaming.
2. **Public Discovery/Signaling:**
    - The app currently requires a static/hardcoded IP for the signaling server. There is no "Global Matchmaking" or "Username-based Discovery" platform-wide.
3. **User Profiles:**
    - Beyond a "Name" and "Profile Photo" stored locally, there is no cloud-based account management or friend list.

---

## 6. KNOWN ISSUES & TECHNICAL DEBT

1. **Hardcoded Configurations:**
    - **Signaling IPs:** Multiple files (`CallConfig.java`, `SignalingClient.java`) contain hardcoded IP addresses/URLs for the signaling server.
    - **Language Filtering:** The "Most used languages" list is hardcoded in `Global.java`.
2. **Security Concerns:**
    - Signaling communication currently uses plain HTTP. SDP and ICE candidate exchange should ideally move to HTTPS/WSS to prevent MITM attacks.
3. **Threading Logic:**
    - The `pendingTranslationQueue` in `WebRtcClient` was recently made thread-safe, but the legacy Bluetooth components still use complex `IntentService`/`Handler` patterns that could lead to `ConcurrentModificationException` if revived.

---

## 7. RECOMMENDED NEXT STEPS

1. **PRIORITY 1: Dynamic Signaling:**
    - Implement a mechanism (e.g., QR code, Invite Link, or a central lookup server) to replace hardcoded IP addresses.
2. **PRIORITY 2: UI/UX Refinement for Calls:**
    - Add a "Connecting/Dialing" state UI to inform users when WebRTC is negotiating (currently it jumps from pairing to the chat UI abruptly).
3. **PRIORITY 3: Hybrid Connectivity:**
    - Unify the "Walkie-Talkie" and "Conversation" modes under the WebRTC architecture so that Bluetooth-only code can be deprecated or used only as a fallback.
4. **PRIORITY 4: Production-Ready Signaling:**
    - Move to a secure (HTTPS) signaling environment and implement session timeouts to clean up idle resources on the server.

---

## 8. DIRECTORY STRUCTURE

The project is organized into several key packages reflecting the core pillars of the application:

```text
app/src/main/java/nie/translator/rtranslator/
│
├── access/              # User onboarding, permissions, and data download
├── bluetooth/           # Legacy Bluetooth P2P communication logic
├── database/            # Room Database (DAOs and Entities for peer history)
├── settings/            # Application settings and user preferences
├── tools/               # Cross-cutting utilities (TTS, GUI, Object Serialization)
│   └── gui/             # Custom UI components, animations, and adapters
├── voice_translation/   # Base classes for translation services and UI
│   ├── neural_networks/ # ONNX Runtime integration (Translator, Recognizer)
│   ├── _conversation/   # Conversation mode logic (Legacy Bluetooth)
│   ├── _text_translation/ # Standalone text translation fragment
│   └── _walkie_talkie/  # Walkie-Talkie mode logic (Legacy Bluetooth)
└── webrtc/              # Modern WebRTC calling & Signaling client [NEW]
    ├── model/           # Kotlin data classes for Signaling messages
    └── WebRtcClient     # Core PeerConnection & DataChannel wrapper
```

**Resource Layer:**
- `app/src/main/res/layout/`: XML UI definitions for activities and fragments.
- `app/src/main/res/values/`: Strings (internationalization), colors, and theme attributes.
- `app/src/main/assets/`: Location for bundled neural network models (optional/cached).
