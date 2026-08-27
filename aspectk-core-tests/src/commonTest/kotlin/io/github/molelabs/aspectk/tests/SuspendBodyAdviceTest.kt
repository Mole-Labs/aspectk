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
import io.github.molelabs.aspectk.runtime.Aspect
import io.github.molelabs.aspectk.runtime.Before
import io.github.molelabs.aspectk.runtime.JoinPoint
import io.github.molelabs.aspectk.tests.SuspendBodyAdviceTest.CombinedTarget
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Regression tests for weaving advice into `suspend` functions whose body actually
 * performs a suspension (i.e. calls another suspend function such as `delay`).
 *
 * `@After`/`@Around` copy the original body into a generated local function `$<name>`.
 * Before the fix that local function was always non-suspend, so a suspend call inside
 * the copied body made the JVM `AddContinuationLowering` fail with:
 *
 *   FUN LOCAL_FUNCTION name:$work ... has no continuation;
 *   can't call FUN ... delay ... [suspend]
 *
 * The fix marks the generated local function `isSuspend` when the target is suspend.
 */
@Suppress("UNUSED")
class SuspendBodyAdviceTest {
    @BeforeTest
    fun reset() {
        AfterValueAspect.ran = false
        AfterThrowAspect.ran = false
        CombinedAspect.events.clear()
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

    // 3. @Before + @After on the same top-level suspend function (no dispatch receiver):
    //    both fire, in order, around a body that suspends.

    @Target(AnnotationTarget.FUNCTION)
    annotation class CombinedTarget

    @Aspect
    object CombinedAspect {
        val events = mutableListOf<String>()

        @Before(CombinedTarget::class)
        fun doBefore(joinPoint: JoinPoint) {
            events += "before"
        }

        @After(CombinedTarget::class)
        fun doAfter(joinPoint: JoinPoint) {
            events += "after"
        }
    }

    @Test
    fun `before and after both weave into a top-level suspending function`() = runTest {
        val result = topLevelSuspendingWork("x")
        assertEquals("done-x", result)
        assertEquals(listOf("before", "body", "after"), CombinedAspect.events)
    }
}

@CombinedTarget
private suspend fun topLevelSuspendingWork(tag: String): String {
    delay(1)
    SuspendBodyAdviceTest.CombinedAspect.events += "body"
    return "done-$tag"
}
