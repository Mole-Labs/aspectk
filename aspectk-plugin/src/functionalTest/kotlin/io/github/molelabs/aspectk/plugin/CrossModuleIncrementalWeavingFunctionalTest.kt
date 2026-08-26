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

// HintsCodec.write() overwrites hints.json wholesale from whatever AspectVisitor's raw IR walk
// saw THIS round (AdviceGenerationExtension.kt:68-70). Real Gradle incremental compilation may
// only include the "dirty" subset of a module's files in that round's moduleFragment. This
// reproduces the resulting risk: an upstream (aspect-declaring) module recompiles incrementally
// for a reason that has nothing to do with its @Aspect/@Before declarations -- do those
// declarations survive into hints.json, and does a downstream module consuming it still weave
// correctly? See docs/design-decision/cross-module-weaving.md.
class CrossModuleIncrementalWeavingFunctionalTest {
    @TempDir
    lateinit var projectDir: File

    // Control: a single clean build across both modules. If this fails, the harness/multi-module
    // wiring itself is broken, not incremental compilation specifically.
    @Test
    fun `cross-module advice applies on a single clean build`() {
        writeProjectSkeleton()
        writeFile(projectDir, "aspect-module/src/main/kotlin/Aspect.kt", aspectFile())
        writeFile(projectDir, "aspect-module/src/main/kotlin/Unrelated.kt", "val unrelated = 1\n")
        writeFile(projectDir, "feature-module/src/main/kotlin/Target.kt", targetFile())
        writeFile(projectDir, "feature-module/src/test/kotlin/WeavingTest.kt", weavingTestFile())

        val result = runGradle(projectDir, testKitDir(), "test")
        assertEquals(TaskOutcome.SUCCESS, result.task(":feature-module:test")?.outcome, result.output)
    }

    @Test
    fun `cross-module advice survives an incremental rebuild of an unrelated file in the aspect module`() {
        writeProjectSkeleton()
        writeFile(projectDir, "aspect-module/src/main/kotlin/Aspect.kt", aspectFile())
        writeFile(projectDir, "aspect-module/src/main/kotlin/Unrelated.kt", "val unrelated = 1\n")
        writeFile(projectDir, "feature-module/src/main/kotlin/Target.kt", targetFile())
        writeFile(projectDir, "feature-module/src/test/kotlin/WeavingTest.kt", weavingTestFile())

        // Round 1: establishes the incremental-compilation baseline for both modules.
        runGradle(projectDir, testKitDir(), "test").also {
            assertEquals(TaskOutcome.SUCCESS, it.task(":feature-module:test")?.outcome, it.output)
        }

        // Round 2 (incremental, same project/build dirs): edit ONLY an unrelated file in
        // aspect-module. Aspect.kt (the @Aspect/@Before declarations) and Target.kt (the
        // feature-module target) are both untouched.
        writeFile(projectDir, "aspect-module/src/main/kotlin/Unrelated.kt", "val unrelated = 2\n")

        val result = runGradle(projectDir, testKitDir(), "test")

        // Asserted directly against hints.json rather than through feature-module's test outcome:
        // feature-module's own compileKotlin task can come back UP-TO-DATE on this round (none of
        // ITS declared inputs changed), in which case it never re-reads hints.json at all and
        // just reuses its already-correct bytecode from round 1 -- masking a shrunk hints.json
        // entirely. That's a real, separate gap (the hintsPath option isn't tracked as a
        // content-sensitive task input), but it means feature-module's test outcome is not a
        // reliable signal for whether aspect-module's hints.json survived this round.
        val hintsFile =
            File(projectDir, "aspect-module/build/generated/aspectk/hints")
                .walkTopDown()
                .firstOrNull { it.name == "hints.json" }
        val hintsContent = hintsFile?.readText().orEmpty()
        assertTrue(
            hintsContent.contains("LoggingAspect"),
            "hints.json after the incremental rebuild did not contain LoggingAspect. " +
                "Content: $hintsContent\n\nBuild output:\n${result.output}",
        )
    }

