plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    id("io.github.mole-labs.aspectk") version "0.3.0"
}

android {
    namespace = "sample.multiplatform.feature.catalog"
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

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core"))
        }
        commonTest.dependencies {
            implementation(project(":core"))
            implementation(kotlin("test"))
        }
    }
}
