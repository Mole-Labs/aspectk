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

import io.github.molelabs.aspectk.runtime.Aspect
import io.github.molelabs.aspectk.runtime.Before
import io.github.molelabs.aspectk.runtime.JoinPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@Suppress("UNCHECKED_CAST", "UNUSED")
class MultipleAspectTest {
    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample1

    @Aspect
    private object ExampleAspect1A {
        var executionCount = 0

        @Before(TargetExample1::class)
        fun doBefore(joinPoint: JoinPoint) {
            executionCount++
        }
    }

    @Aspect
    private object ExampleAspect1B {
        var executionCount = 0

        @Before(TargetExample1::class)
        fun doBefore(joinPoint: JoinPoint) {
            executionCount++
        }
    }

    private class Example1 {
        @TargetExample1
        fun run() {}
    }

    @Test
    fun `Both aspects should execute when two aspects target the same annotation on the same function`() {
        Example1().run()
        assertEquals(1, ExampleAspect1A.executionCount)
        assertEquals(1, ExampleAspect1B.executionCount)
    }

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample2A

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample2B

    @Aspect
    private object ExampleAspect2A {
        var executionCount = 0

        @Before(TargetExample2A::class)
        fun doBefore(joinPoint: JoinPoint) {
            executionCount++
        }
    }

    @Aspect
    private object ExampleAspect2B {
        var executionCount = 0

        @Before(TargetExample2B::class)
        fun doBefore(joinPoint: JoinPoint) {
            executionCount++
        }
    }

    private class Example2 {
        @TargetExample2A
        @TargetExample2B
        fun run() {}
    }

    @Test
    fun `Both aspects should execute when function is annotated with two different target annotations`() {
        Example2().run()
        assertEquals(1, ExampleAspect2A.executionCount)
        assertEquals(1, ExampleAspect2B.executionCount)
    }

    // AC-3: 세 개의 독립된 Aspect가 동일한 함수에 적용

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample3

    @Aspect
    private object ExampleAspect3A {
        var executionCount = 0

        @Before(TargetExample3::class)
        fun doBefore(joinPoint: JoinPoint) {
            executionCount++
        }
    }

    @Aspect
    private object ExampleAspect3B {
        var executionCount = 0

        @Before(TargetExample3::class)
        fun doBefore(joinPoint: JoinPoint) {
            executionCount++
        }
    }

    @Aspect
    private object ExampleAspect3C {
        var executionCount = 0

        @Before(TargetExample3::class)
        fun doBefore(joinPoint: JoinPoint) {
            executionCount++
        }
    }

    private class Example3 {
        @TargetExample3
        fun run() {}
    }

    @Test
    fun `All three aspects should execute when three aspects target the same function`() {
        Example3().run()
        assertEquals(1, ExampleAspect3A.executionCount)
        assertEquals(1, ExampleAspect3B.executionCount)
        assertEquals(1, ExampleAspect3C.executionCount)
    }

    // AC-4: 복수의 Aspect가 적용될 때 각 advice가 수신하는 JoinPoint 정보가 동일한지 검증

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample4

    @Aspect
    private object ExampleAspect4A {
        var argsSize = -1
        var arg1: Any? = null
        var arg2: Any? = null
        var target: Any? = null

        @Before(TargetExample4::class)
        fun doBefore(joinPoint: JoinPoint) {
            argsSize = joinPoint.args.size
            arg1 = joinPoint.args[1]
            arg2 = joinPoint.args[2]
            target = joinPoint.target
        }
    }

    @Aspect
    private object ExampleAspect4B {
        var argsSize = -1
        var arg1: Any? = null
        var arg2: Any? = null
        var target: Any? = null

        @Before(TargetExample4::class)
        fun doBefore(joinPoint: JoinPoint) {
            argsSize = joinPoint.args.size
            arg1 = joinPoint.args[1]
            arg2 = joinPoint.args[2]
            target = joinPoint.target
        }
    }

    private class Example4 {
        @TargetExample4
        fun run(
            name: String,
            value: Int,
        ) {}
    }

    @Test
    fun `All aspects should receive identical JoinPoint information when applied to the same function`() {
        val instance = Example4()
        instance.run("hello", 42)

        assertEquals(ExampleAspect4A.argsSize, ExampleAspect4B.argsSize)
        assertEquals(ExampleAspect4A.arg1, ExampleAspect4B.arg1)
        assertEquals(ExampleAspect4A.arg2, ExampleAspect4B.arg2)
        assertEquals(ExampleAspect4A.target, ExampleAspect4B.target)

        assertEquals(3, ExampleAspect4A.argsSize)
        assertEquals("hello", ExampleAspect4A.arg1)
        assertEquals(42, ExampleAspect4A.arg2)
        assertIs<Example4>(ExampleAspect4A.target)
    }

    // AC-5: 복수의 Aspect가 적용되어도 원본 함수 본문이 모든 advice 실행 이후 정상적으로 실행되는지 검증

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample5A

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample5B

    @Aspect
    private object ExampleAspect5A {
        var executed = false

        @Before(TargetExample5A::class)
        fun doBefore(joinPoint: JoinPoint) {
            executed = true
        }
    }

    @Aspect
    private object ExampleAspect5B {
        var executed = false

        @Before(TargetExample5B::class)
        fun doBefore(joinPoint: JoinPoint) {
            executed = true
        }
    }

    private class Example5 {
        var bodyExecuted = false

        @TargetExample5A
        @TargetExample5B
        fun run() {
            assertEquals(true, ExampleAspect5A.executed)
            assertEquals(true, ExampleAspect5B.executed)
            bodyExecuted = true
        }
    }

    @Test
    fun `Original function body should execute after all advice when multiple aspects are applied`() {
        val example = Example5()
        example.run()
        assertEquals(true, example.bodyExecuted)
    }
}
