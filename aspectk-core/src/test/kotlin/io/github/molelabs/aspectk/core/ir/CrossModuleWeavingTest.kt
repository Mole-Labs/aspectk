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
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.net.URLClassLoader
import kotlin.io.path.createTempDirectory

@OptIn(ExperimentalCompilerApi::class)
class CrossModuleWeavingTest {
    @Test
    fun `advice declared in one compilation weaves into a target function compiled separately`() {
        val hintsDir = createTempDirectory("aspectk-hints").toFile()

        val moduleA =
            compileWithHints(
                sourceFiles =
                listOf(
                    SourceFile.kotlin(
                        "ModuleA.kt",
                        """
                        import io.github.molelabs.aspectk.runtime.Aspect
                        import io.github.molelabs.aspectk.runtime.Before
                        import io.github.molelabs.aspectk.runtime.JoinPoint

                        @Target(AnnotationTarget.FUNCTION)
                        annotation class LogCall

                        @Aspect
                        object LoggingAspect {
                            @JvmStatic
                            var executionCount = 0

                            @Before(LogCall::class)
                            fun log(joinPoint: JoinPoint) {
                                executionCount++
                            }
                        }
                        """.trimIndent(),
                    ),
                ),
                hintsOutputDir = hintsDir,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, moduleA.exitCode)

        val moduleB =
            compileWithHints(
                sourceFiles =
                listOf(
                    SourceFile.kotlin(
                        "ModuleB.kt",
                        """
                        class Target {
                            @LogCall
                            fun run() {}
                        }
                        """.trimIndent(),
                    ),
                ),
                hintsPaths = listOf(hintsDir),
                extraClasspath = listOf(moduleA.outputDirectory),
            )
        assertEquals(KotlinCompilation.ExitCode.OK, moduleB.exitCode)

        val classLoader =
            URLClassLoader(
                arrayOf(moduleA.outputDirectory.toURI().toURL(), moduleB.outputDirectory.toURI().toURL()),
                this::class.java.classLoader,
            )
        val targetInstance = classLoader.loadClass("Target").getDeclaredConstructor().newInstance()
        targetInstance::class.java.getMethod("run").invoke(targetInstance)

        val executionCount =
            classLoader
                .loadClass("LoggingAspect")
                .getDeclaredField("executionCount")
                .apply { isAccessible = true }
                .get(null)

        assertEquals(1, executionCount)
    }
}
