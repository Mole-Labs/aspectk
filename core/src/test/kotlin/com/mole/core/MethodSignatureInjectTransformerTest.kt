package com.mole.core

import com.mole.runtime.MethodParameter
import com.mole.runtime.MethodSignature
import com.tschuchort.compiletesting.KotlinCompilation
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class MethodSignatureInjectTransformerTest {
    @Test
    fun `MethodSignature must be created in existing companion object`() {
        // given
        val result =
            compile(
                """
                import com.mole.runtime.Before
                
                class Test {
                    companion object {
                    }
                    
                    @Before
                    fun test1() {
                    }
                }
                """.trimIndent(),
            )

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // when
        val loader = result.classLoader
        val companionClass = loader.loadClass($$"Test$Companion")
        val injectedField = companionClass.getDeclaredField($$"ajc$tjp_0")
        injectedField.setAccessible(true)

        // then
        val expected =
            MethodSignature(
                methodName = "test1",
                parameter =
                    listOf(
                        MethodParameter(
                            name = "<this>",
                            type = loader.loadClass("Test").kotlin,
                            typeName = "Test",
                            annotations = listOf(),
                            annotationsName = listOf(),
                            isNullable = false,
                        ),
                    ),
                returnType = Unit::class,
                returnTypeName = "kotlin.Unit",
            )
        assertNotNull(injectedField)
        val actual = injectedField.get(null)
        assertEquals(expected, actual)
    }

    @Test
    fun `MethodSignature must contain annotations of method parameters`() {
        // given
        val result =
            compile(
                """
                import com.mole.runtime.Before
                
                class Test {
                    companion object {
                    }
                    
                    @Before
                    fun test1(
                        arg1:Int,
                        @Suppress("test") arg2:String
                    ) {
                    }
                }
                """.trimIndent(),
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // when
        val loader = result.classLoader
        val companionClass = loader.loadClass($$"Test$Companion")
        val injectedField = companionClass.getDeclaredField($$"ajc$tjp_0")
        injectedField.setAccessible(true)

        // then
        val expected =
            MethodSignature(
                methodName = "test1",
                parameter =
                    listOf(
                        MethodParameter(
                            name = "<this>",
                            type = loader.loadClass("Test").kotlin,
                            typeName = "Test",
                            annotations = listOf(),
                            annotationsName = listOf(),
                            isNullable = false,
                        ),
                    ),
                returnType = Unit::class,
                returnTypeName = "kotlin.Unit",
            )
        assertNotNull(injectedField)
        val actual = injectedField.get(null)
        assertEquals(expected, actual)
    }
}
