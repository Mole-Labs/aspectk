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
}
