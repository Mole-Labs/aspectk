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

@Suppress("UNCHECKED_CAST", "UNUSED")
class JoinPointPolymorphicTest {
    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample1

    @Aspect
    private object ExampleAspect1 {
        var executed = false

        @Before(TargetExample1::class)
        fun doBefore(joinPoint: JoinPoint) {
            executed = true
        }
    }

    private interface MyInterface {
        @TargetExample1
        fun run()
    }

    private class Derived1 : MyInterface {
        override fun run() {
            // Advice should not run here
        }
    }

    @Test
    fun `Advice should not execute for implementations of annotated interface methods when inherits is false`() {
        Derived1().run()
        assertEquals(false, ExampleAspect1.executed)
    }

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample2

    @Aspect
    private object ExampleAspect2 {
        var executionCount = 0

        @Before(TargetExample2::class)
        fun doBefore(joinPoint: JoinPoint) {
            executionCount++
        }
    }

    private open class Base2 {
        @TargetExample2
        open fun run() {}
    }

    private class Derived2 : Base2() {
        override fun run() {
        }
    }

    @Test
    fun `Advice should not execute on overriding method when annotation is on superclass and inherits is false`() {
        Derived2().run()
        assertEquals(0, ExampleAspect2.executionCount)

        Base2().run()
        assertEquals(1, ExampleAspect2.executionCount)
    }

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample3

    @Aspect
    private object ExampleAspect3 {
        var executionCount = 0

        @Before(TargetExample3::class)
        fun doBefore(joinPoint: JoinPoint) {
            executionCount++
        }
    }

    private open class Base3 {
        open fun run() {}
    }

    private class Derived3 : Base3() {
        @TargetExample3
        override fun run() {
            super.run()
        }
    }

    @Test
    fun `Advice should execute only on overriding method when annotated on child and inherits is false`() {
        Derived3().run()
        assertEquals(1, ExampleAspect3.executionCount)

        Base3().run()
        assertEquals(1, ExampleAspect3.executionCount)
    }

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample4

    @Aspect
    private object ExampleAspect4 {
        var executed = false

        @Before(TargetExample4::class)
        fun doBefore(joinPoint: JoinPoint) {
            executed = true
        }
    }

    private abstract class Base4 {
        @TargetExample4
        abstract fun run()
    }

    private class Derived4 : Base4() {
        override fun run() {}
    }

    @Test
    fun `Advice should not execute for overriding method of annotated abstract method when inherits is false`() {
        Derived4().run()
        assertEquals(false, ExampleAspect4.executed)
    }

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample5

    @Aspect
    private object ExampleAspect5 {
        var executionCount = 0

        @Before(TargetExample5::class, inherits = true)
        fun doBefore(joinPoint: JoinPoint) {
            executionCount++
        }
    }

    private interface Base5 {
        @TargetExample5
        fun run()
    }

    private class Derived5 : Base5 {
        override fun run() {}
    }

    @Test
    fun `Advice should execute on both parent and overriding methods when inherits is true`() {
        Derived5().run()
        assertEquals(1, ExampleAspect5.executionCount)
    }

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample6A

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample6B

    @Aspect
    private object ExampleAspect6 {
        var executionCount = 0

        @Before(TargetExample6A::class, TargetExample6B::class, inherits = true)
        fun doBefore(joinPoint: JoinPoint) {
            executionCount++
        }
    }

    private interface Base6A {
        @TargetExample6A
        fun methodA()
    }

    private interface Base6B {
        @TargetExample6B
        fun methodB()
    }

    private class Derived6 :
        Base6A,
        Base6B {
        override fun methodA() {}

        override fun methodB() {}
    }

    @Test
    fun `Advice should execute for methods from multiple interfaces when inherits is true`() {
        Derived6().methodA()
        assertEquals(1, ExampleAspect6.executionCount)

        Derived6().methodB()
        assertEquals(2, ExampleAspect6.executionCount)
    }

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample7A

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample7B

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample7C

    @Aspect
    private object ExampleAspect7 {
        var executionCount1 = 0
        var executionCount2 = 0
        var executionCount3 = 0

