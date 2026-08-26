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

// AspectVisitor discovers @Aspect/@Before by walking whatever IrModuleFragment the K2 compiler
// hands to IrGenerationExtension.generate() (moduleFragment.acceptChildren(...)), never through a
// symbol-resolution API (referenceClass/referenceFunctions/lookupFunctions). Real Gradle
// incremental compilation may only include the "dirty" subset of files in that moduleFragment on
// a non-clean build. These tests reproduce (or disprove) that hypothesis against a real Gradle
// build, split into the two ways an incremental round can go stale:
//  - Scenario A: the file with the TARGET annotation is edited; the file with the @Aspect/@Before
//    is untouched. The edited file is trivially dirty either way; the question is whether the
//    untouched aspect file is still visible to AspectVisitor during this same round.
//  - Scenario B: the file with the @Aspect/@Before is edited; the target file is untouched. The
//    target file isn't even part of this round's dirty set, so nothing can fix this within the
//    round -- see docs/design-decision/cross-module-weaving.md for the LookupTracker-based
//    recovery path this pipeline does not currently participate in.
class IncrementalWeavingFunctionalTest {
    @TempDir
    lateinit var projectDir: File

    // Control: everything present from a single clean build (no incremental round at all).
    // If this fails too, the harness/plugin setup itself is broken, not IC specifically.
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

        // Round 1: establishes the incremental-compilation baseline. Target isn't annotated yet.
        runGradle(projectDir, testKitDir(), "compileKotlin").also {
            assertEquals(TaskOutcome.SUCCESS, it.task(":compileKotlin")?.outcome)
        }

        // Round 2 (incremental, same project/build dirs): ONLY Target.kt is edited.
        // Aspect.kt is untouched.
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
        // Target is annotated from the start and never edited again -- only Aspect.kt changes
        // between rounds.
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

        // Round 1: establishes the incremental-compilation baseline. LoggingAspect has no advice
        // yet, so there's nothing to weave into Target.
        runGradle(projectDir, testKitDir(), "compileKotlin").also {
            assertEquals(TaskOutcome.SUCCESS, it.task(":compileKotlin")?.outcome)
        }

        // Round 2 (incremental, same project/build dirs): ONLY Aspect.kt is edited -- a @Before
        // advice is added. Target.kt is untouched.
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
