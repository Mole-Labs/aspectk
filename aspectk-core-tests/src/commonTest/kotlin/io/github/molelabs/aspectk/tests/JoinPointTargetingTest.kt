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
import io.github.molelabs.aspectk.tests.JoinPointTargetingTest.Example1
import io.github.molelabs.aspectk.tests.JoinPointTargetingTest.Example11
import io.github.molelabs.aspectk.tests.JoinPointTargetingTest.Example6
import io.github.molelabs.aspectk.tests.JoinPointTargetingTest.ExampleAspect1
import io.github.molelabs.aspectk.tests.JoinPointTargetingTest.ExampleAspect3
import io.github.molelabs.aspectk.tests.JoinPointTargetingTest.TargetExample1
import io.github.molelabs.aspectk.tests.JoinPointTargetingTest.TargetExample11
import io.github.molelabs.aspectk.tests.JoinPointTargetingTest.TargetExample12
import io.github.molelabs.aspectk.tests.JoinPointTargetingTest.TargetExample3
import io.github.molelabs.aspectk.tests.JoinPointTargetingTest.TargetExample6
import io.github.molelabs.aspectk.tests.JoinPointTargetingTest.TargetExample8
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@Suppress("UNCHECKED_CAST", "UNUSED")
class JoinPointTargetingTest {
    @Target(AnnotationTarget.FUNCTION)
    annotation class TargetExample1(
        val name: String,
    )

    @Aspect
    internal object ExampleAspect1 {
        var executed: Boolean = false

        @Before(TargetExample1::class)
        fun doBefore(joinPoint: JoinPoint) {
            executed = true
            assertEquals(2, joinPoint.args.size)
            assertEquals(null, joinPoint.target)
            assertIs<Example1>(joinPoint.args[0])
            assertEquals("extensionArg", joinPoint.args[1])
        }
    }

    internal class Example1

    @Test
    fun `JoinPoint should be injected into extension function`() {
        Example1().extension("extensionArg")
    }

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample2(
        val name: String,
    )

    @Aspect
    private object ExampleAspect2 {
        var executed: Boolean = false

        @Before(TargetExample2::class)
        fun doBefore(joinPoint: JoinPoint) {
            val scope = joinPoint.args[2] as CoroutineScope
            scope.launch {
                executed = true
                assertEquals(3, joinPoint.args.size)
                assertIs<ExampleAspect2>(joinPoint.target)
                assertEquals("suspendArg", joinPoint.args[1])
            }
        }
    }

    private class Example2 {
        fun run(
            arg: String,
            scope: CoroutineScope,
        ) {
            scope.launch {
                suspendFun(arg, scope)
            }
        }

        @TargetExample2("example1")
        private suspend fun suspendFun(
            arg: String,
            scope: CoroutineScope,
        ) {
            assertEquals(true, ExampleAspect2.executed)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `JoinPoint should be injected into suspend function`() = runTest {
        Example2().run("suspendArg", CoroutineScope(StandardTestDispatcher()))
        advanceUntilIdle()
    }

    @Target(AnnotationTarget.FUNCTION)
    annotation class TargetExample3(
        val name: String,
    )

    @Aspect
    internal object ExampleAspect3 {
        var executed: Boolean = false

        @Before(TargetExample3::class)
        fun doBefore(joinPoint: JoinPoint) {
            executed = true
            assertEquals(1, joinPoint.args.size)
            assertNull(joinPoint.target)
            assertEquals("topLevelArg", joinPoint.args[0])
        }
    }

    private class Example3 {
        fun run(arg: String) {
            topLevelFunction(arg)
        }
    }

    @Test
    fun `JoinPoint should be injected into top-level function`() {
        Example3().run("topLevelArg")
    }

    @Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
    private annotation class TargetExample4(
        val name: String,
    )

    @Aspect
    private object ExampleAspect4 {
        var executed: Boolean = false

        @Before(TargetExample4::class)
        fun doBefore(joinPoint: JoinPoint) {
            executed = true
            assertEquals(1, joinPoint.args.size)
            assertIs<Example4>(joinPoint.args[0])
        }
    }

    private class Example4 {
        private val arg1: String
            @TargetExample4("example1")
            get() = "hello"

        fun run() {
            arg1
        }
    }

    @Test
    fun `JoinPoint should be injected into getter`() {
        Example4().run()
    }

    @Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_SETTER)
    private annotation class TargetExample5(
        val name: String,
    )

    @Aspect
    object ExampleAspect5 {
        var executed: Boolean = false

        @Before(TargetExample5::class)
        fun doBefore(joinPoint: JoinPoint) {
            executed = true
            assertEquals(2, joinPoint.args.size)
            assertIs<Example5>(joinPoint.args[0])
            assertEquals("hello", joinPoint.args[1])
        }
    }

    class Example5 {
        private var arg1: String = ""
            @TargetExample5("example1")
            set(value) {}

        fun run() {
            arg1 = "hello"
        }
    }

    @Test
    fun `JoinPoint should be injected into setter`() {
        Example5().run()
    }

    @Test
    fun `JoinPoint should be injected into expect function`() {
        expectRun("hello")
        assertEquals(true, ExpectAspect.executed)
        assertEquals(1, ExpectAspect.size)
        assertEquals(null, ExpectAspect.type)
        assertEquals("hello", ExpectAspect.arg1)
    }

    // ── @After ───────────────────────────────────────────────────────────────

    @Target(AnnotationTarget.FUNCTION)
    annotation class TargetExample6(val name: String)

