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

// How several advices on the same function chain together.
//
// CH: what flows through the chain — arguments going in, the result coming out, a skipped or
// repeated proceed(), and exceptions.
// ORD: how the advices are ordered —
//   @Before outermost, runs once ahead of everything else
//   @Around nest in declaration order: the first one is the outermost, each later one inside it
//   @After wraps only the body, inside every @Around, in declaration order
// The declaration order only matters between advices of the same kind.
@OptIn(ExperimentalCompilerApi::class)
class AdviceChainingTest {
    // CH-1: 바깥 @Around가 바꾼 인자는 안쪽 @Around, 본문, @After에 보이고 @Before에는 원래 인자가 보인다
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

                        val executionLog = mutableListOf<String>()

                        @Aspect
                        object ChainAspect {
                            @Before(Chained::class)
                            fun doBefore(jp: JoinPoint) {
                                executionLog.add("before:" + jp.getArg<String>("name"))
                            }

                            @Around(Chained::class)
                            fun outer(pjp: ProceedingJoinPoint): Any? {
                                executionLog.add("outer:" + pjp.getArg<String>("name"))
                                return pjp.proceed("from-outer")
                            }

                            @Around(Chained::class)
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

                        val executionLog = mutableListOf<String>()

                        @Aspect
                        object ChainAspect {
                            @Around(Chained::class)
                            fun outer(pjp: ProceedingJoinPoint): Any? = (pjp.proceed() as Int) * 2

                            @Around(Chained::class)
                            fun inner(pjp: ProceedingJoinPoint): Any? = (pjp.proceed() as Int) + 10
                        }

                        class Example {
                            @Chained
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

    // CH-3: 바깥 @Around가 proceed를 건너뛰면 안쪽 @Around, 본문, @After 모두 실행되지 않는다
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

                        val executionLog = mutableListOf<String>()

                        @Aspect
                        object ChainAspect {
                            @Before(Chained::class)
                            fun doBefore(jp: JoinPoint) {
                                executionLog.add("before")
                            }

                            @Around(Chained::class)
                            fun outer(pjp: ProceedingJoinPoint): Any? {
                                executionLog.add("outer")
                                return "skipped"
                            }

                            @Around(Chained::class)
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

                        val executionLog = mutableListOf<String>()

                        @Aspect
                        object ChainAspect {
                            @Around(Chained::class)
                            fun outer(pjp: ProceedingJoinPoint): Any? {
                                executionLog.add("outer-in")
                                return (pjp.proceed() as String).also { executionLog.add("outer-out:" + it) }
                            }

                            @Around(Chained::class)
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

                        val executionLog = mutableListOf<String>()

                        @Aspect
                        object ChainAspect {
                            @Around(Chained::class)
                            fun outer(pjp: ProceedingJoinPoint): Any? {
                                pjp.proceed()
                                return pjp.proceed()
                            }

                            @Around(Chained::class)
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
        val actual = runTestKt.getMethod("runTest").invoke(null)
        val log = runTestKt.getDeclaredField("executionLog").apply { isAccessible = true }.get(null) as List<*>

        // then
        assertEquals(listOf("inner", "body", "after", "inner", "body", "after"), log)
    }

    // CH-6: 안쪽 @Around가 던진 예외를 바깥 @Around가 잡을 수 있다
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

                        val executionLog = mutableListOf<String>()

                        @Aspect
                        object ChainAspect {
                            @Around(Chained::class)
                            fun outer(pjp: ProceedingJoinPoint): Any? = try {
                                pjp.proceed()
                            } catch (e: IllegalStateException) {
                                executionLog.add("outer-caught:" + e.message)
                                "recovered"
                            }

                            @Around(Chained::class)
                            fun inner(pjp: ProceedingJoinPoint): Any? = throw IllegalStateException("inner")
                        }

                        class Example {
                            @Chained
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

                        val executionLog = mutableListOf<String>()

                        @Aspect
                        object ChainAspect {
                            @After(Chained::class)
                            fun first(jp: JoinPoint) {
                                executionLog.add("first")
                                throw IllegalStateException("first")
                            }

                            @After(Chained::class)
                            fun second(jp: JoinPoint) {
                                executionLog.add("second")
                            }
                        }

                        class Example {
                            @Chained
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

                        val executionLog = mutableListOf<String>()

                        @Aspect
                        object ChainAspect {
                            @Around(Chained::class)
                            fun outer(pjp: ProceedingJoinPoint): Any? = pjp.proceed().also { executionLog.add("outer") }

                            @Around(Chained::class)
                            fun inner(pjp: ProceedingJoinPoint): Any? = pjp.proceed().also { executionLog.add("inner") }
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

                        val executionLog = mutableListOf<String>()

                        @Aspect
                        object ChainAspect {
                            @Around(Chained::class)
                            fun outer(pjp: ProceedingJoinPoint): Any? = pjp.proceed(10, 20)

