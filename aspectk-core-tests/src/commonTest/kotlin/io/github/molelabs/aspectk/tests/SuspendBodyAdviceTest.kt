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
import io.github.molelabs.aspectk.runtime.JoinPoint
import io.github.molelabs.aspectk.runtime.SuspendProceedingJoinPoint
import io.github.molelabs.aspectk.tests.SuspendBodyAdviceTest.AroundTopLevelTarget
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Weaving advice into `suspend` functions whose body actually performs a suspension
 * (calls another suspend function such as `delay`).
 *
 * `@After`/`@Around` copy the original body into a generated local function `$<name>`.
 * That local function must be `suspend` when the target is `suspend`, or the JVM
 * `AddContinuationLowering` fails with "has no continuation".
 *
 * For `@Around` the wrapper lambda also goes through a SAM interface: on a `suspend`
 * target the plugin emits a `DefaultSuspendProceedingJoinPoint` with a `suspend`
 * `SuspendOnProceedListener`, and the advice must take a [SuspendProceedingJoinPoint]
 * and be declared `suspend`.
 */
@Suppress("UNUSED")
class SuspendBodyAdviceTest {
    @BeforeTest
    fun reset() {
        AfterValueAspect.ran = false
        AfterThrowAspect.ran = false
        AroundMemberAspect.proceeded = false
    }

    // 1. @After on a suspend member function that suspends and then returns a value.

    @Target(AnnotationTarget.FUNCTION)
    annotation class AfterValueTarget

    @Aspect
    object AfterValueAspect {
        var ran = false

        @After(AfterValueTarget::class)
        fun doAfter(joinPoint: JoinPoint) {
            ran = true
            assertEquals("compute", joinPoint.signature.methodName)
        }
    }

    class Calculator {
        @AfterValueTarget
        suspend fun compute(a: Int, b: Int): Int {
            delay(1)
            return a + b
        }
    }

    @Test
    fun `after advice on a suspending member function preserves the return value`() = runTest {
        val result = Calculator().compute(2, 3)
        assertEquals(5, result)
        assertTrue(AfterValueAspect.ran)
    }

    // 2. @After on a suspend function that throws *after* suspending:
    //    the advice still runs (finally semantics) and the exception still propagates.

    @Target(AnnotationTarget.FUNCTION)
    annotation class AfterThrowTarget

    @Aspect
    object AfterThrowAspect {
        var ran = false

        @After(AfterThrowTarget::class)
        fun doAfter(joinPoint: JoinPoint) {
            ran = true
        }
    }

    class FailingService {
        @AfterThrowTarget
        suspend fun boom(): String {
            delay(1)
            throw IllegalStateException("kaboom")
        }
    }

    @Test
    fun `after advice runs and the exception propagates when a suspending body throws`() = runTest {
        val error =
            assertFailsWith<IllegalStateException> {
                FailingService().boom()
            }
        assertEquals("kaboom", error.message)
        assertTrue(AfterThrowAspect.ran)
    }

    // 3. @Around on a suspend member function: proceed() resumes the suspending body,
    //    and the advice transforms the result.

    @Target(AnnotationTarget.FUNCTION)
    annotation class AroundMemberTarget

    @Aspect
    object AroundMemberAspect {
        var proceeded = false

        @Around(AroundMemberTarget::class)
        suspend fun doAround(pjp: SuspendProceedingJoinPoint): Any? {
            val result = pjp.proceed() as String
            proceeded = true
            return result.uppercase()
        }
    }

    class Greeter {
        @AroundMemberTarget
        suspend fun greet(name: String): String {
            delay(1)
            return "hi $name"
        }
    }

    @Test
    fun `around advice can proceed and transform the result of a suspending member function`() = runTest {
        val result = Greeter().greet("sam")
        assertEquals("HI SAM", result)
        assertTrue(AroundMemberAspect.proceeded)
    }

    // 4. @Around on a top-level suspend function: proceed(vararg) substitutes an argument.

    @Target(AnnotationTarget.FUNCTION)
    annotation class AroundTopLevelTarget

    @Aspect
    object AroundTopLevelAspect {
        @Around(AroundTopLevelTarget::class)
        suspend fun doAround(pjp: SuspendProceedingJoinPoint): Any? {
            assertEquals(null, pjp.target)
            return pjp.proceed("replaced")
        }
    }

    @Test
    fun `around advice on a top-level suspending function can substitute an argument`() = runTest {
        assertEquals("got=replaced", topLevelEcho("original"))
    }

    // 5. @Around that never calls proceed(): the suspending body is skipped entirely.

    @Target(AnnotationTarget.FUNCTION)
    annotation class AroundSkipTarget

    @Aspect
    object AroundSkipAspect {
        @Around(AroundSkipTarget::class)
        suspend fun doAround(pjp: SuspendProceedingJoinPoint): Any? = "stubbed"
    }

    class Loader {
        var bodyRan = false

        @AroundSkipTarget
        suspend fun load(): String {
            delay(1)
            bodyRan = true
            return "real"
        }
    }

    @Test
    fun `around advice that skips proceed never runs the suspending body`() = runTest {
        val loader = Loader()
        assertEquals("stubbed", loader.load())
        assertFalse(loader.bodyRan)
    }
}

@AroundTopLevelTarget
private suspend fun topLevelEcho(value: String): String {
    delay(1)
    return "got=$value"
}
