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

import com.mole.core.ir.companionObjectWorkMethodSignature
import com.mole.core.ir.defaultParamFunMethodSignature
import com.mole.core.ir.operatorPlusMethodSignature
import com.mole.core.ir.returnsSuspendFunMethodSignature
import com.mole.core.ir.singleFieldWithNoAnnotationArgs
import com.mole.core.ir.singleFieldWithNoThisParameter
import com.mole.core.ir.varargFunMethodSignature
import com.mole.runtime.MethodParameter
import com.mole.runtime.MethodSignature
import com.tschuchort.compiletesting.KotlinCompilation
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
@Suppress("UNCHECKED_CAST")
class MethodSignatureFunctionTypeTest {
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
                className = $$$"Test$$MethodSignatures",
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
                className = $$$"Test$$MethodSignatures",
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
                """
                import com.mole.runtime.Aspect
                import com.mole.runtime.Before
                import com.mole.runtime.JoinPoint

                @Target(AnnotationTarget.FUNCTION)
                annotation class TargetExample1

                @Aspect
                object ExampleAspect1 {
                    @Before(TargetExample1::class)
                    fun doBefore(joinPoint: JoinPoint) {
                    }
                }

                @TargetExample1
                fun test1() {}
                """.trimIndent(),
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val loader = result.classLoader

        // when
        val actual =
            loader.assertAndGetField(
                className = $$$"$MethodSignatures$0_aspectk-testkt",
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
                className = $$$"Test$$MethodSignatures",
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
                className = $$$"Test$$MethodSignatures",
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
                className = $$$"$MethodSignatures",
                fieldName = $$"ajc$tjp_0",
            )

        // then
        val expected = singleFieldWithNoAnnotationArgs(loader, "extensionFun", "TargetExample")
        assertEquals(expected, actual)
    }

    @Test
    fun `MethodSignature should be created for functions with vararg parameters`() {
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

        // when
        val actual = loader.assertAndGetField(className = $$$"Test$$MethodSignatures", fieldName = $$"ajc$tjp_0")
        val expected = varargFunMethodSignature(loader)

        // then
        assertEquals(expected, actual)
    }

    @Test
    fun `MethodSignature should be created for functions with default parameters`() {
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

        // when
        val actual = loader.assertAndGetField(className = $$$"Test$$MethodSignatures", fieldName = $$"ajc$tjp_0")
        val expected = defaultParamFunMethodSignature(loader)

        // then
        assertEquals(expected, actual)
    }

    @Test
    fun `MethodSignature should be created for functions returning suspend functions`() {
        // given
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

        // when
        val actual = loader.assertAndGetField(className = $$$"Test$$MethodSignatures", fieldName = $$"ajc$tjp_0")
        val expected = returnsSuspendFunMethodSignature(loader)

        // then
        assertEquals(expected, actual)
    }

    @Test
    fun `MethodSignature should be created for local functions`() {
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

        // when
        val actual =
            loader.assertAndGetField(
                className = $$$"Test$$MethodSignatures",
                fieldName = $$"ajc$tjp_0",
            ) as MethodSignature

        // then
        assertEquals("localFun", actual.methodName)
        assertNotNull(actual)
    }

    @Test
    fun `MethodSignature should be created for operator functions`() {
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

        // when
        val actual = loader.assertAndGetField(className = $$$"Point$$MethodSignatures", fieldName = $$"ajc$tjp_0")
        val expected = operatorPlusMethodSignature(loader)

        // then
        assertEquals(expected, actual)
    }

    @Test
    fun `MethodSignature should be created for annotated functions in a companion object`() {
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

        // when
        val actual =
            loader.assertAndGetField(
                className = $$$"MyClassWithCompanion$Companion$$MethodSignatures",
                fieldName = $$"ajc$tjp_0",
            )
        val expected = companionObjectWorkMethodSignature(loader)

        // then
        assertEquals(expected, actual)
    }
}