                            @Around(Chained::class)
                            fun inner(pjp: ProceedingJoinPoint): Any? = pjp.proceed(pjp.getArg<Int>("a") + 1, pjp.getArg<Int>("b"))
                        }

                        @Chained
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

                        val executionLog = mutableListOf<String>()

                        @Aspect
                        object ChainAspect {
                            @Around(Chained::class)
                            fun outer(pjp: ProceedingJoinPoint): Any? = pjp.proceed("!!")

                            @Around(Chained::class)
                            fun inner(pjp: ProceedingJoinPoint): Any? = pjp.proceed(pjp.getArg<String>("suffix") + "?")
                        }

                        @Chained
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

                        val executionLog = mutableListOf<String>()

                        @Aspect
                        object ChainAspect {
                            @Before(Chained::class)
                            fun doBefore(jp: JoinPoint) {
                                executionLog.add("before")
                            }

                            @Around(Chained::class)
                            suspend fun outer(pjp: SuspendProceedingJoinPoint): Any? {
                                executionLog.add("outer-in")
                                kotlinx.coroutines.delay(1)
                                return ((pjp.proceed(5) as Int) * 2).also { executionLog.add("outer-out") }
                            }

                            @Around(Chained::class)
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
        val actual = runTestKt.getMethod("runTest").invoke(null)
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
        val actual = runTestKt.getMethod("runTest").invoke(null)
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
        val actual = runTestKt.getMethod("runTest").invoke(null)
        val log = runTestKt.getDeclaredField("executionLog").apply { isAccessible = true }.get(null) as List<*>

        // then
        assertEquals(listOf("before", "around-in", "body", "after", "around-out"), log)
    }

    @Test
    fun `mixed - two of each kind interleaved keep declaration order within each kind`() {
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
                            fun after1(jp: JoinPoint) {
                                executionLog.add("after1")
                            }

                            @Around(Chained::class)
                            fun around1(pjp: ProceedingJoinPoint): Any? {
                                executionLog.add("around1-in")
                                return pjp.proceed().also { executionLog.add("around1-out") }
                            }

                            @Before(Chained::class)
                            fun before1(jp: JoinPoint) {
                                executionLog.add("before1")
                            }

                            @Around(Chained::class)
                            fun around2(pjp: ProceedingJoinPoint): Any? {
                                executionLog.add("around2-in")
                                return pjp.proceed().also { executionLog.add("around2-out") }
                            }

                            @After(Chained::class)
                            fun after2(jp: JoinPoint) {
                                executionLog.add("after2")
                            }

                            @Before(Chained::class)
                            fun before2(jp: JoinPoint) {
                                executionLog.add("before2")
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
        val actual = runTestKt.getMethod("runTest").invoke(null)
        val log = runTestKt.getDeclaredField("executionLog").apply { isAccessible = true }.get(null) as List<*>

        // then
        assertEquals(
            listOf("before1", "before2", "around1-in", "around2-in", "body", "after1", "after2", "around2-out", "around1-out"),
            log,
        )
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

    @Test
    fun `cross-aspect - @Arounds of two aspects nest in the order the aspects are declared`() {
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
                        object AspectA {
                            @Around(Chained::class)
                            fun doAround(pjp: ProceedingJoinPoint): Any? {
                                executionLog.add("a-in")
                                return pjp.proceed().also { executionLog.add("a-out") }
                            }
                        }

                        @Aspect
                        object AspectB {
                            @Around(Chained::class)
                            fun doAround(pjp: ProceedingJoinPoint): Any? {
                                executionLog.add("b-in")
                                return pjp.proceed().also { executionLog.add("b-out") }
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
        assertEquals(listOf("a-in", "b-in", "body", "b-out", "a-out"), log)
    }

    @Test
    fun `cross-aspect - @Afters of two aspects execute in the order the aspects are declared`() {
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
                        object AspectA {
                            @After(Chained::class)
                            fun doAfter(jp: JoinPoint) {
                                executionLog.add("a")
                            }
                        }

                        @Aspect
                        object AspectB {
                            @After(Chained::class)
                            fun doAfter(jp: JoinPoint) {
                                executionLog.add("b")
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
        assertEquals(listOf("body", "a", "b"), log)
    }

    @Test
    fun `three @Arounds nest in declaration order`() {
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
                            fun first(pjp: ProceedingJoinPoint): Any? {
                                executionLog.add("first-in")
                                return pjp.proceed().also { executionLog.add("first-out") }
                            }

                            @Around(Chained::class)
                            fun second(pjp: ProceedingJoinPoint): Any? {
                                executionLog.add("second-in")
                                return pjp.proceed().also { executionLog.add("second-out") }
                            }

                            @Around(Chained::class)
                            fun third(pjp: ProceedingJoinPoint): Any? {
                                executionLog.add("third-in")
                                return pjp.proceed().also { executionLog.add("third-out") }
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
        assertEquals(listOf("first-in", "second-in", "third-in", "body", "third-out", "second-out", "first-out"), log)
    }
}
