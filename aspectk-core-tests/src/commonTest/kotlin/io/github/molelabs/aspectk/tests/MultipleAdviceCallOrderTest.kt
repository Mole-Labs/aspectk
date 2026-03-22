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
class MultipleAdviceCallOrderTest {
    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample1A

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample1B

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample1C

    @Aspect
    private object ExampleAspect1 {
        var count = 0

        @Around(TargetExample1A::class)
        fun doAround1(pjp: ProceedingJoinPoint): Any? {
            count++
            return pjp.proceed()
        }

        @Around(TargetExample1B::class)
        fun doAround2(pjp: ProceedingJoinPoint): Any? {
            count++
            return pjp.proceed()
        }

        @Around(TargetExample1C::class)
        fun doAround3(pjp: ProceedingJoinPoint): Any? {
            count++
            return pjp.proceed()
        }
    }

    private class Example1 {
        @TargetExample1A
        @TargetExample1B
        @TargetExample1C
        fun greet(): String = "hello"
    }

    // TODO support multiple @Around, @After annotations
    @Test
    fun `@Around advice is only invoked once per function`() {
        Example1().greet()
        assertEquals(1, ExampleAspect1.count, "Expected @Around advice to be called only once")
    }

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample2A

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample2B

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample2C

    @Aspect
    private object ExampleAspect2 {
        var count = 0

        @After(TargetExample2A::class)
        fun doAfter1(pjp: JoinPoint) {
            count++
        }

        @After(TargetExample2B::class)
        fun doAfter2(pjp: JoinPoint) {
            count++
        }

        @After(TargetExample2C::class)
        fun doAfter3(pjp: JoinPoint) {
            count++
        }
    }

    private class Example2 {
        @TargetExample2A
        @TargetExample2B
        @TargetExample2C
        fun greet(): String = "hello"
    }

    // TODO support multiple @Around, @After annotations
    @Test
    fun `@After advice is only invoked once per function`() {
        Example2().greet()
        assertEquals(1, ExampleAspect2.count, "Expected @After advice to be called only once")
    }

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample3A

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample3B

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample3C

    @Aspect
    private object ExampleAspect3 {
        var count = 0

        @After(TargetExample3A::class)
        fun doAfter1(pjp: JoinPoint) {
            count++
        }

        @After(TargetExample3B::class)
        fun doAfter2(pjp: JoinPoint) {
            count++
        }

        @After(TargetExample3C::class)
        fun doAfter3(pjp: JoinPoint) {
            count++
        }
    }

    private class Example3 {
        @TargetExample3A
        @TargetExample3B
        @TargetExample3C
        fun greet(): String = "hello"
    }

    // TODO support multiple @Around, @After annotations
    @Test
    fun `@After and @Around advice is only invoked once per function`() {
        Example3().greet()
        assertEquals(1, ExampleAspect3.count, "Expected @Around advice to be called only once")
    }

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample4A

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample4B

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample4C

    @Aspect
    private object ExampleAspect4 {
        var count = 0

        @Around(TargetExample4A::class)
        fun doAround1(pjp: ProceedingJoinPoint): Any? {
            count++
            return pjp.proceed()
        }

        @After(TargetExample4B::class, TargetExample4C::class)
        fun doAfter2(pjp: JoinPoint) {
            count++
        }
    }

    private class Example4 {
        @TargetExample4A
        @TargetExample4B
        @TargetExample4C
        fun greet(): String = "hello"
    }

    @Test
    fun `multiple @Before advice is ordinally invoked`() {
        Example4().greet()
        assertEquals(1, ExampleAspect4.count, "Expected @Around advice to be called only once")
    }

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample5Around1

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample5Around2

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample5Before1

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample5Before2

    @Aspect
    private object ExampleAspect5 {
        var beforeCount = 0
        var aroundCount = 0

        @Around(TargetExample5Around1::class, TargetExample5Around2::class)
        fun doAround1(pjp: ProceedingJoinPoint): Any? {
            aroundCount++
            return pjp.proceed()
        }

        @Before(TargetExample5Before1::class, TargetExample5Before2::class)
        fun doBefore2(pjp: JoinPoint) {
            beforeCount++
        }
    }

    private class Example5 {
        @TargetExample5Before1
        @TargetExample5Before2
        @TargetExample5Around1
        @TargetExample5Around2
        fun greet(): String = "hello"
    }

    @Test
    fun `multiple @Before and @Around advice is ordinally invoked`() {
        /*
        Current architectural limitations and the absence of an advice ordering engine
        restrict how multiple advices are applied. Specifically, All @Before advice is called,
        but only the last @Around advice is called as the latter types effectively override
        the original function body
         */
        Example5().greet()
        // all @Before aspect and only one @Around are called
        assertEquals(2, ExampleAspect5.beforeCount)
        assertEquals(1, ExampleAspect5.aroundCount)
    }

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample6Around1

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample6Around2

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample6Before1

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample6Before2

