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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@Suppress("UNUSED")
class AdviceCallOrderTest {
    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample1

    @Aspect
    private object ExampleAspect1 {
        val executionLog = mutableListOf<String>()

        @Before(TargetExample1::class)
        fun doBefore(joinPoint: JoinPoint) {
            executionLog.add("before")
        }
    }

    private class Example1 {
        @TargetExample1
        fun work() {
            ExampleAspect1.executionLog.add("body")
        }
    }

    @Test
    fun `Before advice executes before target function body`() {
        Example1().work()
        assertEquals(listOf("before", "body"), ExampleAspect1.executionLog)
    }

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample2

    @Aspect
    private object ExampleAspect2 {
        val executionLog = mutableListOf<String>()

        @After(TargetExample2::class)
        fun doAfter(joinPoint: JoinPoint) {
            executionLog.add("after")
        }
    }

    private class Example2 {
        @TargetExample2
        fun work() {
            ExampleAspect2.executionLog.add("body")
        }
    }

    @Test
    fun `After advice executes after target function body`() {
        Example2().work()
        assertEquals(listOf("body", "after"), ExampleAspect2.executionLog)
    }

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample3

    @Aspect
    private object ExampleAspect3 {
        val executionLog = mutableListOf<String>()

        @Around(TargetExample3::class)
        fun doAround(pjp: ProceedingJoinPoint): Any? {
            executionLog.add("before")
            val result = pjp.proceed()
            executionLog.add("after")
            return result
        }
    }

    private class Example3 {
        @TargetExample3
        fun work() {
            ExampleAspect3.executionLog.add("body")
        }
    }

    @Test
    fun `Around advice executes both before and after target function body`() {
        Example3().work()
        assertEquals(listOf("before", "body", "after"), ExampleAspect3.executionLog)
    }

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample4

    @Aspect
    private object ExampleAspect4 {
        var called = false

        @After(TargetExample4::class)
        fun doAfter(jp: JoinPoint) {
            called = true
        }
    }

    private class Example4 {
        @TargetExample4
        fun riskyWork(): Unit = throw RuntimeException("boom")
    }

    @Test
    fun `After advice is invoked even when the original function throws`() {
        assertFailsWith<RuntimeException> {
            Example4().riskyWork()
        }
        assertTrue(ExampleAspect4.called, "Expected @After advice to be called even when the function throws")
    }

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample5

    @Aspect
    private object ExampleAspect5 {
        var called = false

        @After(TargetExample5::class)
        fun doAfter(jp: JoinPoint) {
            called = true
        }
    }

    private class Example5 {
        @TargetExample5
        fun normalWork(): String = "done"
    }

    @Test
    fun `After advice is invoked after a normally returning function`() {
        Example5().normalWork()
        assertTrue(ExampleAspect5.called, "Expected @After advice to be called after normal return")
    }
}
