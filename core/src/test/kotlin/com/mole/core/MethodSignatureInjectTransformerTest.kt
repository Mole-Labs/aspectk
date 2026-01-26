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
    fun `MethodSignature should be created in with static field`() {
        // given
        val result =
            compile(
                """                
                class Test {
                    @JvmName("example1")
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
        val expected =
            MethodSignature(
                methodName = "test1",
                annotations =
                    listOf(
                        AnnotationInfo(
                            type = JvmName::class,
                            typeName = "kotlin.jvm.JvmName",
                            arguments = mapOf("name" to "example1"),
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
                    @JvmName("example1")
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
        val expected =
            MethodSignature(
                methodName = "test1",
                annotations =
                    listOf(
                        AnnotationInfo(
                            type = JvmName::class,
                            typeName = "kotlin.jvm.JvmName",
                            arguments = mapOf("name" to "example1"),
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

    @Test
    fun `multiple MethodSignature can be created`() {
        val result =
            compile(
                """              
                class Test {                    
                    @JvmName("example1")
                    fun test1() {}
                    
                    @JvmName("example2")
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

        val expected1 =
            MethodSignature(
                methodName = "test1",
                annotations =
                    listOf(
                        AnnotationInfo(
                            type = JvmName::class,
                            typeName = "kotlin.jvm.JvmName",
                            arguments = mapOf("name" to "example1"),
                        ),
                    ),
                parameter =
                    listOf(
                        loader.thisParameterInfo(),
                    ),
                returnType = Unit::class,
                returnTypeName = "kotlin.Unit",
            )

        val expected2 =
            expected1.copy(
                methodName = "test2",
                annotations =
                    listOf(
                        AnnotationInfo(
                            type = JvmName::class,
                            typeName = "kotlin.jvm.JvmName",
                            arguments = mapOf("name" to "example2"),
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
                class Test1 {                    
                    @JvmName("example1")
                    fun test1() {}
                }
                
                class Test2 {
                    @JvmName("example1")
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

        val expected1 =
            MethodSignature(
                methodName = "test1",
                annotations =
                    listOf(
                        AnnotationInfo(
                            type = JvmName::class,
                            typeName = "kotlin.jvm.JvmName",
                            arguments = mapOf("name" to "example1"),
                        ),
                    ),
                parameter =
                    listOf(
                        loader.thisParameterInfo("Test1"),
                    ),
                returnType = Unit::class,
                returnTypeName = "kotlin.Unit",
            )
        val expected2 =
            expected1.copy(
                parameter =
                    listOf(
                        loader.thisParameterInfo("Test2"),
                    ),
            )
        assertEquals(expected1, actual1)
        assertEquals(expected2, actual2)
    }
}
