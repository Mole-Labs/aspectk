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

// See docs/design-decision/cross-module-weaving.md §9 for the two incremental-round failure
// modes these reproduce against a real Gradle build.
class IncrementalWeavingFunctionalTest {
    @TempDir
    lateinit var projectDir: File

    // Control: no incremental round at all.
    @Test
    fun `advice applies on a single clean build`() {
        writeSettingsAndBuildFiles()
        writeFile(
            projectDir,
            "src/main/kotlin/Aspect.kt",
            """
            import io.github.molelabs.aspectk.runtime.Aspect
            import io.github.molelabs.aspectk.runtime.Before
            import io.github.molelabs.aspectk.runtime.JoinPoint

            @Aspect
            object LoggingAspect {
                var executionCount: Int = 0

                @Before(LogCall::class)
                fun log(joinPoint: JoinPoint) {
                    executionCount++
                }
            }
            """.trimIndent(),
        )
        writeFile(
            projectDir,
            "src/main/kotlin/Target.kt",
            """
            annotation class LogCall

            class Target {
                @LogCall
                fun run() {}
            }
            """.trimIndent(),
        )
        writeFile(
            projectDir,
            "src/test/kotlin/WeavingTest.kt",
            """
            import org.junit.jupiter.api.Assertions.assertEquals
            import org.junit.jupiter.api.Test

            class WeavingTest {
                @Test
                fun `advice fires`() {
                    LoggingAspect.executionCount = 0
                    Target().run()
                    assertEquals(1, LoggingAspect.executionCount)
                }
            }
            """.trimIndent(),
        )

        val result = runGradle(projectDir, testKitDir(), "test")
        assertEquals(TaskOutcome.SUCCESS, result.task(":test")?.outcome, result.output)
    }

    @Test
    fun `advice applies after an incremental build that only edits the target file`() {
        writeSettingsAndBuildFiles()
        writeFile(
            projectDir,
            "src/main/kotlin/Aspect.kt",
            """
            import io.github.molelabs.aspectk.runtime.Aspect
            import io.github.molelabs.aspectk.runtime.Before
            import io.github.molelabs.aspectk.runtime.JoinPoint

            @Aspect
            object LoggingAspect {
                var executionCount: Int = 0

                @Before(LogCall::class)
                fun log(joinPoint: JoinPoint) {
                    executionCount++
                }
            }
            """.trimIndent(),
        )
        writeFile(
            projectDir,
            "src/main/kotlin/Target.kt",
            """
            annotation class LogCall

            class Target {
                fun run() {}
            }
            """.trimIndent(),
        )

        // given: round 1, Target unannotated
        runGradle(projectDir, testKitDir(), "compileKotlin").also {
            assertEquals(TaskOutcome.SUCCESS, it.task(":compileKotlin")?.outcome)
        }

        // when: round 2, only Target.kt edited
        writeFile(
            projectDir,
            "src/main/kotlin/Target.kt",
            """
            annotation class LogCall

            class Target {
                @LogCall
                fun run() {}
            }
            """.trimIndent(),
        )
        writeFile(
            projectDir,
            "src/test/kotlin/WeavingTest.kt",
            """
            import org.junit.jupiter.api.Assertions.assertEquals
            import org.junit.jupiter.api.Test

            class WeavingTest {
                @Test
                fun `advice fires`() {
                    LoggingAspect.executionCount = 0
                    Target().run()
                    assertEquals(1, LoggingAspect.executionCount)
                }
            }
            """.trimIndent(),
        )

        val result = runGradle(projectDir, testKitDir(), "test")
        assertEquals(TaskOutcome.SUCCESS, result.task(":test")?.outcome, result.output)
    }

    @Test
    fun `advice applies after an incremental build that only edits the aspect file`() {
        writeSettingsAndBuildFiles()
        writeFile(
            projectDir,
            "src/main/kotlin/Aspect.kt",
            """
            import io.github.molelabs.aspectk.runtime.Aspect

            @Aspect
            object LoggingAspect {
                var executionCount: Int = 0
            }
            """.trimIndent(),
        )
        // Target annotated from the start, never edited again
        writeFile(
            projectDir,
            "src/main/kotlin/Target.kt",
            """
            annotation class LogCall

            class Target {
                @LogCall
                fun run() {}
            }
            """.trimIndent(),
        )

        // given: round 1, LoggingAspect has no advice yet
        runGradle(projectDir, testKitDir(), "compileKotlin").also {
            assertEquals(TaskOutcome.SUCCESS, it.task(":compileKotlin")?.outcome)
        }

        // when: round 2, only Aspect.kt edited (adds @Before)
        writeFile(
            projectDir,
            "src/main/kotlin/Aspect.kt",
            """
            import io.github.molelabs.aspectk.runtime.Aspect
            import io.github.molelabs.aspectk.runtime.Before
            import io.github.molelabs.aspectk.runtime.JoinPoint

            @Aspect
            object LoggingAspect {
                var executionCount: Int = 0

                @Before(LogCall::class)
                fun log(joinPoint: JoinPoint) {
                    executionCount++
                }
            }
            """.trimIndent(),
        )
        writeFile(
            projectDir,
            "src/test/kotlin/WeavingTest.kt",
            """
            import org.junit.jupiter.api.Assertions.assertEquals
            import org.junit.jupiter.api.Test

            class WeavingTest {
                @Test
                fun `advice fires`() {
                    LoggingAspect.executionCount = 0
                    Target().run()
                    assertEquals(1, LoggingAspect.executionCount)
                }
            }
            """.trimIndent(),
        )

        val result = runGradle(projectDir, testKitDir(), "test")
        assertEquals(TaskOutcome.SUCCESS, result.task(":test")?.outcome, result.output)
    }

    private fun writeSettingsAndBuildFiles() {
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
            rootProject.name = "ic-test"
            """.trimIndent(),
        )
        writeFile(
            projectDir,
            "build.gradle.kts",
            """
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
            """.trimIndent(),
        )
    }
}
