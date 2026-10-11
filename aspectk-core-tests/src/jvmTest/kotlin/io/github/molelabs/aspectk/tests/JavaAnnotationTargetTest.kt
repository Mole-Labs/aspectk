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
import org.jetbrains.annotations.Blocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A target annotation written in Java with CLASS retention, the shape of the threading annotations
 * Android code already carries (`androidx.annotation.WorkerThread`). [Blocking] stands in for them.
 */
@Suppress("UNUSED")
class JavaAnnotationTargetTest {
    @Aspect
    private object BlockingAspect {
        val log = mutableListOf<String>()

        @Before(Blocking::class)
        fun onBlocking(jp: JoinPoint) {
            log.add("before:" + jp.signature.methodName)
        }
    }

    private class Repository {
        @Blocking
        fun load(id: Int): String {
            BlockingAspect.log.add("body")
            return "row-$id"
        }

        fun peek(): String {
            BlockingAspect.log.add("body")
            return "cached"
        }
    }

    @BeforeTest
    fun reset() {
        BlockingAspect.log.clear()
    }

    @Test
    fun `before advice applies to a function carrying a Java annotation`() {
        assertEquals("row-7", Repository().load(7))
        assertEquals(listOf("before:load", "body"), BlockingAspect.log)
    }

    @Test
    fun `a function without the Java annotation is left alone`() {
        assertEquals("cached", Repository().peek())
        assertEquals(listOf("body"), BlockingAspect.log)
    }
}
