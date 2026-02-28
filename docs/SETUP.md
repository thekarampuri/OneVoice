# OneVoice Setup Guide

## Prerequisites

### Required Software
- **Android Studio** - Arctic Fox (2020.3.1) or later
- **JDK** - Java Development Kit 11 or later
- **Android SDK** - API Level 33 (Android 13)
- **Git** - For version control

### Hardware Requirements
- **Development Machine:** 8GB+ RAM recommended
- **Test Device:** Android 7.0+ with 6GB+ RAM

---

## Installation Steps

### 1. Clone the Repository

```bash
git clone https://github.com/thekarampuri/Translate-Call.git
cd Translate-Call
```

### 2. Open in Android Studio

1. Launch Android Studio
2. Select **File → Open**
3. Navigate to the cloned `Translate-Call` directory
4. Click **OK**

Android Studio will automatically:
- Download Gradle dependencies
- Sync the project
- Index files

### 3. Configure SDK

Ensure you have the required SDK components:

1. Go to **Tools → SDK Manager**
2. Install:
   - Android SDK Platform 33
   - Android SDK Build-Tools 33.0.0+
   - NDK (for C++ native code)
   - CMake (for native builds)

### 4. Build the Project

#### Using Android Studio:
1. Select **Build → Make Project** (Ctrl+F9)
2. Wait for build to complete

#### Using Command Line:
```bash
# Windows
gradlew.bat assembleDebug

# Linux/Mac
./gradlew assembleDebug
```

### 5. Run on Device/Emulator

#### Physical Device:
1. Enable **Developer Options** on your Android device
2. Enable **USB Debugging**
3. Connect device via USB
4. Click **Run** (Shift+F10) in Android Studio

#### Emulator:
1. Go to **Tools → AVD Manager**
2. Create a new Virtual Device (Pixel 5 recommended)
3. Select System Image: API 33 (Android 13)
4. Allocate at least 6GB RAM
5. Click **Run**

---

## First Launch

When you first run the app:

1. **Grant Permissions:**
   - Microphone (required for speech recognition)
   - Bluetooth (required for conversation mode)
   - Location (required for Bluetooth LE)
   - Storage (required for AI model downloads)

2. **Download AI Models:**
   - The app will download Whisper and NLLB models
   - This requires ~2-3GB of storage
   - Requires internet connection (one-time only)
   - Keep the app open during download

3. **Accept Privacy Policy:**
   - Read and accept the privacy policy
   - Confirm you are 14+ years old

---

## Development Setup

### Project Structure
```
Translate-Call/
├── app/src/main/
│   ├── java/              # Java source code
│   ├── cpp/               # Native C++ code
│   ├── res/               # Resources (layouts, strings, etc.)
│   └── AndroidManifest.xml
├── docs/                  # Documentation
├── privacy/               # Privacy policies
└── images/                # Project images
```

### Key Files
- `app/build.gradle` - App-level build configuration
- `build.gradle` - Project-level build configuration
- `settings.gradle` - Project settings
- `local.properties` - Local SDK path (auto-generated)

### Building Release APK

```bash
# Generate signed release APK
./gradlew assembleRelease

# Output location:
# app/build/outputs/apk/release/app-release.apk
```

**Note:** You'll need to configure signing keys for release builds.

---

## Troubleshooting

### Build Fails

**Issue:** Gradle sync fails
```
Solution: File → Invalidate Caches → Invalidate and Restart
```

**Issue:** NDK not found
```
Solution: Install NDK via SDK Manager (Tools → SDK Manager → SDK Tools → NDK)
```

**Issue:** Out of memory
```
Solution: Increase Gradle memory in gradle.properties:
org.gradle.jvmargs=-Xmx4096m
```

### Runtime Issues

**Issue:** App crashes on startup
```
Check:
- Device has 6GB+ RAM
- All permissions granted
- Models downloaded successfully
```

**Issue:** Models won't download
```
Check:
- Internet connection active
- Sufficient storage (3GB+)
- App has storage permission
```

**Issue:** Bluetooth won't connect
```
Check:
- Bluetooth enabled on both devices
- Location permission granted
- Both devices have the app installed
```

---

## Testing

### Run Unit Tests
```bash
./gradlew test
```

### Run Instrumented Tests
```bash
./gradlew connectedAndroidTest
```

---

## Contributing

See [CONTRIBUTING.md](../CONTRIBUTING.md) for contribution guidelines.

---

## Support

- **Issues:** https://github.com/thekarampuri/Translate-Call/issues
- **Discussions:** https://github.com/thekarampuri/Translate-Call/discussions

---

## License

This project is licensed under the Apache License 2.0 - see [LICENSE](../LICENSE) for details.
