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

// What flows through a chain of several advices on the same function: arguments going in, the
// result coming out, a skipped or repeated proceed(), and exceptions. Which advice is the outer one
// is fixed by the order of @Outer and @Inner on the target (TargetAnnotationOrderTest)
@OptIn(ExperimentalCompilerApi::class)
class AdviceChainingTest {
    @Test
    fun `args replaced by an outer @Around reach the inner @Around, the body and @After`() {
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

                        // Written on the target in this order, so Outer's advices come first
                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Outer

                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Inner

                        val executionLog = mutableListOf<String>()

                        @Aspect
                        object ChainAspect {
                            @Before(Chained::class)
                            fun doBefore(jp: JoinPoint) {
                                executionLog.add("before:" + jp.getArg<String>("name"))
                            }

                            @Around(Outer::class)
                            fun outer(pjp: ProceedingJoinPoint): Any? {
                                executionLog.add("outer:" + pjp.getArg<String>("name"))
                                return pjp.proceed("from-outer")
                            }

                            @Around(Inner::class)
                            fun inner(pjp: ProceedingJoinPoint): Any? {
                                executionLog.add("inner:" + pjp.getArg<String>("name"))
                                return pjp.proceed("from-inner")
                            }

                            @After(Chained::class)
                            fun doAfter(jp: JoinPoint) {
                                executionLog.add("after:" + jp.getArg<String>("name"))
                            }
                        }

                        class Example {
                            @Chained
                            @Outer
                            @Inner
                            fun greet(name: String): String {
                                executionLog.add("body:" + name)
                                return name
                            }
                        }

                        fun runTest(): String = Example().greet("original")
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
        assertEquals("from-inner", actual)
        assertEquals(listOf("before:original", "outer:original", "inner:from-outer", "body:from-inner", "after:from-inner"), log)
    }

    @Test
    fun `the result is transformed by the inner @Around first, then by the outer one`() {
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

                        // Written on the target in this order, so Outer's advices come first
                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Outer

                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Inner

                        val executionLog = mutableListOf<String>()

                        @Aspect
                        object ChainAspect {
                            @Around(Outer::class)
                            fun outer(pjp: ProceedingJoinPoint): Any? = (pjp.proceed() as Int) * 2

                            @Around(Inner::class)
                            fun inner(pjp: ProceedingJoinPoint): Any? = (pjp.proceed() as Int) + 10
                        }

                        class Example {
                            @Chained
                            @Outer
                            @Inner
                            fun one(): Int = 1
                        }

                        fun runTest(): Int = Example().one()
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
        assertEquals(22, actual)
        assertEquals(emptyList<String>(), log)
    }

