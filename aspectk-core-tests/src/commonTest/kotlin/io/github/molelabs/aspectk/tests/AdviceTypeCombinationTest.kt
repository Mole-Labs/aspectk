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
class AdviceTypeCombinationTest {
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
    fun `same aspect - Before and After both execute in correct order`() {
        ExampleAc1A().work()
        assertEquals(listOf("before", "body", "after"), AspectAc1A.log)
    }

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
    fun `same aspect - Before and After both execute even when function throws`() {
        assertFailsWith<RuntimeException> { ExampleAc1B().work() }
        assertTrue(AspectAc1B.log.contains("before"))
        assertTrue(AspectAc1B.log.contains("after"))
    }

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
    fun `same aspect - Before executes before Around wraps the body`() {
        ExampleAc2().work()
        assertEquals(listOf("before", "around-before", "body", "around-after"), AspectAc2.log)
    }

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
    fun `cross-aspect - Before and After in different aspects execute in correct order`() {
        ExampleAc5().work()
        assertEquals(listOf("before", "body", "after"), AspectAc5Before.log)
    }

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
    fun `cross-aspect - Before and Around in different aspects execute in correct order`() {
        ExampleAc6().work()
        assertEquals(listOf("before", "around-before", "body", "around-after"), AspectAc6Before.log)
    }

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetAc9

    @Aspect
    private object AspectAc9 {
        @Before(TargetAc9::class)
        fun doBefore(jp: JoinPoint) {}

        @Around(TargetAc9::class)
        fun doAround(pjp: ProceedingJoinPoint): Any? = pjp.proceed("modified", 99)
    }

    private class ExampleAc9 {
        @TargetAc9
        fun greet(
            name: String,
            count: Int,
        ): String = "$name-$count"
    }

    @Test
    fun `same aspect - Around can substitute parameters via proceed when combined with Before`() {
        val result = ExampleAc9().greet("alice", 3)
        assertEquals("modified-99", result)
    }

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetAc10A

    @Aspect
    private object AspectAc10A {
        @After(TargetAc10A::class)
        fun doAfter(jp: JoinPoint) {}

        @Around(TargetAc10A::class)
        fun doAround(pjp: ProceedingJoinPoint): Any? = pjp.proceed("modified", 99)
    }

    private class ExampleAc10A {
        @TargetAc10A
        fun greet(
            name: String,
            count: Int,
        ): String = "$name-$count"
    }

    @Test
    fun `same aspect - Around can substitute parameters via proceed when declared after After`() {
        val result = ExampleAc10A().greet("alice", 3)
        assertEquals("modified-99", result)
    }
}
