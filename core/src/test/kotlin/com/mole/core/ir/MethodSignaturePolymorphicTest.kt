package com.mole.core.ir

import com.mole.core.assertAndGetField
import com.mole.core.compile
import com.tschuchort.compiletesting.KotlinCompilation
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
@Suppress("UNCHECKED_CAST")
class MethodSignaturePolymorphicTest {
    @Test
    fun `MethodSignature should be created for implementations of annotated interface methods`() {
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

        // when
        val actual = loader.assertAndGetField(className = $$$"MyClass$$MethodSignatures", fieldName = $$"ajc$tjp_0")
        val expected = interfaceWorkMethodSignature(loader)

        // then
        assertEquals(expected, actual)
    }

    @Test
    fun `MethodSignature should be created for overriding methods with annotation on superclass`() {
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
        val actual = loader.assertAndGetField(className = $$$"Derived$$MethodSignatures", fieldName = $$"ajc$tjp_1")
        val expected = derivedClassWorkMethodSignature(loader)

        val baseActual = loader.assertAndGetField(className = $$$"Base$$MethodSignatures", fieldName = $$"ajc$tjp_0")
        val baseExpected = baseClassWorkMethodSignature(loader)

        // then
        assertEquals(expected, actual)
        assertEquals(baseExpected, baseActual)
    }
}