        @Before(TargetExample7A::class, TargetExample7B::class, inherits = true)
        fun doBefore1(joinPoint: JoinPoint) {
            executionCount1++
        }

        @Before(TargetExample7A::class, inherits = true)
        fun doBefore2(joinPoint: JoinPoint) {
            executionCount2++
        }

        @Before(TargetExample7B::class, TargetExample7C::class) // inherits = false
        fun doBefore3(joinPoint: JoinPoint) {
            executionCount3++
        }
    }

    private interface Base7A {
        @TargetExample7A
        fun work1() {}

        @TargetExample7B @TargetExample7C
        fun work2() {}
    }

    private abstract class Base7B : Base7A {
        @TargetExample7C
        abstract fun work3()
    }

    private class Derived7 : Base7B() {
        override fun work1() {}

        override fun work2() {}

        @TargetExample7B
        override fun work3() {}
    }

    @Test
    fun `Advice should execute correctly based on complex inheritance and multiple advice rules`() {
        // Test work1()
        Derived7().work1()
        assertEquals(1, ExampleAspect7.executionCount1)
        assertEquals(1, ExampleAspect7.executionCount2)
        assertEquals(0, ExampleAspect7.executionCount3)

        // Test work2()
        Derived7().work2()
        assertEquals(2, ExampleAspect7.executionCount1)
        assertEquals(1, ExampleAspect7.executionCount2)
        assertEquals(0, ExampleAspect7.executionCount3)

        // Test work3()
        Derived7().work3()
        assertEquals(3, ExampleAspect7.executionCount1)
        assertEquals(1, ExampleAspect7.executionCount2)
        assertEquals(1, ExampleAspect7.executionCount3)
    }

    // ── @After inheritance ───────────────────────────────────────────────────

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample8

    @Aspect
    private object ExampleAspect8 {
        var executionCount = 0

        @After(TargetExample8::class)
        fun doAfter(joinPoint: JoinPoint) {
            executionCount++
        }
    }

    private open class Base8 {
        @TargetExample8
        open fun run() {}
    }

    private class Derived8 : Base8() {
        override fun run() {}
    }

    @Test
    fun `@After advice should not execute for overriding method when annotation is on superclass and inherits is false`() {
        Derived8().run()
        assertEquals(0, ExampleAspect8.executionCount)

        Base8().run()
        assertEquals(1, ExampleAspect8.executionCount)
    }

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample9

    @Aspect
    private object ExampleAspect9 {
        var executionCount = 0

        @After(TargetExample9::class, inherits = true)
        fun doAfter(joinPoint: JoinPoint) {
            executionCount++
        }
    }

    private interface Base9 {
        @TargetExample9
        fun run()
    }

    private class Derived9 : Base9 {
        override fun run() {}
    }

    @Test
    fun `@After advice should execute on overriding method when inherits is true`() {
        Derived9().run()
        assertEquals(1, ExampleAspect9.executionCount)
    }

    // ── @Around inheritance ──────────────────────────────────────────────────

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample10

    @Aspect
    private object ExampleAspect10 {
        var executionCount = 0

        @Around(TargetExample10::class)
        fun doAround(pjp: ProceedingJoinPoint): Any? {
            executionCount++
            return pjp.proceed()
        }
    }

    private open class Base10 {
        @TargetExample10
        open fun run() {}
    }

    private class Derived10 : Base10() {
        override fun run() {}
    }

    @Test
    fun `@Around advice should not execute for overriding method when annotation is on superclass and inherits is false`() {
        Derived10().run()
        assertEquals(0, ExampleAspect10.executionCount)

        Base10().run()
        assertEquals(1, ExampleAspect10.executionCount)
    }

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetExample11

    @Aspect
    private object ExampleAspect11 {
        var executionCount = 0

        @Around(TargetExample11::class, inherits = true)
        fun doAround(pjp: ProceedingJoinPoint): Any? {
            executionCount++
            return pjp.proceed()
        }
    }

    private interface Base11 {
        @TargetExample11
        fun run()
    }

    private class Derived11 : Base11 {
        override fun run() {}
    }

    @Test
    fun `@Around advice should execute on overriding method when inherits is true`() {
        Derived11().run()
        assertEquals(1, ExampleAspect11.executionCount)
    }
}
