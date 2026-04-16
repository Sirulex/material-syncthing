# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class dev.lostf1sh.syncthing.**$$serializer { *; }
-keepclassmembers class dev.lostf1sh.syncthing.** { *** Companion; }
-keepclasseswithmembers class dev.lostf1sh.syncthing.** { kotlinx.serialization.KSerializer serializer(...); }

# Ktor
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# ZXing
-keep class com.google.zxing.** { *; }
