# ProGuard / R8 rules for Al-Makhzanji Dictionary

# Keep Kotlin Metadata & Coroutines
-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# Retrofit
-keepattributes AnnotationDefault
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.Platform
-dontwarn retrofit2.Platform$Java8
-keepattributes Exceptions
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Moshi + Kotlin codegen
-keepclassmembers class * {
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}
-keep @com.squareup.moshi.JsonClass class * { *; }
-keep class com.squareup.moshi.** { *; }

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.domain.model.**$$serializer { *; }
-keepclassmembers class com.example.domain.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.domain.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.ui.navigation.**$$serializer { *; }
-keepclassmembers class com.example.ui.navigation.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.ui.navigation.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.data.repository.**$$serializer { *; }
-keepclassmembers class com.example.data.repository.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.data.repository.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# App DTOs & Audio/TTS
-keep class com.example.data.remote.*Dto { *; }
-keep class com.example.data.remote.*JsonAdapter { *; }
-keep class com.example.core.AudioPlayerManager { *; }
-keep class * extends android.speech.tts.UtteranceProgressListener { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-dontwarn androidx.room.**

# ML Kit Translate & Text Recognition
-keep class com.google.mlkit.nl.translate.** { *; }
-keepclassmembers class com.google.mlkit.nl.translate.** { *; }
-keep class com.google.mlkit.vision.text.** { *; }
-keepclassmembers class com.google.mlkit.vision.text.** { *; }
-keep class com.google.mlkit.vision.common.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text_common.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**

# PDFBox-Android (Section 17.1)
-keep class com.tom_roush.pdfbox.pdmodel.** { *; }
-keep class com.tom_roush.pdfbox.text.** { *; }
-keep class com.tom_roush.pdfbox.android.PDFBoxResourceLoader { *; }
-keep class com.tom_roush.pdfbox.io.MemoryUsageSetting { *; }
-keep class com.tom_roush.fontbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn com.tom_roush.fontbox.**
-dontwarn org.bouncycastle.**
