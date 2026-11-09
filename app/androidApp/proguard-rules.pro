# Preserve line numbers and attributes for stacktraces and reflection
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod

# Disable aggressive bytecode optimizations that strip synthetic ABI bridges and alter method signatures
-dontoptimize

# Kotlin Runtime & Reflection for dynamic plugins
-keep class kotlin.** { *; }
-keepclassmembers class kotlin.** { *; }
-keep class kotlin.jvm.internal.** { *; }
-keepclassmembers class kotlin.jvm.internal.** { *; }

# WhiteRTC Core ABI & dynamic plugin loading SPI
-keep class com.whitertc.core.** {
    <init>(...);
    <fields>;
    <methods>;
}
-keepclassmembers class com.whitertc.core.** {
    <init>(...);
    <fields>;
    <methods>;
}
-keep interface com.whitertc.core.provider.RelayProvider { *; }
-keep class * implements com.whitertc.core.provider.RelayProvider {
    <init>(...);
    <fields>;
    <methods>;
}
-keep class com.whitertc.plugin.** {
    <init>(...);
    <fields>;
    <methods>;
}
-keepclassmembers class com.whitertc.plugin.** {
    <init>(...);
    <fields>;
    <methods>;
}

# Shared libraries exposed to plugins (Ktor, Coroutines, Serialization)
-dontwarn java.lang.management.**
-keep class io.ktor.** { *; }
-keepclassmembers class io.ktor.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keepclassmembers class kotlinx.coroutines.** { *; }
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class kotlinx.serialization.** { *; }

# Room Database & SQLite Multiplatform
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class com.whitertc.data.db.** { *; }

# JNI Native method bindings
-keepclasseswithmembernames class * {
    native <methods>;
}

# Android Components
-keep class com.whitertc.vpn.WhiteVpnService { *; }
-keep class com.whitertc.WhiteRtcApp { *; }
-keep class com.whitertc.WhiteRtcTileService { *; }
-keep class com.whitertc.MainActivity { *; }

# Koin Dependency Injection
-keep class com.whitertc.di.** { *; }
-keep @org.koin.core.annotation.* class * { *; }