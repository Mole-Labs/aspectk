plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    // No version: resolved from the parent aspectk build (includeBuild in settings.gradle.kts)
    id("io.github.mole-labs.aspectk")
}

kotlin {
    jvm()
    jvmToolchain(17)
    android {
        namespace = "sample.multiplatform.feature.catalog"
        compileSdk = 37
        minSdk = 24
        // commonTest also runs on the Android host (testAndroidHostTest)
        withHostTest {}
    }
    iosArm64()
    iosSimulatorArm64()
    js { nodejs() }

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
