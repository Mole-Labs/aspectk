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
package io.github.molelabs.aspectk.core

import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class AspectKCommandLineProcessorTest {
    private val processor = AspectKCommandLineProcessor()

    @Test
    fun `stores the hints output dir option in the compiler configuration`() {
        val configuration = CompilerConfiguration()
        val option = processor.pluginOptions.first { it.optionName == AspectKCommandLineProcessor.HINTS_OUTPUT_DIR_OPTION }

        processor.processOption(option, "/tmp/out", configuration)

        assertEquals("/tmp/out", configuration.get(AspectKCommandLineProcessor.HINTS_OUTPUT_DIR_KEY))
    }

    @Test
    fun `accumulates repeated hints path options in declaration order`() {
        val configuration = CompilerConfiguration()
        val option = processor.pluginOptions.first { it.optionName == AspectKCommandLineProcessor.HINTS_PATH_OPTION }

        processor.processOption(option, "/tmp/a", configuration)
        processor.processOption(option, "/tmp/b", configuration)

        assertEquals(listOf("/tmp/a", "/tmp/b"), configuration.getList(AspectKCommandLineProcessor.HINTS_PATHS_KEY))
    }
}
