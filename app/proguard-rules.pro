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

-keep,includedescriptorclasses class com.radley.applock.**$$serializer { *; }
-keepclassmembers class com.radley.applock.** {
    *** Companion;
}
-keepclasseswithmembers class com.radley.applock.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Entry points the framework instantiates by name from the manifest ----------
-keep class com.radley.applock.AppLockApp { *; }
-keep class * extends android.accessibilityservice.AccessibilityService { *; }
-keep class * extends android.app.Service { *; }
-keep class * extends android.content.BroadcastReceiver { *; }
-keep class * extends android.app.Activity { *; }
