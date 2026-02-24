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
package com.mole.aspectk.core

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration

// Handles -P plugin:<pluginId>:<key>=<value> arguments passed to the Kotlin compiler.
// AspectK requires no configuration options at this time, so pluginOptions is empty and
// processOption is a no-op. The pluginId must match the subpluginId declared in the
// Gradle plugin (AspectKGradleSubPlugin).
@OptIn(ExperimentalCompilerApi::class)
internal class AspectKCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String = "com.mole.aspectk"

    override val pluginOptions: Collection<AbstractCliOption> = emptyList()

    override fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration,
    ) = Unit
}
