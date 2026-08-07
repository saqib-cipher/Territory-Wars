# Territory Wars - ProGuard / R8 rules.

# Retrofit / OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class com.bumptech.glide.** { *; }

# Room
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase

# Gson models (server DTOs live under com.territorywars.models.*)
-keep class com.territorywars.models.** { *; }
-keep class com.territorywars.network.dto.** { *; }

# Firebase Auth
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Google Play Billing
-dontwarn com.android.billingclient.**
-keep class com.android.billingclient.** { *; }

# Google Mobile Ads
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

# Gson
-keepclassmembers,enumbetter class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
keepattributes Signature, AnnotationDefault