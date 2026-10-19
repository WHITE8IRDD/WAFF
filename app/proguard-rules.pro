# NewPipe Extractor uses Mozilla Rhino for some transformations.
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter { *; }
-dontwarn org.mozilla.javascript.tools.**
# Rhino ships optional JSR-223 / Dynalink adapters that are not present on Android.
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**
-dontwarn java.beans.**
# yt-dlp metadata is deserialized reflectively by Jackson.
-keep class com.yausername.youtubedl_android.mapper.** { *; }
