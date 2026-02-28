# Translate-Call (RTranslator)

OneVoice is a free, open-source, and completely offline real-time translation application for Android. It allows users to have simultaneous conversations with people who speak different languages, ensuring privacy and functionality without an internet connection.

## Features

- **Conversation Mode:** Connect two devices via Bluetooth and have a seamless real-time conversation.
    - **Offline Speech-to-Speech Translation:** Captures audio, translates it, and speaks it out on the other device.
    - **Bluetooth Headset Support:** Use Bluetooth headsets for a more natural conversation experience.
    - **Multi-Device Connection:** Connect multiple devices to translate conversations between more than two people.
    - **Peer Profile Display:** View the profile picture of the connected user for a more personal interaction.

- **WalkieTalkie Mode:** A single-device mode for quick interactions.
    - **Language Detection:** Automatically detects which of the two selected languages is being spoken.
    - **Turn-based Translation:** Perfect for quick questions or directions.

- **Text Translation Mode:** A standard text translator for quick text lookups.

- **Privacy Focused:** All processing happens locally on the device. No data is sent to any server.

## Tech Stack

- **Platform:** Android (Java)
- **Minimum SDK:** Android 7.0 (API level 24)
- **Target SDK:** Android 13 (API level 33)
- **UI Framework:** Android View System (XML layouts)
- **Database:** Room (SQLite)
- **Bluetooth:** Custom [BluetoothCommunicator](cci:2://file:///e:/Projects/Translate-Call/app/src/main/java/nie/translator/rtranslator/bluetooth/BluetoothCommunicator.java:239:0-1437:1) library for robust BLE connections.

### AI & Machine Learning
- **Translation:** Meta's **NLLB** (No Language Left Behind)
    - *Model:* NLLB-Distilled-600M (Quantized to int8 with KV cache)
- **Speech Recognition:** OpenAI's **Whisper**
    - *Model:* Whisper-Small-244M (Quantized to int8 with KV cache)
- **Inference Engine:** Microsoft **ONNX Runtime** (with extensions)
- **Language Identification:** Google ML Kit (for WalkieTalkie mode)
- **Tokenizer:** SentencePiece

## Installation Process

1.  **Download:** Get the latest APK from the [Releases](https://github.com/thekarampuri/Translate-Call/releases) page.
2.  **Install:** Install the APK on your Android device.
3.  **First Launch:** Open the app. It will automatically download the necessary AI models (approx. 1.2GB).
4.  **Permissions:** Grant the necessary permissions (Microphone, Bluetooth, Storage) when prompted.
5.  **Offline Use:** Once models are downloaded, the app works completely offline.

*(Note: If automatic download fails, you can manually sideload models following the [Sideloading Guide](https://github.com/thekarampuri/Translate-Call/blob/main/Sideloading.md))*

## Supported Languages

**High Quality (NLLB & Whisper):**
Arabic, Bulgarian, Catalan, Chinese, Croatian, Czech, Danish, Dutch, English, Finnish, French, Galician, German, Greek, Italian, Japanese, Korean, Macedonian, Polish, Portuguese, Romanian, Russian, Slovak, Spanish, Swedish, Tamil, Thai, Turkish, Ukrainian, Urdu, Vietnamese.

**Low Quality (Optional Enable):**
Afrikaans, Akan, Amharic, Assamese, Bambara, Bangla, Bashkir, Basque, Belarusian, Bosnian, Dzongkha, Esperanto, Estonian, Ewe, Faroese, Fijian, Georgian, Guarani, Gujarati, Hausa, Hebrew, Hindi, Hungarian, Irish, Javanese, Kannada, Kashmiri, Kazakh, Kikuyu, Kinyarwanda, Kyrgyz, Lao, Limburghish, Lingala, Lithuanian, Luxembourghish, Tagalog, Tibetan.
