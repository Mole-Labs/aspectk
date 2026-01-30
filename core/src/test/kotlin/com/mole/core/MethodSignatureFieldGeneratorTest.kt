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
import com.tschuchort.compiletesting.KotlinCompilation
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
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

//        loader.loadClass("Test").let { clazz ->
//            val instance = clazz.getDeclaredConstructor().newInstance()
//            clazz
//                .getMethod("test1", Integer::class.java, java.lang.String::class.java)
//                .invoke(instance, 1, "nono")
//        }
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

        val expected1 = singleFieldWithDoubleClass(loader, "Test1")
        val expected2 = singleFieldWithDoubleClass(loader, "Test2")
        assertEquals(expected1, actual1)
        assertEquals(expected2, actual2)
    }
}
