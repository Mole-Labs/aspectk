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

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey

// Handles -P plugin:<pluginId>:<key>=<value> arguments passed to the Kotlin compiler.
// Two options support cross-module weaving (docs/design-decision/cross-module-weaving.md):
// - hintsOutputDir: where this module writes its own advice hints (single value).
// - hintsPath: a directory containing an upstream module's hints.json (repeatable).
// The pluginId must match the subpluginId declared in the Gradle plugin (AspectKGradleSubPlugin).
@OptIn(ExperimentalCompilerApi::class)
internal class AspectKCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String = "io.github.mole-labs.aspectk"

    override val pluginOptions: Collection<AbstractCliOption> =
        listOf(
            CliOption(
                optionName = HINTS_OUTPUT_DIR_OPTION,
                valueDescription = "<path>",
                description = "Directory this module writes its own aspect hints to.",
                required = false,
            ),
            CliOption(
                optionName = HINTS_PATH_OPTION,
                valueDescription = "<path>",
                description = "Directory containing an upstream module's aspect hints (hints.json). May repeat.",
                required = false,
                allowMultipleOccurrences = true,
            ),
        )

    override fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration,
    ) {
        when (option.optionName) {
            HINTS_OUTPUT_DIR_OPTION -> configuration.put(HINTS_OUTPUT_DIR_KEY, value)
            HINTS_PATH_OPTION -> configuration.put(HINTS_PATHS_KEY, (configuration.getList(HINTS_PATHS_KEY) + value).toMutableList())
            else -> error("Unexpected AspectK plugin option: ${option.optionName}")
        }
    }

    companion object {
        const val HINTS_OUTPUT_DIR_OPTION = "hintsOutputDir"
        const val HINTS_PATH_OPTION = "hintsPath"

        val HINTS_OUTPUT_DIR_KEY: CompilerConfigurationKey<String> =
            CompilerConfigurationKey.create("aspectk hints output directory")
        val HINTS_PATHS_KEY: CompilerConfigurationKey<MutableList<String>> =
            CompilerConfigurationKey.create("aspectk cross-module hints directories")
    }
}
