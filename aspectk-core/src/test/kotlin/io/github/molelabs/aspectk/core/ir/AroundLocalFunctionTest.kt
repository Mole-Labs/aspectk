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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import java.lang.reflect.InvocationTargetException

@OptIn(ExperimentalCompilerApi::class)
@Suppress("UNCHECKED_CAST")
class AroundLocalFunctionTest {
    @Test
    fun `@After advice executes after target function body`() {
        // given
        val result =
            compile(
                listOf(
                    SourceFile.kotlin(
                        "RunTest.kt",
                        """
                        import io.github.molelabs.aspectk.runtime.Aspect
                        import io.github.molelabs.aspectk.runtime.After
                        import io.github.molelabs.aspectk.runtime.JoinPoint

                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Tracked

                        val executionLog = mutableListOf<String>()

                        @Aspect
                        object TrackingAspect {
                            @After(Tracked::class)
                            fun doAfter(joinPoint: JoinPoint) {
                                executionLog.add("after")
                            }
                        }

                        class Test {
                            @Tracked
                            fun work() {
                                executionLog.add("body")
                            }
                        }

                        fun runTest() = Test().work()
                        """,
                    ),
                ),
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // when
        val loader = result.classLoader
        val testKt = loader.loadClass("RunTestKt")
        testKt.getMethod("runTest").invoke(null)

        val logField = testKt.getDeclaredField("executionLog")
        logField.isAccessible = true
        val log = logField.get(null) as MutableList<String>

        // then — body must execute before after-advice
        assertEquals(listOf("body", "after"), log)
    }

    @Test
    fun `@After advice executes after target function body that has a return value`() {
        // given
        val result =
            compile(
                listOf(
                    SourceFile.kotlin(
                        "RunTest.kt",
                        """
                        import io.github.molelabs.aspectk.runtime.Aspect
                        import io.github.molelabs.aspectk.runtime.After
                        import io.github.molelabs.aspectk.runtime.JoinPoint

                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Tracked

                        val executionLog = mutableListOf<String>()

                        @Aspect
                        object TrackingAspect {
                            @After(Tracked::class)
                            fun doAfter(joinPoint: JoinPoint) {
                                executionLog.add("after")
                            }
                        }

                        class Test {
                            @Tracked
                            fun greet(): String {
                                executionLog.add("body")
                                return "hello"
                            }
                        }

                        fun runTest(): String = Test().greet()
                        """,
                    ),
                ),
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // when
        val loader = result.classLoader
        val testKt = loader.loadClass("RunTestKt")
        val returnValue = testKt.getMethod("runTest").invoke(null)

        val logField = testKt.getDeclaredField("executionLog")
        logField.isAccessible = true
        val log = logField.get(null) as MutableList<String>

        // then — return value is preserved and after-advice still runs after body
        assertAll(
            { assertEquals("hello", returnValue) },
            { assertEquals(listOf("body", "after"), log) },
        )
    }

    // ─── @Around local function generation ───────────────────────────────────

    @Test
    fun `@Around advice proceed returns original function return value`() {
        // given
        val result =
            compile(
                listOf(
                    SourceFile.kotlin(
                        "RunTest.kt",
                        """
                        import io.github.molelabs.aspectk.runtime.Aspect
                        import io.github.molelabs.aspectk.runtime.Around
                        import io.github.molelabs.aspectk.runtime.ProceedingJoinPoint

                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Intercepted

                        @Aspect
                        object DoubleValueAspect {
                            @Around(Intercepted::class)
                            fun doAround(pjp: ProceedingJoinPoint): Any? {
                                return (pjp.proceed() as Int) * 2
                            }
                        }

                        class Test {
                            @Intercepted
                            fun getValue(): Int = 21
                        }

                        fun runTest(): Int = Test().getValue()
                        """,
                    ),
                ),
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // when
        val testKt = result.classLoader.loadClass("RunTestKt")
        val returnValue = testKt.getMethod("runTest").invoke(null)

        // then — proceed() returns the original value; advice multiplies it by 2
        assertEquals(42, returnValue)
    }

    @Test
    fun `@Around advice proceed returns Unit for Unit-returning function`() {
        // given
        val result =
            compile(
                listOf(
                    SourceFile.kotlin(
                        "RunTest.kt",
                        """
                        import io.github.molelabs.aspectk.runtime.Aspect
                        import io.github.molelabs.aspectk.runtime.Around
                        import io.github.molelabs.aspectk.runtime.ProceedingJoinPoint

                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Intercepted

                        var capturedProceedResult: Any? = "UNSET"

                        @Aspect
                        object InspectAspect {
                            @Around(Intercepted::class)
                            fun doAround(pjp: ProceedingJoinPoint): Any? {
                                capturedProceedResult = pjp.proceed()
                                return capturedProceedResult
                            }
                        }

                        class Test {
                            @Intercepted
                            fun work() { }
                        }

                        fun runTest() { Test().work() }
                        """,
                    ),
                ),
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // when
        val loader = result.classLoader
        val testKt = loader.loadClass("RunTestKt")
        testKt.getMethod("runTest").invoke(null)

        val capturedField = testKt.getDeclaredField("capturedProceedResult")
        capturedField.isAccessible = true
        val capturedValue = capturedField.get(null)

        // then — proceed() for a Unit-returning function returns kotlin.Unit (not null)
        assertEquals(Unit, capturedValue)
    }

    @Test
    fun `@Around advice without calling proceed skips original function body`() {
        // given
        val result =
            compile(
                listOf(
                    SourceFile.kotlin(
                        "RunTest.kt",
                        """
                        import io.github.molelabs.aspectk.runtime.Aspect
                        import io.github.molelabs.aspectk.runtime.Around
                        import io.github.molelabs.aspectk.runtime.ProceedingJoinPoint

                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Intercepted

                        val executionLog = mutableListOf<String>()

                        @Aspect
                        object SkipAspect {
                            @Around(Intercepted::class)
                            fun doAround(pjp: ProceedingJoinPoint) {
                                executionLog.add("around")
                                // intentionally NOT calling pjp.proceed()
                            }
                        }

                        class Test {
                            @Intercepted
                            fun work() {
                                executionLog.add("body")
                            }
                        }

                        fun runTest() = Test().work()
                        """,
                    ),
                ),
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // when
        val loader = result.classLoader
        val testKt = loader.loadClass("RunTestKt")
        testKt.getMethod("runTest").invoke(null)

        val logField = testKt.getDeclaredField("executionLog")
        logField.isAccessible = true
        val log = logField.get(null) as MutableList<String>

        // then — "body" must not appear; the original function was not invoked
        assertEquals(listOf("around"), log)
    }

    @Test
    fun `@Around advice wraps function with non-local return via for loop`() {
        // Verifies that the local closure correctly handles an early return
        // branching out of a for-loop (non-local-style return at IR level).
        val result =
            compile(
                listOf(
                    SourceFile.kotlin(
                        "RunTest.kt",
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
                            fun findFirst(items: List<Int>): Int? {
                                for (item in items) {
                                    if (item > 5) return item
                                }
                                return null
                            }
                        }

                        fun runTestFound(): Int? = Test().findFirst(listOf(1, 10, 3))
                        fun runTestNotFound(): Int? = Test().findFirst(listOf(1, 2, 3))
                        """,
                    ),
                ),
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // when
        val testKt = result.classLoader.loadClass("RunTestKt")
        val found = testKt.getMethod("runTestFound").invoke(null)
        val notFound = testKt.getMethod("runTestNotFound").invoke(null)

        // then — early return inside the local closure exits correctly
        assertAll(
            { assertEquals(10, found) },
            { assertNull(notFound) },
        )
    }

    @Test
    fun `@Around advice method with non-Any return type causes ClassCastException for non-Unit target`() {
        // given — @Around returns Unit (not Any?) but the target function returns String
        val result =
            compile(
                listOf(
                    SourceFile.kotlin(
                        "RunTest.kt",
                        """
                        import io.github.molelabs.aspectk.runtime.Aspect
                        import io.github.molelabs.aspectk.runtime.Around
                        import io.github.molelabs.aspectk.runtime.ProceedingJoinPoint

                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Intercepted

                        @Aspect
                        object BadReturnAspect {
                            @Around(Intercepted::class)
                            fun doAround(pjp: ProceedingJoinPoint) {  // returns Unit, not Any?
                                pjp.proceed()
                            }
                        }

                        class Test {
                            @Intercepted
                            fun getName(): String = "AspectK"
                        }

                        fun runTest(): String = Test().getName()
                        """,
                    ),
                ),
            )

        // compilation itself succeeds — the plugin emits a cast (Unit as String) in IR
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // when — invoking the function triggers the unsafe cast at runtime
        val testKt = result.classLoader.loadClass("RunTestKt")
        val ex =
            assertThrows<InvocationTargetException> {
                testKt.getMethod("runTest").invoke(null)
            }

        // then — the runtime cast (Unit as String) throws ClassCastException
        assertEquals(ClassCastException::class.java, ex.cause?.javaClass)
    }
}
