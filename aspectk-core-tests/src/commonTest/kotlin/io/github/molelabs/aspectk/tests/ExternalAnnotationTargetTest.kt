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
import io.github.molelabs.aspectk.runtime.Before
import io.github.molelabs.aspectk.runtime.JoinPoint
import io.github.molelabs.aspectk.runtime.ProceedingJoinPoint
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A target annotation that this project does not declare: one from the standard library and one
 * from a third-party library. The advice has to apply to functions of this module that carry it.
 */
@Suppress("UNUSED")
class ExternalAnnotationTargetTest {
    @Aspect
    private object ExternalAspect {
        val log = mutableListOf<String>()

        @Before(PublishedApi::class)
        fun onPublishedApi(jp: JoinPoint) {
            val listed = jp.signature.annotations.any { it.type == PublishedApi::class }
            log.add("before:" + jp.signature.methodName + ":listed=" + listed)
        }

        @Around(DelicateCoroutinesApi::class)
        fun onDelicateApi(pjp: ProceedingJoinPoint): Any? {
            log.add("in:" + pjp.signature.methodName)
            return pjp.proceed().also { log.add("out") }
        }
    }

    private class Service {
        @PublishedApi
        internal fun exposed(): String {
            ExternalAspect.log.add("body")
            return "exposed"
        }

        @DelicateCoroutinesApi
        fun delicate(value: Int): Int {
            ExternalAspect.log.add("body")
            return value + 1
        }

        fun plain(): String {
            ExternalAspect.log.add("body")
            return "plain"
        }
    }

    @BeforeTest
    fun reset() {
        ExternalAspect.log.clear()
    }

    @Test
    fun `before advice applies to a function carrying a standard library annotation`() {
        assertEquals("exposed", Service().exposed())
        assertEquals(listOf("before:exposed:listed=true", "body"), ExternalAspect.log)
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun `around advice applies to a function carrying a third-party library annotation`() {
        assertEquals(2, Service().delicate(1))
        assertEquals(listOf("in:delicate", "body", "out"), ExternalAspect.log)
    }

    @Test
    fun `a function without the external annotation is left alone`() {
        assertEquals("plain", Service().plain())
        assertEquals(listOf("body"), ExternalAspect.log)
    }
}
