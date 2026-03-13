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
import io.github.molelabs.aspectk.core.assertAndGetField
import io.github.molelabs.aspectk.core.compile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows

@OptIn(ExperimentalCompilerApi::class)
@Suppress("UNCHECKED_CAST")
class AfterMethodSignatureTargetingTest {
    @Test
    fun `MethodSignature should be created only for @After target annotations`() {
        // given
        val result =
            compile(
                """
                import io.github.molelabs.aspectk.runtime.Aspect
                import io.github.molelabs.aspectk.runtime.After
                import io.github.molelabs.aspectk.runtime.JoinPoint

                @Target(AnnotationTarget.FUNCTION)
                annotation class TargetExample(
                    val name:String
                )

                @Aspect
                object ExampleAspect {
                    @After(TargetExample::class)
                    fun doAfter(joinPoint: JoinPoint) {
                    }
                }

                class Test1 {
                    fun test1() {}
                }
                """,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val loader = result.classLoader

        // when - then
        assertThrows<ClassNotFoundException> {
            loader.assertAndGetField(
                className = $$$"Test1$$MethodSignatures",
                fieldName = $$"ajc$tjp_0",
            )
        }
    }

    @Test
    fun `MethodSignature should be created for multiple target annotations with @After advice`() {
        // given
        val result =
            compile(
                """
                import io.github.molelabs.aspectk.runtime.Aspect
                import io.github.molelabs.aspectk.runtime.After
                import io.github.molelabs.aspectk.runtime.JoinPoint

                @Target(AnnotationTarget.FUNCTION)
                annotation class TargetExample1

                @Target(AnnotationTarget.FUNCTION)
                annotation class TargetExample2

                @Target(AnnotationTarget.FUNCTION)
                annotation class TargetExample3

                @Aspect
                object ExampleAspect {
                    @After(TargetExample1::class, TargetExample2::class, TargetExample3::class)
                    fun doAfter(joinPoint: JoinPoint) {
                    }
                }

                class Test {
                    @TargetExample1
                    fun test1() {}

                    @TargetExample2
                    fun test2() {}

                    @TargetExample3
                    fun test3() {}
                }
                """,
            )

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val loader = result.classLoader

        // when
        val actual1 =
            loader.assertAndGetField(
                className = $$$"Test$$MethodSignatures",
                fieldName = $$"ajc$tjp_0",
            )

        val actual2 =
            loader.assertAndGetField(
                className = $$$"Test$$MethodSignatures",
                fieldName = $$"ajc$tjp_1",
            )

        val actual3 =
            loader.assertAndGetField(
                className = $$$"Test$$MethodSignatures",
                fieldName = $$"ajc$tjp_2",
            )

        // then
        val expected1 = singleFieldWithNoAnnotationArgs(loader, "test1", "TargetExample1")
        val expected2 = singleFieldWithNoAnnotationArgs(loader, "test2", "TargetExample2")
        val expected3 = singleFieldWithNoAnnotationArgs(loader, "test3", "TargetExample3")

        assertAll(
            { assertEquals(expected1, actual1) },
            { assertEquals(expected2, actual2) },
            { assertEquals(expected3, actual3) },
        )
    }

    @Test
    fun `MethodSignature should be created only once when multiple target annotations exists with @After advice`() {
        // given
        val result =
            compile(
                """
                import io.github.molelabs.aspectk.runtime.Aspect
                import io.github.molelabs.aspectk.runtime.After
                import io.github.molelabs.aspectk.runtime.JoinPoint

                @Target(AnnotationTarget.FUNCTION)
                annotation class TargetExample1

                @Target(AnnotationTarget.FUNCTION)
                annotation class TargetExample2

                @Target(AnnotationTarget.FUNCTION)
                annotation class TargetExample3

                @Aspect
                object ExampleAspect {
                    @After(TargetExample1::class, TargetExample2::class, TargetExample3::class)
                    fun doAfter(joinPoint: JoinPoint) {
                    }
                }

                class Test {
                    @TargetExample1
                    @TargetExample2
                    @TargetExample3
                    fun test1() {}
                }
                """,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val loader = result.classLoader

        // when
        val actual1 =
            loader.assertAndGetField(
                className = $$$"Test$$MethodSignatures",
                fieldName = $$"ajc$tjp_0",
            )

        val expected1 =
            singleFieldWithMultipleAnnotations(
                loader,
                "test1",
                "TargetExample1",
                "TargetExample2",
                "TargetExample3",
            )

        // then
        assertAll(
            { assertEquals(expected1, actual1) },
            {
                assertThrows<NoSuchFieldException> {
                    loader.assertAndGetField(
                        className = $$$"Test$$MethodSignatures",
                        fieldName = $$"ajc$tjp_1",
                    )
                }
            },
            {
                assertThrows<NoSuchFieldException> {
                    loader.assertAndGetField(
                        className = $$$"Test$$MethodSignatures",
                        fieldName = $$"ajc$tjp_2",
                    )
                }
            },
        )
    }

    @Test
    fun `MethodSignature should be created once for multiple @After advices`() {
        // given
        val result =
            compile(
                """
                import io.github.molelabs.aspectk.runtime.Aspect
                import io.github.molelabs.aspectk.runtime.After
                import io.github.molelabs.aspectk.runtime.JoinPoint

                @Target(AnnotationTarget.FUNCTION)
                annotation class TargetExample1

                @Aspect
                object ExampleAspect {
                    @After(TargetExample1::class)
                    fun doAfter1(joinPoint: JoinPoint) {
                    }

                    @After(TargetExample1::class)
                    fun doAfter2(joinPoint: JoinPoint) {
                    }

                    @After(TargetExample1::class)
                    fun doAfter3(joinPoint: JoinPoint) {
                    }
                }

                class Test {
                    @TargetExample1
                    fun test1() {}
                }
                """,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val loader = result.classLoader

        // when
        val actual1 =
            loader.assertAndGetField(
                className = $$$"Test$$MethodSignatures",
                fieldName = $$"ajc$tjp_0",
            )

        val expected1 =
            singleFieldWithMultipleAnnotations(
                loader,
                "test1",
                "TargetExample1",
            )

        assertEquals(expected1, actual1)
    }

    @Test
    fun `MethodSignatures should be created for many-to-many relationship between @After advices and targets`() {
        // given
        val result =
            compile(
                """
            import io.github.molelabs.aspectk.runtime.Aspect
            import io.github.molelabs.aspectk.runtime.After
            import io.github.molelabs.aspectk.runtime.JoinPoint

            @Target(AnnotationTarget.FUNCTION)
            annotation class TargetExample1

            @Target(AnnotationTarget.FUNCTION)
            annotation class TargetExample2

            @Target(AnnotationTarget.FUNCTION)
            annotation class TargetExample3

            @Aspect
            object ExampleAspect {
                @After(TargetExample1::class, TargetExample2::class, TargetExample3::class)
                fun doAfter1(joinPoint: JoinPoint) {
                }

                @After(TargetExample1::class, TargetExample3::class)
                fun doAfter2(joinPoint: JoinPoint) {
                }

                @After(TargetExample2::class)
                fun doAfter3(joinPoint: JoinPoint) {
                }
            }

            class Test {
                @TargetExample1
                @TargetExample2
                fun test1() {}

                @TargetExample3
                fun test2() {}
            }
            """,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val loader = result.classLoader

        // when
        val actual1 =
            loader.assertAndGetField(
                className = $$$"Test$$MethodSignatures",
                fieldName = $$"ajc$tjp_0",
            )

        val actual2 =
            loader.assertAndGetField(
                className = $$$"Test$$MethodSignatures",
                fieldName = $$"ajc$tjp_1",
            )

        val expected1 =
            singleFieldWithMultipleAnnotations(
                loader,
                "test1",
                "TargetExample1",
                "TargetExample2",
            )

        val expected2 =
            singleFieldWithMultipleAnnotations(
                loader,
                "test2",
                "TargetExample3",
            )

        // then
        assertAll(
            { assertEquals(expected1, actual1) },
            { assertEquals(expected2, actual2) },
        )
    }
}
