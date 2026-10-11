plugins {
    // AGP 9 compiles Kotlin itself, so there is no Kotlin plugin here. It also no longer allows an
    // application to be a Kotlin Multiplatform module: the shared UI stays in :composeApp, and
    // this module is only the Android entry point.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // No version: resolved from the parent aspectk build (includeBuild in settings.gradle.kts)
    id("io.github.mole-labs.aspectk")
}

android {
    namespace = "sample.multiplatform"
    compileSdk = 37
    defaultConfig {
        applicationId = "sample.multiplatform"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // The app has variants (playDebug, internalDebug, ...) that the library modules do not. The
    // aspects of :core have to reach this module all the same.
    flavorDimensions += "store"
    productFlavors {
        create("play") { dimension = "store" }
        create("internal") { dimension = "store" }
    }
}

dependencies {
    implementation(project(":composeApp"))
    implementation(libs.androidx.activity.compose)
    // Not a multiplatform module, so kotlin-test does not pick its JUnit binding by itself
    testImplementation(libs.kotlin.test.junit)
}
