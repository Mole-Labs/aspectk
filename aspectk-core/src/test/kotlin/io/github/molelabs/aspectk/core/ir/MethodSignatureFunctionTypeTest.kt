package io.github.molelabs.aspectk.core.ir

import com.tschuchort.compiletesting.KotlinCompilation
import io.github.molelabs.aspectk.core.assertAndGetField
import io.github.molelabs.aspectk.core.compile
import io.github.molelabs.aspectk.core.thisParameterInfo
import io.github.molelabs.aspectk.runtime.MethodParameter
import io.github.molelabs.aspectk.runtime.MethodSignature
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions
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
                import io.github.molelabs.aspectk.runtime.Aspect
                import io.github.molelabs.aspectk.runtime.Before
                import io.github.molelabs.aspectk.runtime.JoinPoint

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
        Assertions.assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val loader = result.classLoader

        // when
        val actual =
            loader.assertAndGetField(
                className = $$$"Test$$MethodSignatures",
                fieldName = $$"ajc$tjp_0",
            )

        // then
        val expected = singleFieldWithNoAnnotationArgs(loader, "inlineFun", "TargetExample")
        Assertions.assertEquals(expected, actual)
    }

    @Test
    fun `MethodSignature should be created for suspend functions`() {
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
                    fun doBefore(joinPoint: JoinPoint) {
                    }
                }

                class Test {
                    @TargetExample
                    suspend fun suspendFun() {}
                }
                """,
            )
        Assertions.assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val loader = result.classLoader

        // when
        val actual =
            loader.assertAndGetField(
                className = $$$"Test$$MethodSignatures",
                fieldName = $$"ajc$tjp_0",
            )

        // then
        val expected = singleFieldWithNoAnnotationArgs(loader, "suspendFun", "TargetExample")
        Assertions.assertEquals(expected, actual)
    }

    @Test
    fun `MethodSignature should be created for top-level functions`() {
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
                    fun doBefore(joinPoint: JoinPoint) {
                    }
                }

                @TargetExample
                fun test() {}
                """,
                """
                import io.github.molelabs.aspectk.runtime.Aspect
                import io.github.molelabs.aspectk.runtime.Before
                import io.github.molelabs.aspectk.runtime.JoinPoint

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
        Assertions.assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
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

        Assertions.assertEquals(expected, actual)
    }

    @Test
    fun `MethodSignature should be created for property getter`() {
        // given
        val result =
            compile(
                """
                import io.github.molelabs.aspectk.runtime.Aspect
                import io.github.molelabs.aspectk.runtime.Before
                import io.github.molelabs.aspectk.runtime.JoinPoint

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
        Assertions.assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
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
        Assertions.assertEquals(expected, actual)
    }

    @Test
    fun `MethodSignature should be created for property setter`() {
        // given
        val result =
            compile(
                """
                import io.github.molelabs.aspectk.runtime.Aspect
                import io.github.molelabs.aspectk.runtime.Before
                import io.github.molelabs.aspectk.runtime.JoinPoint

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
        Assertions.assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
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
        Assertions.assertEquals(expected, actual)
    }

    @Test
    fun `MethodSignature should be created for extension functions`() {
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
                    fun doBefore(joinPoint: JoinPoint) {
                    }
                }

                class Test

                @TargetExample
                fun Test.extensionFun() {}
                """,
                name = "Test.kt",
            )
        Assertions.assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val loader = result.classLoader

        // when
        val actual =
            loader.assertAndGetField(
                className = $$$"$MethodSignatures$0_Testkt",
                fieldName = $$"ajc$tjp_0",
            )

        // then
        val expected = singleFieldWithNoAnnotationArgs(loader, "extensionFun", "TargetExample")
        Assertions.assertEquals(expected, actual)
    }

    @Test
    fun `MethodSignature should be created for functions with vararg parameters`() {
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

            class Test {
                @TargetExample
                fun varargFun(vararg names: String) {}
            }
            """,
            )
        Assertions.assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val loader = result.classLoader

        // when
        val actual = loader.assertAndGetField(className = $$$"Test$$MethodSignatures", fieldName = $$"ajc$tjp_0")
        val expected = varargFunMethodSignature(loader)

        // then
        Assertions.assertEquals(expected, actual)
    }

    @Test
    fun `MethodSignature should be created for functions with default parameters`() {
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

            class Test {
                @TargetExample
                fun defaultParamFun(name: String = "default") {}
            }
            """,
            )
        Assertions.assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val loader = result.classLoader

        // when
        val actual = loader.assertAndGetField(className = $$$"Test$$MethodSignatures", fieldName = $$"ajc$tjp_0")
        val expected = defaultParamFunMethodSignature(loader)

        // then
        Assertions.assertEquals(expected, actual)
    }

    @Test
    fun `MethodSignature should be created for functions returning suspend functions`() {
        // given
        val result =
            compile(
                """
            import io.github.molelabs.aspectk.runtime.Aspect
            import io.github.molelabs.aspectk.runtime.Before
            import io.github.molelabs.aspectk.runtime.JoinPoint
            import kotlin.reflect.KClass

            @Target(AnnotationTarget.FUNCTION)
            annotation class TargetExample

            @Aspect
            object ExampleAspect {
                @Before(TargetExample::class)
                fun doBefore(joinPoint: JoinPoint) {
                    joinPoint.signature.methodName
                    joinPoint.target
                }
            }

            class Test {
                object A {
                    val a = 1
                }
                @TargetExample
                fun returnsSuspendFun(): suspend () -> Unit {
                    Test.A.a
                    return {}
                }
            }
            """,
            )
        Assertions.assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val loader = result.classLoader

        // when
        val actual = loader.assertAndGetField(className = $$$"Test$$MethodSignatures", fieldName = $$"ajc$tjp_0")
        val expected = returnsSuspendFunMethodSignature(loader)

        // then
        Assertions.assertEquals(expected, actual)
    }

    @Test
    fun `MethodSignature should be created for local functions`() {
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

            class Test {
                fun outer() {
                    @TargetExample
                    fun localFun() {}
                    localFun()
                }
            }
            """,
            )
        Assertions.assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val loader = result.classLoader

        // when
        val actual =
            loader.assertAndGetField(
                className = $$$"Test$$MethodSignatures",
                fieldName = $$"ajc$tjp_0",
            ) as MethodSignature

        // then
        Assertions.assertEquals("localFun", actual.methodName)
        Assertions.assertNotNull(actual)
    }

    @Test
    fun `MethodSignature should be created for operator functions`() {
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

            class Point(val x: Int, val y: Int) {
                @TargetExample
                operator fun plus(other: Point): Point {
                    return Point(x + other.x, y + other.y)
                }
            }
            """,
            )
        Assertions.assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val loader = result.classLoader

        // when
        val actual = loader.assertAndGetField(className = $$$"Point$$MethodSignatures", fieldName = $$"ajc$tjp_0")
        val expected = operatorPlusMethodSignature(loader)

        // then
        Assertions.assertEquals(expected, actual)
    }

    @Test
    fun `MethodSignature should be created for annotated functions in a companion object`() {
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

            class MyClassWithCompanion {
                companion object {
                    @TargetExample
                    fun work() {}
                }
            }
            """,
            )
        Assertions.assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val loader = result.classLoader

        // when
        val actual =
            loader.assertAndGetField(
                className = $$$"MyClassWithCompanion$Companion$$MethodSignatures",
                fieldName = $$"ajc$tjp_0",
            )
        val expected = companionObjectWorkMethodSignature(loader)

        // then
        Assertions.assertEquals(expected, actual)
    }
}
