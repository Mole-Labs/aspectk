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

//        aspect-module (declares @Aspect/@Before)
//         /        \
//   branch-a      branch-b
//         \        /
//       feature-module (has the @LogCall target, depends on BOTH branches)
//
// See docs/design-decision/cross-module-weaving.md §3 "Gradle wiring".
class DiamondDependencyWeavingFunctionalTest {
    @TempDir
    lateinit var projectDir: File

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
        // branch-a/branch-b re-expose aspect-module via `api` so feature-module can see it
        writeFile(projectDir, "aspect-module/build.gradle.kts", moduleBuildFile)
        val branchBuildFile = """
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
            """
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
            """.trimIndent(),
        )
        // pass-through, no aspect content of their own
        writeFile(projectDir, "branch-a/src/main/kotlin/BranchA.kt", "class BranchA\n")
        writeFile(projectDir, "branch-b/src/main/kotlin/BranchB.kt", "class BranchB\n")
        writeFile(
            projectDir,
            "feature-module/src/main/kotlin/Target.kt",
            """
            class Target {
                @LogCall
                fun run() {}
            }
            """.trimIndent(),
        )
        writeFile(
            projectDir,
            "feature-module/src/test/kotlin/WeavingTest.kt",
            """
            import org.junit.jupiter.api.Assertions.assertEquals
            import org.junit.jupiter.api.Test

            class WeavingTest {
                @Test
                fun `advice fires exactly once`() {
                    LoggingAspect.executionCount = 0
                    Target().run()
                    assertEquals(1, LoggingAspect.executionCount)
                }
            }
            """.trimIndent(),
        )

        val result = runGradle(projectDir, testKitDir(), "test")
        assertEquals(TaskOutcome.SUCCESS, result.task(":feature-module:test")?.outcome, result.output)
    }
}
