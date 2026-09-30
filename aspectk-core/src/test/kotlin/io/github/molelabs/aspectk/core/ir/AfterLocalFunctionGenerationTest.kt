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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

@OptIn(ExperimentalCompilerApi::class)
class AfterLocalFunctionGenerationTest {
    @Test
    fun `@After generates a private local function named after the original function`() {
        // given
        val result =
            compile(
                """
                import io.github.molelabs.aspectk.runtime.Aspect
                import io.github.molelabs.aspectk.runtime.After
                import io.github.molelabs.aspectk.runtime.JoinPoint

                @Target(AnnotationTarget.FUNCTION)
                annotation class Intercepted

                @Aspect
                object LogAspect {
                    @After(Intercepted::class)
                    fun doAfter(jp: JoinPoint) { }
                }

                class Test {
                    @Intercepted
                    fun work() { }
                }
                """,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // when - on JVM level, local function is compiled to a static method with mangled name
        val testClass = result.classLoader.loadClass("Test")
        val localFn = testClass.declaredMethods.firstOrNull { it.name == $$$"work$_work" }

        // then — $$work must exist as a private method on Test (try-catch body wrapper)
        assertNotNull(localFn, "Expected local function '\$\$work' to be generated on class Test")
    }

    @Test
    fun `@After local function mirrors the value parameters of the original function`() {
        // given
        val result =
            compile(
                """
                import io.github.molelabs.aspectk.runtime.Aspect
                import io.github.molelabs.aspectk.runtime.After
                import io.github.molelabs.aspectk.runtime.JoinPoint

                @Target(AnnotationTarget.FUNCTION)
                annotation class Intercepted

                @Aspect
                object LogAspect {
                    @After(Intercepted::class)
                    fun doAfter(jp: JoinPoint) { }
                }

                class Test {
                    @Intercepted
                    fun compute(x: Int, label: String) { }
                }
                """,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // when
        val testClass = result.classLoader.loadClass("Test")
        val localFn = testClass.declaredMethods.first { it.name == "compute\$_compute" }

        // then — $$compute(x: Int, label: String) mirrors the value parameters of compute()
        assertAll(
            { assertEquals(2, localFn.parameterCount) },
            { assertEquals(Int::class.javaPrimitiveType, localFn.parameterTypes[0]) },
            { assertEquals(String::class.java, localFn.parameterTypes[1]) },
        )
    }

    @Test
    fun `@After local function preserves the return type of the original function`() {
        // given
        val result =
            compile(
                """
                import io.github.molelabs.aspectk.runtime.Aspect
                import io.github.molelabs.aspectk.runtime.After
                import io.github.molelabs.aspectk.runtime.JoinPoint

                @Target(AnnotationTarget.FUNCTION)
                annotation class Intercepted

                @Aspect
                object LogAspect {
                    @After(Intercepted::class)
                    fun doAfter(jp: JoinPoint) { }
                }

                class Test {
                    @Intercepted
                    fun greet(): String = "hello"
                }
                """,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // when
        val testClass = result.classLoader.loadClass("Test")
        val localFn = testClass.declaredMethods.first { it.name == "greet\$_greet" }

        // then — $$greet() must return String, matching the original function's return type
        assertEquals(String::class.java, localFn.returnType)
    }

    @Test
    fun `local function is created only once when multiple @After annotations are present`() {
        // given
        val result =
            compile(
                """
                import io.github.molelabs.aspectk.runtime.Aspect
                import io.github.molelabs.aspectk.runtime.After
                import io.github.molelabs.aspectk.runtime.ProceedingJoinPoint

                @Target(AnnotationTarget.FUNCTION)
                annotation class Intercepted1

                 @Target(AnnotationTarget.FUNCTION)
                annotation class Intercepted2

                @Aspect
                object PassThroughAspect {
                    @After(Intercepted1::class)
                    fun doAround1(pjp: ProceedingJoinPoint): Any? = pjp.proceed()

                    @After(Intercepted2::class)
                    fun doAround2(pjp: ProceedingJoinPoint): Any? = pjp.proceed()
                }

                class Test {
                    @Intercepted1
                    @Intercepted2
                    fun greet(): String = "hello"
                }
                """.trimIndent(),
            )

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // when
        val testClass = result.classLoader.loadClass("Test")
        val localFn = testClass.declaredMethods.filter { it.name == "greet\$_greet" }

        // then — $greet() is created only once
        assertEquals(1, localFn.size)
    }

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
}
