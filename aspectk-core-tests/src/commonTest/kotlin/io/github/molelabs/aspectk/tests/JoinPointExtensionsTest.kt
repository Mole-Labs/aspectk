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

import io.github.molelabs.aspectk.runtime.AnnotationInfo
import io.github.molelabs.aspectk.runtime.Around
import io.github.molelabs.aspectk.runtime.Aspect
import io.github.molelabs.aspectk.runtime.Before
import io.github.molelabs.aspectk.runtime.JoinPoint
import io.github.molelabs.aspectk.runtime.ProceedingJoinPoint
import io.github.molelabs.aspectk.runtime.findAnnotation
import io.github.molelabs.aspectk.runtime.getArg
import io.github.molelabs.aspectk.runtime.getArgOrNull
import io.github.molelabs.aspectk.runtime.getTarget
import io.github.molelabs.aspectk.runtime.getTargetOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@Suppress("UNUSED")
class JoinPointExtensionsTest {
    // --- getArg ---

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetGetArg

    @Aspect
    private object GetArgAspect {
        var capturedName: String? = null
        var capturedAge: Int? = null

        @Before(TargetGetArg::class)
        fun doBefore(jp: JoinPoint) {
            capturedName = jp.getArg<String>("name")
            capturedAge = jp.getArg<Int>("age")
        }
    }

    private class GetArgExample {
        @TargetGetArg
        fun greet(
            name: String,
            age: Int,
        ): String = "Hello $name, age $age"
    }

    @Test
    fun `getArg retrieves argument value by parameter name`() {
        GetArgAspect.capturedName = null
        GetArgAspect.capturedAge = null
        GetArgExample().greet("Alice", 30)
        assertEquals("Alice", GetArgAspect.capturedName)
        assertEquals(30, GetArgAspect.capturedAge)
    }

    // --- getArg throws when name not found ---

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetGetArgMissing

    @Aspect
    private object GetArgMissingAspect {
        var thrown: NoSuchElementException? = null

        @Before(TargetGetArgMissing::class)
        fun doBefore(jp: JoinPoint) {
            thrown =
                runCatching { jp.getArg<String>("nonexistent") }
                    .exceptionOrNull() as? NoSuchElementException
        }
    }

    private class GetArgMissingExample {
        @TargetGetArgMissing
        fun work(x: String): String = x
    }

    @Test
    fun `getArg throws NoSuchElementException when parameter name does not exist`() {
        GetArgMissingAspect.thrown = null
        GetArgMissingExample().work("test")
        assertNotNull(GetArgMissingAspect.thrown)
    }

    // --- getArgOrNull ---

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetGetArgOrNull

    @Aspect
    private object GetArgOrNullAspect {
        var foundValue: String? = "sentinel"
        var missingValue: String? = "sentinel"

        @Before(TargetGetArgOrNull::class)
        fun doBefore(jp: JoinPoint) {
            foundValue = jp.getArgOrNull<String>("label")
            missingValue = jp.getArgOrNull<String>("nonexistent")
        }
    }

    private class GetArgOrNullExample {
        @TargetGetArgOrNull
        fun work(label: String): String = label
    }

    @Test
    fun `getArgOrNull returns value when found and null when not found`() {
        GetArgOrNullAspect.foundValue = "sentinel"
        GetArgOrNullAspect.missingValue = "sentinel"
        GetArgOrNullExample().work("hello")
        assertEquals("hello", GetArgOrNullAspect.foundValue)
        assertNull(GetArgOrNullAspect.missingValue)
    }

    // --- getTarget ---

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetGetTarget

    private class TargetGetTargetExample {
        val id = "instance-42"

        @Aspect
        object GetTargetAspect {
            var capturedId: String? = null

            @Before(TargetGetTarget::class)
            fun doBefore(jp: JoinPoint) {
                capturedId = jp.getTarget<TargetGetTargetExample>().id
            }
        }

        @TargetGetTarget
        fun work(): String = id
    }

    @Test
    fun `getTarget casts target to the specified type`() {
        TargetGetTargetExample.GetTargetAspect.capturedId = null
        TargetGetTargetExample().work()
        assertEquals("instance-42", TargetGetTargetExample.GetTargetAspect.capturedId)
    }

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetGetTargetOrNull

    @Aspect
    private object GetTargetOrNullAspect {
        var capturedTarget: Any? = "sentinel"
        var wrongTypeCast: String? = "sentinel"

        @Before(TargetGetTargetOrNull::class)
        fun doBefore(jp: JoinPoint) {
            capturedTarget = jp.getTargetOrNull<GetTargetOrNullExample>()
            wrongTypeCast = jp.getTargetOrNull<String>()
        }
    }

    private class GetTargetOrNullExample {
        @TargetGetTargetOrNull
        fun work(): String = "ok"
    }

    @Test
    fun `getTargetOrNull returns instance for correct type and null for wrong type`() {
        GetTargetOrNullAspect.capturedTarget = "sentinel"
        GetTargetOrNullAspect.wrongTypeCast = "sentinel"
        GetTargetOrNullExample().work()
        assertNotNull(GetTargetOrNullAspect.capturedTarget)
        assertNull(GetTargetOrNullAspect.wrongTypeCast)
    }

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetFindAnnotation(
        val role: String = "user",
    )

    @Aspect
    private object FindAnnotationAspect {
        var foundAnnotation: AnnotationInfo? = null
        var notFound: AnnotationInfo? = null

        @Before(TargetFindAnnotation::class)
        fun doBefore(jp: JoinPoint) {
            foundAnnotation = jp.findAnnotation<TargetFindAnnotation>()
            notFound = jp.findAnnotation<Before>()
        }
    }

    private class FindAnnotationExample {
        @TargetFindAnnotation(role = "admin")
        fun work(): String = "ok"
    }

    @Test
    fun `findAnnotation returns AnnotationInfo when present and null when absent`() {
        FindAnnotationAspect.foundAnnotation = null
        FindAnnotationAspect.notFound = null
        FindAnnotationExample().work()
        assertNotNull(FindAnnotationAspect.foundAnnotation)
        assertNull(FindAnnotationAspect.notFound)
    }

    @Target(AnnotationTarget.FUNCTION)
    private annotation class TargetAroundGetArg

    @Aspect
    private object AroundGetArgAspect {
        @Around(TargetAroundGetArg::class)
        fun doAround(pjp: ProceedingJoinPoint): Any {
            val multiplier = pjp.getArg<Int>("multiplier")
            val result = pjp.proceed() as Int
            return result * multiplier
        }
    }

    private class AroundGetArgExample {
        @TargetAroundGetArg
        fun compute(
            value: Int,
            multiplier: Int,
        ): Int = value + 1
    }

    @Test
    fun `getArg inside Around advice can read args and combine with proceed result`() {
        val result = AroundGetArgExample().compute(4, 3)
        assertEquals(15, result) // (4+1) * 3
    }
}
