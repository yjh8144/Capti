# Capti ProGuard Rules

# Keep sherpa-onnx JNI
-keep class com.k2fsa.sherpa.onnx.** { *; }

# OkHttp
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Compose
-dontwarn androidx.compose.**
