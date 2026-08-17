-keepattributes *Annotation*
-keep class com.quicktap.pos.data.entity.** { *; }

# ---- QuickTap hardening -----------------------------------------------------
# Room entities are read reflectively by the generated DAOs.
-keep class com.quicktap.pos.data.entity.** { *; }

# Firebase Remote Config is reached through reflection in RemoteEndpoint.
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.tasks.** { *; }
-dontwarn com.google.firebase.**

# The licence banner is inflated from XML by name.
-keep class com.quicktap.pos.ui.license.LicenseBanner {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Strip log calls from the release build so nothing leaks in logcat.
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
}

# Make reverse engineering harder.
-repackageclasses ''
-allowaccessmodification
-optimizationpasses 5
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable
