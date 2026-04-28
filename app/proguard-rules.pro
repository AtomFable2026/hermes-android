# Proguard rules for AetherisAI

# Keep Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.aetheris.chat.**$$serializer { *; }
-keepclassmembers class com.aetheris.chat.** { *** Companion; }
-keepclasseswithmembers class com.aetheris.chat.** { kotlinx.serialization.KSerializer serializer(...); }

# Keep Retrofit interfaces
-keep,allowobfuscation interface * { @retrofit2.http.* <methods>; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Room
-keep class * extends androidx.room.RoomDatabase
-keepclassmembers class * { @androidx.room.* <methods>; }
