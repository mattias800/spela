# Keep libretro JNI bridge
-keep class com.spela.player.libretro.** { *; }

# Keep Ktor
-keep class io.ktor.** { *; }

# Keep kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.spela.player.**$$serializer { *; }
-keepclassmembers class com.spela.player.** { *** Companion; }
-keepclasseswithmembers class com.spela.player.** { kotlinx.serialization.KSerializer serializer(...); }
