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
import io.github.molelabs.aspectk.core.compile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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

                @Aspect
                object PassThroughAspect {
                    @Around(Intercepted::class)
                    fun doAround(pjp: ProceedingJoinPoint): Any? = pjp.proceed()
                }

                class Test {
                    @Intercepted
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
}
