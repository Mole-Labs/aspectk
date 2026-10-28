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
package io.github.molelabs.aspectk.core.ir.checkers

import io.github.molelabs.aspectk.core.AspectKDiagnostics
import io.github.molelabs.aspectk.core.ir.AspectContext
import io.github.molelabs.aspectk.core.ir.AspectContext.Kind
import io.github.molelabs.aspectk.core.ir.AspectKIrCompilerContext
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory1
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.isInlineParameter

@OptIn(UnsafeDuringIrConstructionAPI::class)
internal object AspectMappingChecker {
    context(aspectKContext: AspectKIrCompilerContext)
    fun check(
        declaration: IrSimpleFunction,
        contexts: List<AspectContext>,
    ): Boolean = declaration.checkTarget(contexts)

    context(aspectKContext: AspectKIrCompilerContext)
    private fun IrSimpleFunction.checkTarget(contexts: List<AspectContext>): Boolean {
        var valid = true
        contexts.filter { it.kind == Kind.AROUND }.forEach { context ->
            if (context.advice.owner.isSuspend != isSuspend) {
                report(AspectKDiagnostics.AROUND_SUSPEND_MISMATCH, context)
                valid = false
            }
            // The body moves into the proceed lambda, which cannot call inline lambda parameters
            if (isInline && parameters.any { it.isInlineParameter() }) {
                report(AspectKDiagnostics.AROUND_INLINE_LAMBDA, context)
                valid = false
            }
        }
        if (isTailrec) {
            contexts.filter { it.kind != Kind.BEFORE }
                .forEach { report(AspectKDiagnostics.TAILREC_ADVICE, it) }
        }
        return valid
    }

    context(aspectKContext: AspectKIrCompilerContext)
    private fun IrFunction.report(
        factory: KtDiagnosticFactory1<String>,
        context: AspectContext,
    ) {
        val advice = "${context.aspect.owner.name}.${context.advice.owner.name}"
        aspectKContext.irCompat.report(aspectKContext.pluginContext, this, factory, advice)
    }
}
