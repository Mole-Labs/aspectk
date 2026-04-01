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
package io.github.molelabs.aspectk.core.ir

import io.github.molelabs.aspectk.core.compat.IrCompat
import io.github.molelabs.aspectk.core.ir.generator.AdviceCallGenerator
import io.github.molelabs.aspectk.core.ir.generator.JoinPointGenerator
import io.github.molelabs.aspectk.core.ir.generator.LocalFunctionGenerator
import io.github.molelabs.aspectk.core.ir.generator.MethodSignatureGenerator
import io.github.molelabs.aspectk.core.ir.generator.ProceedingJoinPointGenerator
import io.github.molelabs.aspectk.core.ir.generator.TryCatchWrapperGenerator
import io.github.molelabs.aspectk.core.trace
import io.github.molelabs.aspectk.core.tracer
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI

@OptIn(UnsafeDuringIrConstructionAPI::class)
internal class AdviceGenerationExtension(private val irCompat: IrCompat) : IrGenerationExtension {
    override fun generate(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
    ) {
        val aspectkContext = AspectKIrCompilerContext(pluginContext)
        val joinPointGenerator = JoinPointGenerator(aspectkContext)
        val methodSignatureGenerator = MethodSignatureGenerator(aspectkContext, irCompat)
        val adviceCallGenerator = AdviceCallGenerator(aspectkContext)
        val proceedingJoinPointGenerator = ProceedingJoinPointGenerator(aspectkContext, irCompat)
        val tryCatchWrapperGenerator = TryCatchWrapperGenerator(aspectkContext, irCompat)
        val localFunctionGenerator = LocalFunctionGenerator(aspectkContext, irCompat)

        aspectkContext
            .tracer(
                tag =
                moduleFragment.name
                    .asString()
                    .removePrefix("<")
                    .removeSuffix(">"),
                description = "Advice Generation",
            ).trace {
                moduleFragment.acceptChildren(AspectVisitor(aspectkContext), null)
                moduleFragment.acceptChildren(InheritableVisitor(aspectkContext), null)
                moduleFragment.transform(
                    AspectTransformer(
                        joinPointGenerator,
                        methodSignatureGenerator,
                        adviceCallGenerator,
                        proceedingJoinPointGenerator,
                        tryCatchWrapperGenerator,
                        localFunctionGenerator,
                        aspectkContext,
                    ),
                    null,
                )
            }
    }
}
