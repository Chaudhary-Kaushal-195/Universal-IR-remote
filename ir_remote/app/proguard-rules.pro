# ==============================================================================
# Universal Hub - Production ProGuard / R8 Obfuscation & Security Rules
# ==============================================================================

# ------------------------------------------------------------------------------
# 1. Code Obfuscation & Hardening
# ------------------------------------------------------------------------------
# Flatten package hierarchy to conceal internal architecture from decompilers
-repackageclasses 'com.example.universalhub.internal'
-allowaccessmodification

# Optimization passes for tighter code size and performance
-optimizationpasses 5
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*

# Strip out debug and info logs in release builds to prevent credential/IR code leakage
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# ------------------------------------------------------------------------------
# 2. Android Core & Custom Views
# ------------------------------------------------------------------------------
# Keep Android Application components
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# Keep Custom Views used in XML layouts
-keep public class com.example.universalhub.ArcTemperatureWheelView {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

-keepclassmembers class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Keep ViewBinding and AndroidX UI components
-keep class androidx.appcompat.widget.** { *; }
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# ------------------------------------------------------------------------------
# 3. Model Classes & Serialization
# ------------------------------------------------------------------------------
# Keep data model classes so JSON serialization/deserialization fields are intact
-keep class com.example.universalhub.AcTimer { *; }
-keep class com.example.universalhub.CustomIrButton { *; }
-keep class com.example.universalhub.BackupProfileItem { *; }
-keep class com.example.universalhub.ProfileManager$DeviceCategory { *; }

# ------------------------------------------------------------------------------
# 4. Third-Party Libraries (MQTT Paho & USB Serial)
# ------------------------------------------------------------------------------
# Eclipse Paho MQTT
-keep class org.eclipse.paho.client.mqttv3.** { *; }
-dontwarn org.eclipse.paho.client.mqttv3.**
-keep interface org.eclipse.paho.client.mqttv3.** { *; }

# USB Serial for Android
-keep class com.hoho.android.usbserial.** { *; }
-dontwarn com.hoho.android.usbserial.**

# Keep native methods if any
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep Parcelable and Serializable implementations
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
