package com.mole.core.ir

import com.mole.core.assertAndGetField
import com.mole.core.compile
import com.tschuchort.compiletesting.KotlinCompilation
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows

@OptIn(ExperimentalCompilerApi::class)
@Suppress("UNCHECKED_CAST")
class MethodSignatureGenericTest {
    @Test
    fun `generic type arguments of MethodSignature should be erased to Any`() {
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
                    fun <T> test1(arg1: T) {
                    }
                }
                """,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // when
        val loader = result.classLoader
        val actual =
            loader.assertAndGetField(
                className = $$$"Test$$MethodSignatures",
                fieldName = $$"ajc$tjp_0",
            )

        // then
        val expected = singleGenericField(loader, nullable = true)
        assertEquals(expected, actual)
    }

    @Test
    fun `generic nested type arguments of MethodSignature should be erased to Any`() {
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
                    fun <T, R> test1(arg1: T, arg2: List<R>) {
                    }
                }
                """,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // when
        val loader = result.classLoader
        val actual =
            loader.assertAndGetField(
                className = $$$"Test$$MethodSignatures",
                fieldName = $$"ajc$tjp_0",
            )

        // then
        val expected = doubleGenericField(loader, nullable = true)
        assertEquals(expected, actual)
    }

    @Test
    fun `reified generic type type arguments of MethodSignature should be erased to Any`() {
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
                    inline fun <reified T, R> test1(arg1: T, arg2: List<R>) {
                    }
                }
                """,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // when
        val loader = result.classLoader
        val actual =
            loader.assertAndGetField(
                className = $$$"Test$$MethodSignatures",
                fieldName = $$"ajc$tjp_0",
            )

        // then
        val expected = doubleGenericField(loader, nullable = true)
        assertEquals(expected, actual)
    }

    @Test
    fun `generic types with an upper bound should be erased to that upper bound`() {
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
                    fun <T> test1(arg1: T) where T : Comparable<T>, T : java.io.Serializable {
                    }
                }
                """,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // when
        val loader = result.classLoader
        val actual =
            loader.assertAndGetField(
                className = $$$"Test$$MethodSignatures",
                fieldName = $$"ajc$tjp_0",
            )

        // then
        val expected = singleGenericField(loader, Comparable::class, "kotlin.Comparable")
        assertEquals(expected, actual)
    }

    @Test
    fun `star-projection should be erased to Any`() {
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
                    fun test1(arg1: List<*>) {
                    }
                }
                """,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // when
        val loader = result.classLoader
        val actual =
            loader.assertAndGetField(
                className = $$$"Test$$MethodSignatures",
                fieldName = $$"ajc$tjp_0",
            )

        // then
        val expected = singleGenericField(loader, List::class, "kotlin.collections.List")
        assertEquals(expected, actual)
    }

    @Test
    fun `MethodSignature for inherited generic methods should reflect specialized types`() {
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
                    @Before(TargetExample::class, inherits = true)
                    fun doBefore(joinPoint: JoinPoint) {
                        System.out.println(joinPoint.args)
                    }
                }

                abstract class Box<T> {
                    @TargetExample("put")
                    abstract fun put(arg1: T)
                    abstract fun get(): T
                }

                class IntBox : Box<Int>() {
                    override fun put(arg1: Int) {}
                    override fun get(): Int = 0
                }

                class StringBox : Box<String>() {
                    override fun put(arg1: String) {}
                    override fun get(): String = ""
                }

                class AnyBox : Box<Any>() {
                    override fun put(arg1: Any) {}
                    override fun get(): Any = Any()
                }
                """,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val loader = result.classLoader

        // when
        val actual1 =
            loader.assertAndGetField(
                className = $$$"IntBox$$MethodSignatures",
                fieldName = $$"ajc$tjp_0",
            )
        val expected1 =
            singleGenericField(loader, Int::class, "kotlin.Int", nullable = false, "IntBox").copy(
                methodName = "put",
                annotations = listOf(),
            )

        val actual2 =
            loader.assertAndGetField(
                className = $$$"StringBox$$MethodSignatures",
                fieldName = $$"ajc$tjp_1",
            )
        val expected2 =
            singleGenericField(
                loader,
                String::class,
                "kotlin.String",
                nullable = false,
                "StringBox",
            ).copy(
                methodName = "put",
                annotations = listOf(),
            )

        val actual3 =
            loader.assertAndGetField(
                className = $$$"AnyBox$$MethodSignatures",
                fieldName = $$"ajc$tjp_2",
            )
        val expected3 =
            singleGenericField(loader, Any::class, "kotlin.Any", nullable = false, "AnyBox").copy(
                methodName = "put",
                annotations = listOf(),
            )

        // then
        assertAll(
            {
                assertThrows<ClassNotFoundException> {
                    loader.assertAndGetField(
                        className = $$$"Box$$MethodSignatures",
                        fieldName = $$"ajc$tjp_0",
                    )
                }
            },
            { assertEquals(expected1, actual1) },
            { assertEquals(expected2, actual2) },
            { assertEquals(expected3, actual3) },
        )
    }

    @Test
    fun `MethodSignature for type variance should reflect specialized types`() {
        // given
        val result =
            compile(
                """
                import com.mole.runtime.Aspect
                import com.mole.runtime.Before
                import com.mole.runtime.JoinPoint

                @Target(AnnotationTarget.FUNCTION)
                annotation class TargetExample(val name:String)

                @Aspect
                object ExampleAspect {
                    @Before(TargetExample::class, inherits = true)
                    fun doBefore(joinPoint: JoinPoint) {}
                }

                interface Consumer<in T> {
                    @TargetExample("consume")
                    fun consume(item: T)
                }

                class StringConsumer : Consumer<String> {
                    override fun consume(item: String) {}
                }

                interface Producer<out T> {
                    @TargetExample("produce")
                    fun produce(): T
                }

                class StringProducer : Producer<String> {
                    override fun produce(): String = ""
                }
                """,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val loader = result.classLoader

        // when
        val actual1 =
            loader.assertAndGetField(
                className = $$$"StringConsumer$$MethodSignatures",
                fieldName = $$"ajc$tjp_0",
            )
        val expected1 = stringConsumerConsumeMethodSignature(loader)

        val actual2 =
            loader.assertAndGetField(
                className = $$$"StringProducer$$MethodSignatures",
                fieldName = $$"ajc$tjp_1",
            )
        val expected2 = stringProducerProduceMethodSignature(loader)

        // then
        assertAll(
            {
                assertThrows<ClassNotFoundException> {
                    loader.assertAndGetField(
                        className = $$$"Consumer$$MethodSignatures",
                        fieldName = $$"ajc$tjp_0",
                    )
                }
            },
            {
                assertThrows<ClassNotFoundException> {
                    loader.assertAndGetField(
                        className = $$$"Producer$$MethodSignatures",
                        fieldName = $$"ajc$tjp_0",
                    )
                }
            },
            { assertEquals(expected1, actual1) },
            { assertEquals(expected2, actual2) },
        )
    }

    @Test
    fun `MethodSignature for complex generic types should reflect erased types`() {
        // given
        val result =
            compile(
                """
                import com.mole.runtime.Aspect
                import com.mole.runtime.Before
                import com.mole.runtime.JoinPoint
                import java.util.List
                import java.util.Map

                @Target(AnnotationTarget.FUNCTION)
                annotation class TargetExample(val name:String)

                @Aspect
                object ExampleAspect {
                    @Before(TargetExample::class)
                    fun doBefore(joinPoint: JoinPoint) {}
                }

                class TestClass {
                    @TargetExample("process")
                    fun <T : Number, R> process(input: List<T>, output: Map<String, R>) {}
                }
                """,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val loader = result.classLoader

        // when
        val actual =
            loader.assertAndGetField(
                className = $$$"TestClass$$MethodSignatures",
                fieldName = $$"ajc$tjp_0",
            )
        // then
        val expected = complexGenericMethodSignature(loader)
        assertEquals(expected, actual)
    }
}
