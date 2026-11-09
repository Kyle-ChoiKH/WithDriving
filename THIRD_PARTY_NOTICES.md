# Third-party notices

The APK includes the following components. Versions are those resolved by Gradle for the release build.

| Component | Version | License | Source |
|---|---|---|---|
| Pleos Playground SDK: Vehicle, FusedLocation, NaviHelper, TextToSpeech, SpeechToText (+ vehicle-data, vehicle-aidl, fused-location-data, fused-location-aidl, navi-data, navi-aidl, universal-access) | 2.3.2 / 1.1.3 / 3.0.5 / 3.3.1 / 3.3.1 | Proprietary, Hyundai Motor Group Pleos Playground terms | https://pleos.ai/playground , https://nexus-playground.pleos.ai/repository/maven-releases/ |
| Kotlin standard library, kotlin-reflect | 2.0.21 / 1.9.25 | Apache License 2.0 | https://github.com/JetBrains/kotlin |
| kotlinx.coroutines | 1.8.1 | Apache License 2.0 | https://github.com/Kotlin/kotlinx.coroutines |
| kotlinx.serialization (via NaviHelper) | as resolved | Apache License 2.0 | https://github.com/Kotlin/kotlinx.serialization |
| AndroidX Core, Activity, Lifecycle | 1.13.1 / 1.9.2 / 2.8.6 | Apache License 2.0 | https://android.googlesource.com/platform/frameworks/support |
| Jetpack Compose (UI, Foundation, Material 3) via BOM | 2024.09.03 | Apache License 2.0 | https://developer.android.com/jetpack/compose |
| Gson (via Pleos SDKs) | 2.10.1 | Apache License 2.0 | https://github.com/google/gson |
| Android Automotive car library (`android.car`, provided by the system image, not bundled) | platform 34 | Apache License 2.0 | https://source.android.com/docs/automotive |

Test-only (not in the APK): JUnit 4.13.2 (Eclipse Public License 1.0), org.json 20240303 (Public Domain), kotlinx-coroutines-test (Apache 2.0), Paparazzi 1.3.5 (Apache 2.0, renders screenshots on the JVM: https://github.com/cashapp/paparazzi).

Art, character, icon and voice lines are original work of the team, drawn in code. No third-party images, fonts or sounds are bundled; the system font is used.

The full Apache License 2.0 text: https://www.apache.org/licenses/LICENSE-2.0
