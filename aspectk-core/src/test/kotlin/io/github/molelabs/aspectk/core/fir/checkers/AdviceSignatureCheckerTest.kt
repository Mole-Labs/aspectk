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
package io.github.molelabs.aspectk.core.fir.checkers

import com.tschuchort.compiletesting.KotlinCompilation.ExitCode
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class AdviceSignatureCheckerTest {
    @Test
    fun `valid advice compiles without AspectK diagnostics`() {
        val result =
            check(
                """
                @Aspect
                object Valid {
                    @Before(Logged::class) fun before(jp: JoinPoint) {}
                    @After(Logged::class) fun after(jp: JoinPoint): Int = 1
                    @Around(Logged::class) fun around(jp: ProceedingJoinPoint): Any? = jp.proceed()
                    @Around(Logged::class) fun anyResult(jp: ProceedingJoinPoint): Any = "x"
                    @Around(Logged::class) suspend fun suspending(jp: SuspendProceedingJoinPoint): Any? = jp.proceed()
                }
                """.trimIndent(),
            )
        assertEquals(ExitCode.OK, result.exitCode, result.messages)
        assertFalse(result.messages.contains("advice"), result.messages)
    }

    @Test
    fun `advice without exactly one join point parameter is an error`() {
        check(
            """
            @Aspect
            object A {
                @Before(Logged::class) fun none() {}
                @After(Logged::class) fun two(jp: JoinPoint, x: Int) {}
                @Before(Logged::class) fun wrongType(x: String) {}
                @Before(Logged::class) fun JoinPoint.receiver(jp: JoinPoint) {}
            }
            """.trimIndent(),
        ).run {
            assertError("of type JoinPoint")
            assertEquals(4, count("Advice must take exactly one parameter"), messages)
        }
    }

    @Test
    fun `@Around without exactly one proceeding join point is an error`() {
        check(
            """
            @Aspect
            object A {
                @Around(Logged::class) fun string(jp: String): Any? = null
                @Around(Logged::class) suspend fun suspendString(jp: String): Any? = null
                @Around(Logged::class) suspend fun two(jp: SuspendProceedingJoinPoint, x: Int): Any? = null
            }
            """.trimIndent(),
        ).run {
            assertError("of type ProceedingJoinPoint")
            assertEquals(2, count("of type SuspendProceedingJoinPoint"), messages)
            assertEquals(3, count("Advice must take exactly one parameter"), messages)
        }
    }

    @Test
    fun `suspend @Around taking a ProceedingJoinPoint is an error`() {
        check(
            """
            @Aspect
            object A {
                @Around(Logged::class) suspend fun around(jp: ProceedingJoinPoint): Any? = null
            }
            """.trimIndent(),
        ).assertError("of type SuspendProceedingJoinPoint")
    }

    @Test
    fun `non-suspend @Around taking a SuspendProceedingJoinPoint is an error`() {
        check(
            """
            @Aspect
            object A {
                @Around(Logged::class) fun around(jp: SuspendProceedingJoinPoint): Any? = null
            }
            """.trimIndent(),
        ).assertError("declare the advice suspend to take a SuspendProceedingJoinPoint")
    }

    @Test
    fun `@Around not returning Any is an error`() {
        check(
            """
            @Aspect
            object A {
                @Around(Logged::class) fun around(jp: ProceedingJoinPoint): String = ""
                @Around(Logged::class) suspend fun suspendAround(jp: SuspendProceedingJoinPoint): String = ""
            }
            """.trimIndent(),
        ).run {
            assertError("@Around advice must return Any?")
            assertEquals(2, count("@Around advice must return Any?"), messages)
        }
    }

    @Test
    fun `advice outside an @Aspect object is a warning`() {
        val result =
            check(
                """
                class Plain {
                    @Before(Logged::class) fun member(jp: JoinPoint) {}
                }

                @Before(Logged::class) fun topLevel(jp: JoinPoint) {}
                """.trimIndent(),
            )
        assertEquals(ExitCode.OK, result.exitCode, result.messages)
        assertEquals(2, result.count("This advice is never applied"), result.messages)
    }
}
