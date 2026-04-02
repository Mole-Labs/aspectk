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

import io.github.molelabs.aspectk.runtime.Around
import io.github.molelabs.aspectk.runtime.Aspect
import io.github.molelabs.aspectk.runtime.ProceedingJoinPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

@Suppress("UNUSED")
class ProceedingJoinPointTest {
    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample1

    @Aspect
    private object ExampleAspect1 {
        @Around(TargetExample1::class)
        fun doAround(pjp: ProceedingJoinPoint): Any = "intercepted"
    }

    private class Example1 {
        var bodyExecuted = false

        @TargetExample1
        fun work(): String {
            bodyExecuted = true
            return "original"
        }
    }

    @Test
    fun `proceed not called — original body is skipped and advice return value is used`() {
        val example = Example1()
        val result = example.work()
        assertEquals("intercepted", result)
        assertFalse(example.bodyExecuted)
    }

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample2

    @Aspect
    private object ExampleAspect2 {
        @Around(TargetExample2::class)
        fun doAround(pjp: ProceedingJoinPoint): Any = (pjp.proceed() as String).uppercase()
    }

    private class Example2 {
        @TargetExample2
        fun work(): String = "hello"
    }

    @Test
    fun `proceed return value can be transformed by advice`() {
        val result = Example2().work()
        assertEquals("HELLO", result)
    }

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample3

    @Aspect
    private object ExampleAspect3 {
        @Around(TargetExample3::class)
        fun doAround(pjp: ProceedingJoinPoint): Any? {
            pjp.proceed()
            return pjp.proceed()
        }
    }

    private class Example3 {
        var callCount = 0

        @TargetExample3
        fun work(): String {
            callCount++
            return "result"
        }
    }

    @Test
    fun `calling proceed twice executes the original body twice`() {
        val example = Example3()
        example.work()
        assertEquals(2, example.callCount)
    }

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample4

    @Aspect
    private object ExampleAspect4 {
        @Around(TargetExample4::class)
        fun doAround(pjp: ProceedingJoinPoint): Any? {
            val originalA = pjp.args[1] as String
            return pjp.proceed(originalA, "replaced")
        }
    }

    private class Example4 {
        @TargetExample4
        fun work(
            a: String,
            b: String,
        ): String = "$a-$b"
    }

    @Test
    fun `proceed with partial arg substitution keeps unmodified args and replaces specified args`() {
        val result = Example4().work("hello", "world")
        assertEquals("hello-replaced", result)
    }

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample5

    @Aspect
    private object ExampleAspect5 {
        @Around(TargetExample5::class)
        fun doAround(pjp: ProceedingJoinPoint): Any? = pjp.proceed()
    }

    private class Example5 {
        @TargetExample5
        fun work(): String = throw RuntimeException("boom")
    }

    @Test
    fun `exception thrown by original body propagates through proceed`() {
        val ex = assertFailsWith<RuntimeException> { Example5().work() }
        assertEquals("boom", ex.message)
    }

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample6

    @Aspect
    private object ExampleAspect6 {
        @Around(TargetExample6::class)
        fun doAround(pjp: ProceedingJoinPoint): Any? = try {
            pjp.proceed()
        } catch (e: RuntimeException) {
            "suppressed"
        }
    }

    private class Example6 {
        @TargetExample6
        fun work(): String = throw RuntimeException("boom")
    }

    @Test
    fun `advice can catch exception from proceed and return a fallback value`() {
        val result = Example6().work()
        assertEquals("suppressed", result)
    }

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample7

    @Aspect
    private object ExampleAspect7 {
        @Around(TargetExample7::class)
        fun doAround(pjp: ProceedingJoinPoint): Any? = pjp.proceed()
    }

    private class Example7 {
        var executed = false

        @TargetExample7
        fun work() {
            executed = true
        }
    }

    @Test
    fun `Around on Unit-returning function does not throw ClassCastException`() {
        val example = Example7()
        example.work()
        assertEquals(true, example.executed)
    }
}
