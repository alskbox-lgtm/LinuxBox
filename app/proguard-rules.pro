-keep class com.linuxbox.android.core.NativePty { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# Apache Commons Compress & Tukaani XZ
-dontwarn org.apache.commons.compress.**
-dontwarn org.tukaani.xz.**
-dontwarn javax.naming.**
-dontwarn java.lang.invoke.**
-keep class org.tukaani.xz.** { *; }
-keep class org.apache.commons.compress.archivers.tar.** { *; }
-keep class org.apache.commons.compress.compressors.gzip.** { *; }
-keep class org.apache.commons.compress.compressors.xz.** { *; }
