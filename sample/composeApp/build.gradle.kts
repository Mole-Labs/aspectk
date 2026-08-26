import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    // No AspectK plugin here -- composeApp declares no @Aspect and no advice-target annotations
    // of its own. It only assembles already-woven feature modules; it doesn't need to know
    // AspectK exists.
}

android {
    namespace = "sample.multiplatform"
    compileSdk = 36
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
}

kotlin {
    jvm()
    jvmToolchain(17)

    androidTarget()

    iosArm64()
    iosSimulatorArm64()

    applyDefaultHierarchyTemplate()

    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "composeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":core"))
                implementation(project(":data"))
                implementation(project(":feature-user"))
                implementation(project(":feature-payment"))
                implementation(project(":feature-catalog"))
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.components.resources)
            }
        }
        jvmMain {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }
        androidMain {
            dependencies {
                implementation(libs.androidx.activity.compose)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "sample.multiplatform.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "aspectk-sample"
            packageVersion = "1.0.0"
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
