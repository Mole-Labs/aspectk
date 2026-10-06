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

/**
 * Verifies how several advices on the same function are ordered.
 *
 *   @Before outermost, runs once ahead of everything else
 *   @Around nest in declaration order: the first one is the outermost, each later one inside it
 *   @After wraps only the body, inside every @Around, in declaration order
 *
 * The declaration order only matters between advices of the same kind.
 */
@Suppress("UNUSED")
class AdviceOrderingTest {
    // ORD-1: @Around 2개 — 먼저 선언한 쪽이 바깥쪽, 나중에 선언한 쪽이 안쪽

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetOrder1

    @Aspect
    private object AspectOrder1 {
        val log = mutableListOf<String>()

        @Around(TargetOrder1::class)
        fun first(pjp: ProceedingJoinPoint): Any? {
            log.add("first-in")
            return pjp.proceed().also { log.add("first-out") }
        }

        @Around(TargetOrder1::class)
        fun second(pjp: ProceedingJoinPoint): Any? {
            log.add("second-in")
            return pjp.proceed().also { log.add("second-out") }
        }
    }

    private class ExampleOrder1 {
        @TargetOrder1
        fun work(): String {
            AspectOrder1.log.add("body")
            return "result"
        }
    }

    @Test
    fun `same aspect - two Arounds nest in declaration order`() {
        val result = ExampleOrder1().work()
        assertEquals("result", result)
        assertEquals(listOf("first-in", "second-in", "body", "second-out", "first-out"), AspectOrder1.log)
    }

    // ORD-2: @After 2개 — 선언 순서대로 실행

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetOrder2

    @Aspect
    private object AspectOrder2 {
        val log = mutableListOf<String>()

        @After(TargetOrder2::class)
        fun first(jp: JoinPoint) {
            log.add("first")
        }

        @After(TargetOrder2::class)
        fun second(jp: JoinPoint) {
            log.add("second")
        }
    }

    private class ExampleOrder2 {
        @TargetOrder2
        fun work() {
            AspectOrder2.log.add("body")
        }
    }

    @Test
    fun `same aspect - two Afters execute in declaration order`() {
        ExampleOrder2().work()
        assertEquals(listOf("body", "first", "second"), AspectOrder2.log)
    }

    // ORD-3: 타깃 A의 @Before는 타깃 B의 @Around 바깥에 남는다 — proceed를 두 번 불러도 한 번만 실행

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetOrder3A

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetOrder3B

    @Aspect
    private object AspectOrder3 {
        val log = mutableListOf<String>()

        @Before(TargetOrder3A::class)
        fun doBefore(jp: JoinPoint) {
            log.add("before")
        }

        @Around(TargetOrder3B::class)
        fun doAround(pjp: ProceedingJoinPoint): Any? {
            pjp.proceed()
            return pjp.proceed()
        }
    }

    private class ExampleOrder3 {
        @TargetOrder3A
        @TargetOrder3B
        fun work() {
            AspectOrder3.log.add("body")
        }
    }

    @Test
    fun `different targets - Before stays outside an Around that proceeds twice`() {
        ExampleOrder3().work()
        assertEquals(listOf("before", "body", "body"), AspectOrder3.log)
    }

    // ORD-4: @Around를 @After보다 먼저 선언해도 @After는 @Around 안쪽 — proceed를 건너뛰면 실행되지 않는다

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetOrder4

    @Aspect
    private object AspectOrder4 {
        val log = mutableListOf<String>()

        @Around(TargetOrder4::class)
        fun doAround(pjp: ProceedingJoinPoint): Any? {
            log.add("around")
            return Unit
        }

        @After(TargetOrder4::class)
        fun doAfter(jp: JoinPoint) {
            log.add("after")
        }
    }

    private class ExampleOrder4 {
        @TargetOrder4
        fun work() {
            AspectOrder4.log.add("body")
        }
    }

    @Test
    fun `same aspect - After does not run when an Around declared before it skips proceed`() {
        ExampleOrder4().work()
        assertEquals(listOf("around"), AspectOrder4.log)
    }

