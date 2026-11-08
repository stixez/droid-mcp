# R8 rules for the droid-mcp app.
#
# Networking and crypto libraries reach parts of themselves through reflection (Netty picks
# channel and logger classes by name, Ktor loads engine config, BouncyCastle registers providers
# by class name), so they're kept whole. Everything else, including the droid-mcp tools that are
# referenced directly, is shrunk normally.

-keep class io.netty.** { *; }
-keep class io.ktor.** { *; }
-keep class org.bouncycastle.** { *; }
-keepnames class kotlinx.coroutines.** { *; }

# Netty and Ktor reference optional platform pieces (JDK-only SSL engines, BlockHound, Log4j,
# SLF4J bindings, reactive streams) that don't exist on Android and are never loaded there.
-dontwarn io.netty.**
-dontwarn io.ktor.**
-dontwarn org.bouncycastle.**
-dontwarn org.slf4j.**
-dontwarn org.apache.log4j.**
-dontwarn org.apache.logging.log4j.**
-dontwarn reactor.blockhound.**
-dontwarn com.aayushatharva.brotli4j.**
-dontwarn com.github.luben.zstd.**
-dontwarn com.jcraft.jzlib.**
-dontwarn com.ning.compress.**
-dontwarn lzma.sdk.**
-dontwarn net.jpountz.**
-dontwarn org.conscrypt.**
-dontwarn org.eclipse.jetty.**
-dontwarn org.jboss.marshalling.**
-dontwarn sun.security.**
-dontwarn javax.naming.**
-dontwarn java.lang.management.**
-dontwarn javax.management.**
-dontwarn com.oracle.svm.**
-dontwarn io.micrometer.**

# kotlinx.serialization: keep generated serializers (Nav3 keys are @Serializable).
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class io.droidmcp.app.**$$serializer { *; }
-keepclassmembers class io.droidmcp.app.** {
    *** Companion;
}

# Readable stack traces in crash reports.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# Removed from the compile SDK; the biometric tool only touches it on old Android versions.
-dontwarn android.hardware.fingerprint.**
