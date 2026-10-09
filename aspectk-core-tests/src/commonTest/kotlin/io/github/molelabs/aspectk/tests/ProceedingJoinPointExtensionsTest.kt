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

import io.github.molelabs.aspectk.runtime.Around
import io.github.molelabs.aspectk.runtime.Aspect
import io.github.molelabs.aspectk.runtime.ProceedingJoinPoint
import io.github.molelabs.aspectk.runtime.SuspendProceedingJoinPoint
import io.github.molelabs.aspectk.runtime.getArg
import io.github.molelabs.aspectk.runtime.proceedAs
import io.github.molelabs.aspectk.runtime.proceedWith
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private const val REPLACED_BY_NAME = "extension-replaced"

@Target(AnnotationTarget.FUNCTION)
private annotation class TargetProceedWithExtension

@Aspect
private object ProceedWithExtensionAspect {
    @Around(TargetProceedWithExtension::class)
    fun doAround(pjp: ProceedingJoinPoint): Any? = pjp.proceedWith("suffix" to REPLACED_BY_NAME)
}

@TargetProceedWithExtension
private fun String.proceedWithJoin(
    separator: String,
    suffix: String,
): String = this + separator + suffix

@Suppress("UNUSED")
class ProceedingJoinPointExtensionsTest {
    // --- proceedAs ---

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetProceedAs

    @Aspect
    private object ProceedAsAspect {
        @Around(TargetProceedAs::class)
        fun doAround(pjp: ProceedingJoinPoint): Any? = pjp.proceedAs<Int>() * 2
    }

    private class ProceedAsExample {
        @TargetProceedAs
        fun three(): Int = 3
    }

    @Test
    fun `proceedAs returns the result of the body as the requested type`() {
        assertEquals(6, ProceedAsExample().three())
    }

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetProceedAsArgs

    @Aspect
    private object ProceedAsArgsAspect {
        @Around(TargetProceedAsArgs::class)
        fun doAround(pjp: ProceedingJoinPoint): Any? = pjp.proceedAs<String>("replaced").uppercase()
    }

    private class ProceedAsArgsExample {
        @TargetProceedAsArgs
        fun echo(value: String): String = value
    }

    @Test
    fun `proceedAs with arguments substitutes them before casting the result`() {
        assertEquals("REPLACED", ProceedAsArgsExample().echo("original"))
    }

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetProceedAsWrongType

    @Aspect
    private object ProceedAsWrongTypeAspect {
        @Around(TargetProceedAsWrongType::class)
        fun doAround(pjp: ProceedingJoinPoint): Any? = pjp.proceedAs<Int>()
    }

    private class ProceedAsWrongTypeExample {
        @TargetProceedAsWrongType
        fun text(): String = "text"
    }

    @Test
    fun `proceedAs throws ClassCastException when the result is not of the requested type`() {
        assertFailsWith<ClassCastException> { ProceedAsWrongTypeExample().text() }
    }

    // --- proceedWith ---

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetProceedWith

    @Aspect
    private object ProceedWithAspect {
        @Around(TargetProceedWith::class)
        fun doAround(pjp: ProceedingJoinPoint): Any? = pjp.proceedWith("b" to pjp.getArg<Int>("b") * 10)
    }

    private class ProceedWithExample {
        @TargetProceedWith
        fun describe(
            a: Int,
            b: Int,
            c: Int,
        ): String = "$a-$b-$c"
    }

    @Test
    fun `proceedWith replaces only the named argument of a member function`() {
        assertEquals("1-20-3", ProceedWithExample().describe(1, 2, 3))
    }

    @Test
    fun `proceedWith keeps the receiver of an extension function`() {
        assertEquals("hi, $REPLACED_BY_NAME", "hi".proceedWithJoin(", ", "original"))
    }

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetProceedWithUnknown

    @Aspect
    private object ProceedWithUnknownAspect {
        @Around(TargetProceedWithUnknown::class)
        fun doAround(pjp: ProceedingJoinPoint): Any? = pjp.proceedWith("missing" to 1)
    }

    private class ProceedWithUnknownExample {
        @TargetProceedWithUnknown
        fun work(value: Int): Int = value
    }

    @Test
    fun `proceedWith throws NoSuchElementException for a name that is not a parameter`() {
        assertFailsWith<NoSuchElementException> { ProceedWithUnknownExample().work(1) }
    }

    // --- suspend ---

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetSuspendProceed

    @Aspect
    private object SuspendProceedAspect {
        @Around(TargetSuspendProceed::class)
        suspend fun doAround(pjp: SuspendProceedingJoinPoint): Any? {
            val original = pjp.proceedAs<Int>()
            val replaced = pjp.proceedWith("value" to 100) as Int
            return original + replaced
        }
    }

    private class SuspendProceedExample {
        @TargetSuspendProceed
        suspend fun load(value: Int): Int = value
    }

    @Test
    fun `proceedAs and proceedWith work on a suspend function`() = runTest {
        assertEquals(101, SuspendProceedExample().load(1))
    }
}
