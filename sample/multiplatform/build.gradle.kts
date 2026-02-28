plugins {
    id("io.github.mole-labs.aspectk") version "0.1.5-ALPHA"
    id("org.jetbrains.kotlin.multiplatform") version "2.2.21"
}

kotlin {
    jvm()
    jvmToolchain(17)

    iosArm64()
    iosSimulatorArm64()
    macosArm64()

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.aspectk.runtime)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
