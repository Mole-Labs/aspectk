plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    // No version: resolved from the parent aspectk build (includeBuild in settings.gradle.kts)
    id("io.github.mole-labs.aspectk")
}

android {
    namespace = "sample.multiplatform.core"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvm()
    jvmToolchain(17)
    androidTarget()
    iosArm64()
    iosSimulatorArm64()
    js { nodejs() }

    applyDefaultHierarchyTemplate()
}
