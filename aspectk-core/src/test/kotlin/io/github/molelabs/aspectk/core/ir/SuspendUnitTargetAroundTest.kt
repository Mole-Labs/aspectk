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

// A suspend Unit function under @Around whose last statement is a suspend call that suspends and
// returns a value. proceed() has to return Unit, not that value: the JVM backend takes a trailing
// suspend call followed by `return Unit` for a tail call and hands its result straight to the caller.
@OptIn(ExperimentalCompilerApi::class)
class SuspendUnitTargetAroundTest {
    @Test
    fun `proceed returns Unit to an advice that reads the result`() {
        // given
        val result =
            compile(
                listOf(
                    SourceFile.kotlin(
                        "RunTest.kt",
                        """
                        import io.github.molelabs.aspectk.runtime.Around
                        import io.github.molelabs.aspectk.runtime.Aspect
                        import io.github.molelabs.aspectk.runtime.SuspendProceedingJoinPoint
                        import kotlinx.coroutines.delay
                        import kotlinx.coroutines.runBlocking

                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Wrapped

                        val executionLog = mutableListOf<String>()

                        @Aspect
                        object WrapAspect {
                            @Around(Wrapped::class)
                            suspend fun doAround(pjp: SuspendProceedingJoinPoint): Any? {
                                val result = pjp.proceed()
                                executionLog.add("proceed:" + result)
                                return result
                            }
                        }

                        suspend fun valueAfterSuspending(): String {
                            delay(1)
                            return "value"
                        }

                        class Example {
                            @Wrapped
                            suspend fun work() {
                                executionLog.add("body")
                                valueAfterSuspending()
                            }
                        }

                        fun runTest(): Any? = runBlocking { Example().work() }
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
        assertEquals(Unit, actual)
        assertEquals(listOf("body", "proceed:kotlin.Unit"), log)
    }

    @Test
    fun `an advice that is itself a tail call to proceed returns Unit`() {
        // given
        val result =
            compile(
                listOf(
                    SourceFile.kotlin(
                        "RunTest.kt",
                        """
                        import io.github.molelabs.aspectk.runtime.Around
                        import io.github.molelabs.aspectk.runtime.Aspect
                        import io.github.molelabs.aspectk.runtime.SuspendProceedingJoinPoint
                        import kotlinx.coroutines.delay
                        import kotlinx.coroutines.runBlocking

                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Wrapped

                        val executionLog = mutableListOf<String>()

                        @Aspect
                        object WrapAspect {
                            @Around(Wrapped::class)
                            suspend fun doAround(pjp: SuspendProceedingJoinPoint): Any? = pjp.proceed()
                        }

                        suspend fun valueAfterSuspending(): String {
                            delay(1)
                            return "value"
                        }

                        class Example {
                            @Wrapped
                            suspend fun work() {
                                executionLog.add("body")
                                valueAfterSuspending()
                            }
                        }

                        fun runTest(): Any? = runBlocking { Example().work() }
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
        assertEquals(Unit, actual)
        assertEquals(listOf("body"), log)
    }

    @Test
    fun `an advice that proceeds inside withContext returns Unit`() {
        // given
        val result =
            compile(
                listOf(
                    SourceFile.kotlin(
                        "RunTest.kt",
                        """
                        import io.github.molelabs.aspectk.runtime.Around
                        import io.github.molelabs.aspectk.runtime.Aspect
                        import io.github.molelabs.aspectk.runtime.SuspendProceedingJoinPoint
                        import kotlinx.coroutines.Dispatchers
                        import kotlinx.coroutines.delay
                        import kotlinx.coroutines.runBlocking
                        import kotlinx.coroutines.withContext

                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Wrapped

                        val executionLog = mutableListOf<String>()

                        @Aspect
                        object WrapAspect {
                            @Around(Wrapped::class)
                            suspend fun doAround(pjp: SuspendProceedingJoinPoint): Any? = withContext(Dispatchers.Default) { pjp.proceed() }
                        }

                        suspend fun valueAfterSuspending(): String {
                            delay(1)
                            return "value"
                        }

                        class Example {
                            @Wrapped
                            suspend fun work() {
                                executionLog.add("body")
                                valueAfterSuspending()
                            }
                        }

                        fun runTest(): Any? = runBlocking { Example().work() }
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
        assertEquals(Unit, actual)
        assertEquals(listOf("body"), log)
    }
}
