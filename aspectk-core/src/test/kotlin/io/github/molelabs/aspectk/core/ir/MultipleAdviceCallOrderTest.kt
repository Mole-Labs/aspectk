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
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
@Suppress("UNCHECKED_CAST")
class MultipleAdviceCallOrderTest {
    // TODO support multiple @Around, @After annotations
    @Test
    fun `@Around advice is only invoked once per function`() {
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

                @Target(AnnotationTarget.FUNCTION)
                annotation class Intercepted3


                @Aspect
                object PassThroughAspect {
                    var count = 0

                    @Around(Intercepted1::class)
                    fun doAround1(pjp: ProceedingJoinPoint): Any? {
                        count++
                        return pjp.proceed()
                    }

                    @Around(Intercepted2::class)
                    fun doAround2(pjp: ProceedingJoinPoint): Any? {
                        count++
                        return pjp.proceed()
                    }

                    @Around(Intercepted3::class)
                    fun doAround3(pjp: ProceedingJoinPoint): Any? {
                        count++
                        return pjp.proceed()
                    }
                }

                class Test {
                    @Intercepted1
                    @Intercepted2
                    @Intercepted3
                    fun greet(): String = "hello"
                }
                """.trimIndent(),
            )

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // when
        val testClass = result.classLoader.loadClass("Test")
        val instance = testClass.getDeclaredConstructor().newInstance()
        testClass.getMethod("greet").invoke(instance)

        // then
        val aspectInstance =
            result.classLoader
                .loadClass("PassThroughAspect")
                .getField("INSTANCE")
                .get(null)
        val calledField =
            aspectInstance.javaClass.getDeclaredField("count").apply { isAccessible = true }
        assertEquals(
            1,
            calledField.getInt(aspectInstance),
            "Expected @Around advice to be called only once",
        )
    }

    // TODO support multiple @Around, @After annotations
    @Test
    fun `@After advice is only invoked once per function`() {
        // given
        val result =
            compile(
                """
                import io.github.molelabs.aspectk.runtime.Aspect
                import io.github.molelabs.aspectk.runtime.After
                import io.github.molelabs.aspectk.runtime.JoinPoint

                @Target(AnnotationTarget.FUNCTION)
                annotation class Intercepted1

                @Target(AnnotationTarget.FUNCTION)
                annotation class Intercepted2

                @Target(AnnotationTarget.FUNCTION)
                annotation class Intercepted3


                @Aspect
                object PassThroughAspect {
                    var count = 0

                    @After(Intercepted1::class)
                    fun doAround1(pjp: JoinPoint) {
                        count++
                    }

                    @After(Intercepted2::class)
                    fun doAround2(pjp: JoinPoint) {
                        count++
                    }

                    @After(Intercepted3::class)
                    fun doAround3(pjp: JoinPoint) {
                        count++
                    }
                }

                class Test {
                    @Intercepted1
                    @Intercepted2
                    @Intercepted3
                    fun greet(): String = "hello"
                }
                """.trimIndent(),
            )

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // when
        val testClass = result.classLoader.loadClass("Test")
        val instance = testClass.getDeclaredConstructor().newInstance()
        testClass.getMethod("greet").invoke(instance)

        // then
        val aspectInstance =
            result.classLoader
                .loadClass("PassThroughAspect")
                .getField("INSTANCE")
                .get(null)
        val calledField =
            aspectInstance.javaClass.getDeclaredField("count").apply { isAccessible = true }
        assertEquals(
            1,
            calledField.getInt(aspectInstance),
            "Expected @Around advice to be called only once",
        )
    }

    // TODO support multiple @Around, @After annotations
    @Test
    fun `@After and @Around advice is only invoked once per function`() {
        // given
        val result =
            compile(
                """
                import io.github.molelabs.aspectk.runtime.Aspect
                import io.github.molelabs.aspectk.runtime.After
                import io.github.molelabs.aspectk.runtime.JoinPoint

                @Target(AnnotationTarget.FUNCTION)
                annotation class Intercepted1

                @Target(AnnotationTarget.FUNCTION)
                annotation class Intercepted2

                @Target(AnnotationTarget.FUNCTION)
                annotation class Intercepted3


                @Aspect
                object PassThroughAspect {
                    var count = 0

                    @After(Intercepted1::class)
                    fun doAround1(pjp: JoinPoint) {
                        count++
                    }

                    @After(Intercepted2::class)
                    fun doAround2(pjp: JoinPoint) {
                        count++
                    }

                    @After(Intercepted3::class)
                    fun doAround3(pjp: JoinPoint) {
                        count++
                    }
                }

                class Test {
                    @Intercepted1
                    @Intercepted2
                    @Intercepted3
                    fun greet(): String = "hello"
                }
                """.trimIndent(),
            )

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // when
        val testClass = result.classLoader.loadClass("Test")
        val instance = testClass.getDeclaredConstructor().newInstance()
        testClass.getMethod("greet").invoke(instance)

        // then
        val aspectInstance =
            result.classLoader
                .loadClass("PassThroughAspect")
                .getField("INSTANCE")
                .get(null)
        val calledField =
            aspectInstance.javaClass.getDeclaredField("count").apply { isAccessible = true }
        assertEquals(
            1,
            calledField.getInt(aspectInstance),
            "Expected @Around advice to be called only once",
        )
    }

    @Test
    fun `multiple @Before advice is ordinally invoked`() {
        // given
        val result =
            compile(
                """
                import io.github.molelabs.aspectk.runtime.Aspect
                import io.github.molelabs.aspectk.runtime.After
                import io.github.molelabs.aspectk.runtime.Around
                import io.github.molelabs.aspectk.runtime.JoinPoint
                import io.github.molelabs.aspectk.runtime.ProceedingJoinPoint

                @Target(AnnotationTarget.FUNCTION)
                annotation class Intercepted1

                @Target(AnnotationTarget.FUNCTION)
                annotation class Intercepted2

                @Target(AnnotationTarget.FUNCTION)
                annotation class Intercepted3


                @Aspect
                object PassThroughAspect {
                    var count = 0

                    @Around(Intercepted1::class)
                    fun doAround1(pjp: ProceedingJoinPoint):Any? {
                        count++
                        return pjp.proceed()
                    }

                    @After(Intercepted2::class, Intercepted3::class)
                    fun doAround2(pjp: JoinPoint) {
                        count++
                    }
                }

                class Test {
                    @Intercepted1
                    @Intercepted2
                    @Intercepted3
                    fun greet(): String = "hello"
                }
                """.trimIndent(),
            )

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // when
        val testClass = result.classLoader.loadClass("Test")
        val instance = testClass.getDeclaredConstructor().newInstance()
        testClass.getMethod("greet").invoke(instance)

        // then
        val aspectInstance =
            result.classLoader
                .loadClass("PassThroughAspect")
                .getField("INSTANCE")
                .get(null)
        val calledField =
            aspectInstance.javaClass.getDeclaredField("count").apply { isAccessible = true }
        assertEquals(
            1,
            calledField.getInt(aspectInstance),
            "Expected @Around advice to be called only once",
        )
    }

    @Test
    fun `multiple @Before and @Around advice is ordinally invoked`() {
        /*
        Current architectural limitations and the absence of an advice ordering engine
        restrict how multiple advices are applied. Specifically, All @Before advice is called,
        but only the last @Around advice is called as the latter types effectively override
        the original function body
         */

        // given
        val result =
            compile(
                """
                import io.github.molelabs.aspectk.runtime.Aspect
                import io.github.molelabs.aspectk.runtime.Before
                import io.github.molelabs.aspectk.runtime.Around
                import io.github.molelabs.aspectk.runtime.JoinPoint
                import io.github.molelabs.aspectk.runtime.ProceedingJoinPoint

                @Target(AnnotationTarget.FUNCTION)
                annotation class AroundAspect1

                @Target(AnnotationTarget.FUNCTION)
                annotation class AroundAspect2

                @Target(AnnotationTarget.FUNCTION)
                annotation class BeforeAspect1

                @Target(AnnotationTarget.FUNCTION)
                annotation class BeforeAspect2

                @Aspect
                object PassThroughAspect {
                    var beforeCount = 0
                    var aroundCount = 0

                    @Around(AroundAspect1::class, AroundAspect2::class)
                    fun doAround1(pjp: ProceedingJoinPoint):Any? {
                        aroundCount++
                        return pjp.proceed()
                    }

                    @Before(BeforeAspect1::class, BeforeAspect2::class)
                    fun doAround2(pjp: JoinPoint) {
                        beforeCount++
                    }
                }

                class Test {
                    @BeforeAspect1
                    @BeforeAspect2
                    @AroundAspect1
                    @AroundAspect2
                    fun greet(): String = "hello"
                }
                """.trimIndent(),
            )

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // when
        val testClass = result.classLoader.loadClass("Test")
        val instance = testClass.getDeclaredConstructor().newInstance()
        testClass.getMethod("greet").invoke(instance)

        // then
        val aspectInstance =
            result.classLoader
                .loadClass("PassThroughAspect")
                .getField("INSTANCE")
                .get(null)

        val aroundCallField =
            aspectInstance.javaClass.getDeclaredField("aroundCount").apply { isAccessible = true }

        val beforeCallField =
            aspectInstance.javaClass.getDeclaredField("beforeCount").apply { isAccessible = true }

        // all @Before aspect and only AroundAspect2 are called
        assertEquals(
            2,
            beforeCallField.getInt(aspectInstance),
        )

        assertEquals(
            1,
            aroundCallField.getInt(aspectInstance),
        )
    }

    @Test
    fun `multiple @Before and @After advice is ordinally invoked`() {
        /*
        Current architectural limitations and the absence of an advice ordering engine
        restrict how multiple advices are applied. Specifically, @Before advice only
        functions correctly when positioned relative to @Around or @After interceptors,
        as the latter types effectively override the original function body
         */

        // given
        val result =
            compile(
                """
                import io.github.molelabs.aspectk.runtime.Aspect
                import io.github.molelabs.aspectk.runtime.Before
                import io.github.molelabs.aspectk.runtime.After
                import io.github.molelabs.aspectk.runtime.JoinPoint

                @Target(AnnotationTarget.FUNCTION)
                annotation class AroundAspect1

                @Target(AnnotationTarget.FUNCTION)
                annotation class AroundAspect2

                @Target(AnnotationTarget.FUNCTION)
                annotation class BeforeAspect1

                @Target(AnnotationTarget.FUNCTION)
                annotation class BeforeAspect2

                @Aspect
                object PassThroughAspect {
                    var beforeCount = 0
                    var aroundCount = 0

                    @After(AroundAspect1::class, AroundAspect2::class)
                    fun doAfter(jp: JoinPoint) {
                        aroundCount++
                    }

                    @Before(BeforeAspect1::class, BeforeAspect2::class)
                    fun doBefore(jp: JoinPoint) {
                        beforeCount++
                    }
                }

                class Test {
                    @BeforeAspect1
                    @BeforeAspect2
                    @AroundAspect1
                    @AroundAspect2
                    fun greet(): String = "hello"
                }
                """.trimIndent(),
            )

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // when
        val testClass = result.classLoader.loadClass("Test")
        val instance = testClass.getDeclaredConstructor().newInstance()
        testClass.getMethod("greet").invoke(instance)

        // then
        val aspectInstance =
            result.classLoader
                .loadClass("PassThroughAspect")
                .getField("INSTANCE")
                .get(null)

        val aroundCallField =
            aspectInstance.javaClass.getDeclaredField("aroundCount").apply { isAccessible = true }

        val beforeCallField =
            aspectInstance.javaClass.getDeclaredField("beforeCount").apply { isAccessible = true }

        // all @Before aspect and only AroundAspect2 are called
        assertEquals(
            2,
            beforeCallField.getInt(aspectInstance),
        )

        assertEquals(
            1,
            aroundCallField.getInt(aspectInstance),
        )
    }
}
