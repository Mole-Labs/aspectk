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
 * The order of several advices on one function, by priority:
 *
 *   1. Kind: @Before, then @Around, then the body, then @After inside every @Around
 *   2. Target annotation: the order the annotations are written on the function
 *
 * "First" means it runs first for @Before and @After, and is the outermost for @Around. The order
 * between advices of the same kind that target the same annotation is not guaranteed.
 *
 * One everyday use case per rule, run on every platform. The exhaustive cases are in aspectk-core:
 * AdviceKindOrderTest and TargetAnnotationOrderTest.
 */
@Suppress("UNUSED")
class AdviceOrderTest {
    @Target(AnnotationTarget.FUNCTION)
    private annotation class Transfer

    @Aspect
    private object TransferAspect {
        val log = mutableListOf<String>()

        @After(Transfer::class)
        fun audit(jp: JoinPoint) {
            log.add("audit")
        }

        @Around(Transfer::class)
        fun transaction(pjp: ProceedingJoinPoint): Any? {
            log.add("begin")
            return pjp.proceed().also { log.add("commit") }
        }

        @Before(Transfer::class)
        fun checkPermission(jp: JoinPoint) {
            log.add("check")
        }
    }

    private class Bank {
        @Transfer
        fun transfer(amount: Int): Int {
            TransferAspect.log.add("transfer:$amount")
            return amount
        }
    }

    @Test
    fun `kind - a permission check runs first and an audit log runs inside the transaction`() {
        assertEquals(100, Bank().transfer(100))
        assertEquals(listOf("check", "begin", "transfer:100", "audit", "commit"), TransferAspect.log)
    }

    @Target(AnnotationTarget.FUNCTION)
    private annotation class Cached

    @Target(AnnotationTarget.FUNCTION)
    private annotation class Timed

    @Aspect
    private object CacheAndTimeAspect {
        val log = mutableListOf<String>()
        private val cache = mutableMapOf<Any?, Any?>()

        @Around(Cached::class)
        fun cache(pjp: ProceedingJoinPoint): Any? {
            val key = pjp.signature.methodName to pjp.args.last()
            if (key in cache) {
                log.add("hit")
                return cache[key]
            }
            log.add("miss")
            return pjp.proceed().also { cache[key] = it }
        }

        @Around(Timed::class)
        fun time(pjp: ProceedingJoinPoint): Any? {
            log.add("timer-start")
            return pjp.proceed().also { log.add("timer-stop") }
        }
    }

    private class Calculator {
        @Timed
        @Cached
        fun timedThenCached(x: Int): Int {
            CacheAndTimeAspect.log.add("compute")
            return x * 2
        }

        @Cached
        @Timed
        fun cachedThenTimed(x: Int): Int {
            CacheAndTimeAspect.log.add("compute")
            return x * 2
        }
    }

    @Test
    fun `target annotation - the annotation written first wraps the one written after it`() {
        val calculator = Calculator()

        // @Timed outside: the timer also covers a cache hit
        assertEquals(4, calculator.timedThenCached(2))
        assertEquals(4, calculator.timedThenCached(2))
        assertEquals(
            listOf("timer-start", "miss", "compute", "timer-stop", "timer-start", "hit", "timer-stop"),
            CacheAndTimeAspect.log,
        )

        // @Cached outside: a cache hit never reaches the timer
        CacheAndTimeAspect.log.clear()
        assertEquals(6, calculator.cachedThenTimed(3))
        assertEquals(6, calculator.cachedThenTimed(3))
        assertEquals(listOf("miss", "timer-start", "compute", "timer-stop", "hit"), CacheAndTimeAspect.log)
    }
}
