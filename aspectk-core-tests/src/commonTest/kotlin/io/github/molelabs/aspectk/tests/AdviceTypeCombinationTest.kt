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

/**
 * Verifies execution order and correctness when multiple advice types
 * (@Before, @After, @Around) target the same annotation on the same function.
 *
 * Each acceptance criterion (AC) asserts that all applied advice types execute,
 * that they fire in the correct sequence, and that @After is guaranteed to run
 * even when the target function throws.
 *
 * Expected execution order:
 *   @Before → (@Around start) → body → (@Around end) → @After
 *
 * Test groups:
 *   - Same Aspect  (AC-1a ~ AC-4b): multiple advice types within a single @Aspect targeting the same annotation
 *   - Cross-Aspect (AC-5  ~ AC-8 ): multiple advice types across different @Aspect objects targeting the same annotation
 */
@Suppress("UNUSED")
class AdviceTypeCombinationTest {
    // ── Same Aspect ────────────────────────────────────────────────────────────

    // AC-1a: @Before + @After — 정상 흐름

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetAc1A

    @Aspect
    private object AspectAc1A {
        val log = mutableListOf<String>()

        @Before(TargetAc1A::class)
        fun doBefore(jp: JoinPoint) {
            log.add("before")
        }

        @After(TargetAc1A::class)
        fun doAfter(jp: JoinPoint) {
            log.add("after")
        }
    }

    private class ExampleAc1A {
        @TargetAc1A
        fun work() {
            AspectAc1A.log.add("body")
        }
    }

    @Test
    fun `same aspect - @Before and @After both execute in correct order`() {
        ExampleAc1A().work()
        assertEquals(listOf("before", "body", "after"), AspectAc1A.log)
    }

    // AC-1b: @Before + @After — 예외 발생

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetAc1B

    @Aspect
    private object AspectAc1B {
        val log = mutableListOf<String>()

        @Before(TargetAc1B::class)
        fun doBefore(jp: JoinPoint) {
            log.add("before")
        }

        @After(TargetAc1B::class)
        fun doAfter(jp: JoinPoint) {
            log.add("after")
        }
    }

    private class ExampleAc1B {
        @TargetAc1B
        fun work(): Unit = throw RuntimeException("boom")
    }

    @Test
    fun `same aspect - @Before and @After both execute even when function throws`() {
        assertFailsWith<RuntimeException> { ExampleAc1B().work() }
        assertTrue(AspectAc1B.log.contains("before"))
        assertTrue(AspectAc1B.log.contains("after"))
    }

    // AC-2: @Before + @Around — 정상 흐름

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetAc2

    @Aspect
    private object AspectAc2 {
        val log = mutableListOf<String>()

        @Before(TargetAc2::class)
        fun doBefore(jp: JoinPoint) {
            log.add("before")
        }

        @Around(TargetAc2::class)
        fun doAround(pjp: ProceedingJoinPoint): Any? {
            log.add("around-before")
            val result = pjp.proceed()
            log.add("around-after")
            return result
        }
    }

    private class ExampleAc2 {
        @TargetAc2
        fun work() {
            AspectAc2.log.add("body")
        }
    }

    @Test
    fun `same aspect - @Before executes before @Around wraps the body`() {
        ExampleAc2().work()
        assertEquals(listOf("before", "around-before", "body", "around-after"), AspectAc2.log)
    }

    // AC-3a: @After + @Around — 정상 흐름

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetAc3A

    @Aspect
    private object AspectAc3A {
        val log = mutableListOf<String>()

        @After(TargetAc3A::class)
        fun doAfter(jp: JoinPoint) {
            log.add("after")
        }

        @Around(TargetAc3A::class)
        fun doAround(pjp: ProceedingJoinPoint): Any? {
            log.add("around-before")
            val result = pjp.proceed()
            log.add("around-after")
            return result
        }
    }

    private class ExampleAc3A {
        @TargetAc3A
        fun work() {
            AspectAc3A.log.add("body")
        }
    }

    @Test
    fun `same aspect - @After executes after @Around wraps the body`() {
        ExampleAc3A().work()
        assertEquals(listOf("around-before", "body", "around-after", "after"), AspectAc3A.log)
    }

    // AC-3b: @After + @Around — 예외 발생

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetAc3B

    @Aspect
    private object AspectAc3B {
        val log = mutableListOf<String>()

        @After(TargetAc3B::class)
        fun doAfter(jp: JoinPoint) {
            log.add("after")
        }

        @Around(TargetAc3B::class)
        fun doAround(pjp: ProceedingJoinPoint): Any? {
            log.add("around-before")
            val result = pjp.proceed()
            log.add("around-after")
            return result
        }
    }

    private class ExampleAc3B {
        @TargetAc3B
        fun work(): Unit = throw RuntimeException("boom")
    }

    @Test
    fun `same aspect - @After executes even when @Around proceed throws`() {
        assertFailsWith<RuntimeException> { ExampleAc3B().work() }
        assertTrue(AspectAc3B.log.contains("around-before"))
        assertTrue(AspectAc3B.log.contains("after"))
    }

    // AC-4a: @Before + @After + @Around — 정상 흐름

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetAc4A

    @Aspect
    private object AspectAc4A {
        val log = mutableListOf<String>()

        @Before(TargetAc4A::class)
        fun doBefore(jp: JoinPoint) {
            log.add("before")
        }

        @After(TargetAc4A::class)
        fun doAfter(jp: JoinPoint) {
            log.add("after")
        }

        @Around(TargetAc4A::class)
        fun doAround(pjp: ProceedingJoinPoint): Any? {
            log.add("around-before")
            val result = pjp.proceed()
            log.add("around-after")
            return result
        }
    }

