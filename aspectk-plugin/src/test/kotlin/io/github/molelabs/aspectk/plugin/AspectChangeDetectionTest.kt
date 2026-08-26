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
package io.github.molelabs.aspectk.plugin

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File

class AspectChangeDetectionTest {
    @TempDir
    lateinit var dir: File

    private fun file(
        name: String,
        content: String,
    ): File = File(dir, name).apply { writeText(content) }

    @Test
    fun `a file with no marker is not relevant`() {
        val f = file("Plain.kt", "class Plain { fun run() {} }")

        assertFalse(fileHasAspectMarker(f))
    }

    @ParameterizedTest
    @ValueSource(strings = ["@Aspect", "@Before(LogCall::class)", "@After(LogCall::class)", "@Around(LogCall::class)"])
    fun `each advice-relevant annotation is detected`(annotationLine: String) {
        val f = file("Aspect.kt", "$annotationLine\nfun advice() {}")

        assertTrue(fileHasAspectMarker(f))
    }

    @Test
    fun `a marker in a multi-line annotation argument list is detected`() {
        val f =
            file(
                "Aspect.kt",
                """
                @Before(
                    LogCall::class,
                    OtherCall::class,
                )
                fun log() {}
                """.trimIndent(),
            )

        assertTrue(fileHasAspectMarker(f))
    }

    @Test
    fun `a marker mentioned only in a line comment does not make the file relevant`() {
        val f = file("Notes.kt", "// like JUnit's @Before, this runs first\nfun setup() {}")

        assertFalse(fileHasAspectMarker(f))
    }

    @Test
    fun `a marker mentioned only in a block comment does not make the file relevant`() {
        val f =
            file(
                "Notes.kt",
                """
                /*
                 * Similar in spirit to @Around advice.
                 */
                fun setup() {}
                """.trimIndent(),
            )

        assertFalse(fileHasAspectMarker(f))
    }

    @Test
    fun `a real marker is still detected alongside an unrelated comment mentioning other markers`() {
        val f =
            file(
                "Aspect.kt",
                """
                // not @After or @Around, just @Before
                @Before(LogCall::class)
                fun log() {}
                """.trimIndent(),
            )

        assertTrue(fileHasAspectMarker(f))
    }

    @Test
    fun `every advice kind marker is covered`() {
        assertTrue("@Aspect" in ASPECT_RELEVANT_MARKERS)
        assertTrue("@Before" in ASPECT_RELEVANT_MARKERS)
        assertTrue("@After" in ASPECT_RELEVANT_MARKERS)
        assertTrue("@Around" in ASPECT_RELEVANT_MARKERS)
    }
}
