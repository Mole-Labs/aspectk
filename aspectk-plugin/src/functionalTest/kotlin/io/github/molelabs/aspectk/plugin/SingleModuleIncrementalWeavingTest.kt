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

class SingleModuleIncrementalWeavingTest {
    @TempDir
    lateinit var projectDir: File

    @Test
    fun `advice applies after an incremental build that only edits the target file`() {
        writeProjectSkeleton()
        writeFile(
            projectDir,
            "src/main/kotlin/Aspect.kt",
            aspectFile(),
        )
        writeFile(
            projectDir,
            "src/main/kotlin/Target.kt",
            targetFileWithoutAnnotation(),
        )

        // given
        runGradle(projectDir, testKitDir(), "compileKotlin", "spyTask").also {
            assertEquals(TaskOutcome.SUCCESS, it.task(":compileKotlin")?.outcome)
        }

        // when: only Target.kt edited
        writeFile(
            projectDir,
            "src/main/kotlin/Target.kt",
            targetFile(),
        )
        writeFile(
            projectDir,
            "src/test/kotlin/WeavingTest.kt",
            weavingTestFile(),
        )

        val result = runGradle(projectDir, testKitDir(), "test", "spyTask")
        val reportText = File(projectDir, "build/changes.txt").readText()
        assertTrue(!reportText.contains("Aspect"))
        assertEquals(TaskOutcome.SUCCESS, result.task(":test")?.outcome, result.output)
    }

    @Test
    fun `advice applies after an incremental build that only edits the aspect file`() {
        writeProjectSkeleton()
        writeFile(
            projectDir,
            "src/main/kotlin/Aspect.kt",
            aspectFileWithoutAdvice(),
        )
        // Target annotated from the start, never edited again
        writeFile(
            projectDir,
            "src/main/kotlin/Target.kt",
            targetFile(),
        )

        // given
        runGradle(projectDir, testKitDir(), "compileKotlin", "spyTask").also {
            assertEquals(TaskOutcome.SUCCESS, it.task(":compileKotlin")?.outcome)
        }

        // when: only Aspect.kt edited (adds @Before)
        writeFile(
            projectDir,
            "src/test/kotlin/WeavingTest.kt",
            weavingTestFile(),
        )
        writeFile(
            projectDir,
            "src/main/kotlin/Aspect.kt",
            aspectFile(),
        )

        val result = runGradle(projectDir, testKitDir(), "test", "spyTask")
        val reportText = File(projectDir, "build/changes.txt").readText()
        assertTrue(!reportText.contains("Target"))
        assertEquals(TaskOutcome.SUCCESS, result.task(":test")?.outcome, result.output)
    }

    @Test
    fun `advice changes after an incremental build that only changes the aspect file`() {
        writeProjectSkeleton()
        writeFile(
            projectDir,
            "src/main/kotlin/Aspect.kt",
            aspectFile(),
        )
        // Target annotated from the start, never edited again
        writeFile(
            projectDir,
            "src/main/kotlin/Target.kt",
            targetFile(),
        )

        // given
        runGradle(projectDir, testKitDir(), "compileKotlin", "spyTask").also {
            assertEquals(TaskOutcome.SUCCESS, it.task(":compileKotlin")?.outcome)
        }

        // when: only Aspect.kt changed (adds @Around)
        writeFile(
            projectDir,
            "src/test/kotlin/WeavingTest.kt",
            weavingTestFile(executionCount = 2),
        )
        writeFile(
            projectDir,
            "src/main/kotlin/Aspect.kt",
            multipleAspectFile(),
        )

        val result = runGradle(projectDir, testKitDir(), "test", "SpyTask")
        val reportText = File(projectDir, "build/changes.txt").readText()
        assertTrue(!reportText.contains("Target"))
        assertEquals(TaskOutcome.SUCCESS, result.task(":test")?.outcome, result.output)
    }

    @Test
    fun `advice changes after an incremental build that only changes particial aspect files`() {
        writeProjectSkeleton()
        writeFile(
            projectDir,
            "src/main/kotlin/Aspect1.kt",
            aspectFile(postFix = "1"),
        )
        writeFile(
            projectDir,
            "src/main/kotlin/Aspect2.kt",
            aspectFile(postFix = "2"),
        )
        writeFile(
            projectDir,
            "src/main/kotlin/Aspect3.kt",
            aspectFile(postFix = "3"),
        )

        writeFile(
            projectDir,
            "src/main/kotlin/Target1.kt",
            targetFile(postFix = "1"),
        )
        writeFile(
            projectDir,
            "src/main/kotlin/Target2.kt",
            targetFile(postFix = "2"),
        )
        writeFile(
            projectDir,
            "src/main/kotlin/Target3.kt",
            targetFile(postFix = "3"),
        )

        // given
        runGradle(projectDir, testKitDir(), "compileKotlin", "spyTask").also {
            assertEquals(TaskOutcome.SUCCESS, it.task(":compileKotlin")?.outcome)
        }

        // when: only Aspect.kt changed (adds @Around)
        writeFile(
            projectDir,
            "src/test/kotlin/WeavingTest1.kt",
            weavingTestFile(executionCount = 0, postFix = "1"),
        )
        writeFile(
            projectDir,
            "src/test/kotlin/WeavingTest2.kt",
            weavingTestFile(executionCount = 1, postFix = "2"),
        )
        writeFile(
            projectDir,
            "src/test/kotlin/WeavingTest3.kt",
            weavingTestFile(executionCount = 1, postFix = "3"),
        )
        writeFile(
            projectDir,
            "src/main/kotlin/Aspect1.kt",
            aspectFileWithoutAdvice(postFix = "1"),
        )

        val result = runGradle(projectDir, testKitDir(), "test", "spyTask")
        val reportText = File(projectDir, "build/changes.txt").readText()
        assertTrue(
            listOf(
                "Aspect2",
                "Aspect3",
                "Target1",
                "Target2",
                "Target3",
            ).all { !reportText.contains(it) },
        )
        assertEquals(TaskOutcome.SUCCESS, result.task(":test")?.outcome, result.output)
    }

