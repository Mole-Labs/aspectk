plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    id("io.github.mole-labs.aspectk") version "0.3.0"
}

android {
    namespace = "sample.multiplatform.feature.user"
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
            // Target annotations (LogExecution/RequirePermission/Trace) and the aspects that
            // weave them (LoggingAspect/PermissionAspect/TracingAspect) both live upstream in
            // :core -- this module never imports the aspect objects themselves, only the
            // annotations, yet the advice still fires (cross-module weaving).
            implementation(project(":core"))
            implementation(project(":data"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
