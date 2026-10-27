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
package io.github.molelabs.aspectk.core.fir

import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

// Frontend checks on how aspects and advice are declared. They only look at the declaration
// itself and its enclosing class, so they work file by file, as the IDE analyses code.
internal class AspectKFirExtensionRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::AspectKCheckers
        registerDiagnosticContainers(AspectKDiagnostics)
    }
}
