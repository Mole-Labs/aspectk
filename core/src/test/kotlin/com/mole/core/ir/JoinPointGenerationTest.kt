package com.mole.core.ir

import com.mole.core.compile
import com.mole.core.execute
import com.tschuchort.compiletesting.KotlinCompilation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
@Suppress("UNCHECKED_CAST")
class JoinPointGenerationTest {
    @Test
    fun `JoinPoint should be injected into aspect`() {
        // given
        val result =
            compile(
                """
                import com.mole.runtime.Aspect
                import com.mole.runtime.Before
                import com.mole.runtime.JoinPoint
                import org.junit.jupiter.api.Assertions.assertEquals
                import kotlin.test.assertIs
                
                @Target(AnnotationTarget.FUNCTION)
                annotation class TargetExample(
                    val name:String
                )

                @Aspect
                object ExampleAspect {
                    var executed:Boolean = false

                    @Before(TargetExample::class)
                    fun doBefore(joinPoint: JoinPoint) {
                        executed = true
                        assertEquals(1, joinPoint.args.size)
                        assertIs<Test>(joinPoint.target)
                    }
                }

                class Test {
                    @TargetExample("example1")
                    fun test1() {
                        assertEquals(ExampleAspect.executed, true)
                    }
                }
                """,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // when - then
        result.classLoader.execute()
    }

    @Test
    fun `JoinPoint should be injected with arguments into aspect`() {
        // given
        val result =
            compile(
                """
                import com.mole.runtime.Aspect
                import com.mole.runtime.Before
                import com.mole.runtime.JoinPoint
                import org.junit.jupiter.api.Assertions.assertEquals
                import kotlin.test.assertIs
                
                @Target(AnnotationTarget.FUNCTION)
                annotation class TargetExample(
                    val name:String
                )

                @Aspect
                object ExampleAspect {
                    var executed:Boolean = false

                    @Before(TargetExample::class)
                    fun doBefore(joinPoint: JoinPoint) {
                        executed = true
                        assertEquals(joinPoint.args.size, 3)
                        assertIs<Test>(joinPoint.target)

                        assertEquals("hello", joinPoint.args[1])
                        assertEquals("aspectk", joinPoint.args[2])
                    }
                }

                class Test {
                    @TargetExample("example1")
                    fun test1(arg1:String, arg2:String) {
                        assertEquals(ExampleAspect.executed, true)
                    }
                }
                """,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // when - then
        result.classLoader.execute("hello", "aspectk")
    }

    @Test
    fun `JoinPoint should be injected with reference type arguments into aspect`() {
        // given
        val result =
            compile(
                """
                import com.mole.runtime.Aspect
                import com.mole.runtime.Before
                import com.mole.runtime.JoinPoint
                import org.junit.jupiter.api.Assertions.assertEquals
                import kotlin.test.assertIs
                import kotlinx.coroutines.CoroutineScope
                
                @Target(AnnotationTarget.FUNCTION)
                annotation class TargetExample(
                    val name:String
                )

                @Aspect
                object ExampleAspect {
                    var executed:Boolean = false

                    @Before(TargetExample::class)
                    fun doBefore(joinPoint: JoinPoint) {
                        executed = true
                        assertEquals(3, joinPoint.args.size)
                        assertIs<Test>(joinPoint.target)

                        assertEquals(listOf("hello"), joinPoint.args[1])
                        assertIs<CoroutineScope>(joinPoint.args[2])
                    }
                }

                class Test {
                    @TargetExample("example1")
                    fun test1(arg1:List<String>, arg2:CoroutineScope) {
                        assertEquals(true, ExampleAspect.executed)
                    }
                }
                """,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // when - then
        result.classLoader.execute(listOf("hello"), CoroutineScope(Dispatchers.IO))
    }

    @Test
    fun `JoinPoint with generic argument should be injected into aspect with Any`() {
        // given
        val result =
            compile(
                """
                import com.mole.runtime.Aspect
                import com.mole.runtime.Before
                import com.mole.runtime.JoinPoint
                import org.junit.jupiter.api.Assertions.assertEquals
                import kotlin.test.assertIs
                import kotlinx.coroutines.CoroutineScope
                
                @Target(AnnotationTarget.FUNCTION)
                annotation class TargetExample(
                    val name:String
                )

                @Aspect
                object ExampleAspect {
                    var executed:Boolean = false

                    @Before(TargetExample::class)
                    fun doBefore(joinPoint: JoinPoint) {
                        executed = true
                        assertEquals(3, joinPoint.args.size)
                        assertIs<Test>(joinPoint.target)

                        assertIs<List<Any?>>(joinPoint.args[1])
                        assertEquals(listOf("hello"), joinPoint.args[1])

                        assertIs<Any?>(joinPoint.args[2])
                        assertEquals(123, joinPoint.args[2])
                    }
                }

                class Test {
                    @TargetExample("example1")
                    inline fun <T, reified R> test1(arg1:List<T>, arg2:R) {
                        assertEquals(true, ExampleAspect.executed)
                    }
                }
                """,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // when - then
        result.classLoader.execute(listOf("hello"), 123)
    }

    @Test
    fun `JoinPoint should be injected with nullable arguments into`() {
        // given
        val result =
            compile(
                """
                import com.mole.runtime.Aspect
                import com.mole.runtime.Before
                import com.mole.runtime.JoinPoint
                import org.junit.jupiter.api.Assertions.assertEquals
                import kotlin.test.assertIs
                
                @Target(AnnotationTarget.FUNCTION)
                annotation class TargetExample(
                    val name:String
                )

                @Aspect
                object ExampleAspect {
                    var executed:Boolean = false

                    @Before(TargetExample::class)
                    fun doBefore(joinPoint: JoinPoint) {
                        executed = true
                        assertEquals(joinPoint.args.size, 3)
                        assertIs<Test>(joinPoint.target)

                        assertEquals("notNullArg", joinPoint.args[1])
                        assertEquals(null, joinPoint.args[2])
                    }
                }

                class Test {
                    @TargetExample("example1")
                    fun test1(arg1:String, arg2:String?) {
                        assertEquals(true, ExampleAspect.executed)
                    }
                }
                """,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // when - then
        result.classLoader.execute("notNullArg", null)
    }

    @Test
    fun `JoinPoint should be injected with vararg arguments`() {
        // given
        val result =
            compile(
                """
                import com.mole.runtime.Aspect
                import com.mole.runtime.Before
                import com.mole.runtime.JoinPoint
                import org.junit.jupiter.api.Assertions.assertEquals
                import kotlin.test.assertIs

                @Target(AnnotationTarget.FUNCTION)
                annotation class TargetExample(
                    val name:String
                )

                @Aspect
                object ExampleAspect {
                    var executed:Boolean = false

                    @Before(TargetExample::class)
                    fun doBefore(joinPoint: JoinPoint) {
                        executed = true
                        assertEquals(joinPoint.args.size, 3)
                        assertIs<Test>(joinPoint.target)
                        assertEquals(null, joinPoint.args[2])
                        
                        val expected = arrayOf("arg1", "arg2")
                        val actual = (joinPoint.args[1] as Array<Any>)
                        assertEquals(expected[0], actual[0])
                        assertEquals(expected[1], actual[1])
                    }
                }

                class Test {
                    @TargetExample("example1")
                    fun test1(vararg arg1:String, arg2:String?) {
                        assertEquals(true, ExampleAspect.executed)
                    }
                }
                """,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // when - then
        result.classLoader.execute(arrayOf("arg1", "arg2"), null)
    }

    @Test
    fun `JoinPoint should capture function type argument`() {
        // given
        val result =
            compile(
                """
                import com.mole.runtime.Aspect
                import com.mole.runtime.Before
                import com.mole.runtime.JoinPoint
                import org.junit.jupiter.api.Assertions.assertEquals
                import kotlin.test.assertIs

                @Target(AnnotationTarget.FUNCTION)
                annotation class TargetExample(
                    val name:String
                )

                @Aspect
                object ExampleAspect {
                    var executed:Boolean = false

                    @Before(TargetExample::class)
                    fun doBefore(joinPoint: JoinPoint) {
                        executed = true
                        assertEquals(2, joinPoint.args.size)
                        assertIs<Test>(joinPoint.target)
                        assertIs<Function1<String, Unit>>(joinPoint.args[1])
                    }
                }

                class Test {
                    @TargetExample("example1")
                    fun test1(block: (String) -> Unit) {
                        block("test")
                        assertEquals(true, ExampleAspect.executed)
                    }
                }
                """,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // when - then
        result.classLoader.execute({ arg: String -> assertEquals("test", arg) })
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `JoinPoint should capture suspend function type argument`() {
        // given
        val result =
            compile(
                """
                import com.mole.runtime.Aspect
                import com.mole.runtime.Before
                import com.mole.runtime.JoinPoint
                import kotlinx.coroutines.CoroutineScope
                import org.junit.jupiter.api.Assertions.assertEquals
                import kotlin.test.assertIs
                import kotlinx.coroutines.launch

                @Target(AnnotationTarget.FUNCTION)
                annotation class TargetExample(
                    val name:String
                )

                @Aspect
                object ExampleAspect {
                    var executed:Boolean = false

                    @Before(TargetExample::class)
                    fun doBefore(joinPoint: JoinPoint) {
                        executed = true
                        assertEquals(3, joinPoint.args.size)
                        assertIs<Test>(joinPoint.target)
                        assertIs<Function1<String, Unit>>(joinPoint.args[2])
                    }
                }

                class Test {
                    fun test1(scope:CoroutineScope) {
                        scope.launch {
                            suspendHighOrderFun(scope) {  arg ->
                                assertEquals(arg, "test")
                            }
                        }
                    }
                    
                    @TargetExample("example1")
                    suspend fun suspendHighOrderFun(scope:CoroutineScope, block: suspend (String) -> Unit, ) {
                        scope.launch {
                            block("test")
                            assertEquals(true, ExampleAspect.executed)
                        }
                    }
                }
                """,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // when - then
        runTest {
            val scope = CoroutineScope(StandardTestDispatcher())
            result.classLoader.execute(scope)
            advanceUntilIdle()
        }
    }

    @Test
    fun `JoinPoint should capture enum argument`() {
        // given
        val result =
            compile(
                """
                import com.mole.runtime.Aspect
                import com.mole.runtime.Before
                import com.mole.runtime.JoinPoint
                import org.junit.jupiter.api.Assertions.assertEquals
                import kotlin.test.assertIs

                @Target(AnnotationTarget.FUNCTION)
                annotation class TargetExample(
                    val name:String
                )

                enum class TestEnum {
                    VALUE_A, VALUE_B
                }

                @Aspect
                object ExampleAspect {
                    var executed:Boolean = false

                    @Before(TargetExample::class)
                    fun doBefore(joinPoint: JoinPoint) {
                        executed = true
                        assertEquals(2, joinPoint.args.size)
                        assertIs<Test>(joinPoint.target)
                        assertEquals("VALUE_B", (joinPoint.args[1] as Enum<*>).name)
                        assertIs<TestEnum>(joinPoint.args[1])
                    }
                }

                class Test {
                    @TargetExample("example1")
                    fun test1(arg: TestEnum) {
                        assertEquals(true, ExampleAspect.executed)
                    }
                }
                """,
            )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // when - then
        val testEnumClass = result.classLoader.loadClass("TestEnum")
        val enumValue = testEnumClass.getField("VALUE_B").get(null)
        result.classLoader.execute(enumValue)
    }
}
