# ML Kit
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Gson
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.reflect.TypeToken
-keep class sun.misc.Unsafe { *; }

# Prevent obfuscation of BarcodeResult fields (used in JSON serialization)
-keep class com.qrscanner.app.BarcodeResult { *; }
-keepclassmembers class com.qrscanner.app.BarcodeResult { <fields>; }

# Prevent obfuscation of UI models used in bundles/serialization
-keep class com.qrscanner.app.QrStyle { *; }
-keep class com.qrscanner.app.LogoItem { *; }
-keep class com.qrscanner.app.LogoItem$* { *; }

# Strip all debug and verbose logs in release builds
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
