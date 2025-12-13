# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep OkHttp (usually handled by the library, but keeping just in case if older versions)
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep Coroutines (usually handled by the library)
-keepnames class kotlinx.coroutines.** { *; }

# Data classes used in reflection/serialization might need specific rules.
# If crashes occur, add @Keep annotation to specific data classes or add specific rules here.

