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

    // AC-6: interface default 메서드에 복수의 Aspect가 적용될 때 모두 실행되는지 검증

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample6

    @Aspect
    private object ExampleAspect6A {
        var executionCount = 0

        @Before(TargetExample6::class)
        fun doBefore(joinPoint: JoinPoint) {
            executionCount++
        }
    }

    @Aspect
    private object ExampleAspect6B {
        var executionCount = 0

        @Before(TargetExample6::class)
        fun doBefore(joinPoint: JoinPoint) {
            executionCount++
        }
    }

    private interface Example6Interface {
        @TargetExample6
        fun run() {}
    }

    private class Example6 : Example6Interface

    @Test
    fun `Both aspects should execute when two aspects target the same annotation on an interface default method`() {
        Example6().run()
        assertEquals(1, ExampleAspect6A.executionCount)
        assertEquals(1, ExampleAspect6B.executionCount)
    }

    // AC-7: open class의 메서드에 복수의 Aspect가 적용될 때, inherits=false이면 overriding 메서드에서 실행되지 않아야 함

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample7

    @Aspect
    private object ExampleAspect7A {
        var executionCount = 0

        @Before(TargetExample7::class)
        fun doBefore(joinPoint: JoinPoint) {
            executionCount++
        }
    }

    @Aspect
    private object ExampleAspect7B {
        var executionCount = 0

        @Before(TargetExample7::class)
        fun doBefore(joinPoint: JoinPoint) {
            executionCount++
        }
    }

    private open class Example7Base {
        @TargetExample7
        open fun run() {}
    }

    private class Example7Derived : Example7Base() {
        override fun run() {}
    }

    @Test
    fun `Both aspects should execute on open class method but not on overriding method when inherits is false`() {
        Example7Base().run()
        assertEquals(1, ExampleAspect7A.executionCount)
        assertEquals(1, ExampleAspect7B.executionCount)

        Example7Derived().run()
        assertEquals(1, ExampleAspect7A.executionCount)
        assertEquals(1, ExampleAspect7B.executionCount)
    }

    // AC-8: abstract class의 추상 메서드에 복수의 Aspect가 inherits=true로 적용될 때 구현 클래스에서도 모두 실행되어야 함

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample8

    @Aspect
    private object ExampleAspect8A {
        var executionCount = 0

        @Before(TargetExample8::class, inherits = true)
        fun doBefore(joinPoint: JoinPoint) {
            executionCount++
        }
    }

    @Aspect
    private object ExampleAspect8B {
        var executionCount = 0

        @Before(TargetExample8::class, inherits = true)
        fun doBefore(joinPoint: JoinPoint) {
            executionCount++
        }
    }

    private abstract class Example8Base {
        @TargetExample8
        abstract fun run()
    }

    private class Example8 : Example8Base() {
        override fun run() {}
    }

    @Test
    fun `Both aspects should execute on overriding method when inherits is true and annotation is on abstract class method`() {
        Example8().run()
        assertEquals(1, ExampleAspect8A.executionCount)
        assertEquals(1, ExampleAspect8B.executionCount)
    }

    // AC-9: 하나는 inherits=true, 다른 하나는 inherits=false인 Aspect가 interface 구현 메서드에서 다르게 동작해야 함

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample9

    @Aspect
    private object ExampleAspect9A {
        var executionCount = 0

        @Before(TargetExample9::class, inherits = true)
        fun doBefore(joinPoint: JoinPoint) {
            executionCount++
        }
    }

    @Aspect
    private object ExampleAspect9B {
        var executionCount = 0

        @Before(TargetExample9::class)
        fun doBefore(joinPoint: JoinPoint) {
            executionCount++
        }
    }

    private interface Example9Interface {
        @TargetExample9
        fun run()
    }

    private class Example9 : Example9Interface {
        override fun run() {}
    }

    @Test
    fun `Only the aspect with inherits true should execute when implementing interface method with mixed inherits settings`() {
        Example9().run()
        assertEquals(1, ExampleAspect9A.executionCount)
        assertEquals(0, ExampleAspect9B.executionCount)
    }

    // AC-10: interface default 메서드에 서로 다른 annotation을 타겟으로 하는 두 Aspect가 각각 적용될 때 모두 실행되는지 검증

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample10A

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample10B

    @Aspect
    private object ExampleAspect10A {
        var executionCount = 0

        @Before(TargetExample10A::class)
        fun doBefore(joinPoint: JoinPoint) {
            executionCount++
        }
    }

    @Aspect
    private object ExampleAspect10B {
        var executionCount = 0

        @Before(TargetExample10B::class)
        fun doBefore(joinPoint: JoinPoint) {
            executionCount++
        }
    }

    private interface Example10Interface {
        @TargetExample10A
        @TargetExample10B
        fun run() {}
    }

    private class Example10 : Example10Interface

    @Test
    fun `Both aspects with different target annotations should each execute when applied to the same interface default method`() {
        Example10().run()
        assertEquals(1, ExampleAspect10A.executionCount)
        assertEquals(1, ExampleAspect10B.executionCount)
    }

    // AC-11: open class 메서드에 서로 다른 annotation을 타겟으로 하는 두 Aspect가 적용될 때,
    //         inherits=false이면 overriding 메서드에서 둘 다 실행되지 않아야 함

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample11A

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample11B

    @Aspect
    private object ExampleAspect11A {
        var executionCount = 0

        @Before(TargetExample11A::class)
        fun doBefore(joinPoint: JoinPoint) {
            executionCount++
        }
    }

    @Aspect
    private object ExampleAspect11B {
        var executionCount = 0

        @Before(TargetExample11B::class)
        fun doBefore(joinPoint: JoinPoint) {
            executionCount++
        }
    }

    private open class Example11Base {
        @TargetExample11A
        @TargetExample11B
        open fun run() {}
    }

    private class Example11Derived : Example11Base() {
        override fun run() {}
    }

    @Test
    fun `Both aspects with different target annotations should execute on open class method but not on overriding method when inherits is false`() {
        Example11Base().run()
        assertEquals(1, ExampleAspect11A.executionCount)
        assertEquals(1, ExampleAspect11B.executionCount)

        Example11Derived().run()
        assertEquals(1, ExampleAspect11A.executionCount)
        assertEquals(1, ExampleAspect11B.executionCount)
    }

    // AC-12: abstract class의 추상 메서드에 서로 다른 annotation을 타겟으로 하는 두 Aspect가 inherits=true로 적용될 때
    //         구현 클래스에서도 모두 실행되어야 함

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample12A

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample12B

    @Aspect
    private object ExampleAspect12A {
        var executionCount = 0

        @Before(TargetExample12A::class, inherits = true)
        fun doBefore(joinPoint: JoinPoint) {
            executionCount++
        }
    }

    @Aspect
    private object ExampleAspect12B {
        var executionCount = 0

        @Before(TargetExample12B::class, inherits = true)
        fun doBefore(joinPoint: JoinPoint) {
            executionCount++
        }
    }

    private abstract class Example12Base {
        @TargetExample12A
        @TargetExample12B
        abstract fun run()
    }

    private class Example12 : Example12Base() {
        override fun run() {}
    }

    @Test
    fun `Both aspects with different target annotations should execute on overriding method when inherits is true and annotations are on abstract class method`() {
        Example12().run()
        assertEquals(1, ExampleAspect12A.executionCount)
        assertEquals(1, ExampleAspect12B.executionCount)
    }

    // AC-13: interface 메서드에 서로 다른 annotation을 타겟으로 하는 두 Aspect가 있을 때,
    //         inherits=true인 Aspect만 구현 클래스에서 실행되어야 함

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample13A

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample13B

    @Aspect
    private object ExampleAspect13A {
        var executionCount = 0

        @Before(TargetExample13A::class, inherits = true)
        fun doBefore(joinPoint: JoinPoint) {
            executionCount++
        }
    }

    @Aspect
    private object ExampleAspect13B {
        var executionCount = 0

        @Before(TargetExample13B::class)
        fun doBefore(joinPoint: JoinPoint) {
            executionCount++
        }
    }

    private interface Example13Interface {
        @TargetExample13A
        @TargetExample13B
        fun run()
    }

    private class Example13 : Example13Interface {
        override fun run() {}
    }

    @Test
    fun `Only the aspect with inherits true should execute when implementing interface method with mixed inherits settings and different target annotations`() {
        Example13().run()
        assertEquals(1, ExampleAspect13A.executionCount)
        assertEquals(0, ExampleAspect13B.executionCount)
    }
}
