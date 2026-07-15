# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class dev.sirulex.syncthing.**$$serializer { *; }
-keepclassmembers class dev.sirulex.syncthing.** { *** Companion; }
-keepclasseswithmembers class dev.sirulex.syncthing.** { kotlinx.serialization.KSerializer serializer(...); }

# Ktor
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# ZXing
-keep class com.google.zxing.** { *; }

# WorkManager opens its generated Room database through reflection. R8 can
# otherwise remove the no-arg constructor before Application.onCreate().
-keep class androidx.work.impl.WorkDatabase_Impl { *; }

# Firebase component discovery instantiates ML Kit registrars by the class
# names stored in manifest metadata.
-keep class * implements com.google.firebase.components.ComponentRegistrar { *; }