    @Aspect
    private object ExampleAspect6 {
        var beforeCount = 0
        var aroundCount = 0

        @After(TargetExample6Around1::class, TargetExample6Around2::class)
        fun doAfter(jp: JoinPoint) {
            aroundCount++
        }

        @Before(TargetExample6Before1::class, TargetExample6Before2::class)
        fun doBefore(jp: JoinPoint) {
            beforeCount++
        }
    }

    private class Example6 {
        @TargetExample6Before1
        @TargetExample6Before2
        @TargetExample6Around1
        @TargetExample6Around2
        fun greet(): String = "hello"
    }

    @Test
    fun `multiple @Before and @After advice is ordinally invoked`() {
        /*
        Current architectural limitations and the absence of an advice ordering engine
        restrict how multiple advices are applied. Specifically, @Before advice only
        functions correctly when positioned relative to @Around or @After interceptors,
        as the latter types effectively override the original function body
         */
        Example6().greet()
        // all @Before aspect and only one @After are called
        assertEquals(2, ExampleAspect6.beforeCount)
        assertEquals(1, ExampleAspect6.aroundCount)
    }

    // ORDER: multiple @Before all execute before body

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample7A

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample7B

    @Aspect
    private object ExampleAspect7 {
        val executionLog = mutableListOf<String>()

        @Before(TargetExample7A::class)
        fun doBefore1(jp: JoinPoint) {
            executionLog.add("before1")
        }

        @Before(TargetExample7B::class)
        fun doBefore2(jp: JoinPoint) {
            executionLog.add("before2")
        }
    }

    private class Example7 {
        @TargetExample7A
        @TargetExample7B
        fun work() {
            ExampleAspect7.executionLog.add("body")
        }
    }

    @Test
    fun `multiple @Before advices all execute before function body`() {
        Example7().work()
        val log = ExampleAspect7.executionLog
        val bodyIndex = log.indexOf("body")
        assertEquals(3, log.size)
        assertTrue(log.indexOf("before1") < bodyIndex)
        assertTrue(log.indexOf("before2") < bodyIndex)
    }

    // ORDER: @Before + @After on same function produce correct sequence

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample8Before

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample8After

    @Aspect
    private object ExampleAspect8 {
        val executionLog = mutableListOf<String>()

        @Before(TargetExample8Before::class)
        fun doBefore(jp: JoinPoint) {
            executionLog.add("before")
        }

        @After(TargetExample8After::class)
        fun doAfter(jp: JoinPoint) {
            executionLog.add("after")
        }
    }

    private class Example8 {
        @TargetExample8Before
        @TargetExample8After
        fun work() {
            ExampleAspect8.executionLog.add("body")
        }
    }

    @Test
    fun `@Before executes before body and @After executes after body when both target the same function`() {
        Example8().work()
        assertEquals(listOf("before", "body", "after"), ExampleAspect8.executionLog)
    }

    // EXCEPTION: @Before + @After both execute even when function throws

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample9Before

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample9After

    @Aspect
    private object ExampleAspect9 {
        val executionLog = mutableListOf<String>()

        @Before(TargetExample9Before::class)
        fun doBefore(jp: JoinPoint) {
            executionLog.add("before")
        }

        @After(TargetExample9After::class)
        fun doAfter(jp: JoinPoint) {
            executionLog.add("after")
        }
    }

    private class Example9 {
        @TargetExample9Before
        @TargetExample9After
        fun work(): Unit = throw RuntimeException("boom")
    }

    @Test
    fun `@Before and @After both execute even when function throws`() {
        assertFailsWith<RuntimeException> {
            Example9().work()
        }
        assertTrue(ExampleAspect9.executionLog.contains("before"))
        assertTrue(ExampleAspect9.executionLog.contains("after"))
    }

    // DEDUPLICATION: @Before(Ann1, Ann2) on function with both annotations → called once per match

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample10A

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample10B

    @Aspect
    private object ExampleAspect10 {
        var count = 0

        @Before(TargetExample10A::class, TargetExample10B::class)
        fun doBefore(jp: JoinPoint) {
            count++
        }
    }

    private class Example10 {
        @TargetExample10A
        @TargetExample10B
        fun work() {}
    }

    @Test
    fun `@Before advice is invoked once per matching annotation on the same function`() {
        Example10().work()
        assertEquals(2, ExampleAspect10.count)
    }

    // DEDUPLICATION: @After(Ann1, Ann2) on function with both annotations → called only once

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample11A

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample11B

    @Aspect
    private object ExampleAspect11 {
        var count = 0

        @After(TargetExample11A::class, TargetExample11B::class)
        fun doAfter(jp: JoinPoint) {
            count++
        }
    }

    private class Example11 {
        @TargetExample11A
        @TargetExample11B
        fun work() {}
    }

    @Test
    fun `@After advice is invoked only once even when multiple target annotations match the same function`() {
        Example11().work()
        assertEquals(1, ExampleAspect11.count)
    }
}
