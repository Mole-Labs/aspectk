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
package io.github.molelabs.aspectk.tests

import io.github.molelabs.aspectk.runtime.After
import io.github.molelabs.aspectk.runtime.Around
import io.github.molelabs.aspectk.runtime.Aspect
import io.github.molelabs.aspectk.runtime.Before
import io.github.molelabs.aspectk.runtime.JoinPoint
import io.github.molelabs.aspectk.runtime.ProceedingJoinPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@Suppress("UNCHECKED_CAST", "UNUSED")
class JoinPointGenerationTest {
    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample1(
        val name: String,
    )

    @Aspect
    private object ExampleAspect1 {
        var executed: Boolean = false

        @Before(TargetExample1::class)
        fun doBefore(joinPoint: JoinPoint) {
            executed = true
            assertEquals(1, joinPoint.args.size)
            assertIs<Example1>(joinPoint.target)
        }
    }

    private class Example1 {
        @TargetExample1("example1")
        fun run() {
            assertEquals(ExampleAspect1.executed, true)
        }
    }

    @Test
    fun `JoinPoint should be injected into aspect`() = Example1().run()

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample2(
        val name: String,
    )

    @Aspect
    private object ExampleAspect2 {
        var executed: Boolean = false

        @Before(TargetExample2::class)
        fun doBefore(joinPoint: JoinPoint) {
            executed = true
            assertEquals(joinPoint.args.size, 3)
            assertIs<Example2>(joinPoint.target)

            assertEquals("hello", joinPoint.args[1])
            assertEquals("aspectk", joinPoint.args[2])
        }
    }

    private class Example2 {
        @TargetExample2("example1")
        fun run(
            arg1: String,
            arg2: String,
        ) {
            assertEquals(ExampleAspect2.executed, true)
        }
    }

    @Test
    fun `JoinPoint should be injected with arguments into aspect`() = Example2().run("hello", "aspectk")

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample3(
        val name: String,
    )

    @Aspect
    private object ExampleAspect3 {
        var executed: Boolean = false

        @Before(TargetExample3::class)
        fun doBefore(joinPoint: JoinPoint) {
            executed = true
            assertEquals(3, joinPoint.args.size)
            assertIs<Example3>(joinPoint.target)

            assertEquals(listOf("hello"), joinPoint.args[1])
            assertIs<CoroutineScope>(joinPoint.args[2])
        }
    }

    private class Example3 {
        @TargetExample3("example1")
        fun run(
            arg1: List<String>,
            arg2: CoroutineScope,
        ) {
            assertEquals(true, ExampleAspect3.executed)
        }
    }

    @Test
    fun `JoinPoint should be injected with reference type arguments into aspect`() = runTest {
        Example3().run(listOf("hello"), CoroutineScope(StandardTestDispatcher()))
    }

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample4(
        val name: String,
    )

    @Aspect
    private object ExampleAspect4 {
        var executed: Boolean = false

        @Before(TargetExample4::class)
        fun doBefore(joinPoint: JoinPoint) {
            executed = true
            assertEquals(3, joinPoint.args.size)
            assertIs<Example4>(joinPoint.target)

            assertIs<List<Any?>>(joinPoint.args[1])
            assertEquals(listOf("hello"), joinPoint.args[1])

            assertIs<Any?>(joinPoint.args[2])
            assertEquals(123, joinPoint.args[2])
        }
    }

    private class Example4 {
        @TargetExample4("example1")
        inline fun <T, reified R> run(
            arg1: List<T>,
            arg2: R,
        ) {
            assertEquals(true, ExampleAspect4.executed)
        }
    }

    @Test
    fun `JoinPoint with generic argument should be injected into aspect with Any`() = Example4().run(
        listOf("hello"),
        123,
    )

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample5(
        val name: String,
    )

    @Aspect
    private object ExampleAspect5 {
        var executed: Boolean = false

        @Before(TargetExample5::class)
        fun doBefore(joinPoint: JoinPoint) {
            executed = true
            assertEquals(joinPoint.args.size, 3)
            assertIs<Example5>(joinPoint.target)

            assertEquals("notNullArg", joinPoint.args[1])
            assertEquals(null, joinPoint.args[2])
        }
    }

    private class Example5 {
        @TargetExample5("example1")
        fun run(
            arg1: String,
            arg2: String?,
        ) {
            assertEquals(true, ExampleAspect5.executed)
        }
    }

    @Test
    fun `JoinPoint should be injected with nullable arguments into`() = Example5().run("notNullArg", null)

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample6(
        val name: String,
    )

    @Aspect
    private object ExampleAspect6 {
        var executed: Boolean = false

        @Before(TargetExample6::class)
        fun doBefore(joinPoint: JoinPoint) {
            executed = true
            assertEquals(joinPoint.args.size, 3)
            assertIs<Example6>(joinPoint.target)
            assertEquals(null, joinPoint.args[2])

            val expected = arrayOf("arg1", "arg2")
            val actual = (joinPoint.args[1] as Array<Any>)
            assertEquals(expected[0], actual[0])
            assertEquals(expected[1], actual[1])
        }
    }

    private class Example6 {
        @TargetExample6("example1")
        fun run(
            vararg arg1: String,
            arg2: String?,
        ) {
            assertEquals(true, ExampleAspect6.executed)
        }
    }

    @Test
    fun `JoinPoint should be injected with vararg arguments`() = Example6().run("arg1", "arg2", arg2 = null)

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample7(
        val name: String,
    )

    @Aspect
    private object ExampleAspect7 {
        var executed: Boolean = false

        @Before(TargetExample7::class)
        fun doBefore(joinPoint: JoinPoint) {
            executed = true
            assertEquals(2, joinPoint.args.size)
            assertIs<Example7>(joinPoint.target)
            assertIs<Function1<String, Unit>>(joinPoint.args[1])
        }
    }