    // ORD-5: @After → @Around → @Before 순으로 선언 — 종류별 층은 선언 순서와 무관

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetOrder5

    @Aspect
    private object AspectOrder5 {
        val log = mutableListOf<String>()

        @After(TargetOrder5::class)
        fun doAfter(jp: JoinPoint) {
            log.add("after")
        }

        @Around(TargetOrder5::class)
        fun doAround(pjp: ProceedingJoinPoint): Any? {
            log.add("around-in")
            return pjp.proceed().also { log.add("around-out") }
        }

        @Before(TargetOrder5::class)
        fun doBefore(jp: JoinPoint) {
            log.add("before")
        }
    }

    private class ExampleOrder5 {
        @TargetOrder5
        fun work() {
            AspectOrder5.log.add("body")
        }
    }

    @Test
    fun `mixed - After then Around then Before declared in that order still run as Before - Around - body - After`() {
        ExampleOrder5().work()
        assertEquals(
            listOf("before", "around-in", "body", "after", "around-out"),
            AspectOrder5.log,
        )
    }

    // ORD-6: @Around → @Before → @After 순으로 선언 — 먼저 선언한 @Around도 @Before를 감싸지 않는다

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetOrder6

    @Aspect
    private object AspectOrder6 {
        val log = mutableListOf<String>()

        @Around(TargetOrder6::class)
        fun doAround(pjp: ProceedingJoinPoint): Any? {
            log.add("around-in")
            return pjp.proceed().also { log.add("around-out") }
        }

        @Before(TargetOrder6::class)
        fun doBefore(jp: JoinPoint) {
            log.add("before")
        }

        @After(TargetOrder6::class)
        fun doAfter(jp: JoinPoint) {
            log.add("after")
        }
    }

    private class ExampleOrder6 {
        @TargetOrder6
        fun work() {
            AspectOrder6.log.add("body")
        }
    }

    @Test
    fun `mixed - Around then Before then After declared in that order still run as Before - Around - body - After`() {
        ExampleOrder6().work()
        assertEquals(
            listOf("before", "around-in", "body", "after", "around-out"),
            AspectOrder6.log,
        )
    }

    // ORD-7: 종류별 2개씩 섞어서 선언 — 같은 종류끼리만 선언 순서를 따른다

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetOrder7

    @Aspect
    private object AspectOrder7 {
        val log = mutableListOf<String>()

        @After(TargetOrder7::class)
        fun after1(jp: JoinPoint) {
            log.add("after1")
        }

        @Around(TargetOrder7::class)
        fun around1(pjp: ProceedingJoinPoint): Any? {
            log.add("around1-in")
            return pjp.proceed().also { log.add("around1-out") }
        }

        @Before(TargetOrder7::class)
        fun before1(jp: JoinPoint) {
            log.add("before1")
        }

        @Around(TargetOrder7::class)
        fun around2(pjp: ProceedingJoinPoint): Any? {
            log.add("around2-in")
            return pjp.proceed().also { log.add("around2-out") }
        }

        @After(TargetOrder7::class)
        fun after2(jp: JoinPoint) {
            log.add("after2")
        }

        @Before(TargetOrder7::class)
        fun before2(jp: JoinPoint) {
            log.add("before2")
        }
    }

    private class ExampleOrder7 {
        @TargetOrder7
        fun work() {
            AspectOrder7.log.add("body")
        }
    }

    @Test
    fun `mixed - two of each kind interleaved keep declaration order within each kind`() {
        ExampleOrder7().work()
        assertEquals(
            listOf("before1", "before2", "around1-in", "around2-in", "body", "after1", "after2", "around2-out", "around1-out"),
            AspectOrder7.log,
        )
    }

    // ORD-8: 섞어서 선언 + 본문 예외 — @After가 실행된 뒤 예외가 @Around의 proceed()로 전파된다

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetOrder8

    @Aspect
    private object AspectOrder8 {
        val log = mutableListOf<String>()

        @After(TargetOrder8::class)
        fun doAfter(jp: JoinPoint) {
            log.add("after")
        }

