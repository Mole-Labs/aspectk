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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class AroundAdviceWeavingTest {
    @Test
    fun `@Around on a reified inline function keeps it inlinable`() {
        // given
        val result =
            compile(
                listOf(
                    SourceFile.kotlin(
                        "RunTest.kt",
                        """
                        import io.github.molelabs.aspectk.runtime.Around
                        import io.github.molelabs.aspectk.runtime.Aspect
                        import io.github.molelabs.aspectk.runtime.ProceedingJoinPoint

                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Intercepted

                        val executionLog = mutableListOf<String>()

                        @Aspect
                        object PassThroughAspect {
                            @Around(Intercepted::class)
                            fun doAround(pjp: ProceedingJoinPoint): Any? {
                                executionLog.add("around")
                                return pjp.proceed()
                            }
                        }

                        @Intercepted
                        inline fun <reified T> typeName(): String = T::class.simpleName!!

                        fun runTest(): String = typeName<String>()
                        """,
                    ),
                ),
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // when
        val runTestKt = result.classLoader.loadClass("RunTestKt")
        val actual = runTestKt.getMethod("runTest").invoke(null)
        val log = runTestKt.getDeclaredField("executionLog").apply { isAccessible = true }.get(null) as List<*>

        // then
        assertEquals("String", actual)
        assertEquals(listOf("around"), log)
    }

    @Test
    fun `@Around on a tailrec function runs for every recursive call`() {
        // given
        val result =
            compile(
                listOf(
                    SourceFile.kotlin(
                        "RunTest.kt",
                        """
                        import io.github.molelabs.aspectk.runtime.Around
                        import io.github.molelabs.aspectk.runtime.Aspect
                        import io.github.molelabs.aspectk.runtime.ProceedingJoinPoint

                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Intercepted

                        val executionLog = mutableListOf<String>()

                        @Aspect
                        object PassThroughAspect {
                            @Around(Intercepted::class)
                            fun doAround(pjp: ProceedingJoinPoint): Any? {
                                executionLog.add("around")
                                return pjp.proceed()
                            }
                        }

                        @Intercepted
                        tailrec fun countDown(n: Int): Int = if (n == 0) 0 else countDown(n - 1)

                        fun runTest(): Int = countDown(3)
                        """,
                    ),
                ),
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // when
        val runTestKt = result.classLoader.loadClass("RunTestKt")
        val actual = runTestKt.getMethod("runTest").invoke(null)
        val log = runTestKt.getDeclaredField("executionLog").apply { isAccessible = true }.get(null) as List<*>

        // then
        assertEquals(0, actual)
        assertEquals(List(4) { "around" }, log)
    }

    @Test
    fun `@Around on an inline function runs with the inlined body`() {
        // given
        val result =
            compile(
                listOf(
                    SourceFile.kotlin(
                        "RunTest.kt",
                        """
                        import io.github.molelabs.aspectk.runtime.Around
                        import io.github.molelabs.aspectk.runtime.Aspect
                        import io.github.molelabs.aspectk.runtime.ProceedingJoinPoint

                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Intercepted

                        val executionLog = mutableListOf<String>()

                        @Aspect
                        object PassThroughAspect {
                            @Around(Intercepted::class)
                            fun doAround(pjp: ProceedingJoinPoint): Any? {
                                executionLog.add("around")
                                return pjp.proceed()
                            }
                        }

                        @Intercepted
                        inline fun twice(x: Int): Int = x * 2

                        fun runTest(): Int = twice(3)
                        """,
                    ),
                ),
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // when
        val runTestKt = result.classLoader.loadClass("RunTestKt")
        val actual = runTestKt.getMethod("runTest").invoke(null)
        val log = runTestKt.getDeclaredField("executionLog").apply { isAccessible = true }.get(null) as List<*>

        // then
        assertEquals(6, actual)
        assertEquals(listOf("around"), log)
    }

    @Test
    fun `@Around replaces a noinline lambda parameter of an inline function through proceed`() {
        // given
        val result =
            compile(
                listOf(
                    SourceFile.kotlin(
                        "RunTest.kt",
                        """
                        import io.github.molelabs.aspectk.runtime.Around
                        import io.github.molelabs.aspectk.runtime.Aspect
                        import io.github.molelabs.aspectk.runtime.ProceedingJoinPoint

                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Intercepted

                        val executionLog = mutableListOf<Any?>()

                        @Aspect
                        object ReplacingAspect {
                            @Around(Intercepted::class)
                            fun doAround(pjp: ProceedingJoinPoint): Any? {
                                executionLog.addAll(pjp.args)
                                return pjp.proceed("changed", { "replaced" })
                            }
                        }

                        @Intercepted
                        inline fun describe(label: String, noinline block: () -> String): String = label + ":" + block()

                        fun runTest(): String = describe("original") { "original" }
                        """,
                    ),
                ),
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // when
        val runTestKt = result.classLoader.loadClass("RunTestKt")
        val actual = runTestKt.getMethod("runTest").invoke(null)
        val seenArgs = runTestKt.getDeclaredField("executionLog").apply { isAccessible = true }.get(null) as List<*>

        // then: the lambda is in args as a value, and the one passed to proceed replaced it
        assertEquals("changed:replaced", actual)
        assertEquals("original", seenArgs[0])
        assertTrue(seenArgs[1] is Function0<*>)
    }

    @Test
    fun `@Around an early return in the middle of the body returns from the function`() {
        // given
        val result =
            compile(
                listOf(
                    SourceFile.kotlin(
                        "RunTest.kt",
                        """
                        import io.github.molelabs.aspectk.runtime.Around
                        import io.github.molelabs.aspectk.runtime.Aspect
                        import io.github.molelabs.aspectk.runtime.ProceedingJoinPoint

                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Intercepted

                        val executionLog = mutableListOf<String>()

                        @Aspect
                        object PassThroughAspect {
                            @Around(Intercepted::class)
                            fun doAround(pjp: ProceedingJoinPoint): Any? {
                                executionLog.add("around")
                                return pjp.proceed()
                            }
                        }

                        @Intercepted
                        fun classify(x: Int): String {
                            if (x < 0) return "negative"
                            val doubled = x * 2
                            return "positive:" + doubled
                        }

                        @Intercepted
                        inline fun classifyInline(x: Int): String {
                            if (x < 0) return "negative"
                            val doubled = x * 2
                            return "positive:" + doubled
                        }

                        fun runTest(): String = listOf(classify(-1), classify(2), classifyInline(-1), classifyInline(2)).joinToString()
                        """,
                    ),
                ),
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // when
        val runTestKt = result.classLoader.loadClass("RunTestKt")
        val actual = runTestKt.getMethod("runTest").invoke(null)
        val log = runTestKt.getDeclaredField("executionLog").apply { isAccessible = true }.get(null) as List<*>

        // then
        assertEquals("negative, positive:4, negative, positive:4", actual)
        assertEquals(List(4) { "around" }, log)
    }

    // The return inside forEach targets the woven function, not the lambda
    @Test
    fun `@Around a non-local return from a lambda in the body returns from the function`() {
        // given
        val result =
            compile(
                listOf(
                    SourceFile.kotlin(
                        "RunTest.kt",
                        """
                        import io.github.molelabs.aspectk.runtime.Around
                        import io.github.molelabs.aspectk.runtime.Aspect
                        import io.github.molelabs.aspectk.runtime.ProceedingJoinPoint

                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Intercepted

                        val executionLog = mutableListOf<String>()

                        @Aspect
                        object PassThroughAspect {
                            @Around(Intercepted::class)
                            fun doAround(pjp: ProceedingJoinPoint): Any? {
                                executionLog.add("around")
                                return pjp.proceed()
                            }
                        }

                        @Intercepted
                        fun firstAbove(values: List<Int>, limit: Int): Int {
                            values.forEach { if (it > limit) return it }
                            return -1
                        }

                        @Intercepted
                        inline fun firstAboveInline(values: List<Int>, limit: Int): Int {
                            values.forEach { if (it > limit) return it }
                            return -1
                        }

                        fun runTest(): String =
                            listOf(firstAbove(listOf(1, 5, 9), 3), firstAbove(listOf(1), 3), firstAboveInline(listOf(1, 5, 9), 3), firstAboveInline(listOf(1), 3)).joinToString()
                        """,
                    ),
                ),
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // when
        val runTestKt = result.classLoader.loadClass("RunTestKt")
        val actual = runTestKt.getMethod("runTest").invoke(null)
        val log = runTestKt.getDeclaredField("executionLog").apply { isAccessible = true }.get(null) as List<*>

        // then
        assertEquals("5, -1, 5, -1", actual)
        assertEquals(List(4) { "around" }, log)
    }

    // return@map targets the lambda, so moving the body must not retarget it to the function
    @Test
    fun `@Around a labeled return from a lambda in the body only leaves the lambda`() {
        // given
        val result =
            compile(
                listOf(
                    SourceFile.kotlin(
                        "RunTest.kt",
                        """
                        import io.github.molelabs.aspectk.runtime.Around
                        import io.github.molelabs.aspectk.runtime.Aspect
                        import io.github.molelabs.aspectk.runtime.ProceedingJoinPoint

                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Intercepted

                        val executionLog = mutableListOf<String>()

                        @Aspect
                        object PassThroughAspect {
                            @Around(Intercepted::class)
                            fun doAround(pjp: ProceedingJoinPoint): Any? {
                                executionLog.add("around")
                                return pjp.proceed()
                            }
                        }

                        @Intercepted
                        fun doubled(values: List<Int>): List<Int> = values.map { if (it < 0) return@map 0; it * 2 }

                        @Intercepted
                        inline fun doubledInline(values: List<Int>): List<Int> = values.map { if (it < 0) return@map 0; it * 2 }

                        fun runTest(): String = (doubled(listOf(-1, 2)) + doubledInline(listOf(-1, 2))).joinToString()
                        """,
                    ),
                ),
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // when
        val runTestKt = result.classLoader.loadClass("RunTestKt")
        val actual = runTestKt.getMethod("runTest").invoke(null)
        val log = runTestKt.getDeclaredField("executionLog").apply { isAccessible = true }.get(null) as List<*>

        // then
        assertEquals("0, 4, 0, 4", actual)
        assertEquals(List(2) { "around" }, log)
    }

    // Kotlin has no local functions in inline functions, so only a regular target is covered
    @Test
    fun `@Around a return inside a local function in the body only leaves the local function`() {
        // given
        val result =
            compile(
                listOf(
                    SourceFile.kotlin(
                        "RunTest.kt",
                        """
                        import io.github.molelabs.aspectk.runtime.Around
                        import io.github.molelabs.aspectk.runtime.Aspect
                        import io.github.molelabs.aspectk.runtime.ProceedingJoinPoint

                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Intercepted

                        val executionLog = mutableListOf<String>()

                        @Aspect
                        object PassThroughAspect {
                            @Around(Intercepted::class)
                            fun doAround(pjp: ProceedingJoinPoint): Any? {
                                executionLog.add("around")
                                return pjp.proceed()
                            }
                        }

                        @Intercepted
                        fun sumOfSquares(values: List<Int>): Int {
                            fun square(x: Int): Int {
                                return x * x
                            }
                            var total = 0
                            for (value in values) total += square(value)
                            return total
                        }

                        fun runTest(): Int = sumOfSquares(listOf(1, 2, 3))
                        """,
                    ),
                ),
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // when
        val runTestKt = result.classLoader.loadClass("RunTestKt")
        val actual = runTestKt.getMethod("runTest").invoke(null)
        val log = runTestKt.getDeclaredField("executionLog").apply { isAccessible = true }.get(null) as List<*>

        // then
        assertEquals(14, actual)
        assertEquals(List(1) { "around" }, log)
    }
}
