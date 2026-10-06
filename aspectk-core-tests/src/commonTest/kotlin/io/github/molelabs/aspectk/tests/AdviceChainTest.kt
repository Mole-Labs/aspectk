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
import io.github.molelabs.aspectk.runtime.SuspendProceedingJoinPoint
import io.github.molelabs.aspectk.runtime.getArg
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@Target(AnnotationTarget.FUNCTION)
private annotation class TargetChainTopLevel

@Aspect
private object AspectChainTopLevel {
    @Around(TargetChainTopLevel::class)
    fun outer(pjp: ProceedingJoinPoint): Any? = pjp.proceed(10, 20)

    @Around(TargetChainTopLevel::class)
    fun inner(pjp: ProceedingJoinPoint): Any? = pjp.proceed(pjp.getArg<Int>("a") + 1, pjp.getArg<Int>("b"))
}

@TargetChainTopLevel
private fun chainTopLevelSum(
    a: Int,
    b: Int,
): Int = a + b

@Target(AnnotationTarget.FUNCTION)
private annotation class TargetChainExtension

@Aspect
private object AspectChainExtension {
    @Around(TargetChainExtension::class)
    fun outer(pjp: ProceedingJoinPoint): Any? = pjp.proceed("!!")

    @Around(TargetChainExtension::class)
    fun inner(pjp: ProceedingJoinPoint): Any? = pjp.proceed(pjp.getArg<String>("suffix") + "?")
}

@TargetChainExtension
private fun String.chainShout(suffix: String): String = this + suffix

/**
 * Verifies what flows through a chain of several advices on the same function: arguments going
 * in, the result coming out, a skipped or repeated proceed(), and exceptions.
 *
 * The order the advices run in is covered by [AdviceOrderingTest].
 */
@Suppress("UNUSED")
class AdviceChainTest {
    // CH-1: 바깥 @Around가 바꾼 인자는 안쪽 @Around, 본문, @After에 보이고 @Before에는 원래 인자가 보인다

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetChain1

    @Aspect
    private object AspectChain1 {
        val log = mutableListOf<String>()

        @Before(TargetChain1::class)
        fun doBefore(jp: JoinPoint) {
            log.add("before:" + jp.getArg<String>("name"))
        }

        @Around(TargetChain1::class)
        fun outer(pjp: ProceedingJoinPoint): Any? {
            log.add("outer:" + pjp.getArg<String>("name"))
            return pjp.proceed("from-outer")
        }

        @Around(TargetChain1::class)
        fun inner(pjp: ProceedingJoinPoint): Any? {
            log.add("inner:" + pjp.getArg<String>("name"))
            return pjp.proceed("from-inner")
        }

        @After(TargetChain1::class)
        fun doAfter(jp: JoinPoint) {
            log.add("after:" + jp.getArg<String>("name"))
        }
    }

    private class ExampleChain1 {
        @TargetChain1
        fun greet(name: String): String {
            AspectChain1.log.add("body:$name")
            return name
        }
    }

    @Test
    fun `args replaced by an outer Around reach the inner Around and the body and After`() {
        val result = ExampleChain1().greet("original")
        assertEquals("from-inner", result)
        assertEquals(
            listOf("before:original", "outer:original", "inner:from-outer", "body:from-inner", "after:from-inner"),
            AspectChain1.log,
        )
    }

    // CH-2: 반환값은 안쪽 @Around에서 바깥쪽 @Around 순으로 변환된다

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetChain2

    @Aspect
    private object AspectChain2 {
        @Around(TargetChain2::class)
        fun outer(pjp: ProceedingJoinPoint): Any? = (pjp.proceed() as Int) * 2

        @Around(TargetChain2::class)
        fun inner(pjp: ProceedingJoinPoint): Any? = (pjp.proceed() as Int) + 10
    }

    private class ExampleChain2 {
        @TargetChain2
        fun one(): Int = 1
    }

    @Test
    fun `the result is transformed by the inner Around first then by the outer one`() {
        assertEquals(22, ExampleChain2().one())
    }

