# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ONVIF library rules
#noinspection ExpensiveKeepRuleInspection
-keep class org.apache.http.** { *; }
-dontwarn org.apache.http.**
# Keep line numbers so release crash stack traces stay readable
-keepattributes SourceFile,LineNumberTable

# libVLC: native code calls back into Java by name, and the AAR ships no rules
#noinspection ExpensiveKeepRuleInspection
-keep class org.videolan.libvlc.** { *; }
-keep class org.videolan.medialibrary.** { *; }

# JSch: algorithms are loaded by class name from its config
#noinspection ExpensiveKeepRuleInspection
-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**
-dontwarn org.bouncycastle.**
-dontwarn org.newsclub.net.unix.**
-dontwarn com.sun.jna.**
-dontwarn org.slf4j.**
-dontwarn org.apache.logging.log4j.**

# Gson: models saved to and loaded from settings JSON must keep their field names
-keep class com.odiousapps.gardencam.StreamDataModel { <fields>; <init>(...); }
-keep class com.odiousapps.gardencam.GroupDataModel { <fields>; <init>(...); }
-keep class com.odiousapps.gardencam.SourceDataModel { <fields>; <init>(...); }
-keep class com.odiousapps.gardencam.onvif.DeviceCapabilities { <fields>; <init>(...); }
-keep class com.odiousapps.gardencam.onvif.PTZCapabilities { <fields>; <init>(...); }
-keep class com.odiousapps.gardencam.onvif.EventCapabilities { <fields>; <init>(...); }
-keep class com.odiousapps.gardencam.onvif.PTZPreset { <fields>; <init>(...); }
