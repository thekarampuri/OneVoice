# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the Android SDK tools proguard-rules file.

# Keep WebRTC classes
-keep class org.webrtc.** { *; }

# Keep OkHttp and WebSocket
-keepattributes Signature
-keepattributes *Annotation*
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Keep GSON models
-keep class com.example.voicetranslate.data.model.** { *; }

# Keep DataStore
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
  <fields>;
}
