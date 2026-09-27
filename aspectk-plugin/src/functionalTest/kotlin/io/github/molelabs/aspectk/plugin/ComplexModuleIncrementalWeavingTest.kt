/*
 * Copyright (C) 2026 aspectk
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.molelabs.aspectk.plugin

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ComplexModuleIncrementalWeavingTest {
    @TempDir
    lateinit var projectDir: File

    //        aspect-module (declares @Aspect/@Before)
    //         /        \
    //   branch-a      branch-b
    //         \        /
    //       feature-module (has the @LogCall target, depends on BOTH branches)
    //

    @Test
    fun `advice declared at the top of a diamond weaves into the target at the bottom exactly once`() {
        writeFile(
            projectDir,
            "settings.gradle.kts",
            """
            pluginManagement {
                repositories {
                    maven(url = "${testRepo()}")
                    gradlePluginPortal()
                    mavenCentral()
                }
            }
            dependencyResolutionManagement {
                repositories {
                    maven(url = "${testRepo()}")
                    mavenCentral()
                }
            }
            rootProject.name = "diamond-test"
            include(":aspect-module")
            include(":branch-a")
            include(":branch-b")
            include(":feature-module")
            """.trimIndent(),
        )

        // branch-a/branch-b re-expose aspect-module via `api` so feature-module can see it
        writeFile(projectDir, "aspect-module/build.gradle.kts", moduleBuildFile)
        writeFile(projectDir, "branch-a/build.gradle.kts", branchBuildFile)
        writeFile(projectDir, "branch-b/build.gradle.kts", branchBuildFile)
        writeFile(
            projectDir,
            "feature-module/build.gradle.kts",
            """
            $moduleBuildFile

            dependencies {
                implementation(project(":branch-a"))
                implementation(project(":branch-b"))
            }
            """.trimIndent(),
        )

        writeFile(
            projectDir,
            "aspect-module/src/main/kotlin/Aspect.kt",
            aspectFile(),
        )
        // pass-through, no aspect content of their own
        writeFile(projectDir, "branch-a/src/main/kotlin/BranchA.kt", "class BranchA\n")
        writeFile(projectDir, "branch-b/src/main/kotlin/BranchB.kt", "class BranchB\n")

        writeFile(
            projectDir,
            "feature-module/src/main/kotlin/Target.kt",
            targetFile(),
        )
        writeFile(
            projectDir,
            "feature-module/src/test/kotlin/WeavingTest.kt",
            weavingTestFile(),
        )

        val result = runGradle(projectDir, testKitDir(), "test")
        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":feature-module:test")?.outcome,
            result.output,
        )

        // when
        writeFile(
            projectDir,
            "feature-module/src/main/kotlin/Target.kt",
            targetFileWithoutAnnotation(),
        )
        writeFile(
            projectDir,
            "feature-module/src/test/kotlin/WeavingTest.kt",
            weavingTestFile(executionCount = 0),
        )

        // then
        val incrementalResult = runGradle(projectDir, testKitDir(), "test")
        assertEquals(
            TaskOutcome.UP_TO_DATE,
            incrementalResult.task(":aspect-module:compileKotlin")?.outcome,
        )
        assertEquals(
            TaskOutcome.UP_TO_DATE,
            incrementalResult.task(":branch-a:compileKotlin")?.outcome,
        )
        assertEquals(
            TaskOutcome.UP_TO_DATE,
            incrementalResult.task(":branch-b:compileKotlin")?.outcome,
        )
        assertEquals(
            TaskOutcome.SUCCESS,
            incrementalResult.task(":feature-module:test")?.outcome,
            result.output,
        )
    }

    //        aspect-module (declares @Aspect/@Before)
    //              |
    //           branch-a (has the @LogCall target)
    //              |
    //       feature-module (has the @LogCall target)

    @Test
    fun `advice declared at the top of a linear propagtes into the target at the bottom`() {
        writeFile(
            projectDir,
            "settings.gradle.kts",
            """
            pluginManagement {
                repositories {
                    maven(url = "${testRepo()}")
                    gradlePluginPortal()
                    mavenCentral()
                }
            }
            dependencyResolutionManagement {
                repositories {
                    maven(url = "${testRepo()}")
                    mavenCentral()
                }
            }
            rootProject.name = "diamond-test"
            include(":aspect-module")
            include(":branch-a")
            include(":feature-module")
            """.trimIndent(),
        )

        writeFile(projectDir, "aspect-module/build.gradle.kts", moduleBuildFile)
        writeFile(
            projectDir,
            "branch-a/build.gradle.kts",
            """
            plugins {
                id("org.jetbrains.kotlin.jvm") version "${kotlinVersion()}"
                id("io.github.mole-labs.aspectk") version "${aspectkVersion()}"
                id("java-library")
            }
            kotlin {
                jvmToolchain(17)
            }
            tasks.test {
                useJUnitPlatform()
            }
            dependencies {
                testImplementation("org.junit.jupiter:junit-jupiter:5.8.1")
                api(project(":aspect-module"))
            }
            """.trimIndent(),
        )
        writeFile(
            projectDir,
            "feature-module/build.gradle.kts",
            """
            $moduleBuildFile

            dependencies {
                implementation(project(":branch-a"))
            }
            """.trimIndent(),
        )

        writeFile(
            projectDir,
            "aspect-module/src/main/kotlin/Aspect.kt",
            aspectFile(),
        )
        writeFile(projectDir, "branch-a/src/main/kotlin/BranchA.kt", "class BranchA\n")
        writeFile(
            projectDir,
            "branch-a/src/main/kotlin/Target.kt",
            targetFile(),
        )
        writeFile(
            projectDir,
            "branch-a/src/test/kotlin/WeavingTest.kt",
            weavingTestFile(),
        )
        writeFile(
            projectDir,
            "feature-module/src/main/kotlin/Target.kt",
            targetFile(),
        )
        writeFile(
            projectDir,
            "feature-module/src/test/kotlin/WeavingTest.kt",
            weavingTestFile(),
        )

        val result = runGradle(projectDir, testKitDir(), "test")
        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":feature-module:test")?.outcome,
            result.output,
        )

        // when
        writeFile(
            projectDir,
            "branch-a/src/main/kotlin/Target.kt",
            targetFileWithoutAnnotation(),
        )
        writeFile(
            projectDir,
            "branch-a/src/test/kotlin/WeavingTest.kt",
            weavingTestFile(executionCount = 0),
        )

        // then
        val incrementalResult = runGradle(projectDir, testKitDir(), "test")
        assertEquals(
            TaskOutcome.UP_TO_DATE,
            incrementalResult.task(":aspect-module:compileKotlin")?.outcome,
        )

        // default kotlin compiler action
        assertEquals(
            TaskOutcome.SUCCESS,
            incrementalResult.task(":feature-module:compileKotlin")?.outcome,
        )
        assertEquals(
            TaskOutcome.SUCCESS,
            incrementalResult.task(":branch-a:test")?.outcome,
            result.output,
        )
        assertEquals(
            TaskOutcome.SUCCESS,
            incrementalResult.task(":feature-module:test")?.outcome,
            result.output,
        )
    }

    private val moduleBuildFile = """
            plugins {
                id("org.jetbrains.kotlin.jvm") version "${kotlinVersion()}"
                id("io.github.mole-labs.aspectk") version "${aspectkVersion()}"
            }
            kotlin {
                jvmToolchain(17)
            }
            dependencies {
                testImplementation("org.junit.jupiter:junit-jupiter:5.8.1")
            }
            tasks.test {
                useJUnitPlatform()
            }
    """.trimIndent()

    private val branchBuildFile = """
            plugins {
                id("org.jetbrains.kotlin.jvm") version "${kotlinVersion()}"
                id("io.github.mole-labs.aspectk") version "${aspectkVersion()}"
                id("java-library")
            }
            kotlin {
                jvmToolchain(17)
            }
            dependencies {
                api(project(":aspect-module"))
            }
    """.trimIndent()
}
