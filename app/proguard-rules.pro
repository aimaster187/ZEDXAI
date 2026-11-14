# ZEDX AI ProGuard / R8 Keep Rules

-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*

# Firebase Firestore @IgnoreExtraProperties data models
-keepclassmembers class com.titan.zedxai.firebase.** { *; }
-keep class com.titan.zedxai.firebase.** { *; }

# Moshi / OkHttp / Retrofit
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-dontwarn okhttp3.**
-dontwarn okio.**

# Google Credential Manager & Identity
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