    @Test
    fun `advice changes after an incremental build that only removes particial aspect files`() {
        writeProjectSkeleton()
        writeFile(
            projectDir,
            "src/main/kotlin/Aspect1.kt",
            aspectFile(postFix = "1"),
        )
        writeFile(
            projectDir,
            "src/main/kotlin/Aspect2.kt",
            aspectFile(postFix = "2"),
        )
        writeFile(
            projectDir,
            "src/main/kotlin/Aspect3.kt",
            aspectFile(postFix = "3"),
        )

        writeFile(
            projectDir,
            "src/main/kotlin/Target1.kt",
            targetFile(postFix = "1"),
        )
        writeFile(
            projectDir,
            "src/main/kotlin/Target2.kt",
            targetFile(postFix = "2"),
        )
        writeFile(
            projectDir,
            "src/main/kotlin/Target3.kt",
            targetFile(postFix = "3"),
        )

        // given
        runGradle(projectDir, testKitDir(), "compileKotlin", "spyTask").also {
            assertEquals(TaskOutcome.SUCCESS, it.task(":compileKotlin")?.outcome)
        }

        // when
        writeFile(
            projectDir,
            "src/test/kotlin/WeavingTest1.kt",
            weavingTestFile(executionCount = 1, postFix = "1"),
        )
        writeFile(
            projectDir,
            "src/test/kotlin/WeavingTest3.kt",
            weavingTestFile(executionCount = 1, postFix = "3"),
        )
        removeFile(
            projectDir,
            "src/main/kotlin/Aspect2.kt",
        )
        removeFile(
            projectDir,
            "src/main/kotlin/Target2.kt",
        )

        val result = runGradle(projectDir, testKitDir(), "test", "spyTask")
        val reportText = File(projectDir, "build/changes.txt").readText()
        assertTrue(
            listOf(
                "Aspect1",
                "Aspect3",
                "Target1",
                "Target3",
            ).all { !reportText.contains(it) },
        )

        assertEquals(TaskOutcome.SUCCESS, result.task(":test")?.outcome, result.output)
    }

    @Test
    fun `advice changes after an incremental build that only removes unrelated files`() {
        writeProjectSkeleton()
        writeFile(
            projectDir,
            "src/main/kotlin/Aspect1.kt",
            aspectFile(postFix = "1"),
        )
        writeFile(
            projectDir,
            "src/main/kotlin/Aspect2.kt",
            aspectFile(postFix = "2"),
        )
        writeFile(
            projectDir,
            "src/main/kotlin/Aspect3.kt",
            aspectFile(postFix = "3"),
        )

        writeFile(
            projectDir,
            "src/main/kotlin/Target1.kt",
            targetFile(postFix = "1"),
        )
        writeFile(
            projectDir,
            "src/main/kotlin/Target2.kt",
            targetFile(postFix = "2"),
        )
        writeFile(
            projectDir,
            "src/main/kotlin/Target3.kt",
            targetFile(postFix = "3"),
        )

        writeFile(
            projectDir,
            "src/main/kotlin/Unrelated.kt",
            "val unrelated = 1",
        )

        // given
        runGradle(projectDir, testKitDir(), "compileKotlin", "spyTask").also {
            assertEquals(TaskOutcome.SUCCESS, it.task(":compileKotlin")?.outcome)
        }

        // when
        writeFile(
            projectDir,
            "src/test/kotlin/WeavingTest1.kt",
            weavingTestFile(executionCount = 1, postFix = "1"),
        )
        writeFile(
            projectDir,
            "src/test/kotlin/WeavingTest2.kt",
            weavingTestFile(executionCount = 1, postFix = "2"),
        )
        writeFile(
            projectDir,
            "src/test/kotlin/WeavingTest3.kt",
            weavingTestFile(executionCount = 1, postFix = "3"),
        )
        removeFile(
            projectDir,
            "src/main/kotlin/Unrelated.kt",
        )

        val result = runGradle(projectDir, testKitDir(), "test", "spyTask")
        val reportText = File(projectDir, "build/changes.txt").readText()
        assertTrue(
            listOf(
                "Aspect1",
                "Aspect2",
                "Aspect3",
                "Target1",
                "Target2",
                "Target3",
            ).all { !reportText.contains(it) },
        )
        assertEquals(TaskOutcome.SUCCESS, result.task(":test")?.outcome, result.output)
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
            rootProject.name = "ic-test"
            """.trimIndent(),
        )
        writeFile(
            projectDir,
            "build.gradle.kts",
            """
            import org.gradle.work.InputChanges
            import org.gradle.work.Incremental

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

            //for intercepting input changes for incremental build tests
            abstract class SpyTask : DefaultTask() {
                @get:Incremental
                @get:InputFiles
                abstract val inputFiles: ConfigurableFileCollection

                @get:OutputFile
                abstract val reportFile: RegularFileProperty

                @TaskAction
                fun execute(inputChanges: InputChanges) {
                    val changes = inputChanges.getFileChanges(inputFiles)
                        .map { it.file.name + " - " + it.changeType }

                    reportFile.get().asFile.writeText(changes.joinToString("\n"))
                }
            }

            val spyTask by tasks.registering(SpyTask::class) {
                dependsOn(":compileKotlin")
                inputFiles.from(fileTree("src/main/kotlin"))
                reportFile.set(layout.buildDirectory.file("changes.txt"))
            }
            """.trimIndent(),
        )
    }
}