    // CH-3: 바깥 @Around가 proceed를 건너뛰면 안쪽 @Around, 본문, @After 모두 실행되지 않는다

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetChain3

    @Aspect
    private object AspectChain3 {
        val log = mutableListOf<String>()

        @Before(TargetChain3::class)
        fun doBefore(jp: JoinPoint) {
            log.add("before")
        }

        @Around(TargetChain3::class)
        fun outer(pjp: ProceedingJoinPoint): Any? {
            log.add("outer")
            return "skipped"
        }

        @Around(TargetChain3::class)
        fun inner(pjp: ProceedingJoinPoint): Any? {
            log.add("inner")
            return pjp.proceed()
        }

        @After(TargetChain3::class)
        fun doAfter(jp: JoinPoint) {
            log.add("after")
        }
    }

    private class ExampleChain3 {
        @TargetChain3
        fun work(): String {
            AspectChain3.log.add("body")
            return "body"
        }
    }

    @Test
    fun `an outer Around that skips proceed keeps the inner Around and the body and After from running`() {
        val result = ExampleChain3().work()
        assertEquals("skipped", result)
        assertEquals(listOf("before", "outer"), AspectChain3.log)
    }

    // CH-4: 안쪽 @Around가 proceed를 건너뛰면 그 반환값이 바깥 @Around의 proceed 결과가 된다

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetChain4

    @Aspect
    private object AspectChain4 {
        val log = mutableListOf<String>()

        @Around(TargetChain4::class)
        fun outer(pjp: ProceedingJoinPoint): Any? {
            log.add("outer-in")
            return (pjp.proceed() as String).also { log.add("outer-out:$it") }
        }

        @Around(TargetChain4::class)
        fun inner(pjp: ProceedingJoinPoint): Any? {
            log.add("inner")
            return "inner-value"
        }

        @After(TargetChain4::class)
        fun doAfter(jp: JoinPoint) {
            log.add("after")
        }
    }

    private class ExampleChain4 {
        @TargetChain4
        fun work(): String {
            AspectChain4.log.add("body")
            return "body"
        }
    }

    @Test
    fun `an inner Around that skips proceed hands its own result to the outer one`() {
        val result = ExampleChain4().work()
        assertEquals("inner-value", result)
        assertEquals(listOf("outer-in", "inner", "outer-out:inner-value"), AspectChain4.log)
    }

    // CH-5: 바깥 @Around가 proceed를 두 번 부르면 안쪽 @Around, 본문, @After가 두 번씩 실행된다

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetChain5

    @Aspect
    private object AspectChain5 {
        val log = mutableListOf<String>()

        @Around(TargetChain5::class)
        fun outer(pjp: ProceedingJoinPoint): Any? {
            pjp.proceed()
            return pjp.proceed()
        }

        @Around(TargetChain5::class)
        fun inner(pjp: ProceedingJoinPoint): Any? {
            log.add("inner")
            return pjp.proceed()
        }

        @After(TargetChain5::class)
        fun doAfter(jp: JoinPoint) {
            log.add("after")
        }
    }

    private class ExampleChain5 {
        @TargetChain5
        fun work() {
            AspectChain5.log.add("body")
        }
    }

    @Test
    fun `an outer Around that proceeds twice runs the inner Around and the body and After twice`() {
        ExampleChain5().work()
        assertEquals(listOf("inner", "body", "after", "inner", "body", "after"), AspectChain5.log)
    }

    // CH-6: 안쪽 @Around가 던진 예외를 바깥 @Around가 잡을 수 있다

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetChain6

    @Aspect
    private object AspectChain6 {
        val log = mutableListOf<String>()

        @Around(TargetChain6::class)
        fun outer(pjp: ProceedingJoinPoint): Any? = try {
            pjp.proceed()
        } catch (e: IllegalStateException) {
            log.add("outer-caught:" + e.message)
            "recovered"
        }

        @Around(TargetChain6::class)
        fun inner(pjp: ProceedingJoinPoint): Any? = throw IllegalStateException("inner")
    }

    private class ExampleChain6 {
        @TargetChain6
        fun work(): String {
            AspectChain6.log.add("body")
            return "body"
        }
    }

