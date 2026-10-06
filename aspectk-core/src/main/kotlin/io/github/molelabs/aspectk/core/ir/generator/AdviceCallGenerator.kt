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
package io.github.molelabs.aspectk.core.ir.generator

import io.github.molelabs.aspectk.core.ir.AspectContext
import io.github.molelabs.aspectk.core.ir.AspectKIrCompilerContext
import io.github.molelabs.aspectk.core.ir.add
import io.github.molelabs.aspectk.core.ir.withIrBuilder
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.irAs
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrTryImpl
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols

@OptIn(UnsafeDuringIrConstructionAPI::class)
internal class AdviceCallGenerator(
    private val aspectKContext: AspectKIrCompilerContext,
) {
    /** Prepends @Before advice calls to the function body. */
    fun generateAdviceCalls(
        declaration: IrFunction,
        contexts: List<AspectContext>,
        joinPoint: IrExpression,
    ) = aspectKContext
        .withIrBuilder(declaration.symbol) {
            irBlock { contexts.forEach { +adviceCall(it, joinPoint) } }
        }.also { declaration.body?.add(it) }

    private fun IrBuilderWithScope.adviceCall(
        context: AspectContext,
        joinPointExpr: IrExpression,
    ) = irCall(context.advice).apply {
        dispatchReceiver = irGetObject(context.aspect)
        arguments[1] = joinPointExpr.deepCopyWithSymbols()
    }

    /**
     * Wraps the function body in `try { <body> } finally { <after> }` where it stands. A return
     * inside the try still returns from the function, running the finally first, and an inline
     * function keeps its reified type arguments, inlined lambda parameters and non-local returns.
     */
    fun generateAfterAdviceCalls(
        declaration: IrFunction,
        context: AspectContext,
        joinPoint: IrExpression,
    ) {
        val statements = (declaration.body as? IrBlockBody)?.statements ?: return
        val finallyExpression =
            aspectKContext.withIrBuilder(declaration.symbol) { irBlock { +adviceCall(context, joinPoint) } }
        val unitType = aspectKContext.pluginContext.irBuiltIns.unitType
        val body =
            aspectKContext.withIrBuilder(declaration.symbol) {
                irBlock(resultType = unitType) { statements.forEach { +it } }
            }
        statements.clear()
        statements.add(
            IrTryImpl(
                startOffset = -1,
                endOffset = -1,
                type = unitType,
                tryResult = body,
                catches = emptyList(),
                finallyExpression = finallyExpression,
            ),
        )
    }

    /**
     * Replaces the function body with the @Around advice call, which receives
     * [proceedingJoinPoint] (whose listener already holds the original body).
     */
    fun generateAroundAdviceCalls(
        declaration: IrFunction,
        context: AspectContext,
        proceedingJoinPoint: IrExpression,
    ) {
        val aroundCallback = buildAroundCallBlock(declaration, context, proceedingJoinPoint)
        (declaration.body as? IrBlockBody)?.statements?.let { statement ->
            statement.clear()
            statement.add(aroundCallback)
        }
    }

    private fun buildAroundCallBlock(
        declaration: IrFunction,
        context: AspectContext,
        joinPointExpr: IrExpression,
    ) = aspectKContext.withIrBuilder(declaration.symbol) {
        irBlock {
            +irReturn(
                irAs(
                    irCall(context.advice).apply {
                        dispatchReceiver = irGetObject(context.aspect)
                        arguments[1] = joinPointExpr.deepCopyWithSymbols(declaration)
                    },
                    declaration.returnType,
                ),
            )
        }
    }
}
