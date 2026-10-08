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
import io.github.molelabs.aspectk.core.compile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// The order of several advices on one function, by priority:
//   1. Kind: @Before, then @Around, then the body, then @After inside every @Around  <- this file
//   2. Target annotation: the order the annotations are written on the function (TargetAnnotationOrderTest)
// "First" means it runs first for @Before and @After, and is the outermost for @Around.
// The order between advices of the same kind that target the same annotation is not guaranteed,
// so no test here relies on the order advices are declared in.
//
// Here: the kind layers hold however the advices are declared and whatever they target.
@OptIn(ExperimentalCompilerApi::class)
class AdviceKindOrderTest {
    @Test
    fun `different targets - @Before stays outside an @Around that proceeds twice`() {
        // given
        val result =
            compile(
                listOf(
                    SourceFile.kotlin(
                        "RunTest.kt",
                        """
                        import io.github.molelabs.aspectk.runtime.After
                        import io.github.molelabs.aspectk.runtime.Around
                        import io.github.molelabs.aspectk.runtime.Aspect
                        import io.github.molelabs.aspectk.runtime.Before
                        import io.github.molelabs.aspectk.runtime.JoinPoint
                        import io.github.molelabs.aspectk.runtime.ProceedingJoinPoint
                        import io.github.molelabs.aspectk.runtime.SuspendProceedingJoinPoint
                        import io.github.molelabs.aspectk.runtime.getArg

                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Chained

                        val executionLog = mutableListOf<String>()

                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Repeated

                        @Aspect
                        object ChainAspect {
                            @Before(Chained::class)
                            fun doBefore(jp: JoinPoint) {
                                executionLog.add("before")
                            }

                            @Around(Repeated::class)
                            fun doAround(pjp: ProceedingJoinPoint): Any? {
                                pjp.proceed()
                                return pjp.proceed()
                            }
                        }

                        class Example {
                            @Chained
                            @Repeated
                            fun work() {
                                executionLog.add("body")
                            }
                        }

                        fun runTest() = Example().work()
                        """,
                    ),
                ),
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

        // when
        val runTestKt = result.classLoader.loadClass("RunTestKt")
        runTestKt.getMethod("runTest").invoke(null)
        val log = runTestKt.getDeclaredField("executionLog").apply { isAccessible = true }.get(null) as List<*>

        // then
        assertEquals(listOf("before", "body", "body"), log)
    }

    @Test
    fun `mixed - @After, @Around, @Before declared in that order still run as @Before, @Around, body, @After`() {
        // given
        val result =
            compile(
                listOf(
                    SourceFile.kotlin(
                        "RunTest.kt",
                        """
                        import io.github.molelabs.aspectk.runtime.After
                        import io.github.molelabs.aspectk.runtime.Around
                        import io.github.molelabs.aspectk.runtime.Aspect
                        import io.github.molelabs.aspectk.runtime.Before
                        import io.github.molelabs.aspectk.runtime.JoinPoint
                        import io.github.molelabs.aspectk.runtime.ProceedingJoinPoint
                        import io.github.molelabs.aspectk.runtime.SuspendProceedingJoinPoint
                        import io.github.molelabs.aspectk.runtime.getArg

                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Chained

                        val executionLog = mutableListOf<String>()

                        @Aspect
                        object ChainAspect {
                            @After(Chained::class)
                            fun doAfter(jp: JoinPoint) {
                                executionLog.add("after")
                            }

                            @Around(Chained::class)
                            fun doAround(pjp: ProceedingJoinPoint): Any? {
                                executionLog.add("around-in")
                                return pjp.proceed().also { executionLog.add("around-out") }
                            }

                            @Before(Chained::class)
                            fun doBefore(jp: JoinPoint) {
                                executionLog.add("before")
                            }
                        }

                        class Example {
                            @Chained
                            fun work() {
                                executionLog.add("body")
                            }
                        }

                        fun runTest() = Example().work()
                        """,
                    ),
                ),
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

        // when
        val runTestKt = result.classLoader.loadClass("RunTestKt")
        runTestKt.getMethod("runTest").invoke(null)
        val log = runTestKt.getDeclaredField("executionLog").apply { isAccessible = true }.get(null) as List<*>

        // then
        assertEquals(listOf("before", "around-in", "body", "after", "around-out"), log)
    }

    @Test
    fun `mixed - @Around, @Before, @After declared in that order still run as @Before, @Around, body, @After`() {
        // given
        val result =
            compile(
                listOf(
                    SourceFile.kotlin(
                        "RunTest.kt",
                        """
                        import io.github.molelabs.aspectk.runtime.After
                        import io.github.molelabs.aspectk.runtime.Around
                        import io.github.molelabs.aspectk.runtime.Aspect
                        import io.github.molelabs.aspectk.runtime.Before
                        import io.github.molelabs.aspectk.runtime.JoinPoint
                        import io.github.molelabs.aspectk.runtime.ProceedingJoinPoint
                        import io.github.molelabs.aspectk.runtime.SuspendProceedingJoinPoint
                        import io.github.molelabs.aspectk.runtime.getArg

                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Chained

                        val executionLog = mutableListOf<String>()

                        @Aspect
                        object ChainAspect {
                            @Around(Chained::class)
                            fun doAround(pjp: ProceedingJoinPoint): Any? {
                                executionLog.add("around-in")
                                return pjp.proceed().also { executionLog.add("around-out") }
                            }

                            @Before(Chained::class)
                            fun doBefore(jp: JoinPoint) {
                                executionLog.add("before")
                            }

                            @After(Chained::class)
                            fun doAfter(jp: JoinPoint) {
                                executionLog.add("after")
                            }
                        }

                        class Example {
                            @Chained
                            fun work() {
                                executionLog.add("body")
                            }
                        }

                        fun runTest() = Example().work()
                        """,
                    ),
                ),
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

        // when
        val runTestKt = result.classLoader.loadClass("RunTestKt")
        runTestKt.getMethod("runTest").invoke(null)
        val log = runTestKt.getDeclaredField("executionLog").apply { isAccessible = true }.get(null) as List<*>

        // then
        assertEquals(listOf("before", "around-in", "body", "after", "around-out"), log)
    }

    @Test
    fun `mixed - @After runs before the exception of the body reaches the @Around`() {
        // given
        val result =
            compile(
                listOf(
                    SourceFile.kotlin(
                        "RunTest.kt",
                        """
                        import io.github.molelabs.aspectk.runtime.After
                        import io.github.molelabs.aspectk.runtime.Around
                        import io.github.molelabs.aspectk.runtime.Aspect
                        import io.github.molelabs.aspectk.runtime.Before
                        import io.github.molelabs.aspectk.runtime.JoinPoint
                        import io.github.molelabs.aspectk.runtime.ProceedingJoinPoint
                        import io.github.molelabs.aspectk.runtime.SuspendProceedingJoinPoint
                        import io.github.molelabs.aspectk.runtime.getArg

                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Chained

                        val executionLog = mutableListOf<String>()

                        @Aspect
                        object ChainAspect {
                            @After(Chained::class)
                            fun doAfter(jp: JoinPoint) {
                                executionLog.add("after")
                            }

                            @Around(Chained::class)
                            fun doAround(pjp: ProceedingJoinPoint): Any? {
                                executionLog.add("around-in")
                                return try {
                                    pjp.proceed()
                                } catch (e: IllegalStateException) {
                                    executionLog.add("around-caught")
                                    "fallback"
                                }
                            }

                            @Before(Chained::class)
                            fun doBefore(jp: JoinPoint) {
                                executionLog.add("before")
                            }
                        }

                        class Example {
                            @Chained
                            fun work(): String {
                                executionLog.add("body")
                                throw IllegalStateException("boom")
                            }
                        }

                        fun runTest(): String = Example().work()
                        """,
                    ),
                ),
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

        // when
        val runTestKt = result.classLoader.loadClass("RunTestKt")
        val actual = runTestKt.getMethod("runTest").invoke(null)
        val log = runTestKt.getDeclaredField("executionLog").apply { isAccessible = true }.get(null) as List<*>

        // then
        assertEquals("fallback", actual)
        assertEquals(listOf("before", "around-in", "body", "after", "around-caught"), log)
    }
}
