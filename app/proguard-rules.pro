# Pleos SDKs (Gson / AIDL / kotlin-reflect / kotlinx.serialization inside)
-keep class ai.pleos.playground.** { *; }
-keep interface ai.pleos.playground.** { *; }
-dontwarn ai.pleos.playground.**

# android.car is provided by the AAOS system image at runtime
-dontwarn android.car.**

# Gson (used by the SDKs)
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
-dontwarn com.google.gson.**

# kotlinx.serialization (NaviHelper data classes)
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-dontwarn kotlinx.serialization.**
