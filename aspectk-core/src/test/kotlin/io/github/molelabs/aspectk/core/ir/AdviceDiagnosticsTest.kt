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

import com.tschuchort.compiletesting.KotlinCompilation.ExitCode
import io.github.molelabs.aspectk.core.fir.checkers.check
import io.github.molelabs.aspectk.core.fir.checkers.count
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class AdviceDiagnosticsTest {
    @Test
    fun `a non-suspend @Around on a suspend target is an error`() {
        check(
            """
            @Aspect object A {
                @Around(target = [Logged::class])
                fun around(pjp: ProceedingJoinPoint): Any? = pjp.proceed()
            }
            @Logged suspend fun load() {}
            """.trimIndent(),
        ).run {
            assertEquals(ExitCode.COMPILATION_ERROR, exitCode, messages)
            assertEquals(1, count("must be suspend and take a SuspendProceedingJoinPoint exactly when the target function is suspend"), messages)
        }
    }

    @Test
    fun `a suspend @Around on a non-suspend target is an error`() {
        check(
            """
            @Aspect object A {
                @Around(target = [Logged::class])
                suspend fun around(pjp: SuspendProceedingJoinPoint): Any? = pjp.proceed()
            }
            @Logged fun load() {}
            """.trimIndent(),
        ).run {
            assertEquals(ExitCode.COMPILATION_ERROR, exitCode, messages)
            assertEquals(1, count("must be suspend and take a SuspendProceedingJoinPoint exactly when the target function is suspend"), messages)
        }
    }

    @Test
    fun `a suspend @Around on a suspend target compiles`() {
        val result =
            check(
                """
                @Aspect object A {
                    @Around(target = [Logged::class])
                    suspend fun around(pjp: SuspendProceedingJoinPoint): Any? = pjp.proceed()
                }
                @Logged suspend fun load() {}
                """.trimIndent(),
            )
        assertEquals(ExitCode.OK, result.exitCode, result.messages)
    }

    @Test
    fun `an @Around on an inline function with inline lambda parameters is an error`() {
        check(
            """
            @Aspect object A {
                @Around(target = [Logged::class])
                fun around(pjp: ProceedingJoinPoint): Any? = pjp.proceed()
            }
            @Logged inline fun run(block: () -> Unit, crossinline other: () -> Unit) = block()
            """.trimIndent(),
        ).run {
            assertEquals(ExitCode.COMPILATION_ERROR, exitCode, messages)
            assertEquals(1, count("cannot wrap an inline function with inline or crossinline lambda parameters"), messages)
        }
    }

    @Test
    fun `an @Around on an inline function with only noinline lambdas compiles`() {
        val result =
            check(
                """
                @Aspect object A {
                    @Around(target = [Logged::class])
                    fun around(pjp: ProceedingJoinPoint): Any? = pjp.proceed()
                }
                @Logged inline fun run(noinline block: () -> Unit) = block()
                """.trimIndent(),
            )
        assertEquals(ExitCode.OK, result.exitCode, result.messages)
        assertEquals(0, result.count("cannot wrap an inline function with inline or crossinline lambda parameters"), result.messages)
    }

    @Test
    fun `@After on a tailrec function warns and @Before does not`() {
        check(
            """
            annotation class Traced
            @Aspect object A {
                @After(target = [Logged::class])
                fun after(jp: JoinPoint) {}
                @Before(target = [Traced::class])
                fun before(jp: JoinPoint) {}
            }
            @Logged tailrec fun down(n: Int): Int = if (n == 0) 0 else down(n - 1)
            @Traced tailrec fun up(n: Int): Int = if (n == 0) 0 else up(n - 1)
            """.trimIndent(),
        ).run {
            assertEquals(ExitCode.OK, exitCode, messages)
            assertEquals(1, count("wraps a tailrec function"), messages)
            assertEquals(1, count("Advice A.after"), messages)
        }
    }
}
