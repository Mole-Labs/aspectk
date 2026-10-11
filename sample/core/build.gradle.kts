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
        namespace = "sample.multiplatform.core"
        compileSdk = 37
        minSdk = 24
    }
    iosArm64()
    iosSimulatorArm64()
    js { nodejs() }

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            // An annotation library this project does not own: MainThreadAspect targets its
            // @MainThread. api, so that the feature modules can put the annotation on their code.
            api(libs.androidx.annotation)
        }
    }
}
