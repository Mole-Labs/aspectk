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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import java.lang.reflect.InvocationTargetException

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
    fun `@After advice is invoked even when the original function throws`() {
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
                object TrackingAspect {
                    var called = false

                    @After(Intercepted::class)
                    fun doAfter(jp: JoinPoint) { called = true }
                }

                class Test {
                    @Intercepted
                    fun riskyWork(): Unit = throw RuntimeException("boom")
                }
                """,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // when — invoke the throwing function, expecting the exception to propagate
        val testClass = result.classLoader.loadClass("Test")
        val instance = testClass.getDeclaredConstructor().newInstance()
        assertThrows<InvocationTargetException> {
            testClass.getMethod("riskyWork").invoke(instance)
        }

        // then — despite the exception, @After advice must have been called (finally block)
        val aspectInstance = result.classLoader.loadClass("TrackingAspect").getField("INSTANCE").get(null)
        val calledField = aspectInstance.javaClass.getDeclaredField("called").apply { isAccessible = true }
        assertTrue(calledField.getBoolean(aspectInstance), "Expected @After advice to be called even when the function throws")
    }

    @Test
    fun `@After advice is invoked after a normally returning function`() {
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
                object TrackingAspect {
                    var called = false

                    @After(Intercepted::class)
                    fun doAfter(jp: JoinPoint) { called = true }
                }

                class Test {
                    @Intercepted
                    fun normalWork(): String = "done"
                }
                """,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // when
        val testClass = result.classLoader.loadClass("Test")
        val instance = testClass.getDeclaredConstructor().newInstance()
        testClass.getMethod("normalWork").invoke(instance)

        // then
        val aspectInstance = result.classLoader.loadClass("TrackingAspect").getField("INSTANCE").get(null)
        val calledField = aspectInstance.javaClass.getDeclaredField("called").apply { isAccessible = true }
        assertTrue(calledField.getBoolean(aspectInstance), "Expected @After advice to be called after normal return")
    }
}