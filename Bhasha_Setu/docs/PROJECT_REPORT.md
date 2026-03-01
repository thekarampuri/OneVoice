# Bhasha Setu - Project Report

**Date:** 2026-01-27
**Status:** Active Development (Migration to WebRTC Phase)

## 1. Executive Summary
**Bhasha Setu** (VoiceTranslate) is a voice communication platform designed to bridge language barriers across India. The project aims to facilitate real-time, peer-to-peer (P2P) voice calls with on-the-fly speech-to-speech (STS) translation. 

Currently, the project is in a transition phase, moving from a legacy WebSocket-based audio relay system to a robust **WebRTC** architecture for lower latency and better stability. The Android client and Signaling Server have been successfully updated to support P2P calls. The Translation Backend is fully functional as a standalone service but has yet to be integrated into the real-time WebRTC media pipeline.

## 2. Architecture Overview
The system consists of three main components:

1.  **Android Client**: The user interface for making calls. Handles audio capture, playback, and P2P connection management.
2.  **Signaling Server**: A lightweight WebSocket server that helps peers find each other and exchange connection details (SDP/ICE) to establish a WebRTC connection.
3.  **STS-Translation Backend**: A heavy-duty ML inference server hosting Speech-to-Text (STT), Machine Translation (MT), and Text-to-Speech (TTS) models.

### High-Level Data Flow (Current WebRTC Implementation)
```mermaid
sequenceDiagram
    participant UserA as Android Client A
    participant Server as Signaling Server
    participant UserB as Android Client B

    Note over UserA, UserB: Signaling Phase (WebSocket)
    UserA->>Server: Join Room (Call ID)
    UserB->>Server: Join Room (Call ID)
    Server-->>UserA: Match Found
    Server-->>UserB: Match Found
    
    UserA->>Server: Offer (SDP)
    Server->>UserB: Offer (SDP)
    UserB->>Server: Answer (SDP)
    Server->>UserA: Answer (SDP)
    
    UserA->>Server: ICE Candidates
    Server->>UserB: ICE Candidates
    UserB->>Server: ICE Candidates
    Server->>UserA: ICE Candidates

    Note over UserA, UserB: Media Phase (WebRTC P2P)
    UserA<-->>UserB: Direct Audio Stream (SRTP)
    Note right of UserB: No server in the middle for audio
```

> **Note:** The translation layer is currently decoupled. The future architecture will likely involve an MCU (Multipoint Control Unit) or a media intercepter to process audio streams for translation.

## 3. Technology Stack

### A. Android Client
*   **Language:** Kotlin
*   **UI Framework:** Jetpack Compose & XML (Hybrid)
*   **Architecture:** MVVM (Model-View-ViewModel)
*   **Core Libraries:**
    *   `io.github.webrtc-sdk:android`: Google's WebRTC implementation.
    *   `org.jetbrains.kotlinx:kotlinx-coroutines`: For asynchronous tasks.
    *   `com.squareup.okhttp3`: For WebSocket signaling connection.
    *   `androidx.lifecycle`: Lifecycle-aware components.

### B. Signaling Server
*   **Language:** Python
*   **Framework:** FastAPI
*   **Server:** Uvicorn (ASGI)
*   **Protocol:** WebSockets (JSON payloads for Offer/Answer/ICE)
*   **Deployment:** Docker-ready (runs on port 8001 by default)

### C. STS-Translation Backend (ML Engine)
*   **Language:** Python
*   **Framework:** FastAPI
*   **ML Libraries:** PyTorch, Transformers (Hugging Face), Librosa, SoundFile, FFmpeg.
*   **Models:**
    *   **ASR/STT (Speech-to-Text):**
        *   `ai4bharat/indic-conformer-600m-multilingual`: State-of-the-art model for Indian languages.
        *   `openai/whisper-tiny`: Efficient English transcription.
    *   **MT (Machine Translation):**
        *   `facebook/nllb-200-distilled-600M` (SafeTensors): No Language Left Behind model, fine-tuned for high-accuracy translation.
    *   **TTS (Text-to-Speech):**
        *   `Indic-TTS` (IIT Madras): FastPitch + HiFiGAN based high-quality synthesis for Indian languages.

## 4. Features & Capabilities

### Current Functional Features
*   **P2P WebRTC Calls:** Stable audio calls between two Android devices.
*   **NAT Traversal:** Integration with STUN (Google) and TURN (Metered.ca) servers to connect devices across different networks/firewalls.
*   **Room Logic:** Simple "Call ID" system allowing any two users to join a shared room.
*   **Audio Controls:** Mute, Unmute, Speakerphone toggle.
*   **Connection Resilience:** Connection state monitoring, auto-reconnection logic, and 30-second connection timeout handling.
*   **ML API (Standalone):** The backend can accept audio files and return translated text/audio.

### Supported Languages (Target)
The system is built to support 10+ languages:
1.  English (`en`)
2.  Hindi (`hi`)
3.  Marathi (`mr`)
4.  Bengali (`bn`)
5.  Gujarati (`gu`)
6.  Kannada (`kn`)
7.  Malayalam (`ml`)
8.  Punjabi (`pa`)
9.  Tamil (`ta`)
10. Telugu (`te`)

## 5. Detailed Component Analysis

### Android Implementation (`com.example.voicetranslate`)
*   **`WebRtcClient.kt`**: The core class. Wraps `PeerConnectionFactory`. It creates the `PeerConnection`, manages local/remote `AudioTrack`, and handles the SDP negotiation state machine.
*   **`SignalingClient.kt`**: Manages the `WebSocket` connection. It serializes signaling messages (Offer, Answer, ICE) using GSON and handles network events.
*   **`CallRepository.kt`**: The mediator. It synchronizes the `SignalingClient` and `WebRtcClient`. For example, it waits for the `onPeerJoined` signal before initiating the WebRTC Offer.

### STS Backend Implementation (`STS-Translation`)
*   **`app.py`**: A FastAPI service exposing:
    *   `/transcribe`: Accepts audio file -> Returns STT text + Translated Text.
    *   `/tts`: Accepts text + language -> Returns synthesized audio (Base64 WAV).
*   **`tts_utils.py`**: A wrapper around `Indic-TTS` to load models dynamically on demand to save VRAM.
*   **Model Optimization**: Uses `cuda` if available for acceleration; falls back to CPU.

## 6. Open Source Licenses & Credits
The project heavily relies on open-source ecosystems:
*   **WebRTC:** BSD-style license (Google).
*   **AI4Bharat (Indic Conformer):** MIT/Apache 2.0.
*   **NLLB (Meta AI):** MIT License.
*   **Indic-TTS (IIT Madras):** MIT License.

## 7. Next Steps / Roadmap
1.  **Phase 1 (Complete):** WebRTC infrastructure set up. P2P Audio working.
2.  **Phase 2 (Immediate):** Integrate Translation.
    *   *Challenge:* WebRTC is P2P. To translate, audio must be intercepted.
    *   *Solution:* Implement a "man-in-the-middle" selective forwarding unit (SFU) or have the receiving client send audio chunks to the STS-API for translation and play the result on top of (or instead of) the original audio.
3.  **Phase 3:** UI Polish. Improved call screen with visual indicators for "Translating...".

## 8. Conclusion
Bhasha Setu allows for high-quality voice communication. The infrastructure is modern (WebRTC) and the ML stack is cutting-edge for Indian languages (AI4Bharat/NLLB). The codebase is clean, modular, and ready for the next phase of translation integration.