    private class ExampleAc4A {
        @TargetAc4A
        fun work() {
            AspectAc4A.log.add("body")
        }
    }

    @Test
    fun `same aspect - @Before, @After, and @Around all execute in correct order`() {
        ExampleAc4A().work()
        assertEquals(listOf("before", "around-before", "body", "around-after", "after"), AspectAc4A.log)
    }

    // AC-4b: @Before + @After + @Around — 예외 발생

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetAc4B

    @Aspect
    private object AspectAc4B {
        val log = mutableListOf<String>()

        @Before(TargetAc4B::class)
        fun doBefore(jp: JoinPoint) {
            log.add("before")
        }

        @After(TargetAc4B::class)
        fun doAfter(jp: JoinPoint) {
            log.add("after")
        }

        @Around(TargetAc4B::class)
        fun doAround(pjp: ProceedingJoinPoint): Any? {
            log.add("around-before")
            val result = pjp.proceed()
            log.add("around-after")
            return result
        }
    }

    private class ExampleAc4B {
        @TargetAc4B
        fun work(): Unit = throw RuntimeException("boom")
    }

    @Test
    fun `same aspect - @Before and @After both execute with @Around even when function throws`() {
        assertFailsWith<RuntimeException> { ExampleAc4B().work() }
        assertTrue(AspectAc4B.log.contains("before"))
        assertTrue(AspectAc4B.log.contains("around-before"))
        assertTrue(AspectAc4B.log.contains("after"))
    }

    // ── Cross-Aspect ───────────────────────────────────────────────────────────

    // AC-5: @Before (AspectA) + @After (AspectB) — 정상 흐름

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetAc5

    @Aspect
    private object AspectAc5Before {
        val log = mutableListOf<String>()

        @Before(TargetAc5::class)
        fun doBefore(jp: JoinPoint) {
            log.add("before")
        }
    }

    @Aspect
    private object AspectAc5After {
        @After(TargetAc5::class)
        fun doAfter(jp: JoinPoint) {
            AspectAc5Before.log.add("after")
        }
    }

    private class ExampleAc5 {
        @TargetAc5
        fun work() {
            AspectAc5Before.log.add("body")
        }
    }

    @Test
    fun `cross-aspect - @Before and @After in different aspects execute in correct order`() {
        ExampleAc5().work()
        assertEquals(listOf("before", "body", "after"), AspectAc5Before.log)
    }

    // AC-6: @Before (AspectA) + @Around (AspectB) — 정상 흐름

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetAc6

    @Aspect
    private object AspectAc6Before {
        val log = mutableListOf<String>()

        @Before(TargetAc6::class)
        fun doBefore(jp: JoinPoint) {
            log.add("before")
        }
    }

    @Aspect
    private object AspectAc6Around {
        @Around(TargetAc6::class)
        fun doAround(pjp: ProceedingJoinPoint): Any? {
            AspectAc6Before.log.add("around-before")
            val result = pjp.proceed()
            AspectAc6Before.log.add("around-after")
            return result
        }
    }

    private class ExampleAc6 {
        @TargetAc6
        fun work() {
            AspectAc6Before.log.add("body")
        }
    }

    @Test
    fun `cross-aspect - @Before and @Around in different aspects execute in correct order`() {
        ExampleAc6().work()
        assertEquals(listOf("before", "around-before", "body", "around-after"), AspectAc6Before.log)
    }

    // AC-7: @After (AspectA) + @Around (AspectB) — 정상 흐름

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetAc7

    @Aspect
    private object AspectAc7After {
        val log = mutableListOf<String>()

        @After(TargetAc7::class)
        fun doAfter(jp: JoinPoint) {
            log.add("after")
        }
    }

    @Aspect
    private object AspectAc7Around {
        @Around(TargetAc7::class)
        fun doAround(pjp: ProceedingJoinPoint): Any? {
            AspectAc7After.log.add("around-before")
            val result = pjp.proceed()
            AspectAc7After.log.add("around-after")
            return result
        }
    }

    private class ExampleAc7 {
        @TargetAc7
        fun work() {
            AspectAc7After.log.add("body")
        }
    }

    @Test
    fun `cross-aspect - @After and @Around in different aspects execute in correct order`() {
        ExampleAc7().work()
        assertEquals(listOf("around-before", "body", "around-after", "after"), AspectAc7After.log)
    }

    // AC-8: @Before (AspectA) + @After (AspectB) + @Around (AspectC) — 정상 흐름

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetAc8

    @Aspect
    private object AspectAc8Before {
        val log = mutableListOf<String>()

        @Before(TargetAc8::class)
        fun doBefore(jp: JoinPoint) {
            log.add("before")
        }
    }

    @Aspect
    private object AspectAc8After {
        @After(TargetAc8::class)
        fun doAfter(jp: JoinPoint) {
            AspectAc8Before.log.add("after")
        }
    }

    @Aspect
    private object AspectAc8Around {
        @Around(TargetAc8::class)
        fun doAround(pjp: ProceedingJoinPoint): Any? {
            AspectAc8Before.log.add("around-before")
            val result = pjp.proceed()
            AspectAc8Before.log.add("around-after")
            return result
        }
    }

    private class ExampleAc8 {
        @TargetAc8
        fun work() {
            AspectAc8Before.log.add("body")
        }
    }

    @Test
    fun `cross-aspect - @Before, @After, and @Around in different aspects all execute in correct order`() {
        ExampleAc8().work()
        assertEquals(listOf("before", "around-before", "body", "around-after", "after"), AspectAc8Before.log)
    }
}
