# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# OkHttp — required for DoH forwarding
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
-keepclassmembers class * implements javax.net.ssl.SSLSocketFactory { *; }

# Hilt entry-point interfaces used via reflection at runtime
-keep @dagger.hilt.EntryPoint interface *

# Room query-result projection classes (not annotated with @Entity but
# instantiated by Room-generated code via constructor parameter names)
-keep class com.example.pardadns.data.db.LifetimeTotalsRow { *; }

# Strip unused JVM metadata to shrink further
-dontwarn kotlin.reflect.jvm.internal.**

# Strip all Log calls in release builds
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}


#-renamesourcefileattribute SourceFile