    // Cross-module analog of the single-module "target file only" case: feature-module's target
    // is edited; aspect-module is untouched (not even rebuilt -- its compileKotlin comes back
    // UP-TO-DATE). aspect-module's hints.json from round 1 is already correct and doesn't change,
    // so this only exercises whether a downstream-only recompile still consumes an unchanged
    // upstream hints.json correctly -- it does not exercise DetectAspectChangeTask at all.
    @Test
    fun `cross-module advice applies after an incremental build that only edits the feature module's target file`() {
        writeProjectSkeleton()
        writeFile(projectDir, "aspect-module/src/main/kotlin/Aspect.kt", aspectFile())
        writeFile(projectDir, "feature-module/src/main/kotlin/Target.kt", targetFileWithoutAnnotation())

        // Round 1: establishes the incremental-compilation baseline. Target isn't annotated yet.
        runGradle(projectDir, testKitDir(), "compileKotlin").also {
            assertEquals(TaskOutcome.SUCCESS, it.task(":feature-module:compileKotlin")?.outcome, it.output)
        }

        // Round 2 (incremental, same project/build dirs): ONLY feature-module/Target.kt is
        // edited. aspect-module is untouched.
        writeFile(projectDir, "feature-module/src/main/kotlin/Target.kt", targetFile())
        writeFile(projectDir, "feature-module/src/test/kotlin/WeavingTest.kt", weavingTestFile())

        val result = runGradle(projectDir, testKitDir(), "test")
        assertEquals(TaskOutcome.SUCCESS, result.task(":feature-module:test")?.outcome, result.output)
    }

    // Complements "survives an incremental rebuild of an unrelated file": that test proves an
    // EXISTING advice isn't lost when something irrelevant changes (the "no-loss" direction).
    // This one proves a NEWLY ADDED advice is actually picked up during an incremental round (the
    // "gain" direction). Asserted directly against aspect-module's own hints.json, not through
    // feature-module's test outcome, for the same reason as the other hints.json assertion above
    // (feature-module's compileKotlin can come back UP-TO-DATE and never re-read hints.json at
    // all -- a separate, already-known gap, not what this test is checking).
    @Test
    fun `cross-module hints gain a new advice after an incremental build that only edits the aspect module`() {
        writeProjectSkeleton()
        writeFile(projectDir, "aspect-module/src/main/kotlin/Aspect.kt", aspectFileWithoutAdvice())
        writeFile(projectDir, "aspect-module/src/main/kotlin/Unrelated.kt", "val unrelated = 1\n")
        writeFile(projectDir, "feature-module/src/main/kotlin/Target.kt", targetFile())

        // Round 1: establishes the incremental-compilation baseline. LoggingAspect has no advice
        // yet.
        runGradle(projectDir, testKitDir(), "compileKotlin").also {
            assertEquals(TaskOutcome.SUCCESS, it.task(":aspect-module:compileKotlin")?.outcome, it.output)
        }

        // Round 2 (incremental, same project/build dirs): ONLY aspect-module/Aspect.kt is
        // edited -- a real @Before advice is added. Unrelated.kt and feature-module are untouched.
        writeFile(projectDir, "aspect-module/src/main/kotlin/Aspect.kt", aspectFile())

        val result = runGradle(projectDir, testKitDir(), "compileKotlin")
        assertEquals(TaskOutcome.SUCCESS, result.task(":aspect-module:compileKotlin")?.outcome, result.output)

        val hintsFile =
            File(projectDir, "aspect-module/build/generated/aspectk/hints")
                .walkTopDown()
                .firstOrNull { it.name == "hints.json" }
        val hintsContent = hintsFile?.readText().orEmpty()
        assertTrue(
            hintsContent.contains("LoggingAspect") && hintsContent.contains("LogCall"),
            "hints.json after adding a real advice to aspect-module did not contain it. " +
                "Content: $hintsContent\n\nBuild output:\n${result.output}",
        )
    }

    private fun aspectFile() = """
        import io.github.molelabs.aspectk.runtime.Aspect
        import io.github.molelabs.aspectk.runtime.Before
        import io.github.molelabs.aspectk.runtime.JoinPoint

        annotation class LogCall

        @Aspect
        object LoggingAspect {
            var executionCount: Int = 0

            @Before(LogCall::class)
            fun log(joinPoint: JoinPoint) {
                executionCount++
            }
        }
    """.trimIndent()

    private fun targetFile() = """
        class Target {
            @LogCall
            fun run() {}
        }
    """.trimIndent()

    private fun targetFileWithoutAnnotation() = """
        class Target {
            fun run() {}
        }
    """.trimIndent()

    private fun aspectFileWithoutAdvice() = """
        import io.github.molelabs.aspectk.runtime.Aspect

        annotation class LogCall

        @Aspect
        object LoggingAspect {
            var executionCount: Int = 0
        }
    """.trimIndent()

    private fun weavingTestFile() = """
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
    """.trimIndent()

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
