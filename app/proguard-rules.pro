# kotlinx.serialization -----------------------------------------------------
# The plugin generates a synthetic Companion.serializer() for every @Serializable
# class; R8 cannot see the reflective link to it, so keep both sides explicitly.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.radley.latch.**$$serializer { *; }
-keepclassmembers class com.radley.latch.** {
    *** Companion;
}
-keepclasseswithmembers class com.radley.latch.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Entry points the framework instantiates by name from the manifest ----------
-keep class com.radley.latch.LatchApp { *; }
-keep class * extends android.accessibilityservice.AccessibilityService { *; }
-keep class * extends android.app.Service { *; }
-keep class * extends android.content.BroadcastReceiver { *; }
-keep class * extends android.app.Activity { *; }
