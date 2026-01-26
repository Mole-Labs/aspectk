package com.mole.core

import com.mole.runtime.AnnotationInfo
import com.mole.runtime.MethodParameter
import com.mole.runtime.MethodSignature
import com.tschuchort.compiletesting.KotlinCompilation
import org.jetbrains.annotations.NotNull
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class MethodSignatureInjectTransformerTest {
    @Test
    fun `MethodSignature should be created in existing companion object`() {
        // given
        val result =
            compile(
                """                
                class Test {
                    companion object {
                    }
                    
                    @JvmName("test2")
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
                className = $$"Test$Companion",
                fieldName = $$"ajc$tjp_0",
            )

        // then
        val expected =
            MethodSignature(
                methodName = "test1",
                annotations =
                    listOf(
                        AnnotationInfo(
                            type = JvmName::class,
                            typeName = "kotlin.jvm.JvmName",
                            arguments = mapOf("name" to "test2"),
                        ),
                    ),
                parameter =
                    listOf(
                        loader.thisParameterInfo(),
                    ),
                returnType = Unit::class,
                returnTypeName = "kotlin.Unit",
            )
        assertEquals(expected, actual)
    }

    @Test
    fun `MethodSignature contains annotations of method parameters`() {
        // given
        val result =
            compile(
                """              
                import org.jetbrains.annotations.NotNull

                class Test {
                    companion object {
                    }
                    
                    @JvmName("test2")
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
                className = $$"Test$Companion",
                fieldName = $$"ajc$tjp_0",
            )

        // then
        val expected =
            MethodSignature(
                methodName = "test1",
                annotations =
                    listOf(
                        AnnotationInfo(
                            type = JvmName::class,
                            typeName = "kotlin.jvm.JvmName",
                            arguments = mapOf("name" to "test2"),
                        ),
                    ),
                parameter =
                    listOf(
                        loader.thisParameterInfo(),
                        MethodParameter(
                            name = "arg1",
                            type = Int::class,
                            typeName = "kotlin.Int",
                            annotations = listOf(),
                            isNullable = true,
                        ),
                        MethodParameter(
                            name = "arg2",
                            type = String::class,
                            typeName = "kotlin.String",
                            annotations =
                                listOf(
                                    AnnotationInfo(
                                        type = NotNull::class,
                                        typeName = "org.jetbrains.annotations.NotNull",
                                        arguments = mapOf("value" to "test"),
                                    ),
                                ),
                            isNullable = false,
                        ),
                    ),
                returnType = Unit::class,
                returnTypeName = "kotlin.Unit",
            )
        assertEquals(expected, actual)
    }
}
