import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.*
import org.jetbrains.kotlin.gradle.dsl.*

plugins {
    kotlin("multiplatform")
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 8
}

internal fun Project.versionCatalog(): VersionCatalog = versionCatalogs.named("libs")

kotlin {
    explicitApi()
    jvm()
    jvmToolchain(8)

    compilerOptions {
        progressiveMode = true
        optIn.addAll(
            listOf(
                "kotlin.ExperimentalMultiplatform",
                "kotlin.ExperimentalSubclassOptIn",
                "kotlinx.serialization.InternalSerializationApi",
                "kotlinx.serialization.SealedSerializationApi",
            )
        )

        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    js {
        nodejs {
            testTask {
                useMocha {
                    timeout = "10s"
                }
            }
        }

        compilerOptions {
            sourceMap = true
            moduleKind = JsModuleKind.MODULE_UMD
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        nodejs()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmWasi {
        nodejs()
    }

    sourceSets.all {
        kotlin.srcDirs("$name/src")
        resources.srcDirs("$name/resources")
    }

    sourceSets {
        commonMain {
            dependencies {
                api(versionCatalog().findLibrary("kotlin.stdlib").get())
            }
        }

        commonTest {
            dependencies {
                implementation(versionCatalog().findLibrary("kotlin.test").get())
            }
        }

        register("wasmMain") {
            dependsOn(commonMain.get())
        }
        register("wasmTest") {
            dependsOn(commonTest.get())
        }

        named("wasmJsMain") {
            dependsOn(named("wasmMain").get())
        }

        named("wasmJsTest") {
            dependsOn(named("wasmTest").get())
        }

        named("wasmWasiMain") {
            dependsOn(named("wasmMain").get())
        }

        named("wasmWasiTest") {
            dependsOn(named("wasmTest").get())
        }
    }
}