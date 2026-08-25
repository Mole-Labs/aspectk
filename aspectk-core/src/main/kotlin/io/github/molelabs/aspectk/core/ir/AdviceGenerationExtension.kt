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
import io.github.molelabs.aspectk.core.hints.HintRecord
import io.github.molelabs.aspectk.core.hints.HintsCodec
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
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import java.io.File

@OptIn(UnsafeDuringIrConstructionAPI::class)
internal class AdviceGenerationExtension(
    private val irCompat: IrCompat,
    private val hintsOutputDir: String? = null,
    private val externalHints: List<HintRecord> = emptyList(),
) : IrGenerationExtension {
    override fun generate(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
    ) {
        val aspectkContext = AspectKIrCompilerContext(pluginContext, irCompat)
        val joinPointGenerator = JoinPointGenerator(aspectkContext)
        val methodSignatureGenerator = MethodSignatureGenerator(aspectkContext)
        val adviceCallGenerator = AdviceCallGenerator(aspectkContext)
        val proceedingJoinPointGenerator = ProceedingJoinPointGenerator(aspectkContext)
        val tryCatchWrapperGenerator = TryCatchWrapperGenerator(aspectkContext)
        val localFunctionGenerator = LocalFunctionGenerator(aspectkContext)

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

                hintsOutputDir?.let { dir ->
                    HintsCodec.write(aspectkContext.localHints, File(dir, "hints.json"))
                }

                mergeExternalHints(aspectkContext, pluginContext)

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

    // Resolves each cross-module hint's advice/aspect symbols against this module's plugin
    // context (works because they were compiled dependencies — see docs/design-decision/
    // cross-module-weaving.md §2) and inserts one AspectContext per (hint, target) pair,
    // after local advice, so discovery order stays "local first" (spec §4).
    // A hint that fails to resolve (e.g. a stale hints.json from a partial rebuild) is
    // silently skipped rather than crashing this module's compilation.
    private fun mergeExternalHints(
        aspectkContext: AspectKIrCompilerContext,
        pluginContext: IrPluginContext,
    ) {
        externalHints.forEach { hint ->
            val classId = ClassId(FqName(hint.packageName), FqName(hint.className), false)
            val aspectSymbol = irCompat.referenceClass(pluginContext, classId) ?: return@forEach
            val callableId = CallableId(classId, Name.identifier(hint.functionName))
            val adviceSymbol = irCompat.referenceFunctions(pluginContext, callableId).firstOrNull() ?: return@forEach
            val kind = AspectContext.Kind.valueOf(hint.kind)

            hint.targets.forEach { targetFqName ->
                aspectkContext.aspectLookUp.add(
                    fqName = FqName(targetFqName),
                    aspectContext =
                    AspectContext(
                        advice = adviceSymbol,
                        aspect = aspectSymbol,
                        kind = kind,
                        inherits = hint.inherits,
                    ),
                )
            }
        }
    }
}
