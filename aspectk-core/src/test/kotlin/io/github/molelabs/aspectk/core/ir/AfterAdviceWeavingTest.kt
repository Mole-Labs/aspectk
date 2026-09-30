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

@OptIn(ExperimentalCompilerApi::class)
class AfterAdviceWeavingTest {
    @Test
    fun `@After on a reified inline function keeps it inlinable`() {
        // given
        val result =
            compile(
                listOf(
                    SourceFile.kotlin(
                        "RunTest.kt",
                        """
                        import io.github.molelabs.aspectk.runtime.After
                        import io.github.molelabs.aspectk.runtime.Aspect
                        import io.github.molelabs.aspectk.runtime.JoinPoint

                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Logged

                        val executionLog = mutableListOf<String>()

                        @Aspect
                        object LoggingAspect {
                            @After(Logged::class)
                            fun doAfter(joinPoint: JoinPoint) {
                                executionLog.add("after")
                            }
                        }

                        @Logged
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
        assertEquals(listOf("after"), log)
    }

    @Test
    fun `@After on a tailrec function runs for every recursive call`() {
        // given
        val result =
            compile(
                listOf(
                    SourceFile.kotlin(
                        "RunTest.kt",
                        """
                        import io.github.molelabs.aspectk.runtime.After
                        import io.github.molelabs.aspectk.runtime.Aspect
                        import io.github.molelabs.aspectk.runtime.JoinPoint

                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Logged

                        val executionLog = mutableListOf<String>()

                        @Aspect
                        object LoggingAspect {
                            @After(Logged::class)
                            fun doAfter(joinPoint: JoinPoint) {
                                executionLog.add("after")
                            }
                        }

                        @Logged
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
        assertEquals(List(4) { "after" }, log)
    }

    @Test
    fun `@After on an inline function runs with the inlined body`() {
        // given
        val result =
            compile(
                listOf(
                    SourceFile.kotlin(
                        "RunTest.kt",
                        """
                        import io.github.molelabs.aspectk.runtime.After
                        import io.github.molelabs.aspectk.runtime.Aspect
                        import io.github.molelabs.aspectk.runtime.JoinPoint

                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Logged

                        val executionLog = mutableListOf<String>()

                        @Aspect
                        object LoggingAspect {
                            @After(Logged::class)
                            fun doAfter(joinPoint: JoinPoint) {
                                executionLog.add("after")
                            }
                        }

                        @Logged
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
        assertEquals(listOf("after"), log)
    }

    @Test
    fun `@After on an inline function with a lambda parameter keeps the lambda inlinable`() {
        // given
        val result =
            compile(
                listOf(
                    SourceFile.kotlin(
                        "RunTest.kt",
                        """
                        import io.github.molelabs.aspectk.runtime.After
                        import io.github.molelabs.aspectk.runtime.Aspect
                        import io.github.molelabs.aspectk.runtime.JoinPoint

                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Logged

                        val executionLog = mutableListOf<String>()

                        @Aspect
                        object LoggingAspect {
                            @After(Logged::class)
                            fun doAfter(joinPoint: JoinPoint) {
                                executionLog.add("after")
                            }
                        }

                        @Logged
                        inline fun <T> measure(block: () -> T): T = block()

                        fun runTest(): String = measure { "x" }
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
        assertEquals("x", actual)
        assertEquals(listOf("after"), log)
    }

    // Returning from findFirstAbove() inside the lambda only works while the lambda is inlined into it
    @Test
    fun `@After on an inline function keeps non-local returns from its lambda`() {
        // given
        val result =
            compile(
                listOf(
                    SourceFile.kotlin(
                        "RunTest.kt",
                        """
                        import io.github.molelabs.aspectk.runtime.After
                        import io.github.molelabs.aspectk.runtime.Aspect
                        import io.github.molelabs.aspectk.runtime.JoinPoint

                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Logged

                        val executionLog = mutableListOf<String>()

                        @Aspect
                        object LoggingAspect {
                            @After(Logged::class)
                            fun doAfter(joinPoint: JoinPoint) {
                                executionLog.add("after")
                            }
                        }

                        @Logged
                        inline fun forEachOf(values: List<Int>, block: (Int) -> Unit) {
                            for (value in values) block(value)
                        }

                        fun findFirstAbove(values: List<Int>, limit: Int): Int {
                            forEachOf(values) { if (it > limit) return it }
                            return -1
                        }

                        fun runTest(): Int = findFirstAbove(listOf(1, 2, 3), 1)
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
        assertEquals(2, actual)
        assertEquals(listOf("after"), log)
    }

    @Test
    fun `@After an early return in the middle of the body returns from the function`() {
        // given
        val result =
            compile(
                listOf(
                    SourceFile.kotlin(
                        "RunTest.kt",
                        """
                        import io.github.molelabs.aspectk.runtime.After
                        import io.github.molelabs.aspectk.runtime.Aspect
                        import io.github.molelabs.aspectk.runtime.JoinPoint

                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Intercepted

                        val executionLog = mutableListOf<String>()

                        @Aspect
                        object LoggingAspect {
                            @After(Intercepted::class)
                            fun doAfter(joinPoint: JoinPoint) {
                                executionLog.add("after")
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
        assertEquals(List(4) { "after" }, log)
    }

    // The return inside forEach targets the woven function, not the lambda
    @Test
    fun `@After a non-local return from a lambda in the body returns from the function`() {
        // given
        val result =
            compile(
                listOf(
                    SourceFile.kotlin(
                        "RunTest.kt",
                        """
                        import io.github.molelabs.aspectk.runtime.After
                        import io.github.molelabs.aspectk.runtime.Aspect
                        import io.github.molelabs.aspectk.runtime.JoinPoint

                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Intercepted

                        val executionLog = mutableListOf<String>()

                        @Aspect
                        object LoggingAspect {
                            @After(Intercepted::class)
                            fun doAfter(joinPoint: JoinPoint) {
                                executionLog.add("after")
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
        assertEquals(List(4) { "after" }, log)
    }

    @Test
    fun `@After a labeled return from a lambda in the body only leaves the lambda`() {
        // given
        val result =
            compile(
                listOf(
                    SourceFile.kotlin(
                        "RunTest.kt",
                        """
                        import io.github.molelabs.aspectk.runtime.After
                        import io.github.molelabs.aspectk.runtime.Aspect
                        import io.github.molelabs.aspectk.runtime.JoinPoint

                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Intercepted

                        val executionLog = mutableListOf<String>()

                        @Aspect
                        object LoggingAspect {
                            @After(Intercepted::class)
                            fun doAfter(joinPoint: JoinPoint) {
                                executionLog.add("after")
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
        assertEquals(List(2) { "after" }, log)
    }

    @Test
    fun `@After a return inside a local function in the body only leaves the local function`() {
        // given
        val result =
            compile(
                listOf(
                    SourceFile.kotlin(
                        "RunTest.kt",
                        """
                        import io.github.molelabs.aspectk.runtime.After
                        import io.github.molelabs.aspectk.runtime.Aspect
                        import io.github.molelabs.aspectk.runtime.JoinPoint

                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Intercepted

                        val executionLog = mutableListOf<String>()

                        @Aspect
                        object LoggingAspect {
                            @After(Intercepted::class)
                            fun doAfter(joinPoint: JoinPoint) {
                                executionLog.add("after")
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
        assertEquals(List(1) { "after" }, log)
    }
}
