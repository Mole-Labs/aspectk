plugins {
    id("org.jetbrains.kotlin.multiplatform") version "2.2.21"
    id("com.mole.aspectk") version "0.1.2-ALPHA"
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
