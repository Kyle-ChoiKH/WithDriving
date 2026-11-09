import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

fun flag(name: String): String = (project.findProperty(name)?.toString() ?: "false").trim().lowercase()

android {
    namespace = "io.vroomi.core.vehicle"
    compileSdk = 34

    defaultConfig {
        minSdk = 28
        consumerProguardFiles("consumer-rules.pro")
        buildConfigField("boolean", "ALLOW_AAOS_SPEED", flag("vroomi.allowAaosSpeed"))
        buildConfigField("boolean", "ALLOW_HVAC_CONTROL", flag("vroomi.allowHvacControl"))
    }
    buildFeatures { buildConfig = true }

    // android.car (CarPropertyManager / CarUxRestrictionsManager) is provided by the AAOS device.
    useLibrary("android.car")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isReturnDefaultValues = true }
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

dependencies {
    api(libs.kotlinx.coroutines.android)
    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.fusedlocation)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    testImplementation(libs.kotlinx.coroutines.test)
}
