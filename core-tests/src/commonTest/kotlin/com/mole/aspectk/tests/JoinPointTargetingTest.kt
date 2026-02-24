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
package com.mole.aspectk.tests

import com.mole.aspectk.runtime.Aspect
import com.mole.aspectk.runtime.Before
import com.mole.aspectk.runtime.JoinPoint
import com.mole.aspectk.tests.JoinPointTargetingTest.Example1
import com.mole.aspectk.tests.JoinPointTargetingTest.ExampleAspect1
import com.mole.aspectk.tests.JoinPointTargetingTest.ExampleAspect3
import com.mole.aspectk.tests.JoinPointTargetingTest.TargetExample1
import com.mole.aspectk.tests.JoinPointTargetingTest.TargetExample3
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
}

@TargetExample3("example1")
private fun topLevelFunction(arg: String) {
    assertEquals(true, ExampleAspect3.executed)
}

@TargetExample1("example1")
private fun Example1.extension(arg: String) {
    assertEquals(true, ExampleAspect1.executed)
}
