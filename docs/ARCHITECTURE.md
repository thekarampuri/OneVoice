# OneVoice Architecture

## Overview

OneVoice is an offline real-time translation app for Android that uses AI models for speech recognition and translation.

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    User Interface                        │
│  (Activities, Fragments, Layouts)                       │
└─────────────────┬───────────────────────────────────────┘
                  │
┌─────────────────▼───────────────────────────────────────┐
│                 Application Layer                        │
│  • GeneralActivity - Main UI controller                 │
│  • LoadingActivity - Initialization & model download    │
│  • Global - Application state management                │
└─────────────────┬───────────────────────────────────────┘
                  │
┌─────────────────▼───────────────────────────────────────┐
│                  Service Layer                           │
│  • GeneralService - Background processing               │
│  • Voice Translation Service - Real-time translation    │
└─────────────────┬───────────────────────────────────────┘
                  │
┌─────────────────▼───────────────────────────────────────┐
│                  Core Components                         │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │  Bluetooth   │  │    Voice     │  │   Database   │  │
│  │   Manager    │  │  Translation │  │   Manager    │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  │
└─────────────────┬───────────────────────────────────────┘
                  │
┌─────────────────▼───────────────────────────────────────┐
│                   AI Models (C++)                        │
│  • Whisper (Speech Recognition)                         │
│  • NLLB (Neural Machine Translation)                    │
└─────────────────────────────────────────────────────────┘
```

## Key Components

### 1. User Interface Layer

#### Activities
- **`GeneralActivity`** - Main application activity, handles UI interactions
- **`LoadingActivity`** - Handles app initialization and model downloads

#### Fragments
- **Conversation Fragment** - Multi-device conversation mode UI
- **WalkieTalkie Fragment** - Single-device translation mode UI
- **Text Translation Fragment** - Text-only translation UI
- **Pairing Fragment** - Bluetooth device discovery and pairing

### 2. Application Layer

#### `Global.java`
- Singleton class managing application-wide state
- Handles configuration and settings
- Manages AI model lifecycle
- Coordinates between components

### 3. Service Layer

#### `GeneralService`
- Background service for continuous operation
- Handles foreground notifications
- Manages app lifecycle when in background

#### Voice Translation Service
- Real-time audio processing
- Speech-to-text conversion
- Translation pipeline
- Text-to-speech output

### 4. Core Components

#### Bluetooth Module (`bluetooth/`)
- **Bluetooth Manager** - Device discovery and connection
- **BLE (Bluetooth Low Energy)** - Low-power communication
- **Peer Management** - Connected device tracking
- **Data Transfer** - Audio/text data exchange

#### Voice Translation Module (`voice_translation/`)
- **Audio Capture** - Microphone input handling
- **Voice Activity Detection (VAD)** - Speech detection
- **Speech Recognition** - Whisper model integration
- **Translation Engine** - NLLB model integration
- **Text-to-Speech (TTS)** - Android TTS integration

#### Database Module (`database/`)
- **Conversation History** - Message storage
- **Device History** - Recent connections
- **Settings Storage** - User preferences

#### Tools Module (`tools/`)
- **Audio Processing** - Audio format conversion, filtering
- **Network Utils** - Download management
- **File Utils** - Model file management
- **Permission Manager** - Runtime permission handling

### 5. Native Layer (C++)

Located in `app/src/main/cpp/`

#### Whisper Integration
- Speech recognition model
- Audio preprocessing
- Inference engine

#### NLLB Integration
- Neural machine translation
- Language detection
- Translation inference

---

## Data Flow

### Conversation Mode

```
User A speaks
    ↓
Microphone captures audio
    ↓
Voice Activity Detection
    ↓
Whisper (Speech-to-Text)
    ↓
NLLB (Translation)
    ↓
Bluetooth transmission to User B
    ↓
User B's TTS speaks translation
```

### WalkieTalkie Mode

```
User speaks in Language A
    ↓
