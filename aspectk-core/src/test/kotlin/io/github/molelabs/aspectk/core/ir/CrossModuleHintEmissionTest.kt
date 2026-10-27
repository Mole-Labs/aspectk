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
package io.github.molelabs.aspectk.core.ir

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.github.molelabs.aspectk.core.compileWithHints
import io.github.molelabs.aspectk.core.hints.HintRecord
import io.github.molelabs.aspectk.core.hints.HintsCodec
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.createTempDirectory

@OptIn(ExperimentalCompilerApi::class)
class CrossModuleHintEmissionTest {
    @Test
    fun `writes one hint record per advice function to the configured output directory`() {
        val hintsDir = createTempDirectory("aspectk-hints").toFile()

        val result =
            compileWithHints(
                sourceFiles =
                listOf(
                    SourceFile.kotlin(
                        "Aspect.kt",
                        """
                        import io.github.molelabs.aspectk.runtime.Aspect
                        import io.github.molelabs.aspectk.runtime.Before
                        import io.github.molelabs.aspectk.runtime.JoinPoint

                        @Target(AnnotationTarget.FUNCTION)
                        annotation class LogCall

                        @Aspect
                        object LoggingAspect {
                            @Before(LogCall::class)
                            fun log(joinPoint: JoinPoint) {}
                        }
                        """.trimIndent(),
                    ),
                ),
                hintsOutputDir = hintsDir,
            )

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        assertEquals(
            listOf(HintRecord("", "LoggingAspect", "log", "BEFORE", listOf("LogCall"), false)),
            HintsCodec.read(File(hintsDir, "hints.json")),
        )
    }

    @Test
    fun `reports applied and dropped advice across modules and dumps only woven files`() {
        // lib is on the classpath; gone's hints arrive without its classes, so they can't resolve
        val lib = compileUpstream("lib", "LibAspect")
        val gone = compileUpstream("gone", "GoneAspect")
        val reportsDir = createTempDirectory("aspectk-reports").toFile()

        val result =
            compileWithHints(
                sourceFiles =
                listOf(
                    SourceFile.kotlin(
                        "Main.kt",
                        """
                        package app

                        import io.github.molelabs.aspectk.runtime.*

                        @Aspect
                        object AppAspect {
                            @Before(shared.Logged::class) fun advice(jp: JoinPoint) {}
                        }

                        class Woven {
                            @shared.Logged fun run() {}
                        }

                        @shared.Logged fun topLevel() {}
                        """.trimIndent(),
                    ),
                    SourceFile.kotlin("Plain.kt", "package app\n\nclass Plain {\n    fun run() {}\n}"),
                ),
                hintsPaths = listOf(lib.hintsDir, gone.hintsDir),
                extraClasspath = listOf(lib.classes),
                reportsDir = reportsDir,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

        assertEquals(
            """
            ## Applied
            shared.Logged
              BEFORE  app.AppAspect.advice  [local]
                -> app.Woven.run
                -> app.topLevel
              BEFORE  lib.LibAspect.advice  [external]
                -> app.Woven.run
                -> app.topLevel

            ## Dropped
            gone.GoneAspect.advice -> shared.Logged  [external]
            """.trimIndent(),
            File(reportsDir, "aspects.txt").readText().trimEnd(),
        )
        assertTrue(File(reportsDir, "woven/app/Main.kt").readText().contains("class Woven"))
        assertFalse(File(reportsDir, "woven/app/Plain.kt").exists())
    }

    private class Upstream(val classes: File, val hintsDir: File)

    // A module declaring the shared `Logged` target and one aspect advising it
    private fun compileUpstream(
        packageName: String,
        aspectName: String,
    ): Upstream {
        val hintsDir = createTempDirectory("aspectk-hints-$packageName").toFile()
        val result =
            compileWithHints(
                sourceFiles =
                listOf(
                    SourceFile.kotlin(
                        "$aspectName.kt",
                        """
                        package $packageName

                        import io.github.molelabs.aspectk.runtime.*

                        @Aspect
                        object $aspectName {
                            @Before(shared.Logged::class) fun advice(jp: JoinPoint) {}
                        }
                        """.trimIndent(),
                    ),
                    SourceFile.kotlin("Logged.kt", "package shared\n\nannotation class Logged"),
                ),
                hintsOutputDir = hintsDir,
                moduleName = packageName,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        return Upstream(result.outputDirectory, hintsDir)
    }
}
