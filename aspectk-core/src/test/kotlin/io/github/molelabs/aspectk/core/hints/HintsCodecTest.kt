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
package io.github.molelabs.aspectk.core.hints

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class HintsCodecTest {
    @Test
    fun `round-trips a list of hint records through encode and decode`() {
        val records = listOf(
            HintRecord("com.core", "LoggingAspect", "log", "BEFORE", listOf("com.example.LogCall"), false),
            HintRecord("com.core", "MetricsAspect", "track", "AROUND", listOf("com.example.Api.A", "com.example.Api.B"), true),
        )

        val decoded = HintsCodec.decode(HintsCodec.encode(records))

        assertEquals(records, decoded)
    }

    @Test
    fun `write then read round-trips through a file`() {
        val dir = createTempDirectory("aspectk-hints-test").toFile()
        val file = File(dir, "hints.json")
        val records = listOf(HintRecord("a.b", "C", "d", "BEFORE", listOf("x.Y"), false))

        HintsCodec.write(records, file)
        val loaded = HintsCodec.read(file)

        assertEquals(records, loaded)
    }

    @Test
    fun `read returns an empty list when the file does not exist`() {
        assertEquals(emptyList<HintRecord>(), HintsCodec.read(File("/nonexistent/aspectk/hints.json")))
    }

    @Test
    fun `escapes and restores special characters in string fields`() {
        val records = listOf(HintRecord("a\"b\\c", "C\nD", "e\tf", "AFTER", listOf("x.\"Y\""), true))

        val decoded = HintsCodec.decode(HintsCodec.encode(records))

        assertEquals(records, decoded)
    }

    @Test
    fun `encode of an empty list produces an empty JSON array`() {
        assertEquals("[]", HintsCodec.encode(emptyList()))
    }
}
