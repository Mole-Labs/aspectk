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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

@OptIn(ExperimentalCompilerApi::class)
class AroundLocalFunctionGenerationTest {
    @Test
    fun `@Around generates a private local function named after the original function`() {
        // given
        val result =
            compile(
                """
                import io.github.molelabs.aspectk.runtime.Aspect
                import io.github.molelabs.aspectk.runtime.Around
                import io.github.molelabs.aspectk.runtime.ProceedingJoinPoint

                @Target(AnnotationTarget.FUNCTION)
                annotation class Intercepted

                @Target(AnnotationTarget.FUNCTION)
                annotation class Intercepted2

                @Aspect
                object PassThroughAspect {
                    @Around(Intercepted::class)
                    fun doAround(pjp: ProceedingJoinPoint): Any? = pjp.proceed()

                    @Around(Intercepted2::class)
                    fun doAround2(pjp: ProceedingJoinPoint): Any? = pjp.proceed()
                }

                class Test {
                    @Intercepted
                    @Intercepted2
                    fun work() { }
                }
                """,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // when - on JVM level, local function is compiled to static method
        val testClass = result.classLoader.loadClass("Test")
        val localFn = testClass.declaredMethods.firstOrNull { it.name == $$$"work$_work" }

        // then — $work must exist as a private method on Test
        assertNotNull(localFn, "Expected local function '\$work' to be generated on class Test")
    }

    @Test
    fun `@Around local function mirrors the value parameters of the original function`() {
        // given
        val result =
            compile(
                """
                import io.github.molelabs.aspectk.runtime.Aspect
                import io.github.molelabs.aspectk.runtime.Around
                import io.github.molelabs.aspectk.runtime.ProceedingJoinPoint

                @Target(AnnotationTarget.FUNCTION)
                annotation class Intercepted

                @Aspect
                object PassThroughAspect {
                    @Around(Intercepted::class)
                    fun doAround(pjp: ProceedingJoinPoint): Any? = pjp.proceed()
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

        // then — $compute(x: Int, label: String) mirrors the value parameters of compute()
        // Note: the local function does not include a `this` parameter
        assertAll(
            { assertEquals(2, localFn.parameterCount) },
            { assertEquals(Int::class.javaPrimitiveType, localFn.parameterTypes[0]) },
            { assertEquals(String::class.java, localFn.parameterTypes[1]) },
        )
    }

    @Test
    fun `@Around local function preserves the return type of the original function`() {
        // given
        val result =
            compile(
                """
                import io.github.molelabs.aspectk.runtime.Aspect
                import io.github.molelabs.aspectk.runtime.Around
                import io.github.molelabs.aspectk.runtime.ProceedingJoinPoint

                @Target(AnnotationTarget.FUNCTION)
                annotation class Intercepted

                @Aspect
                object PassThroughAspect {
                    @Around(Intercepted::class)
                    fun doAround(pjp: ProceedingJoinPoint): Any? = pjp.proceed()
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

        // then — $greet() must return String, matching the original function's return type
        assertEquals(String::class.java, localFn.returnType)
    }

    @Test
    fun `local function is created only once when multiple @Around annotations are present`() {
        // given
        val result =
            compile(
                """
                import io.github.molelabs.aspectk.runtime.Aspect
                import io.github.molelabs.aspectk.runtime.Around
                import io.github.molelabs.aspectk.runtime.ProceedingJoinPoint

                @Target(AnnotationTarget.FUNCTION)
                annotation class Intercepted1

                 @Target(AnnotationTarget.FUNCTION)
                annotation class Intercepted2

                @Aspect
                object PassThroughAspect {
                    @Around(Intercepted1::class)
                    fun doAround1(pjp: ProceedingJoinPoint): Any? = pjp.proceed()

                    @Around(Intercepted2::class)
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
    fun `local function is created only once when multiple @Around and @After annotations are present`() {
        // given
        val result =
            compile(
                """
                import io.github.molelabs.aspectk.runtime.Aspect
                import io.github.molelabs.aspectk.runtime.Around
                import io.github.molelabs.aspectk.runtime.After
                import io.github.molelabs.aspectk.runtime.ProceedingJoinPoint

                @Target(AnnotationTarget.FUNCTION)
                annotation class Intercepted1

                 @Target(AnnotationTarget.FUNCTION)
                annotation class Intercepted2

                @Aspect
                object PassThroughAspect {
                    @Around(Intercepted1::class)
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
    fun `@Around on a tailrec function keeps a constant stack depth`() {
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

                        fun runTest(): Int = countDown(100_000)
                        """,
                    ),
                ),
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // when
        val runTestKt = result.classLoader.loadClass("RunTestKt")
        val actual = runTestKt.getMethod("runTest").invoke(null)

        // then
        assertEquals(0, actual)
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
}
