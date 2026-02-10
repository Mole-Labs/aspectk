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
package com.mole.core.ir

import com.mole.core.compile
import com.mole.core.execute
import com.mole.core.getAspectField
import com.tschuchort.compiletesting.KotlinCompilation
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class JoinPointPolymorphicTest {
    @Test
    fun `Advice should not execute for implementations of annotated interface methods when inherits is false`() {
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
                    var executed = false
                    @Before(TargetExample::class)
                    fun doBefore(joinPoint: JoinPoint) {
                        executed = true
                    }
                }

                interface MyInterface {
                    @TargetExample
                    fun work()
                }

                class MyClass : MyInterface {
                    override fun work() {
                        // Advice should not run here
                    }
                }
                """,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        result.classLoader.execute(className = "MyClass", methodName = "work")

        val executed = result.classLoader.getAspectField()
        assertEquals(false, executed)
    }

    @Test
    fun `Advice should not execute on overriding method when annotation is on superclass and inherits is false`() {
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
                    var executionCount = 0
                    @Before(TargetExample::class)
                    fun doBefore(joinPoint: JoinPoint) {
                        executionCount++
                    }
                }

                open class Base {
                    @TargetExample
                    open fun work() {}
                }

                class Derived : Base() {
                    override fun work() {

                    }
                }
                """,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // when
        result.classLoader.execute(className = "Derived", methodName = "work")
        val executionCountField1 =
            result.classLoader.getAspectField(
                className = "ExampleAspect",
                fieldName = "executionCount",
            )

        // then
        assertEquals(0, executionCountField1)

        // when
        result.classLoader.execute(className = "Base", methodName = "work")
        val executionCountField2 =
            result.classLoader.getAspectField(
                className = "ExampleAspect",
                fieldName = "executionCount",
            )

        // then
        assertEquals(1, executionCountField2)
    }

    @Test
    fun `Advice should execute only on overriding method when annotated on child and inherits is false`() {
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
                    var executionCount = 0
                    @Before(TargetExample::class)
                    fun doBefore(joinPoint: JoinPoint) {
                        executionCount++
                    }
                }

                open class Base {
                    open fun work() {}
                }

                class Derived : Base() {
                    @TargetExample
                    override fun work() {
                        super.work()
                    }
                }
                """,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // when
        result.classLoader.execute(className = "Derived", methodName = "work")
        val executionCountField1 =
            result.classLoader.getAspectField(
                className = "ExampleAspect",
                fieldName = "executionCount",
            )

        // then
        assertEquals(1, executionCountField1)

        // when
        result.classLoader.execute(className = "Base", methodName = "work")
        val executionCountField2 =
            result.classLoader.getAspectField(
                className = "ExampleAspect",
                fieldName = "executionCount",
            )

        // then
        assertEquals(1, executionCountField2)
    }

    @Test
    fun `Advice should not execute for overriding method of annotated abstract method when inherits is false`() {
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
                    var executed = false
                    @Before(TargetExample::class)
                    fun doBefore(joinPoint: JoinPoint) {
                        executed = true
                    }
                }

                abstract class Base {
                    @TargetExample
                    abstract fun work()
                }

                class Derived : Base() {
                    override fun work() {}
                }
                """,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        result.classLoader.execute(className = "Derived", methodName = "work")

        val executedField = result.classLoader.getAspectField()
        assertEquals(false, executedField)
    }

    @Test
    fun `Advice should execute on both parent and overriding methods when inherits is true`() {
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
                var executionCount = 0
                @Before(TargetExample::class, inherits = true)
                fun doBefore(joinPoint: JoinPoint) {
                    executionCount++
                }
            }

            interface Base {
                @TargetExample
                fun work()
            }

            class Derived : Base {
                override fun work() {}
            }
            """,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // when
        result.classLoader.execute(className = "Derived", methodName = "work")
        val executionCountField =
            result.classLoader.getAspectField(
                className = "ExampleAspect",
                fieldName = "executionCount",
            )

        // then
        assertEquals(1, executionCountField)
    }

    @Test
    fun `Advice should execute for methods from multiple interfaces when inherits is true`() {
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

            @Aspect
            object ExampleAspect {
                var executionCount = 0
                @Before(TargetExample1::class, TargetExample2::class, inherits = true)
                fun doBefore(joinPoint: JoinPoint) {
                    executionCount++
                }
            }

            interface Base1 {
                @TargetExample1
                fun methodA()
            }

            interface Base2 {
                @TargetExample2
                fun methodB()
            }

            class Derived : Base1, Base2 {
                override fun methodA() {}
                override fun methodB() {}
            }
            """,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // when
        result.classLoader.execute(className = "Derived", methodName = "methodA")
        val executionCountField1 = result.classLoader.getAspectField(fieldName = "executionCount")

        // then
        assertEquals(1, executionCountField1)

        // when
        result.classLoader.execute(className = "Derived", methodName = "methodB")
        val executionCountField2 = result.classLoader.getAspectField(fieldName = "executionCount")

        // then
        assertEquals(2, executionCountField2)
    }

    @Test
    fun `Advice should execute correctly based on complex inheritance and multiple advice rules`() {
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
                    var executionCount1 = 0
                    var executionCount2 = 0
                    var executionCount3 = 0

                    @Before(TargetExample1::class, TargetExample2::class, inherits = true)
                    fun doBefore1(joinPoint: JoinPoint) { executionCount1++ }

                    @Before(TargetExample1::class, inherits = true)
                    fun doBefore2(joinPoint: JoinPoint) { executionCount2++ }

                    @Before(TargetExample2::class, TargetExample3::class) // inherits = false
                    fun doBefore3(joinPoint: JoinPoint) { executionCount3++ }

                    fun resetCounters() {
                        executionCount1 = 0
                        executionCount2 = 0
                        executionCount3 = 0
                    }
                }

                interface Base1 {
                    @TargetExample1
                    fun work1() {}

                    @TargetExample2 @TargetExample3
                    fun work2() {}
                }

                abstract class Base2 : Base1 {
                    @TargetExample3
                    abstract fun work3()
                }

                class Derived : Base2() {
                    override fun work1() {}
                    override fun work2() {}

                    @TargetExample2
                    override fun work3() {}
                }
                """,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // Test work1()
        result.classLoader.execute(className = "Derived", methodName = "work1")
        val count1Field1 = result.classLoader.getAspectField(fieldName = "executionCount1")
        val count2Field1 = result.classLoader.getAspectField(fieldName = "executionCount2")
        val count3Field1 = result.classLoader.getAspectField(fieldName = "executionCount3")
        assertEquals(1, count1Field1)
        assertEquals(1, count2Field1)
        assertEquals(0, count3Field1)

        // Test work2()
        result.classLoader.execute(className = "Derived", methodName = "work2")
        val count1Field2 = result.classLoader.getAspectField(fieldName = "executionCount1")
        val count2Field2 = result.classLoader.getAspectField(fieldName = "executionCount2")
        val count3Field2 = result.classLoader.getAspectField(fieldName = "executionCount3")
        assertEquals(2, count1Field2)
        assertEquals(1, count2Field2)
        assertEquals(0, count3Field2)

        // Test work3()
        result.classLoader.execute(className = "Derived", methodName = "work3")
        val count1Field3 = result.classLoader.getAspectField(fieldName = "executionCount1")
        val count2Field3 = result.classLoader.getAspectField(fieldName = "executionCount2")
        val count3Field3 = result.classLoader.getAspectField(fieldName = "executionCount3")
        assertEquals(3, count1Field3)
        assertEquals(1, count2Field3)
        assertEquals(1, count3Field3)
    }
}
