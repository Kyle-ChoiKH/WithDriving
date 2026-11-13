# Pleos SDKs (Gson / AIDL / kotlin-reflect / kotlinx.serialization inside)
-keep class ai.pleos.playground.** { *; }
-keep interface ai.pleos.playground.** { *; }
-dontwarn ai.pleos.playground.**

# android.car is provided by the AAOS system image at runtime
-dontwarn android.car.**

# Gson (used by the SDKs)
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
-dontwarn com.google.gson.**
# Gson reads the type argument from the generic signature of an anonymous TypeToken subclass. R8 keeps a signature
# only on classes that are kept, so without these two rules the Vehicle SDK's capability checks fail in the release
# build ("TypeToken must be created with a type argument") and the brake, climate, turn signal, wiper, light, door
# and battery listeners are never registered. Found on the Pleos Connect emulator with the Virtual Console.
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# kotlinx.serialization (NaviHelper data classes)
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-dontwarn kotlinx.serialization.**
