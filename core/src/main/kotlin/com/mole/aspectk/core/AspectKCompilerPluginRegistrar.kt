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

import com.google.auto.service.AutoService
import com.mole.aspectk.core.ir.AdviceGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration

// Entry point for the AspectK Kotlin compiler plugin.
// @AutoService writes a service-provider file into META-INF at compile time so that the
// K2 compiler discovers and loads this registrar automatically at startup.
@OptIn(ExperimentalCompilerApi::class)
@AutoService(CompilerPluginRegistrar::class)
internal class AspectKCompilerPluginRegistrar : CompilerPluginRegistrar() {
    // Declares that this plugin targets the K2 (FIR + IR) compiler.
    override val supportsK2: Boolean
        get() = true

    // Registers AdviceGenerationExtension to participate in the IR generation phase,
    // which is where the compile-time AOP transformations are applied.
    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        IrGenerationExtension.registerExtension(
            AdviceGenerationExtension(),
        )
    }
}