Microphone captures audio
    ↓
Whisper (Speech-to-Text) → Language A text
    ↓
NLLB (Translation) → Language B text
    ↓
TTS speaks Language B
    ↓
(Pause)
    ↓
User speaks in Language B
    ↓
[Repeat process in reverse]
```

---

## Threading Model

### Main Thread
- UI updates
- User interactions
- Activity lifecycle

### Background Threads
- Audio processing
- AI model inference
- Bluetooth communication
- File I/O

### Service Thread
- Foreground service operations
- Notification management

---

## Storage

### Internal Storage
```
/data/data/nie.translator.rtranslator/
├── files/
│   ├── models/
│   │   ├── whisper/          # Speech recognition models
│   │   └── nllb/             # Translation models
│   └── cache/
└── databases/
    └── conversation.db        # SQLite database
```

### External Storage (Download)
- Temporary storage during model download
- Transferred to internal storage after download

---

## Permissions

### Required Permissions
- `RECORD_AUDIO` - Microphone access for speech recognition
- `BLUETOOTH` - Bluetooth connectivity
- `BLUETOOTH_ADMIN` - Bluetooth device management
- `BLUETOOTH_CONNECT` - Connect to Bluetooth devices (Android 12+)
- `BLUETOOTH_SCAN` - Scan for Bluetooth devices (Android 12+)
- `ACCESS_FINE_LOCATION` - Required for Bluetooth LE
- `INTERNET` - Model downloads only
- `WRITE_EXTERNAL_STORAGE` - Model downloads (Android < 10)
- `FOREGROUND_SERVICE` - Background operation
- `POST_NOTIFICATIONS` - Notifications (Android 13+)

---

## AI Models

### Whisper (OpenAI)
- **Purpose:** Speech recognition
- **Size:** ~1.5GB
- **Languages:** 90+ languages
- **Inference:** CPU/GPU (via ONNX Runtime)

### NLLB (Meta)
- **Purpose:** Neural machine translation
- **Size:** ~600MB
- **Languages:** 200+ languages
- **Inference:** CPU (via ONNX Runtime)

### Model Loading
1. Download from GitHub releases (first launch)
2. Verify integrity (checksum)
3. Extract to internal storage
4. Load into memory on demand
5. Keep in memory during active use

---

## Performance Considerations

### Memory Management
- Models loaded on-demand
- Aggressive memory cleanup
- Minimum 6GB RAM required

### Battery Optimization
- Efficient audio processing
- Bluetooth LE for low power
- Background service optimization

### Network Usage
- One-time model download
- No ongoing network usage
- Fully offline operation

---

## Security & Privacy

### Data Privacy
- **No data collection** - All processing is local
- **No analytics** - No tracking or telemetry
- **No cloud services** - Fully offline

### Data Storage
- Conversation history stored locally only
- Can be cleared by user
- No backup to cloud

---

## Future Architecture Improvements

### Potential Enhancements
1. **Modular AI Models** - Downloadable language packs
2. **Plugin System** - Third-party translation engines
3. **Cloud Sync** - Optional conversation backup
4. **Multi-modal** - Image translation support
5. **Improved Caching** - Faster model loading

---

## Technology Stack

### Languages
- **Java** - Application logic
- **C++** - AI model integration
- **XML** - UI layouts and resources

### Frameworks & Libraries
- **Android SDK** - Core platform
- **ONNX Runtime** - AI model inference
- **AndroidX** - Modern Android components
- **Gradle** - Build system

### AI Models
- **Whisper** - OpenAI speech recognition
- **NLLB** - Meta neural translation

---

## References

- [Android Architecture Components](https://developer.android.com/topic/architecture)
- [Whisper Model](https://openai.com/research/whisper)
- [NLLB Model](https://ai.meta.com/research/no-language-left-behind/)
- [ONNX Runtime](https://onnxruntime.ai/)