        @Around(TargetOrder8::class)
        fun doAround(pjp: ProceedingJoinPoint): Any? {
            log.add("around-in")
            return try {
                pjp.proceed()
            } catch (e: IllegalStateException) {
                log.add("around-caught")
                "fallback"
            }
        }

        @Before(TargetOrder8::class)
        fun doBefore(jp: JoinPoint) {
            log.add("before")
        }
    }

    private class ExampleOrder8 {
        @TargetOrder8
        fun work(): String {
            AspectOrder8.log.add("body")
            throw IllegalStateException("boom")
        }
    }

    @Test
    fun `mixed - After runs before the exception of the body reaches the Around`() {
        val result = ExampleOrder8().work()
        assertEquals("fallback", result)
        assertEquals(listOf("before", "around-in", "body", "after", "around-caught"), AspectOrder8.log)
    }

    // ORD-9: 서로 다른 Aspect의 @Around — 먼저 선언한 Aspect가 바깥쪽

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetOrder9

    private object LogOrder9 {
        val log = mutableListOf<String>()
    }

    @Aspect
    private object AspectOrder9A {
        @Around(TargetOrder9::class)
        fun doAround(pjp: ProceedingJoinPoint): Any? {
            LogOrder9.log.add("a-in")
            return pjp.proceed().also { LogOrder9.log.add("a-out") }
        }
    }

    @Aspect
    private object AspectOrder9B {
        @Around(TargetOrder9::class)
        fun doAround(pjp: ProceedingJoinPoint): Any? {
            LogOrder9.log.add("b-in")
            return pjp.proceed().also { LogOrder9.log.add("b-out") }
        }
    }

    private class ExampleOrder9 {
        @TargetOrder9
        fun work() {
            LogOrder9.log.add("body")
        }
    }

    @Test
    fun `cross-aspect - Arounds of two aspects nest in the order the aspects are declared`() {
        ExampleOrder9().work()
        assertEquals(listOf("a-in", "b-in", "body", "b-out", "a-out"), LogOrder9.log)
    }

    // ORD-10: 서로 다른 Aspect의 @After — 먼저 선언한 Aspect부터 실행

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetOrder10

    private object LogOrder10 {
        val log = mutableListOf<String>()
    }

    @Aspect
    private object AspectOrder10A {
        @After(TargetOrder10::class)
        fun doAfter(jp: JoinPoint) {
            LogOrder10.log.add("a")
        }
    }

    @Aspect
    private object AspectOrder10B {
        @After(TargetOrder10::class)
        fun doAfter(jp: JoinPoint) {
            LogOrder10.log.add("b")
        }
    }

    private class ExampleOrder10 {
        @TargetOrder10
        fun work() {
            LogOrder10.log.add("body")
        }
    }

    @Test
    fun `cross-aspect - Afters of two aspects execute in the order the aspects are declared`() {
        ExampleOrder10().work()
        assertEquals(listOf("body", "a", "b"), LogOrder10.log)
    }

    // ORD-11: @Around 3개 — 선언 순서대로 바깥에서 안으로

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetOrder11

    @Aspect
    private object AspectOrder11 {
        val log = mutableListOf<String>()

        @Around(TargetOrder11::class)
        fun first(pjp: ProceedingJoinPoint): Any? {
            log.add("first-in")
            return pjp.proceed().also { log.add("first-out") }
        }

        @Around(TargetOrder11::class)
        fun second(pjp: ProceedingJoinPoint): Any? {
            log.add("second-in")
            return pjp.proceed().also { log.add("second-out") }
        }

        @Around(TargetOrder11::class)
        fun third(pjp: ProceedingJoinPoint): Any? {
            log.add("third-in")
            return pjp.proceed().also { log.add("third-out") }
        }
    }

    private class ExampleOrder11 {
        @TargetOrder11
        fun work() {
            AspectOrder11.log.add("body")
        }
    }

    @Test
    fun `same aspect - three Arounds nest in declaration order`() {
        ExampleOrder11().work()
        assertEquals(
            listOf("first-in", "second-in", "third-in", "body", "third-out", "second-out", "first-out"),
            AspectOrder11.log,
        )
    }
}
