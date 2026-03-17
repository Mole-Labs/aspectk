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
@Suppress("UNCHECKED_CAST")
class AdviceCallOrderTest {
    @Test
    fun `@Before advice executes before target function body`() {
        // given
        val result =
            compile(
                listOf(
                    SourceFile.kotlin(
                        "RunTest.kt",
                        """
                        import io.github.molelabs.aspectk.runtime.Aspect
                        import io.github.molelabs.aspectk.runtime.Before
                        import io.github.molelabs.aspectk.runtime.JoinPoint

                        @Target(AnnotationTarget.FUNCTION)
                        annotation class Tracked

                        val executionLog = mutableListOf<String>()

                        @Aspect
                        object TrackingAspect {
                            @Before(Tracked::class)
                            fun doBefore(joinPoint: JoinPoint) {
                                executionLog.add("before")
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

        // then — before-advice must execute before the function body
        assertEquals(listOf("before", "body"), log)
    }

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
    fun `@Around advice executes both before and after target function body`() {
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
                        annotation class Tracked

                        val executionLog = mutableListOf<String>()

                        @Aspect
                        object TrackingAspect {
                            @Around(Tracked::class)
                            fun doAround(pjp: ProceedingJoinPoint): Any? {
                                executionLog.add("before")
                                val result = pjp.proceed()
                                executionLog.add("after")
                                return result
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

        // then — around-advice wraps the function body: before → body → after
        assertEquals(listOf("before", "body", "after"), log)
    }
}
