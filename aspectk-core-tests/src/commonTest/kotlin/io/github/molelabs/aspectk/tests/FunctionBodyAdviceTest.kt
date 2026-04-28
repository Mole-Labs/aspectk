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
import kotlin.test.assertTrue

@Suppress("UNUSED")
class FunctionBodyAdviceTest {
    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample1

    @Aspect
    private object ExampleAspect1 {
        @Around(TargetExample1::class)
        fun doAround(pjp: ProceedingJoinPoint): Any? = pjp.proceed()
    }

    private class Example1 {
        @TargetExample1
        fun compute(): Int {
            val a = 6
            val b = a * 7
            return b
        }
    }

    @Test
    fun `local variable referencing another local variable computes correctly under Around Aspect`() {
        assertEquals(42, Example1().compute())
    }

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample2

    @Aspect
    private object ExampleAspect2 {
        @Around(TargetExample2::class)
        fun doAround(pjp: ProceedingJoinPoint): Any? = pjp.proceed()
    }

    private class Example2 {
        @TargetExample2
        fun double(x: Int): Int {
            val result = x * 2
            return result
        }
    }

    @Test
    fun `local variable referencing a parameter computes correctly under Around Aspect`() {
        assertEquals(10, Example2().double(5))
    }

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample3

    @Aspect
    private object ExampleAspect3 {
        @Around(TargetExample3::class)
        fun doAround(pjp: ProceedingJoinPoint): Any? = pjp.proceed()
    }

    private class Example3 {
        @TargetExample3
        fun sumWithBase(): Int {
            val base = 10
            return listOf(1, 2, 3).sumOf { it + base }
        }
    }

    @Test
    fun `lambda capturing a local variable executes correctly under Around Aspect`() {
        assertEquals(36, Example3().sumWithBase())
    }

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample4

    @Aspect
    private object ExampleAspect4 {
        @Around(TargetExample4::class)
        fun doAround(pjp: ProceedingJoinPoint): Any? = pjp.proceed()
    }

    private class Example4 {
        @TargetExample4
        fun greet(name: String): String {
            fun prefix() = "Hello"
            return "${prefix()}, $name!"
        }
    }

    @Test
    fun `local function declaration inside body executes correctly under Around Aspect`() {
        assertEquals("Hello, World!", Example4().greet("World"))
    }

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample5

    @Aspect
    private object ExampleAspect5 {
        var called = false

        @Before(TargetExample5::class)
        fun doBefore(jp: JoinPoint) {
            called = true
        }
    }

    private class Example5 {
        @TargetExample5
        fun classify(n: Int): String = when {
            n < 0 -> "negative"
            n == 0 -> "zero"
            else -> "positive"
        }
    }

    @Test
    fun `when expression in body executes correctly under Before Aspect`() {
        val result = Example5().classify(1)
        assertTrue(ExampleAspect5.called)
        assertEquals("positive", result)
    }

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample6

    @Aspect
    private object ExampleAspect6 {
        var called = false

        @After(TargetExample6::class)
        fun doAfter(jp: JoinPoint) {
            called = true
        }
    }

    private class Example6 {
        @TargetExample6
        fun safeOp(input: String?): String = try {
            checkNotNull(input) { "null input" }
            input.uppercase()
        } catch (e: IllegalStateException) {
            "fallback"
        }
    }

    @Test
    fun `try-catch block in body executes correctly under After Aspect`() {
        val result = Example6().safeOp(null)
        assertTrue(ExampleAspect6.called)
        assertEquals("fallback", result)
    }

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample7

    @Aspect
    private object ExampleAspect7 {
        var called = false

        @Before(TargetExample7::class)
        fun doBefore(jp: JoinPoint) {
            called = true
        }
    }

    private class Example7 {
        @TargetExample7
        fun sumUpTo(n: Int): Int {
            var total = 0
            for (i in 1..n) total += i
            return total
        }
    }

    @Test
    fun `for loop in body executes correctly under Before Aspect`() {
        val result = Example7().sumUpTo(10)
        assertTrue(ExampleAspect7.called)
        assertEquals(55, result)
    }

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample8

    @Aspect
    private object ExampleAspect8 {
        @Around(TargetExample8::class)
        fun doAround(pjp: ProceedingJoinPoint): Any? = pjp.proceed()
    }

    private class Example8 {
        val multiplier = 3

        @TargetExample8
        fun scale(x: Int): Int {
            val result = x * multiplier
            return result
        }
    }

    @Test
    fun `member property access in body computes correctly under Around Aspect`() {
        assertEquals(12, Example8().scale(4))
    }

    // --- Case 9: 멤버 함수 호출 ---

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample9

    @Aspect
    private object ExampleAspect9 {
        @Around(TargetExample9::class)
        fun doAround(pjp: ProceedingJoinPoint): Any? = pjp.proceed()
    }

    private class Example9 {
        private fun triple(x: Int) = x * 3

        @TargetExample9
        fun compute(x: Int): Int {
            val result = triple(x)
            return result
        }
    }

    @Test
    fun `member function call in body computes correctly under Around Aspect`() {
        assertEquals(12, Example9().compute(4))
    }

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample10

    @Aspect
    private object ExampleAspect10 {
        var called = false

        @After(TargetExample10::class)
        fun doAfter(jp: JoinPoint) {
            called = true
        }
    }

    private class Transformer {
        fun transform(x: Int) = x + 100
    }

    private class Example10 {
        private val transformer = Transformer()

        @TargetExample10
        fun process(x: Int): Int {
            val result = transformer.transform(x)
            return result
        }
    }

    @Test
    fun `external class function call in body executes correctly under After Aspect`() {
        val result = Example10().process(5)
        assertTrue(ExampleAspect10.called)
        assertEquals(105, result)
    }

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample11

    @Aspect
    private object ExampleAspect11 {
        @Around(TargetExample11::class)
        fun doAround(pjp: ProceedingJoinPoint): Any? = pjp.proceed()
    }

    private class Example11 {
        @TargetExample11
        fun generateWithCoroutine(): Int {
            val numbers =
                sequence {
                    yield(10)
                    yield(20)
                    yield(12)
                }
            return numbers.sum()
        }
    }

    @Test
    fun `coroutine sequence builder in body executes correctly under Around Aspect`() {
        assertEquals(42, Example11().generateWithCoroutine())
    }
}