    @Aspect
    internal object ExampleAspect6 {
        var executed = false

        @After(TargetExample6::class)
        fun doAfter(joinPoint: JoinPoint) {
            executed = true
            assertEquals(2, joinPoint.args.size)
            assertNull(joinPoint.target)
            assertIs<Example6>(joinPoint.args[0])
            assertEquals("extensionArg", joinPoint.args[1])
        }
    }

    internal class Example6

    @Test
    fun `After JoinPoint should be injected into extension function`() {
        Example6().afterExtension("extensionArg")
        assertEquals(true, ExampleAspect6.executed)
    }

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample7(val name: String)

    @Aspect
    private object ExampleAspect7 {
        var executed = false

        @After(TargetExample7::class)
        fun doAfter(joinPoint: JoinPoint) {
            executed = true
            assertEquals(3, joinPoint.args.size)
            assertIs<Example7>(joinPoint.target)
            assertEquals("suspendArg", joinPoint.args[1])
        }
    }

    private class Example7 {
        @TargetExample7("example1")
        suspend fun suspendFun(
            arg: String,
            scope: CoroutineScope,
        ) {}
    }

    @Test
    fun `After JoinPoint should be injected into suspend function`() = runTest {
        Example7().suspendFun("suspendArg", CoroutineScope(StandardTestDispatcher()))
        assertEquals(true, ExampleAspect7.executed)
    }

    @Target(AnnotationTarget.FUNCTION)
    annotation class TargetExample8(val name: String)

    @Aspect
    internal object ExampleAspect8 {
        var executed = false

        @After(TargetExample8::class)
        fun doAfter(joinPoint: JoinPoint) {
            executed = true
            assertEquals(1, joinPoint.args.size)
            assertNull(joinPoint.target)
            assertEquals("topLevelArg", joinPoint.args[0])
        }
    }

    @Test
    fun `After JoinPoint should be injected into top-level function`() {
        afterTopLevel("topLevelArg")
        assertEquals(true, ExampleAspect8.executed)
    }

    @Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
    private annotation class TargetExample9(val name: String)

    @Aspect
    private object ExampleAspect9 {
        var executed = false

        @After(TargetExample9::class)
        fun doAfter(joinPoint: JoinPoint) {
            executed = true
            assertEquals(1, joinPoint.args.size)
            assertIs<Example9>(joinPoint.args[0])
        }
    }

    private class Example9 {
        private val prop: String
            @TargetExample9("example1")
            get() = "hello"

        fun run() {
            prop
        }
    }

    @Test
    fun `After JoinPoint should be injected into getter`() {
        Example9().run()
        assertEquals(true, ExampleAspect9.executed)
    }

    @Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_SETTER)
    private annotation class TargetExample10(val name: String)

    @Aspect
    private object ExampleAspect10 {
        var executed = false

        @After(TargetExample10::class)
        fun doAfter(joinPoint: JoinPoint) {
            executed = true
            assertEquals(2, joinPoint.args.size)
            assertIs<Example10>(joinPoint.args[0])
            assertEquals("hello", joinPoint.args[1])
        }
    }

    private class Example10 {
        private var prop: String = ""
            @TargetExample10("example1")
            set(value) {}

        fun run() {
            prop = "hello"
        }
    }

    @Test
    fun `After JoinPoint should be injected into setter`() {
        Example10().run()
        assertEquals(true, ExampleAspect10.executed)
    }

    // ── @Around ──────────────────────────────────────────────────────────────

    @Target(AnnotationTarget.FUNCTION)
    annotation class TargetExample11(val name: String)

    @Aspect
    internal object ExampleAspect11 {
        var executed = false

        @Around(TargetExample11::class)
        fun doAround(pjp: ProceedingJoinPoint): Any? {
            executed = true
            assertEquals(2, pjp.args.size)
            assertNull(pjp.target)
            assertIs<Example11>(pjp.args[0])
            assertEquals("extensionArg", pjp.args[1])
            return pjp.proceed()
        }
    }

    internal class Example11

    @Test
    fun `Around ProceedingJoinPoint should be injected into extension function`() {
        Example11().aroundExtension("extensionArg")
        assertEquals(true, ExampleAspect11.executed)
    }

    @Target(AnnotationTarget.FUNCTION)
    annotation class TargetExample12(val name: String)

    @Aspect
    internal object ExampleAspect12 {
        var executed = false

        @Around(TargetExample12::class)
        fun doAround(pjp: ProceedingJoinPoint): Any? {
            executed = true
            assertEquals(1, pjp.args.size)
            assertNull(pjp.target)
            assertEquals("topLevelArg", pjp.args[0])
            return pjp.proceed()
        }
    }

    @Test
    fun `Around ProceedingJoinPoint should be injected into top-level function`() {
        aroundTopLevel("topLevelArg")
        assertEquals(true, ExampleAspect12.executed)
    }
}

@TargetExample3("example1")
private fun topLevelFunction(arg: String) {
    assertEquals(true, ExampleAspect3.executed)
}

@TargetExample1("example1")
private fun Example1.extension(arg: String) {
    assertEquals(true, ExampleAspect1.executed)
}

@TargetExample6("example1")
private fun Example6.afterExtension(arg: String) {}

@TargetExample8("example1")
private fun afterTopLevel(arg: String) {}

@TargetExample11("example1")
private fun Example11.aroundExtension(arg: String) {}

@TargetExample12("example1")
private fun aroundTopLevel(arg: String) {}
