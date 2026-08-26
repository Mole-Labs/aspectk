plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    // PUBLISH_VERSION, published to the throwaway build/localMaven repo (see settings.gradle.kts)
    // instead of a real release -- lets this sample build against the current working tree.
    id("io.github.mole-labs.aspectk") version "0.3.0"
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

    applyDefaultHierarchyTemplate()
}
