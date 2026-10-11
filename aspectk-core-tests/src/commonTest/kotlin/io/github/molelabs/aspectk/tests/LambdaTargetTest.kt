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
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A target annotation written on a lambda or an anonymous function instead of a named function.
 *
 * Kotlin applies an annotation in front of a lambda to the function the lambda compiles to, so the
 * advice runs each time the lambda is invoked, not when it is created.
 *
 * A lambda passed to an `inline` function (`forEach`, `let`, `getOrElse`, ...) never becomes a
 * function, so Kotlin rejects a BINARY or RUNTIME annotation there ("The lambda expression here is
 * an inlined argument, so this annotation cannot be stored anywhere"). A SOURCE annotation is
 * allowed, and the advice is woven into the lambda body before it is inlined.
 */
@Suppress("UNUSED")
class LambdaTargetTest {
    @Target(AnnotationTarget.FUNCTION)
    private annotation class Logged

    @Target(AnnotationTarget.FUNCTION)
    private annotation class Audited

    @Target(AnnotationTarget.FUNCTION)
    private annotation class Wrapped

    // SOURCE retention: the only kind Kotlin accepts on a lambda passed to an inline function
    @Target(AnnotationTarget.FUNCTION)
    @Retention(AnnotationRetention.SOURCE)
    private annotation class InlineLogged

    @Target(AnnotationTarget.FUNCTION)
    @Retention(AnnotationRetention.SOURCE)
    private annotation class InlineAudited

    @Target(AnnotationTarget.FUNCTION)
    @Retention(AnnotationRetention.SOURCE)
    private annotation class InlineWrapped

    @Aspect
    private object LambdaAspect {
        val log = mutableListOf<String>()

        @Before(Logged::class)
        fun doBefore(jp: JoinPoint) {
            log.add("before:" + jp.args)
        }

        @Before(InlineLogged::class)
        fun doBeforeInline(jp: JoinPoint) {
            log.add("before:" + jp.args)
        }

        @After(Audited::class, InlineAudited::class)
        fun doAfterInline(jp: JoinPoint) {
            log.add("after:" + jp.args)
        }

        @Around(Wrapped::class, InlineWrapped::class)
        fun doAround(pjp: ProceedingJoinPoint): Any? {
            log.add("in")
            return pjp.proceed().also { log.add("out") }
        }
    }

    private fun call(block: () -> Unit) = block()

    private fun <T> compute(block: () -> T): T = block()

    @BeforeTest
    fun reset() {
        LambdaAspect.log.clear()
    }

    @Test
    fun `before advice runs when a lambda passed as an argument is invoked`() {
        call(@Logged { LambdaAspect.log.add("body") })

        assertEquals(listOf("before:[]", "body"), LambdaAspect.log)
    }

    @Test
    fun `before advice runs when a trailing lambda is invoked`() {
        call @Logged { LambdaAspect.log.add("body") }

        assertEquals(listOf("before:[]", "body"), LambdaAspect.log)
    }

    @Test
    fun `before advice runs on every invocation of a lambda held in a variable and not on its creation`() {
        val handler: () -> Unit = @Logged { LambdaAspect.log.add("body") }
        assertEquals(emptyList(), LambdaAspect.log)

        handler()
        handler()

        assertEquals(listOf("before:[]", "body", "before:[]", "body"), LambdaAspect.log)
    }

    @Test
    fun `before advice runs when an anonymous function is invoked`() {
        call(@Logged fun() {
            LambdaAspect.log.add("body")
        })

        assertEquals(listOf("before:[]", "body"), LambdaAspect.log)
    }

    @Test
    fun `before advice on a lambda sees the arguments the lambda was invoked with`() {
        val greet: (String, Int) -> Unit = @Logged { name, times -> LambdaAspect.log.add("body:$name*$times") }

        greet("kim", 2)

        assertEquals(listOf("before:[kim, 2]", "body:kim*2"), LambdaAspect.log)
    }

    @Test
    fun `after advice runs after the body of a lambda`() {
        call @Audited { LambdaAspect.log.add("body") }

        assertEquals(listOf("body", "after:[]"), LambdaAspect.log)
    }

    @Test
    fun `around advice wraps a lambda that captures a local and returns a value`() {
        val base = 20

        val result = compute @Wrapped { base + 1 }
        assertEquals(21, result)
        assertEquals(listOf("in", "out"), LambdaAspect.log)
    }

    @Test
    fun `before advice runs for each element when the lambda is passed to forEach`() {
        listOf(1, 2).forEach @InlineLogged { LambdaAspect.log.add("body:$it") }

        assertEquals(listOf("before:[1]", "body:1", "before:[2]", "body:2"), LambdaAspect.log)
    }

    @Test
    fun `before advice runs in a lambda passed to let and the lambda still returns its value`() {
        val length = "kim".let @InlineLogged { it.length }

        assertEquals(3, length)
        assertEquals(listOf("before:[kim]"), LambdaAspect.log)
    }

    @Test
    fun `before advice runs in a getOrElse lambda only when the result is a failure`() {
        val ok = Result.success(1).getOrElse @InlineLogged { -1 }
        assertEquals(1, ok)
        assertEquals(emptyList(), LambdaAspect.log)

        val failure = IllegalStateException("boom")
        val fallback = Result.failure<Int>(failure).getOrElse @InlineLogged { -1 }

        assertEquals(-1, fallback)
        assertEquals(listOf("before:[$failure]"), LambdaAspect.log)
    }

    @Test
    fun `before advice runs in an onFailure lambda with the exception as its argument`() {
        val failure = IllegalStateException("boom")

        runCatching<Int> { throw failure }.onFailure @InlineLogged { LambdaAspect.log.add("body") }

        assertEquals(listOf("before:[$failure]", "body"), LambdaAspect.log)
    }

    @Test
    fun `after advice runs after the body of a lambda passed to an inline function`() {
        val doubled = listOf(1, 2).map @InlineAudited { it * 2 }

        assertEquals(listOf(2, 4), doubled)
        assertEquals(listOf("after:[1]", "after:[2]"), LambdaAspect.log)
    }

    @Test
    fun `around advice wraps a lambda passed to an inline function`() {
        val doubled = listOf(1, 2).map @InlineWrapped { it * 2 }

        assertEquals(listOf(2, 4), doubled)
        assertEquals(listOf("in", "out", "in", "out"), LambdaAspect.log)
    }

    @Test
    fun `a non-local return from an inline lambda still returns from the enclosing function`() {
        fun firstEven(values: List<Int>): Int? {
            values.forEach @InlineLogged { if (it % 2 == 0) return it }
            return null
        }

        assertEquals(2, firstEven(listOf(1, 2, 3)))
        assertEquals(listOf("before:[1]", "before:[2]"), LambdaAspect.log)
    }
}
