# Pleos Playground SDKs use Gson / reflection / AIDL internally. Keep them intact.
-keep class ai.pleos.playground.** { *; }
-keep interface ai.pleos.playground.** { *; }
-dontwarn ai.pleos.playground.**