    @Test
    fun `an exception thrown by the inner Around can be caught by the outer one`() {
        val result = ExampleChain6().work()
        assertEquals("recovered", result)
        assertEquals(listOf("outer-caught:inner"), AspectChain6.log)
    }

    // CH-7: 먼저 실행된 @After가 예외를 던져도 다음 @After는 실행되고 예외는 호출자에게 전파된다

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetChain7

    @Aspect
    private object AspectChain7 {
        val log = mutableListOf<String>()

        @After(TargetChain7::class)
        fun first(jp: JoinPoint) {
            log.add("first")
            throw IllegalStateException("first")
        }

        @After(TargetChain7::class)
        fun second(jp: JoinPoint) {
            log.add("second")
        }
    }

    private class ExampleChain7 {
        @TargetChain7
        fun work() {
            AspectChain7.log.add("body")
        }
    }

    @Test
    fun `a later After still runs when an earlier one throws`() {
        val e = assertFailsWith<IllegalStateException> { ExampleChain7().work() }
        assertEquals("first", e.message)
        assertEquals(listOf("body", "first", "second"), AspectChain7.log)
    }

    // CH-8: Unit 반환 함수에 @Around 2개

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetChain8

    @Aspect
    private object AspectChain8 {
        val log = mutableListOf<String>()

        @Around(TargetChain8::class)
        fun outer(pjp: ProceedingJoinPoint): Any? = pjp.proceed().also { log.add("outer") }

        @Around(TargetChain8::class)
        fun inner(pjp: ProceedingJoinPoint): Any? = pjp.proceed().also { log.add("inner") }
    }

    private class ExampleChain8 {
        @TargetChain8
        fun work() {
            AspectChain8.log.add("body")
        }
    }

    @Test
    fun `two Arounds on a Unit-returning function do not throw`() {
        ExampleChain8().work()
        assertEquals(listOf("body", "inner", "outer"), AspectChain8.log)
    }

    // CH-9: 최상위 함수 — 수신 객체가 없어도 인자 치환이 체인을 따라 전달된다

    @Test
    fun `top-level function - args replaced by an outer Around reach the inner one`() {
        assertEquals(31, chainTopLevelSum(1, 2))
    }

    // CH-10: 확장 함수 — 확장 수신 객체는 유지되고 일반 인자만 체인을 따라 바뀐다

    @Test
    fun `extension function - the receiver is kept while args change along the chain`() {
        assertEquals("hi!!?", "hi".chainShout("."))
    }

    // CH-11: suspend 함수 — 실제로 중단되는 본문과 어드바이스에서도 순서와 값 전달이 같다

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetChain11

    @Aspect
    private object AspectChain11 {
        val log = mutableListOf<String>()

        @Before(TargetChain11::class)
        fun doBefore(jp: JoinPoint) {
            log.add("before")
        }

        @Around(TargetChain11::class)
        suspend fun outer(pjp: SuspendProceedingJoinPoint): Any? {
            log.add("outer-in")
            delay(1)
            return ((pjp.proceed(5) as Int) * 2).also { log.add("outer-out") }
        }

        @Around(TargetChain11::class)
        suspend fun inner(pjp: SuspendProceedingJoinPoint): Any? {
            log.add("inner-in:" + pjp.getArg<Int>("value"))
            delay(1)
            return ((pjp.proceed() as Int) + 1).also { log.add("inner-out") }
        }

        @After(TargetChain11::class)
        fun doAfter(jp: JoinPoint) {
            log.add("after")
        }
    }

    private class ExampleChain11 {
        @TargetChain11
        suspend fun load(value: Int): Int {
            delay(1)
            AspectChain11.log.add("body:$value")
            return value
        }
    }

    @Test
    fun `suspend function - two Arounds and Before and After keep their order and pass values along`() = runTest {
        val result = ExampleChain11().load(1)
        assertEquals(12, result)
        assertEquals(
            listOf("before", "outer-in", "inner-in:5", "body:5", "after", "inner-out", "outer-out"),
            AspectChain11.log,
        )
    }
}
