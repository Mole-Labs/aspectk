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
package com.mole.core

import com.mole.runtime.AnnotationInfo
import com.mole.runtime.MethodParameter
import com.mole.runtime.MethodSignature
import com.tschuchort.compiletesting.KotlinCompilation
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import kotlin.reflect.KClass

@OptIn(ExperimentalCompilerApi::class)
@Suppress("UNCHECKED_CAST")
class MethodSignatureFieldGeneratorTest {
    @Test
    fun `MethodSignature should be created in with static field`() {
        // given
        val result =
            compile(
                """
                import com.mole.runtime.Aspect
                import com.mole.runtime.Before
                import com.mole.runtime.JoinPoint

                @Target(AnnotationTarget.FUNCTION)
                annotation class TargetExample(
                    val name:String
                )

                @Aspect
                object ExampleAspect {
                    @Before(TargetExample::class)
                    fun doBefore(joinPoint: JoinPoint) {
                        System.out.println(joinPoint.args)
                    }
                }

                class Test {
                    @TargetExample("example1")
                    fun test1() {
                    }
                }

                fun main() {

                }
                """,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // when
        val loader = result.classLoader
        val actual =
            loader.assertAndGetField(
                className = "Test",
                fieldName = $$"ajc$tjp_0",
            )

        // then
        val expected = singleField(loader)
        assertEquals(expected, actual)
    }

    @Test
    fun `MethodSignature contains annotations of method parameters`() {
        // given
        val result =
            compile(
                """
                import org.jetbrains.annotations.NotNull
                import com.mole.runtime.Aspect
                import com.mole.runtime.Before
                import com.mole.runtime.JoinPoint

                @Target(AnnotationTarget.FUNCTION)
                annotation class TargetExample(
                    val name:String
                )

                @Aspect
                object ExampleAspect {
                    @Before(TargetExample::class)
                    fun doBefore(joinPoint: JoinPoint) {
                        System.out.println(joinPoint.args)
                    }
                }

                class Test {
                    @TargetExample("example1")
                    fun test1(
                        arg1:Int?,
                        @NotNull("test") arg2:String
                    ) {}
                }
                """,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // when
        val loader = result.classLoader
        val actual =
            loader.assertAndGetField(
                className = "Test",
                fieldName = $$"ajc$tjp_0",
            )

        // then
        val expected = singleFieldWithMethodArgs(loader)
        assertEquals(expected, actual)
    }

    @Test
    fun `multiple MethodSignature can be created`() {
        val result =
            compile(
                """
                import com.mole.runtime.Aspect
                import com.mole.runtime.Before
                import com.mole.runtime.JoinPoint

                @Target(AnnotationTarget.FUNCTION)
                annotation class TargetExample(
                    val name:String
                )

                @Aspect
                object ExampleAspect {
                    @Before(TargetExample::class)
                    fun doBefore(joinPoint: JoinPoint) {

                    }
                }

                class Test {
                    @TargetExample("example1")
                    fun test1() {}

                    @TargetExample("example2")
                    fun test2() {}
                }
                """,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // when
        val loader = result.classLoader
        val actual1 =
            loader.assertAndGetField(
                className = "Test",
                fieldName = $$"ajc$tjp_0",
            )
        val actual2 =
            loader.assertAndGetField(
                className = "Test",
                fieldName = $$"ajc$tjp_1",
            )

        // then
        val expected1 = doubleFieldWithMethodArgs(loader)
        val expected2 =
            expected1.copy(
                methodName = "test2",
                annotations =
                    listOf(
                        AnnotationInfo(
                            type = loader.loadClass("TargetExample").kotlin as KClass<out Annotation>,
                            typeName = "TargetExample",
                            args = listOf("example2"),
                            parameterNames = listOf("name"),
                        ),
                    ),
            )
        assertEquals(expected1, actual1)
        assertEquals(expected2, actual2)
    }

    @Test
    fun `multiple MethodSignature can be created in multiple classes`() {
        val result =
            compile(
                """
                import com.mole.runtime.Aspect
                import com.mole.runtime.Before
                import com.mole.runtime.JoinPoint

                @Target(AnnotationTarget.FUNCTION)
                annotation class TargetExample(
                    val name:String
                )

                @Aspect
                object ExampleAspect {
                    @Before(TargetExample::class)
                    fun doBefore(joinPoint: JoinPoint) {

                    }
                }

                class Test1 {
                    @TargetExample("example1")
                    fun test1() {}
                }

                class Test2 {
                    @TargetExample("example1")
                    fun test1() {}
                }
                """,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // when
        val loader = result.classLoader
        val actual1 =
            loader.assertAndGetField(
                className = "Test1",
                fieldName = $$"ajc$tjp_0",
            )
        val actual2 =
            loader.assertAndGetField(
                className = "Test2",
                fieldName = $$"ajc$tjp_1",
            )

        // then
        val expected1 = singleFieldWithDoubleClass(loader, "Test1")
        val expected2 = singleFieldWithDoubleClass(loader, "Test2")
        assertAll(
            { assertEquals(expected1, actual1) },
            { assertEquals(expected2, actual2) },
        )
    }

    @Test
    fun `MethodSignature should be created only for target annotations`() {
        // given
        val result =
            compile(
                """
                import com.mole.runtime.Aspect
                import com.mole.runtime.Before
                import com.mole.runtime.JoinPoint

                @Target(AnnotationTarget.FUNCTION)
                annotation class TargetExample(
                    val name:String
                )

                @Aspect
                object ExampleAspect {
                    @Before(TargetExample::class)
                    fun doBefore(joinPoint: JoinPoint) {
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
        assertThrows<NoSuchFieldException> {
            loader.assertAndGetField(
                className = "Test1",
                fieldName = $$"ajc$tjp_0",
            )
        }
    }

    @Test
    fun `MethodSignature should be created for multiple target annotations`() {
        // given
        val result =
            compile(
                """
                import com.mole.runtime.Aspect
                import com.mole.runtime.Before
                import com.mole.runtime.JoinPoint

                @Target(AnnotationTarget.FUNCTION)
                annotation class TargetExample1

                @Target(AnnotationTarget.FUNCTION)
                annotation class TargetExample2

                @Target(AnnotationTarget.FUNCTION)
                annotation class TargetExample3

                @Aspect
                object ExampleAspect {
                    @Before(TargetExample1::class, TargetExample2::class, TargetExample3::class)
                    fun doBefore(joinPoint: JoinPoint) {
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
                className = "Test",
                fieldName = $$"ajc$tjp_0",
            )

        val actual2 =
            loader.assertAndGetField(
                className = "Test",
                fieldName = $$"ajc$tjp_1",
            )

        val actual3 =
            loader.assertAndGetField(
                className = "Test",
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
    fun `MethodSignature should be created only once when multiple target annotations exists`() {
        // given
        val result =
            compile(
                """
                import com.mole.runtime.Aspect
                import com.mole.runtime.Before
                import com.mole.runtime.JoinPoint

                @Target(AnnotationTarget.FUNCTION)
                annotation class TargetExample1

                @Target(AnnotationTarget.FUNCTION)
                annotation class TargetExample2

                @Target(AnnotationTarget.FUNCTION)
                annotation class TargetExample3

                @Aspect
                object ExampleAspect {
                    @Before(TargetExample1::class, TargetExample2::class, TargetExample3::class)
                    fun doBefore(joinPoint: JoinPoint) {
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
                className = "Test",
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

        assertAll(
            { assertEquals(expected1, actual1) },
            {
                assertThrows<NoSuchFieldException> {
                    loader.assertAndGetField(
                        className = "Test",
                        fieldName = $$"ajc$tjp_1",
                    )
                }
            },
            {
                assertThrows<NoSuchFieldException> {
                    loader.assertAndGetField(
                        className = "Test",
                        fieldName = $$"ajc$tjp_2",
                    )
                }
            },
        )
    }

    @Test
    fun `MethodSignature should be created once for multiple advices`() {
        // given
        val result =
            compile(
                """
                import com.mole.runtime.Aspect
                import com.mole.runtime.Before
                import com.mole.runtime.JoinPoint

                @Target(AnnotationTarget.FUNCTION)
                annotation class TargetExample1

                @Aspect
                object ExampleAspect {
                    @Before(TargetExample1::class)
                    fun doBefore1(joinPoint: JoinPoint) {
                    }

                    @Before(TargetExample1::class)
                    fun doBefore2(joinPoint: JoinPoint) {
                    }

                    @Before(TargetExample1::class)
                    fun doBefore3(joinPoint: JoinPoint) {
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
                className = "Test",
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
    fun `MethodSignatures should be created for many-to-many relationship between advices and targets`() {
        // given
        val result =
            compile(
                """
            import com.mole.runtime.Aspect
            import com.mole.runtime.Before
            import com.mole.runtime.JoinPoint

            @Target(AnnotationTarget.FUNCTION)
            annotation class TargetExample1

            @Target(AnnotationTarget.FUNCTION)
            annotation class TargetExample2

            @Target(AnnotationTarget.FUNCTION)
            annotation class TargetExample3

            @Aspect
            object ExampleAspect {
                @Before(TargetExample1::class, TargetExample2::class, TargetExample3::class)
                fun doBefore1(joinPoint: JoinPoint) {
                }

                @Before(TargetExample1::class, TargetExample3::class)
                fun doBefore2(joinPoint: JoinPoint) {
                }

                @Before(TargetExample2::class)
                fun doBefore3(joinPoint: JoinPoint) {
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
                className = "Test",
                fieldName = $$"ajc$tjp_0",
            )

        val actual2 =
            loader.assertAndGetField(
                className = "Test",
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

        assertAll(
            { assertEquals(expected1, actual1) },
            { assertEquals(expected2, actual2) },
        )
    }

    @Test
    fun `MethodSignatures should be created for inline functions`() {
        // given
        val result =
            compile(
                """
                import com.mole.runtime.Aspect
                import com.mole.runtime.Before
                import com.mole.runtime.JoinPoint

                @Target(AnnotationTarget.FUNCTION)
                annotation class TargetExample

                @Aspect
                object ExampleAspect {
                    @Before(TargetExample::class)
                    fun doBefore(joinPoint: JoinPoint) {
                    }
                }

                class Test {
                    @TargetExample
                    inline fun inlineFun() {}
                }
                """,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val loader = result.classLoader

        // when
        val actual =
            loader.assertAndGetField(
                className = "Test",
                fieldName = $$"ajc$tjp_0",
            )

        // then
        val expected = singleFieldWithNoAnnotationArgs(loader, "inlineFun", "TargetExample")
        assertEquals(expected, actual)
    }

    @Test
    fun `MethodSignature should be created for suspend functions`() {
        // given
        val result =
            compile(
                """
                import com.mole.runtime.Aspect
                import com.mole.runtime.Before
                import com.mole.runtime.JoinPoint

                @Target(AnnotationTarget.FUNCTION)
                annotation class TargetExample

                @Aspect
                object ExampleAspect {
                    @Before(TargetExample::class)
                    fun doBefore(joinPoint: JoinPoint) {
                    }
                }

                class Test {
                    @TargetExample
                    suspend fun suspendFun() {}
                }
                """,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val loader = result.classLoader

        // when
        val actual =
            loader.assertAndGetField(
                className = "Test",
                fieldName = $$"ajc$tjp_0",
            )

        // then
        val expected = singleFieldWithNoAnnotationArgs(loader, "suspendFun", "TargetExample")
        assertEquals(expected, actual)
    }

    @Test
    fun `MethodSignature should be created for top-level functions`() {
        // given
        val result =
            compile(
                """
                import com.mole.runtime.Aspect
                import com.mole.runtime.Before
                import com.mole.runtime.JoinPoint

                @Target(AnnotationTarget.FUNCTION)
                annotation class TargetExample

                @Aspect
                object ExampleAspect {
                    @Before(TargetExample::class)
                    fun doBefore(joinPoint: JoinPoint) {
                    }
                }

                @TargetExample
                fun test() {}
                """,
                name = "Test.kt",
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val loader = result.classLoader

        // when
        val actual =
            loader.assertAndGetField(
                className = "_0_TestKt",
                fieldName = $$"ajc$tjp_0",
            )

        // then
        val expected =
            singleFieldWithNoThisParameter(loader, "test", "TargetExample")

        assertEquals(expected, actual)
    }

    @Test
    fun `MethodSignature should be created for property getter`() {
        // given
        val result =
            compile(
                """
                import com.mole.runtime.Aspect
                import com.mole.runtime.Before
                import com.mole.runtime.JoinPoint

                @Target(AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.FUNCTION)
                annotation class TargetExample

                @Aspect
                object ExampleAspect {
                    @Before(TargetExample::class)
                    fun doBefore(joinPoint: JoinPoint) {
                    }
                }

                class Test {
                    var property: String
                        @TargetExample
                        get() = "Hello"
                        set(value) {}
                }
                """,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val loader = result.classLoader

        // when
        val actual =
            loader.assertAndGetField(
                className = "Test",
                fieldName = $$"ajc$tjp_0",
            )

        // then
        val expected =
            singleFieldWithNoAnnotationArgs(loader, "<get-property>", "TargetExample")
                .copy(
                    returnType = String::class,
                    returnTypeName = "kotlin.String",
                )
        assertEquals(expected, actual)
    }

    @Test
    fun `MethodSignature should be created for property setter`() {
        // given
        val result =
            compile(
                """
                import com.mole.runtime.Aspect
                import com.mole.runtime.Before
                import com.mole.runtime.JoinPoint

                @Target(AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.FUNCTION)
                annotation class TargetExample

                @Aspect
                object ExampleAspect {
                    @Before(TargetExample::class)
                    fun doBefore(joinPoint: JoinPoint) {
                    }
                }

                class Test {
                    var property: String
                        get() = "Hello"
                        @TargetExample
                        set(value) {}
                }
                """,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val loader = result.classLoader

        // when
        val actual =
            loader.assertAndGetField(
                className = "Test",
                fieldName = $$"ajc$tjp_0",
            )

        // then
        val expected =
            singleFieldWithNoAnnotationArgs(loader, "<set-property>", "TargetExample")
                .copy(
                    parameter =
                        listOf(
                            loader.thisParameterInfo(),
                            MethodParameter(
                                name = "value",
                                type = String::class,
                                typeName = "kotlin.String",
                                annotations = listOf(),
                                isNullable = false,
                            ),
                        ),
                )
        assertEquals(expected, actual)
    }

    @Test
    fun `MethodSignature should be created for extension functions`() {
        // given
        val result =
            compile(
                """
                import com.mole.runtime.Aspect
                import com.mole.runtime.Before
                import com.mole.runtime.JoinPoint

                @Target(AnnotationTarget.FUNCTION)
                annotation class TargetExample

                @Aspect
                object ExampleAspect {
                    @Before(TargetExample::class)
                    fun doBefore(joinPoint: JoinPoint) {
                    }
                }

                class Test

                @TargetExample
                fun Test.extensionFun() {}
                """,
                name = "Test.kt",
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val loader = result.classLoader

        // when
        val actual =
            loader.assertAndGetField(
                className = "_0_TestKt",
                fieldName = $$"ajc$tjp_0",
            )

        // then
        val expected = singleFieldWithNoAnnotationArgs(loader, "extensionFun", "TargetExample")
        assertEquals(expected, actual)
    }

    @Test
    fun `MethodSignature should be created for functions with vararg parameters`() {
        val result =
            compile(
                """
            import com.mole.runtime.Aspect
            import com.mole.runtime.Before
            import com.mole.runtime.JoinPoint

            @Target(AnnotationTarget.FUNCTION)
            annotation class TargetExample

            @Aspect
            object ExampleAspect {
                @Before(TargetExample::class)
                fun doBefore(joinPoint: JoinPoint) {}
            }

            class Test {
                @TargetExample
                fun varargFun(vararg names: String) {}
            }
            """,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val loader = result.classLoader
        val actual = loader.assertAndGetField(className = "Test", fieldName = $$"ajc$tjp_0")

        val expected =
            MethodSignature(
                methodName = "varargFun",
                annotations =
                    listOf(
                        AnnotationInfo(
                            type = loader.loadClass("TargetExample").kotlin as KClass<out Annotation>,
                            typeName = "TargetExample",
                            args = listOf(),
                            parameterNames = listOf(),
                        ),
                    ),
                parameter =
                    listOf(
                        loader.thisParameterInfo(),
                        MethodParameter(
                            name = "names",
                            type = Array<String>::class,
                            typeName = "kotlin.Array",
                            annotations = listOf(),
                            isNullable = false,
                        ),
                    ),
                returnType = Unit::class,
                returnTypeName = "kotlin.Unit",
            )
        assertEquals(expected, actual)
    }

    @Test
    fun `MethodSignature should be created for functions with default parameters`() {
        val result =
            compile(
                """
            import com.mole.runtime.Aspect
            import com.mole.runtime.Before
            import com.mole.runtime.JoinPoint

            @Target(AnnotationTarget.FUNCTION)
            annotation class TargetExample

            @Aspect
            object ExampleAspect {
                @Before(TargetExample::class)
                fun doBefore(joinPoint: JoinPoint) {}
            }

            class Test {
                @TargetExample
                fun defaultParamFun(name: String = "default") {}
            }
            """,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val loader = result.classLoader
        val actual = loader.assertAndGetField(className = "Test", fieldName = $$"ajc$tjp_0")

        val expected =
            MethodSignature(
                methodName = "defaultParamFun",
                annotations =
                    listOf(
                        AnnotationInfo(
                            type = loader.loadClass("TargetExample").kotlin as KClass<out Annotation>,
                            typeName = "TargetExample",
                            args = listOf(),
                            parameterNames = listOf(),
                        ),
                    ),
                parameter =
                    listOf(
                        loader.thisParameterInfo(),
                        MethodParameter(
                            name = "name",
                            type = String::class,
                            typeName = "kotlin.String",
                            annotations = listOf(),
                            isNullable = false,
                        ),
                    ),
                returnType = Unit::class,
                returnTypeName = "kotlin.Unit",
            )
        assertEquals(expected, actual)
    }

    @Test
    fun `MethodSignature should be created for functions returning suspend functions`() {
        val result =
            compile(
                """
            import com.mole.runtime.Aspect
            import com.mole.runtime.Before
            import com.mole.runtime.JoinPoint
            import kotlin.reflect.KClass

            @Target(AnnotationTarget.FUNCTION)
            annotation class TargetExample

            @Aspect
            object ExampleAspect {
                @Before(TargetExample::class)
                fun doBefore(joinPoint: JoinPoint) {}
            }

            class Test {
                @TargetExample
                fun returnsSuspendFun(): suspend () -> Unit {
                    return {}
                }
            }
            """,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val loader = result.classLoader
        val actual = loader.assertAndGetField(className = "Test", fieldName = $$"ajc$tjp_0")

        val expected =
            MethodSignature(
                methodName = "returnsSuspendFun",
                annotations =
                    listOf(
                        AnnotationInfo(
                            type = loader.loadClass("TargetExample").kotlin as KClass<out Annotation>,
                            typeName = "TargetExample",
                            args = listOf(),
                            parameterNames = listOf(),
                        ),
                    ),
                parameter = listOf(loader.thisParameterInfo()),
                returnType = kotlin.jvm.functions.Function1::class,
                returnTypeName = "kotlin.coroutines.SuspendFunction0",
            )

        assertEquals(expected, actual)
    }

    @Test
    fun `MethodSignature should be created for local functions`() {
        val result =
            compile(
                """
            import com.mole.runtime.Aspect
            import com.mole.runtime.Before
            import com.mole.runtime.JoinPoint

            @Target(AnnotationTarget.FUNCTION)
            annotation class TargetExample

            @Aspect
            object ExampleAspect {
                @Before(TargetExample::class)
                fun doBefore(joinPoint: JoinPoint) {}
            }

            class Test {
                fun outer() {
                    @TargetExample
                    fun localFun() {}
                    localFun()
                }
            }
            """,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val loader = result.classLoader
        val actual = loader.assertAndGetField(className = "Test", fieldName = $$"ajc$tjp_0") as MethodSignature

        assertEquals("localFun", actual.methodName)
        assertNotNull(actual)
    }

    @Test
    fun `MethodSignature should be created for operator functions`() {
        val result =
            compile(
                """
            import com.mole.runtime.Aspect
            import com.mole.runtime.Before
            import com.mole.runtime.JoinPoint

            @Target(AnnotationTarget.FUNCTION)
            annotation class TargetExample

            @Aspect
            object ExampleAspect {
                @Before(TargetExample::class)
                fun doBefore(joinPoint: JoinPoint) {}
            }

            class Point(val x: Int, val y: Int) {
                @TargetExample
                operator fun plus(other: Point): Point {
                    return Point(x + other.x, y + other.y)
                }
            }
            """,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val loader = result.classLoader
        val actual = loader.assertAndGetField(className = "Point", fieldName = $$"ajc$tjp_0")

        val pointClass = loader.loadClass("Point").kotlin
        val expected =
            MethodSignature(
                methodName = "plus",
                annotations =
                    listOf(
                        AnnotationInfo(
                            type = loader.loadClass("TargetExample").kotlin as KClass<out Annotation>,
                            typeName = "TargetExample",
                            args = listOf(),
                            parameterNames = listOf(),
                        ),
                    ),
                parameter =
                    listOf(
                        MethodParameter(
                            name = "<this>",
                            type = pointClass,
                            typeName = "Point",
                            annotations = listOf(),
                            isNullable = false,
                        ),
                        MethodParameter(
                            name = "other",
                            type = pointClass,
                            typeName = "Point",
                            annotations = listOf(),
                            isNullable = false,
                        ),
                    ),
                returnType = pointClass,
                returnTypeName = "Point",
            )
        assertEquals(expected, actual)
    }

    @Test
    fun `MethodSignature should be created for implementations of annotated interface methods`() {
        val result =
            compile(
                """
            import com.mole.runtime.Aspect
            import com.mole.runtime.Before
            import com.mole.runtime.JoinPoint

            @Target(AnnotationTarget.FUNCTION)
            annotation class TargetExample

            @Aspect
            object ExampleAspect {
                @Before(TargetExample::class)
                fun doBefore(joinPoint: JoinPoint) {}
            }
            
            interface MyInterface {
                @TargetExample
                fun work()
            }

            class MyClass : MyInterface {
                override fun work() {}
            }
            """,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val loader = result.classLoader
        val actual = loader.assertAndGetField(className = "MyClass", fieldName = $$"ajc$tjp_0")

        val expected =
            MethodSignature(
                methodName = "work",
                annotations = listOf(),
                parameter = listOf(loader.thisParameterInfo("MyClass")),
                returnType = Unit::class,
                returnTypeName = "kotlin.Unit",
            )

        assertEquals(expected, actual)
    }

    @Test
    fun `MethodSignature should be created for overriding methods with annotation on superclass`() {
        val result =
            compile(
                """
            import com.mole.runtime.Aspect
            import com.mole.runtime.Before
            import com.mole.runtime.JoinPoint

            @Target(AnnotationTarget.FUNCTION)
            annotation class TargetExample

            @Aspect
            object ExampleAspect {
                @Before(TargetExample::class)
                fun doBefore(joinPoint: JoinPoint) {}
            }

            open class Base {
                @TargetExample
                open fun work() {}
            }

            class Derived : Base() {
                override fun work() {
                    super.work()
                }
            }
            """,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val loader = result.classLoader

        // A field is created for the overridden method in the derived class
        val actual = loader.assertAndGetField(className = "Derived", fieldName = $$"ajc$tjp_1")

        val expected =
            MethodSignature(
                methodName = "work",
                annotations = listOf(),
                parameter = listOf(loader.thisParameterInfo("Derived")),
                returnType = Unit::class,
                returnTypeName = "kotlin.Unit",
            )

        assertEquals(expected, actual)

        // A field should also be created for the base class method itself
        val baseActual = loader.assertAndGetField(className = "Base", fieldName = $$"ajc$tjp_0")
        val baseExpected =
            expected.copy(
                parameter = listOf(loader.thisParameterInfo("Base")),
                annotations =
                    listOf(
                        AnnotationInfo(
                            type = loader.loadClass("TargetExample").kotlin as KClass<out Annotation>,
                            typeName = "TargetExample",
                            args = listOf(),
                            parameterNames = listOf(),
                        ),
                    ),
            )
        assertEquals(baseExpected, baseActual)
    }

    @Test
    fun `MethodSignature should be created for annotated functions in a companion object`() {
        val result =
            compile(
                """
            import com.mole.runtime.Aspect
            import com.mole.runtime.Before
            import com.mole.runtime.JoinPoint

            @Target(AnnotationTarget.FUNCTION)
            annotation class TargetExample

            @Aspect
            object ExampleAspect {
                @Before(TargetExample::class)
                fun doBefore(joinPoint: JoinPoint) {}
            }

            class MyClassWithCompanion {
                companion object {
                    @TargetExample
                    fun work() {}
                }
            }
            """,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val loader = result.classLoader

        val companionClassName = $$"MyClassWithCompanion$Companion"
        val actual = loader.assertAndGetField(className = companionClassName, fieldName = $$"ajc$tjp_0")

        val companionClass = loader.loadClass(companionClassName).kotlin
        val expected =
            MethodSignature(
                methodName = "work",
                annotations =
                    listOf(
                        AnnotationInfo(
                            type = loader.loadClass("TargetExample").kotlin as KClass<out Annotation>,
                            typeName = "TargetExample",
                            args = listOf(),
                            parameterNames = listOf(),
                        ),
                    ),
                parameter =
                    listOf(
                        MethodParameter(
                            name = "<this>",
                            type = companionClass,
                            typeName = "MyClassWithCompanion.Companion",
                            annotations = listOf(),
                            isNullable = false,
                        ),
                    ),
                returnType = Unit::class,
                returnTypeName = "kotlin.Unit",
            )
        assertEquals(expected, actual)
    }
}
