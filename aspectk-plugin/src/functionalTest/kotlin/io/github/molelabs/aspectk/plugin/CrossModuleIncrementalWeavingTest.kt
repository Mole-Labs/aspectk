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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class CrossModuleIncrementalWeavingTest {
    @TempDir
    lateinit var projectDir: File

    @Test
    fun `cross-module advice survives an incremental rebuild of an unrelated file in the aspect module`() {
        writeProjectSkeleton()
        writeFile(projectDir, "aspect-module/src/main/kotlin/Aspect.kt", aspectFile())
        writeFile(projectDir, "aspect-module/src/main/kotlin/Unrelated.kt", "val unrelated = 1\n")
        writeFile(projectDir, "feature-module/src/main/kotlin/Target.kt", targetFile())
        writeFile(projectDir, "feature-module/src/test/kotlin/WeavingTest.kt", weavingTestFile(executionCount = 1))

        // given
        runGradle(projectDir, testKitDir(), "test").also {
            assertEquals(TaskOutcome.SUCCESS, it.task(":feature-module:test")?.outcome, it.output)
        }

        // when: edit only Unrelated.kt
        writeFile(projectDir, "aspect-module/src/main/kotlin/Unrelated.kt", "val unrelated = 2\n")

        val result = runGradle(projectDir, testKitDir(), "test")

        // then: assert against hints.json directly -- feature-module:compileKotlin can stay
        // UP-TO-DATE and never re-read it, so its test outcome alone wouldn't catch a shrunk file.
        val hintsContent = hintsOf(projectDir, "aspect-module")
        assertEquals(TaskOutcome.UP_TO_DATE, result.task(":feature-module:compileKotlin")?.outcome, result.output)
        assertTrue(
            hintsContent.contains("LoggingAspect"),
            "hints.json after the incremental rebuild did not contain LoggingAspect. " +
                "Content: $hintsContent\n\nBuild output:\n${result.output}",
        )
    }

    @Test
    fun `cross-module advice applies after an incremental build that only edits the feature module's target file`() {
        writeProjectSkeleton()
        writeFile(projectDir, "aspect-module/src/main/kotlin/Aspect.kt", aspectFile())
        writeFile(projectDir, "feature-module/src/main/kotlin/Target.kt", targetFileWithoutAnnotation())

        // given
        runGradle(projectDir, testKitDir(), "compileKotlin").also {
            assertEquals(TaskOutcome.SUCCESS, it.task(":feature-module:compileKotlin")?.outcome, it.output)
        }

        // when: only Target.kt edited
        writeFile(projectDir, "feature-module/src/main/kotlin/Target.kt", targetFile())
        writeFile(projectDir, "feature-module/src/test/kotlin/WeavingTest.kt", weavingTestFile(executionCount = 1))

        val result = runGradle(projectDir, testKitDir(), "test")
        assertEquals(TaskOutcome.UP_TO_DATE, result.task(":aspect-module:compileKotlin")?.outcome, result.output)
        assertEquals(TaskOutcome.SUCCESS, result.task(":feature-module:test")?.outcome, result.output)
    }

    @Test
    fun `cross-module advice removes after an incremental build that only removes the feature module's target file`() {
        writeProjectSkeleton()
        writeFile(projectDir, "aspect-module/src/main/kotlin/Aspect.kt", aspectFile())
        writeFile(projectDir, "feature-module/src/main/kotlin/Target.kt", targetFile())

        // given
        runGradle(projectDir, testKitDir(), "compileKotlin").also {
            assertEquals(TaskOutcome.SUCCESS, it.task(":feature-module:compileKotlin")?.outcome, it.output)
        }

        // when: only Target.kt edited
        writeFile(projectDir, "feature-module/src/main/kotlin/Target.kt", targetFileWithoutAnnotation())
        writeFile(projectDir, "feature-module/src/test/kotlin/WeavingTest.kt", weavingTestFile(executionCount = 0))

        val result = runGradle(projectDir, testKitDir(), "test")
        assertEquals(TaskOutcome.UP_TO_DATE, result.task(":aspect-module:compileKotlin")?.outcome, result.output)
        assertEquals(TaskOutcome.SUCCESS, result.task(":feature-module:test")?.outcome, result.output)
    }

    @Test
    fun `cross-module hints gain a new advice after an incremental build that only edits the aspect module`() {
        writeProjectSkeleton()
        writeFile(projectDir, "aspect-module/src/main/kotlin/Aspect.kt", aspectFileWithoutAdvice())
        writeFile(projectDir, "aspect-module/src/main/kotlin/Unrelated.kt", "val unrelated = 1\n")
        writeFile(projectDir, "feature-module/src/main/kotlin/Target.kt", targetFile())

        // given
        runGradle(projectDir, testKitDir(), "compileKotlin").also {
            assertEquals(TaskOutcome.SUCCESS, it.task(":aspect-module:compileKotlin")?.outcome, it.output)
        }

        // when: only Aspect.kt edited (adds advice)
        writeFile(projectDir, "aspect-module/src/main/kotlin/Aspect.kt", aspectFile())
        writeFile(projectDir, "feature-module/src/test/kotlin/WeavingTest.kt", weavingTestFile(executionCount = 1))

        val result = runGradle(projectDir, testKitDir(), "compileKotlin")
        assertEquals(TaskOutcome.SUCCESS, result.task(":aspect-module:compileKotlin")?.outcome, result.output)
        assertEquals(TaskOutcome.SUCCESS, result.task(":feature-module:compileKotlin")?.outcome, result.output)

        val hintsContent = hintsOf(projectDir, "aspect-module")
        assertTrue(
            hintsContent.contains("LoggingAspect") && hintsContent.contains("LogCall"),
            "hints.json after adding a real advice to aspect-module did not contain it. " +
                "Content: $hintsContent\n\nBuild output:\n${result.output}",
        )
    }

    @Test
    fun `cross-module hints gain a new advice after an incremental build that only removes the aspect module`() {
        writeProjectSkeleton()
        writeFile(projectDir, "aspect-module/src/main/kotlin/Aspect.kt", aspectFile())
        writeFile(projectDir, "aspect-module/src/main/kotlin/Unrelated.kt", "val unrelated = 1\n")
        writeFile(projectDir, "feature-module/src/main/kotlin/Target.kt", targetFile())

        // given
        runGradle(projectDir, testKitDir(), "compileKotlin").also {
            assertEquals(TaskOutcome.SUCCESS, it.task(":aspect-module:compileKotlin")?.outcome, it.output)
        }

        // when: only Aspect.kt edited (removes advice)
        writeFile(projectDir, "aspect-module/src/main/kotlin/Aspect.kt", aspectFileWithoutAdvice())
        writeFile(projectDir, "feature-module/src/test/kotlin/WeavingTest.kt", weavingTestFile(executionCount = 0))

        val result = runGradle(projectDir, testKitDir(), "compileKotlin")
        assertEquals(TaskOutcome.SUCCESS, result.task(":aspect-module:compileKotlin")?.outcome, result.output)
        assertEquals(TaskOutcome.SUCCESS, result.task(":feature-module:compileKotlin")?.outcome, result.output)

        val hintsContent = hintsOf(projectDir, "aspect-module")
        assertTrue(
            !hintsContent.contains("LoggingAspect") && !hintsContent.contains("LogCall"),
            "hints.json after removing a real advice to aspect-module contains it. " +
                "Content: $hintsContent\n\nBuild output:\n${result.output}",
        )
    }

    private fun writeProjectSkeleton() {
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
            rootProject.name = "cross-module-ic-test"
            include(":aspect-module")
            include(":feature-module")
            """.trimIndent(),
        )
        val moduleBuildFile = """
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
        writeFile(projectDir, "aspect-module/build.gradle.kts", moduleBuildFile)
        writeFile(
            projectDir,
            "feature-module/build.gradle.kts",
            """
            $moduleBuildFile

            dependencies {
                implementation(project(":aspect-module"))
            }
            """.trimIndent(),
        )
    }
}