    @Test
    fun `an outer @Around that skips proceed keeps the inner @Around, the body and @After from running`() {
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

                        // Written on the target in this order, so Outer's advices come first
                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Outer

                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Inner

                        val executionLog = mutableListOf<String>()

                        @Aspect
                        object ChainAspect {
                            @Before(Chained::class)
                            fun doBefore(jp: JoinPoint) {
                                executionLog.add("before")
                            }

                            @Around(Outer::class)
                            fun outer(pjp: ProceedingJoinPoint): Any? {
                                executionLog.add("outer")
                                return "skipped"
                            }

                            @Around(Inner::class)
                            fun inner(pjp: ProceedingJoinPoint): Any? {
                                executionLog.add("inner")
                                return pjp.proceed()
                            }

                            @After(Chained::class)
                            fun doAfter(jp: JoinPoint) {
                                executionLog.add("after")
                            }
                        }

                        class Example {
                            @Chained
                            @Outer
                            @Inner
                            fun work(): String {
                                executionLog.add("body")
                                return "body"
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
        assertEquals("skipped", actual)
        assertEquals(listOf("before", "outer"), log)
    }

    @Test
    fun `an inner @Around that skips proceed hands its own result to the outer one`() {
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

                        // Written on the target in this order, so Outer's advices come first
                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Outer

                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Inner

                        val executionLog = mutableListOf<String>()

                        @Aspect
                        object ChainAspect {
                            @Around(Outer::class)
                            fun outer(pjp: ProceedingJoinPoint): Any? {
                                executionLog.add("outer-in")
                                return (pjp.proceed() as String).also { executionLog.add("outer-out:" + it) }
                            }

                            @Around(Inner::class)
                            fun inner(pjp: ProceedingJoinPoint): Any? {
                                executionLog.add("inner")
                                return "inner-value"
                            }

                            @After(Chained::class)
                            fun doAfter(jp: JoinPoint) {
                                executionLog.add("after")
                            }
                        }

                        class Example {
                            @Chained
                            @Outer
                            @Inner
                            fun work(): String {
                                executionLog.add("body")
                                return "body"
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
        assertEquals("inner-value", actual)
        assertEquals(listOf("outer-in", "inner", "outer-out:inner-value"), log)
    }

    @Test
    fun `an outer @Around that proceeds twice runs the inner @Around, the body and @After twice`() {
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

                        // Written on the target in this order, so Outer's advices come first
                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Outer

                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Inner

                        val executionLog = mutableListOf<String>()

                        @Aspect
                        object ChainAspect {
                            @Around(Outer::class)
                            fun outer(pjp: ProceedingJoinPoint): Any? {
                                pjp.proceed()
                                return pjp.proceed()
                            }

                            @Around(Inner::class)
                            fun inner(pjp: ProceedingJoinPoint): Any? {
                                executionLog.add("inner")
                                return pjp.proceed()
                            }

                            @After(Chained::class)
                            fun doAfter(jp: JoinPoint) {
                                executionLog.add("after")
                            }
                        }

                        class Example {
                            @Chained
                            @Outer
                            @Inner
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
        assertEquals(listOf("inner", "body", "after", "inner", "body", "after"), log)
    }

    @Test
    fun `an exception thrown by the inner @Around can be caught by the outer one`() {
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

                        // Written on the target in this order, so Outer's advices come first
                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Outer

                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Inner

                        val executionLog = mutableListOf<String>()

                        @Aspect
                        object ChainAspect {
                            @Around(Outer::class)
                            fun outer(pjp: ProceedingJoinPoint): Any? = try {
                                pjp.proceed()
                            } catch (e: IllegalStateException) {
                                executionLog.add("outer-caught:" + e.message)
                                "recovered"
                            }

                            @Around(Inner::class)
                            fun inner(pjp: ProceedingJoinPoint): Any? = throw IllegalStateException("inner")
                        }

                        class Example {
                            @Chained
                            @Outer
                            @Inner
                            fun work(): String {
                                executionLog.add("body")
                                return "body"
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
        assertEquals("recovered", actual)
        assertEquals(listOf("outer-caught:inner"), log)
    }

    @Test
    fun `a later @After still runs when an earlier one throws`() {
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

                        // Written on the target in this order, so Outer's advices come first
                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Outer

                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Inner

                        val executionLog = mutableListOf<String>()

                        @Aspect
                        object ChainAspect {
                            @After(Outer::class)
                            fun first(jp: JoinPoint) {
                                executionLog.add("first")
                                throw IllegalStateException("first")
                            }

                            @After(Inner::class)
                            fun second(jp: JoinPoint) {
                                executionLog.add("second")
                            }
                        }

                        class Example {
                            @Chained
                            @Outer
                            @Inner
                            fun work() {
                                executionLog.add("body")
                            }
                        }

                        // The message of the exception that reached the caller
                        fun runTest(): String? = try {
                            Example().work()
                            null
                        } catch (e: IllegalStateException) {
                            e.message
                        }
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
        assertEquals("first", actual)
        assertEquals(listOf("body", "first", "second"), log)
    }

    @Test
    fun `two @Arounds on a Unit-returning function do not throw`() {
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

                        // Written on the target in this order, so Outer's advices come first
                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Outer

                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Inner

                        val executionLog = mutableListOf<String>()

                        @Aspect
                        object ChainAspect {
                            @Around(Outer::class)
                            fun outer(pjp: ProceedingJoinPoint): Any? = pjp.proceed().also { executionLog.add("outer") }

                            @Around(Inner::class)
                            fun inner(pjp: ProceedingJoinPoint): Any? = pjp.proceed().also { executionLog.add("inner") }
                        }

                        class Example {
                            @Chained
                            @Outer
                            @Inner
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
        assertEquals(listOf("body", "inner", "outer"), log)
    }

    @Test
    fun `top-level function - args replaced by an outer @Around reach the inner one`() {
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

                        // Written on the target in this order, so Outer's advices come first
                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Outer

                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Inner

                        val executionLog = mutableListOf<String>()

                        @Aspect
                        object ChainAspect {
                            @Around(Outer::class)
                            fun outer(pjp: ProceedingJoinPoint): Any? = pjp.proceed(10, 20)

                            @Around(Inner::class)
                            fun inner(pjp: ProceedingJoinPoint): Any? = pjp.proceed(pjp.getArg<Int>("a") + 1, pjp.getArg<Int>("b"))
                        }

                        @Chained
                        @Outer
                        @Inner
                        fun sum(a: Int, b: Int): Int = a + b

                        fun runTest(): Int = sum(1, 2)
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
        assertEquals(31, actual)
        assertEquals(emptyList<String>(), log)
    }

    @Test
    fun `extension function - the receiver is kept while args change along the chain`() {
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

                        // Written on the target in this order, so Outer's advices come first
                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Outer

                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Inner

                        val executionLog = mutableListOf<String>()

                        @Aspect
                        object ChainAspect {
                            @Around(Outer::class)
                            fun outer(pjp: ProceedingJoinPoint): Any? = pjp.proceed("!!")

                            @Around(Inner::class)
                            fun inner(pjp: ProceedingJoinPoint): Any? = pjp.proceed(pjp.getArg<String>("suffix") + "?")
                        }

                        @Chained
                        @Outer
                        @Inner
                        fun String.shout(suffix: String): String = this + suffix

                        fun runTest(): String = "hi".shout(".")
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
        assertEquals("hi!!?", actual)
        assertEquals(emptyList<String>(), log)
    }

    @Test
    fun `suspend function - two @Arounds, @Before and @After keep their order and pass values along`() {
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

                        // Written on the target in this order, so Outer's advices come first
                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Outer

                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Inner

                        val executionLog = mutableListOf<String>()

                        @Aspect
                        object ChainAspect {
                            @Before(Chained::class)
                            fun doBefore(jp: JoinPoint) {
                                executionLog.add("before")
                            }

                            @Around(Outer::class)
                            suspend fun outer(pjp: SuspendProceedingJoinPoint): Any? {
                                executionLog.add("outer-in")
                                kotlinx.coroutines.delay(1)
                                return ((pjp.proceed(5) as Int) * 2).also { executionLog.add("outer-out") }
                            }

                            @Around(Inner::class)
                            suspend fun inner(pjp: SuspendProceedingJoinPoint): Any? {
                                executionLog.add("inner-in:" + pjp.getArg<Int>("value"))
                                kotlinx.coroutines.delay(1)
                                return ((pjp.proceed() as Int) + 1).also { executionLog.add("inner-out") }
                            }

                            @After(Chained::class)
                            fun doAfter(jp: JoinPoint) {
                                executionLog.add("after")
                            }
                        }

                        class Example {
                            @Chained
                            @Outer
                            @Inner
                            suspend fun load(value: Int): Int {
                                kotlinx.coroutines.delay(1)
                                executionLog.add("body:" + value)
                                return value
                            }
                        }

                        fun runTest(): Int = kotlinx.coroutines.runBlocking { Example().load(1) }
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
        assertEquals(12, actual)
        assertEquals(listOf("before", "outer-in", "inner-in:5", "body:5", "after", "inner-out", "outer-out"), log)
    }
}
