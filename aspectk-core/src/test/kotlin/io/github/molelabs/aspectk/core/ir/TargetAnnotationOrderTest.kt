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
//   1. Kind: @Before, then @Around, then the body, then @After inside every @Around (AdviceKindOrderTest)
//   2. Target annotation: the order the annotations are written on the function  <- this file
// "First" means it runs first for @Before and @After, and is the outermost for @Around.
// The order between advices of the same kind that target the same annotation is not guaranteed,
// so no test here relies on the order advices are declared in.
//
// Here: a function carrying several target annotations, each with its own advice.
@OptIn(ExperimentalCompilerApi::class)
class TargetAnnotationOrderTest {
    // Both orders are compiled together, so neither the order the advices are declared in nor one
    // derived from the annotation names (the lookup is a hash map) can satisfy both.
    @Test
    fun `@Arounds of two target annotations nest in the order the annotations are written`() {
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

                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Logged

                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Timed

                        val executionLog = mutableListOf<String>()

                        // The tags recorded while [block] runs
                        fun record(block: () -> Unit): List<String> {
                            executionLog.clear()
                            block()
                            return executionLog.toList()
                        }

                        @Aspect
                        object OrderAspect {
                            @Around(Timed::class)
                            fun time(pjp: ProceedingJoinPoint): Any? {
                                executionLog.add("timed-in")
                                return pjp.proceed().also { executionLog.add("timed-out") }
                            }

                            @Around(Logged::class)
                            fun log(pjp: ProceedingJoinPoint): Any? {
                                executionLog.add("logged-in")
                                return pjp.proceed().also { executionLog.add("logged-out") }
                            }
                        }

                        @Logged
                        @Timed
                        fun loggedFirst() {
                            executionLog.add("body")
                        }

                        @Timed
                        @Logged
                        fun timedFirst() {
                            executionLog.add("body")
                        }

                        fun runTest(): List<List<String>> = listOf(record { loggedFirst() }, record { timedFirst() })
                        """,
                    ),
                ),
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

        // when
        val actual = result.classLoader.loadClass("RunTestKt").getMethod("runTest").invoke(null)

        // then
        assertEquals(
            listOf(
                listOf("logged-in", "timed-in", "body", "timed-out", "logged-out"),
                listOf("timed-in", "logged-in", "body", "logged-out", "timed-out"),
            ),
            actual,
        )
    }

    @Test
    fun `@Afters of two target annotations run in the order the annotations are written`() {
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

                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Logged

                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Timed

                        val executionLog = mutableListOf<String>()

                        // The tags recorded while [block] runs
                        fun record(block: () -> Unit): List<String> {
                            executionLog.clear()
                            block()
                            return executionLog.toList()
                        }

                        @Aspect
                        object OrderAspect {
                            @After(Timed::class)
                            fun time(jp: JoinPoint) {
                                executionLog.add("timed")
                            }

                            @After(Logged::class)
                            fun log(jp: JoinPoint) {
                                executionLog.add("logged")
                            }
                        }

                        @Logged
                        @Timed
                        fun loggedFirst() {
                            executionLog.add("body")
                        }

                        @Timed
                        @Logged
                        fun timedFirst() {
                            executionLog.add("body")
                        }

                        fun runTest(): List<List<String>> = listOf(record { loggedFirst() }, record { timedFirst() })
                        """,
                    ),
                ),
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

        // when
        val actual = result.classLoader.loadClass("RunTestKt").getMethod("runTest").invoke(null)

        // then
        assertEquals(listOf(listOf("body", "logged", "timed"), listOf("body", "timed", "logged")), actual)
    }

    @Test
    fun `@Befores of two target annotations run in the order the annotations are written`() {
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

                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Logged

                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Timed

                        val executionLog = mutableListOf<String>()

                        // The tags recorded while [block] runs
                        fun record(block: () -> Unit): List<String> {
                            executionLog.clear()
                            block()
                            return executionLog.toList()
                        }

                        @Aspect
                        object OrderAspect {
                            @Before(Timed::class)
                            fun time(jp: JoinPoint) {
                                executionLog.add("timed")
                            }

                            @Before(Logged::class)
                            fun log(jp: JoinPoint) {
                                executionLog.add("logged")
                            }
                        }

                        @Logged
                        @Timed
                        fun loggedFirst() {
                            executionLog.add("body")
                        }

                        @Timed
                        @Logged
                        fun timedFirst() {
                            executionLog.add("body")
                        }

                        fun runTest(): List<List<String>> = listOf(record { loggedFirst() }, record { timedFirst() })
                        """,
                    ),
                ),
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

        // when
        val actual = result.classLoader.loadClass("RunTestKt").getMethod("runTest").invoke(null)

        // then
        assertEquals(listOf(listOf("logged", "timed", "body"), listOf("timed", "logged", "body")), actual)
    }

    // Rule 1 over rule 2: Timed is written last but its @Before still runs ahead of the Logged @Around,
    // and the Logged @After, written first, still runs inside the Timed @Around
    @Test
    fun `the kind layers hold across target annotations`() {
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

                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Logged

                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Timed

                        val executionLog = mutableListOf<String>()

                        // The tags recorded while [block] runs
                        fun record(block: () -> Unit): List<String> {
                            executionLog.clear()
                            block()
                            return executionLog.toList()
                        }

                        @Aspect
                        object OrderAspect {
                            @After(Logged::class)
                            fun logAfter(jp: JoinPoint) {
                                executionLog.add("logged-after")
                            }

                            @Around(Logged::class)
                            fun log(pjp: ProceedingJoinPoint): Any? {
                                executionLog.add("logged-in")
                                return pjp.proceed().also { executionLog.add("logged-out") }
                            }

                            @Around(Timed::class)
                            fun time(pjp: ProceedingJoinPoint): Any? {
                                executionLog.add("timed-in")
                                return pjp.proceed().also { executionLog.add("timed-out") }
                            }

                            @Before(Timed::class)
                            fun timeBefore(jp: JoinPoint) {
                                executionLog.add("timed-before")
                            }
                        }

                        @Logged
                        @Timed
                        fun work() {
                            executionLog.add("body")
                        }

                        fun runTest(): List<String> = record { work() }
                        """,
                    ),
                ),
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

        // when
        val actual = result.classLoader.loadClass("RunTestKt").getMethod("runTest").invoke(null)

        // then
        assertEquals(listOf("timed-before", "logged-in", "timed-in", "body", "logged-after", "timed-out", "logged-out"), actual)
    }

    @Test
    fun `three target annotations keep the order they are written in`() {
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

                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Logged

                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Timed

                        val executionLog = mutableListOf<String>()

                        // The tags recorded while [block] runs
                        fun record(block: () -> Unit): List<String> {
                            executionLog.clear()
                            block()
                            return executionLog.toList()
                        }

                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Cached

                        @Aspect
                        object OrderAspect {
                            @Around(Timed::class)
                            fun time(pjp: ProceedingJoinPoint): Any? {
                                executionLog.add("timed-in")
                                return pjp.proceed().also { executionLog.add("timed-out") }
                            }

                            @Around(Cached::class)
                            fun cache(pjp: ProceedingJoinPoint): Any? {
                                executionLog.add("cached-in")
                                return pjp.proceed().also { executionLog.add("cached-out") }
                            }

                            @Around(Logged::class)
                            fun log(pjp: ProceedingJoinPoint): Any? {
                                executionLog.add("logged-in")
                                return pjp.proceed().also { executionLog.add("logged-out") }
                            }
                        }

                        @Cached
                        @Logged
                        @Timed
                        fun work() {
                            executionLog.add("body")
                        }

                        fun runTest(): List<String> = record { work() }
                        """,
                    ),
                ),
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

        // when
        val actual = result.classLoader.loadClass("RunTestKt").getMethod("runTest").invoke(null)

        // then
        assertEquals(listOf("cached-in", "logged-in", "timed-in", "body", "timed-out", "logged-out", "cached-out"), actual)
    }

    // An advice applies once per target annotation it lists that the function carries
    @Test
    fun `an advice listing two target annotations runs once for each one on the function`() {
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

                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Logged

                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Timed

                        val executionLog = mutableListOf<String>()

                        // The tags recorded while [block] runs
                        fun record(block: () -> Unit): List<String> {
                            executionLog.clear()
                            block()
                            return executionLog.toList()
                        }

                        @Aspect
                        object OrderAspect {
                            @Before(Logged::class, Timed::class)
                            fun before(jp: JoinPoint) {
                                executionLog.add("before")
                            }

                            @Around(Logged::class, Timed::class)
                            fun around(pjp: ProceedingJoinPoint): Any? {
                                executionLog.add("around-in")
                                return pjp.proceed().also { executionLog.add("around-out") }
                            }

                            @After(Logged::class, Timed::class)
                            fun after(jp: JoinPoint) {
                                executionLog.add("after")
                            }
                        }

                        @Logged
                        @Timed
                        fun both() {
                            executionLog.add("body")
                        }

                        @Timed
                        fun one() {
                            executionLog.add("body")
                        }

                        fun runTest(): List<List<String>> = listOf(record { both() }, record { one() })
                        """,
                    ),
                ),
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

        // when
        val actual = result.classLoader.loadClass("RunTestKt").getMethod("runTest").invoke(null)

        // then
        assertEquals(
            listOf(
                listOf("before", "before", "around-in", "around-in", "body", "after", "after", "around-out", "around-out"),
                listOf("before", "around-in", "body", "after", "around-out"),
            ),
            actual,
        )
    }

    // An override carries no annotation itself: the order comes from the overridden declaration.
    // Only guaranteed when that declaration is compiled in the same module.
    @Test
    fun `inherited target annotations keep the order they are written in on the overridden function`() {
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

                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Logged

                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Timed

                        val executionLog = mutableListOf<String>()

                        // The tags recorded while [block] runs
                        fun record(block: () -> Unit): List<String> {
                            executionLog.clear()
                            block()
                            return executionLog.toList()
                        }

                        @Aspect
                        object OrderAspect {
                            @Around(Timed::class, inherits = true)
                            fun time(pjp: ProceedingJoinPoint): Any? {
                                executionLog.add("timed-in")
                                return pjp.proceed().also { executionLog.add("timed-out") }
                            }

                            @Around(Logged::class, inherits = true)
                            fun log(pjp: ProceedingJoinPoint): Any? {
                                executionLog.add("logged-in")
                                return pjp.proceed().also { executionLog.add("logged-out") }
                            }
                        }

                        abstract class Base {
                            @Logged
                            @Timed
                            abstract fun loggedFirst()

                            @Timed
                            @Logged
                            abstract fun timedFirst()
                        }

                        class Impl : Base() {
                            override fun loggedFirst() {
                                executionLog.add("body")
                            }

                            override fun timedFirst() {
                                executionLog.add("body")
                            }
                        }

                        fun runTest(): List<List<String>> = listOf(record { Impl().loggedFirst() }, record { Impl().timedFirst() })
                        """,
                    ),
                ),
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

        // when
        val actual = result.classLoader.loadClass("RunTestKt").getMethod("runTest").invoke(null)

        // then
        assertEquals(
            listOf(
                listOf("logged-in", "timed-in", "body", "timed-out", "logged-out"),
                listOf("timed-in", "logged-in", "body", "logged-out", "timed-out"),
            ),
            actual,
        )
    }
}
