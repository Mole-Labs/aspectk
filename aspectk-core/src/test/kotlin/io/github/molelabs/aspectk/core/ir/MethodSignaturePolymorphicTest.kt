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

import io.github.molelabs.aspectk.core.assertAndGetField
import io.github.molelabs.aspectk.core.compile
import io.github.molelabs.aspectk.core.thisParameterInfo
import com.tschuchort.compiletesting.KotlinCompilation
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows

@OptIn(ExperimentalCompilerApi::class)
@Suppress("UNCHECKED_CAST")
class MethodSignaturePolymorphicTest {
    @Test
    fun `MethodSignature should not be created for implementations of annotated interface methods`() {
        // given
        val result =
            compile(
                """
            import io.github.molelabs.aspectk.runtime.Aspect
            import io.github.molelabs.aspectk.runtime.Before
            import io.github.molelabs.aspectk.runtime.JoinPoint

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

        // when - then
        assertAll(
            {
                assertThrows<ClassNotFoundException> {
                    loader.assertAndGetField(className = $$$"MyInterface$$MethodSignatures", fieldName = $$"ajc$tjp_0")
                }
            },
            {
                assertThrows<ClassNotFoundException> {
                    loader.assertAndGetField(className = $$$"MyClass$$MethodSignatures", fieldName = $$"ajc$tjp_0")
                }
            },
        )
    }

    @Test
    fun `MethodSignature should not be created for overriding methods with annotation on superclass`() {
        // given
        val result =
            compile(
                """
            import io.github.molelabs.aspectk.runtime.Aspect
            import io.github.molelabs.aspectk.runtime.Before
            import io.github.molelabs.aspectk.runtime.JoinPoint

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

        // when
        val baseActual = loader.assertAndGetField(className = $$$"Base$$MethodSignatures", fieldName = $$"ajc$tjp_0")
        val baseExpected = baseClassWorkMethodSignature(loader)

        // then
        assertAll(
            {
                assertThrows<ClassNotFoundException> {
                    loader.assertAndGetField(className = $$$"Derived$$MethodSignatures", fieldName = $$"ajc$tjp_1")
                }
            },
            { assertEquals(baseExpected, baseActual) },
        )
    }

    @Test
    fun `MethodSignature should be created only for overriding methods when annotated on child `() {
        // given
        val result =
            compile(
                """
            import io.github.molelabs.aspectk.runtime.Aspect
            import io.github.molelabs.aspectk.runtime.Before
            import io.github.molelabs.aspectk.runtime.JoinPoint

            @Target(AnnotationTarget.FUNCTION)
            annotation class TargetExample

            @Aspect
            object ExampleAspect {
                @Before(TargetExample::class)
                fun doBefore(joinPoint: JoinPoint) {}
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
        val loader = result.classLoader

        // when
        val derivedActual = loader.assertAndGetField(className = $$$"Derived$$MethodSignatures", fieldName = $$"ajc$tjp_0")
        val derivedExpected = baseClassWorkMethodSignature(loader, className = "Derived")

        // then
        assertAll(
            {
                assertThrows<ClassNotFoundException> {
                    loader.assertAndGetField(className = $$$"Base$$MethodSignatures", fieldName = $$"ajc$tjp_0")
                }
            },
            { assertEquals(derivedExpected, derivedActual) },
        )
    }

    @Test
    fun `MethodSignature should not be created for overriding methods with annotation on abstract class`() {
        // given
        val result =
            compile(
                """
            import io.github.molelabs.aspectk.runtime.Aspect
            import io.github.molelabs.aspectk.runtime.Before
            import io.github.molelabs.aspectk.runtime.JoinPoint

            @Target(AnnotationTarget.FUNCTION)
            annotation class TargetExample

            @Aspect
            object ExampleAspect {
                @Before(TargetExample::class)
                fun doBefore(joinPoint: JoinPoint) {}
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
        val loader = result.classLoader

        // when- then
        assertAll(
            {
                assertThrows<ClassNotFoundException> {
                    loader.assertAndGetField(className = $$$"Base$$MethodSignatures", fieldName = $$"ajc$tjp_0")
                }
            },
            {
                assertThrows<ClassNotFoundException> {
                    loader.assertAndGetField(className = $$$"Derived$$MethodSignatures", fieldName = $$"ajc$tjp_0")
                }
            },
        )
    }

    @Test
    fun `MethodSignature should be created for default methods with annotation on interface`() {
        val result =
            compile(
                """
            import io.github.molelabs.aspectk.runtime.Aspect
            import io.github.molelabs.aspectk.runtime.Before
            import io.github.molelabs.aspectk.runtime.JoinPoint

            @Target(AnnotationTarget.FUNCTION)
            annotation class TargetExample

            @Aspect
            object ExampleAspect {
                @Before(TargetExample::class)
                fun doBefore(joinPoint: JoinPoint) {}
            }

            interface Base {
                @TargetExample
                fun work() {
                    println("Hello AspectK")
                }
            }

            class Derived : Base {
                override fun work() {}
            }
            """,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val loader = result.classLoader

        // when
        val baseActual = loader.assertAndGetField(className = $$$"Base$$MethodSignatures", fieldName = $$"ajc$tjp_0")
        val baseExpected = baseClassWorkMethodSignature(loader)

        // then
        assertAll(
            {
                assertThrows<ClassNotFoundException> {
                    loader.assertAndGetField(className = $$$"Derived$$MethodSignatures", fieldName = $$"ajc$tjp_1")
                }
            },
            { assertEquals(baseExpected, baseActual) },
        )
    }

    @Test
    fun `MethodSignature should be created for default methods with annotation on abstract class`() {
        val result =
            compile(
                """
            import io.github.molelabs.aspectk.runtime.Aspect
            import io.github.molelabs.aspectk.runtime.Before
            import io.github.molelabs.aspectk.runtime.JoinPoint

            @Target(AnnotationTarget.FUNCTION)
            annotation class TargetExample

            @Aspect
            object ExampleAspect {
                @Before(TargetExample::class)
                fun doBefore(joinPoint: JoinPoint) {}
            }

            abstract class Base {
                @TargetExample
                open fun work() {
                    println("Hello AspectK")
                }
            }

            class Derived : Base() {
                override fun work() {}
            }
            """,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val loader = result.classLoader

        // when
        val baseActual = loader.assertAndGetField(className = $$$"Base$$MethodSignatures", fieldName = $$"ajc$tjp_0")
        val baseExpected = baseClassWorkMethodSignature(loader)

        // then
        assertAll(
            {
                assertThrows<ClassNotFoundException> {
                    loader.assertAndGetField(className = $$$"Derived$$MethodSignatures", fieldName = $$"ajc$tjp_1")
                }
            },
            { assertEquals(baseExpected, baseActual) },
        )
    }

    @Test
    fun `MethodSignature should be created for both overridden methods and parent methods when inherits is true`() {
        val result =
            compile(
                """
            import io.github.molelabs.aspectk.runtime.Aspect
            import io.github.molelabs.aspectk.runtime.Before
            import io.github.molelabs.aspectk.runtime.JoinPoint

            @Target(AnnotationTarget.FUNCTION)
            annotation class TargetExample1

            @Target(AnnotationTarget.FUNCTION)
            annotation class TargetExample2

            @Aspect
            object ExampleAspect {
                @Before(TargetExample1::class, TargetExample2::class, inherits = true)
                fun doBefore(joinPoint: JoinPoint) {}
            }

            interface Base {
                @TargetExample1
                fun work1() {
                    println("Hello AspectK")
                }

                @TargetExample2
                fun work2()
            }

            class Derived : Base {
                override fun work1() {}

                override fun work2() {}
            }
            """,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val loader = result.classLoader

        // when
        val baseActual = loader.assertAndGetField(className = $$$"Base$$MethodSignatures", fieldName = $$"ajc$tjp_0")
        val baseExpected =
            baseClassWorkMethodSignature(loader, "TargetExample1").copy(
                methodName = "work1",
            )

        val derivedActual1 =
            loader.assertAndGetField(className = $$$"Derived$$MethodSignatures", fieldName = $$"ajc$tjp_1")
        val derivedActual2 =
            loader.assertAndGetField(className = $$$"Derived$$MethodSignatures", fieldName = $$"ajc$tjp_2")
        val derivedExpected1 =
            baseClassWorkMethodSignature(loader, "TargetExample1").copy(
                parameter = listOf(loader.thisParameterInfo("Derived")),
                methodName = "work1",
                annotations = listOf(),
            )
        val derivedExpected2 =
            baseClassWorkMethodSignature(loader, "TargetExample2").copy(
                parameter = listOf(loader.thisParameterInfo("Derived")),
                methodName = "work2",
                annotations = listOf(),
            )

        // then
        assertAll(
            { assertEquals(baseExpected, baseActual) },
            { assertEquals(derivedExpected1, derivedActual1) },
            { assertEquals(derivedExpected2, derivedActual2) },
        )
    }

    @Test
    fun `MethodSignature should be created for both overridden methods and parent methods only when inherits is true`() {
        val result =
            compile(
                """
            import io.github.molelabs.aspectk.runtime.Aspect
            import io.github.molelabs.aspectk.runtime.Before
            import io.github.molelabs.aspectk.runtime.JoinPoint

            @Target(AnnotationTarget.FUNCTION)
            annotation class TargetExample1

            @Target(AnnotationTarget.FUNCTION)
            annotation class TargetExample2

            @Target(AnnotationTarget.FUNCTION)
            annotation class TargetExample3

            @Aspect
            object ExampleAspect {
                @Before(TargetExample1::class, TargetExample2::class, inherits = true)
                fun doBefore1(joinPoint: JoinPoint) {}

                @Before(TargetExample1::class, inherits = true)
                fun doBefore2(joinPoint: JoinPoint) {}

                @Before(TargetExample2::class, TargetExample3::class)
                fun doBefore3(joinPoint: JoinPoint) {}
            }

            interface Base1 {
                @TargetExample1
                fun work1() {
                    println("Hello AspectK")
                }

                @TargetExample2
                @TargetExample3
                fun work2()
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
        val loader = result.classLoader

        // when
        val baseActual1 = loader.assertAndGetField(className = $$$"Base1$$MethodSignatures", fieldName = $$"ajc$tjp_0")
        val baseExpected1 =
            baseClassWorkMethodSignature(loader, "TargetExample1", "Base1").copy(
                methodName = "work1",
            )

        val derivedActual1 =
            loader.assertAndGetField(className = $$$"Derived$$MethodSignatures", fieldName = $$"ajc$tjp_1")
        val derivedActual2 =
            loader.assertAndGetField(className = $$$"Derived$$MethodSignatures", fieldName = $$"ajc$tjp_2")
        val derivedActual3 =
            loader.assertAndGetField(className = $$$"Derived$$MethodSignatures", fieldName = $$"ajc$tjp_3")
        val derivedExpected1 =
            baseClassWorkMethodSignature(loader, "TargetExample1", "Derived").copy(
                methodName = "work1",
                annotations = listOf(),
            )
        val derivedExpected2 =
            baseClassWorkMethodSignature(loader, "TargetExample2", "Derived").copy(
                methodName = "work2",
                annotations =
                listOf(),
            )

        val derivedExpected3 =
            baseClassWorkMethodSignature(loader, "TargetExample2", "Derived").copy(
                methodName = "work3",
            )

        // then
        assertAll(
            { assertEquals(baseExpected1, baseActual1) },
            { assertEquals(derivedExpected1, derivedActual1) },
            { assertEquals(derivedExpected2, derivedActual2) },
            { assertEquals(derivedExpected3, derivedActual3) },
        )
    }

    @Test
    fun `MethodSignature should be created for methods from multiple interfaces when inherits is true`() {
        // given
        val result =
            compile(
                """
            import io.github.molelabs.aspectk.runtime.Aspect
            import io.github.molelabs.aspectk.runtime.Before
            import io.github.molelabs.aspectk.runtime.JoinPoint

            @Target(AnnotationTarget.FUNCTION)
            annotation class TargetExample1

            @Target(AnnotationTarget.FUNCTION)
            annotation class TargetExample2

            @Aspect
            object ExampleAspect {
                @Before(TargetExample1::class, TargetExample2::class, inherits = true)
                fun doBefore(joinPoint: JoinPoint) {}
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
        val loader = result.classLoader

        // when
        val derivedActual1 = loader.assertAndGetField(className = $$$"Derived$$MethodSignatures", fieldName = $$"ajc$tjp_0")
        val derivedExpected1 =
            baseClassWorkMethodSignature(loader, "TargetExample1", "Derived").copy(
                methodName = "methodA",
                annotations = listOf(),
            )

        val derivedActual2 = loader.assertAndGetField(className = $$$"Derived$$MethodSignatures", fieldName = $$"ajc$tjp_1")
        val derivedExpected2 =
            baseClassWorkMethodSignature(loader, "TargetExample2", "Derived").copy(
                methodName = "methodB",
                annotations = listOf(),
            )

        // then
        assertAll(
            {
                assertThrows<ClassNotFoundException> {
                    loader.assertAndGetField(className = $$$"Base1$$MethodSignatures", fieldName = $$"ajc$tjp_0")
                }
            },
            {
                assertThrows<ClassNotFoundException> {
                    loader.assertAndGetField(className = $$$"Base2$$MethodSignatures", fieldName = $$"ajc$tjp_0")
                }
            },
            { assertEquals(derivedExpected1, derivedActual1) },
            { assertEquals(derivedExpected2, derivedActual2) },
        )
    }
}
