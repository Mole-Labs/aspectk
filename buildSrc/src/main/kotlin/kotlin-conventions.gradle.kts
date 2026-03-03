import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JsModuleKind

plugins {
    kotlin("multiplatform")
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
}

internal fun Project.versionCatalog(): VersionCatalog = versionCatalogs.named("libs")

kotlin {
    jvm()
    jvmToolchain(17)
    applyDefaultHierarchyTemplate()

    compilerOptions {
        progressiveMode = true
        optIn.addAll(
            listOf(
                "kotlin.ExperimentalMultiplatform",
                "kotlin.ExperimentalSubclassOptIn",
            ),
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