    private class Example7 {
        @TargetExample7("example1")
        fun run(block: (String) -> Unit) {
            block("test")
            assertEquals(true, ExampleAspect7.executed)
        }
    }

    @Test
    fun `JoinPoint should capture function type argument`() = Example7().run { arg ->
        assertEquals("test", arg)
    }

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample8(
        val name: String,
    )

    @Aspect
    private object ExampleAspect8 {
        var executed: Boolean = false

        @Before(TargetExample8::class)
        fun doBefore(joinPoint: JoinPoint) {
            executed = true
            assertEquals(3, joinPoint.args.size)
            assertIs<Example8>(joinPoint.target)
            assertIs<Function1<String, Unit>>(joinPoint.args[2])
        }
    }

    private class Example8 {
        fun run(scope: CoroutineScope) {
            scope.launch {
                suspendHighOrderFun(scope) { arg ->
                    assertEquals(arg, "test")
                }
            }
        }

        @TargetExample8("example1")
        suspend fun suspendHighOrderFun(
            scope: CoroutineScope,
            block: suspend (String) -> Unit,
        ) {
            scope.launch {
                block("test")
                assertEquals(true, ExampleAspect8.executed)
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `JoinPoint should capture suspend function type argument`() = runTest {
        Example8().run(CoroutineScope(StandardTestDispatcher()))
    }

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample9(
        val name: String,
    )

    private enum class TestEnum {
        VALUE_A,
        VALUE_B,
    }

    @Aspect
    private object ExampleAspect9 {
        var executed: Boolean = false

        @Before(TargetExample9::class)
        fun doBefore(joinPoint: JoinPoint) {
            executed = true
            assertEquals(2, joinPoint.args.size)
            assertIs<Example9>(joinPoint.target)
            assertEquals("VALUE_B", (joinPoint.args[1] as Enum<*>).name)
            assertIs<TestEnum>(joinPoint.args[1])
        }
    }

    private class Example9 {
        @TargetExample9("example1")
        fun run(arg: TestEnum) {
            assertEquals(true, ExampleAspect9.executed)
        }
    }

    @Test
    fun `JoinPoint should capture enum argument`() = Example9().run(TestEnum.VALUE_B)

    // ── @After ───────────────────────────────────────────────────────────────

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample10

    @Aspect
    private object ExampleAspect10 {
        var executed = false

        @After(TargetExample10::class)
        fun doAfter(joinPoint: JoinPoint) {
            executed = true
            assertEquals(1, joinPoint.args.size)
            assertIs<Example10>(joinPoint.target)
        }
    }

    private class Example10 {
        @TargetExample10
        fun run() {}
    }

    @Test
    fun `After JoinPoint should be injected into aspect`() {
        Example10().run()
        assertEquals(true, ExampleAspect10.executed)
    }

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample11

    @Aspect
    private object ExampleAspect11 {
        var executed = false

        @After(TargetExample11::class)
        fun doAfter(joinPoint: JoinPoint) {
            executed = true
            assertEquals(3, joinPoint.args.size)
            assertIs<Example11>(joinPoint.target)
            assertEquals("hello", joinPoint.args[1])
            assertEquals("aspectk", joinPoint.args[2])
        }
    }

    private class Example11 {
        @TargetExample11
        fun run(
            arg1: String,
            arg2: String,
        ) {}
    }

    @Test
    fun `After JoinPoint should be injected with arguments into aspect`() {
        Example11().run("hello", "aspectk")
        assertEquals(true, ExampleAspect11.executed)
    }

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample12

    @Aspect
    private object ExampleAspect12 {
        var executed = false

        @After(TargetExample12::class)
        fun doAfter(joinPoint: JoinPoint) {
            executed = true
            assertEquals(3, joinPoint.args.size)
            assertEquals("notNullArg", joinPoint.args[1])
            assertEquals(null, joinPoint.args[2])
        }
    }

    private class Example12 {
        @TargetExample12
        fun run(
            arg1: String,
            arg2: String?,
        ) {}
    }

    @Test
    fun `After JoinPoint should be injected with nullable arguments`() {
        Example12().run("notNullArg", null)
        assertEquals(true, ExampleAspect12.executed)
    }

    // ── @Around ──────────────────────────────────────────────────────────────

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample13

    @Aspect
    private object ExampleAspect13 {
        var executed = false

        @Around(TargetExample13::class)
        fun doAround(pjp: ProceedingJoinPoint): Any? {
            executed = true
            assertEquals(3, pjp.args.size)
            assertIs<Example13>(pjp.target)
            assertEquals("hello", pjp.args[1])
            assertEquals("aspectk", pjp.args[2])
            return pjp.proceed()
        }
    }

    private class Example13 {
        var bodyExecuted = false

        @TargetExample13
        fun run(
            arg1: String,
            arg2: String,
        ) {
            bodyExecuted = true
        }
    }

    @Test
    fun `ProceedingJoinPoint should be injected with arguments and proceed invokes the body`() {
        val example = Example13()
        example.run("hello", "aspectk")
        assertEquals(true, ExampleAspect13.executed)
        assertEquals(true, example.bodyExecuted)
    }

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample14

    @Aspect
    private object ExampleAspect14 {
        @Around(TargetExample14::class)
        fun doAround(pjp: ProceedingJoinPoint): Any? = pjp.proceed("replaced")
    }

    private class Example14 {
        @TargetExample14
        fun run(arg: String): String = arg
    }

    @Test
    fun `ProceedingJoinPoint proceed with substituted args should override original args`() {
        val result = Example14().run("original")
        assertEquals("replaced", result)
    }
}
