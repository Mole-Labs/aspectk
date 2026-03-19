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
import org.jetbrains.kotlin.ir.builders.irAs
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrTry
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.name.FqName

@OptIn(UnsafeDuringIrConstructionAPI::class)
internal class AdviceCallGenerator(
    private val aspectKContext: AspectKIrCompilerContext,
) {
    /** Prepends @Before advice calls to the function body. */
    fun generateAdviceCalls(
        declaration: IrFunction,
        target: FqName,
        joinPoint: IrExpression,
        checkInherits: Boolean = false,
    ) = buildCallBlock(declaration, target, joinPoint, checkInherits, AspectContext.Kind.BEFORE)
        .also { declaration.body?.add(it) }

    /** Appends @After advice calls to the function body (before any trailing return). */
    fun generateAfterAdviceCalls(
        declaration: IrFunction,
        target: FqName,
        joinPoint: IrExpression,
        tryCatchWrapper: IrTry,
        localFunction: IrSimpleFunction,
        checkInherits: Boolean = false,
    ) {
        val finalExpression =
            buildCallBlock(declaration, target, joinPoint, checkInherits, AspectContext.Kind.AFTER)
        tryCatchWrapper.finallyExpression = finalExpression
        val returnStatement =
            aspectKContext.withIrBuilder(declaration.symbol) {
                irReturn(tryCatchWrapper)
            }

        (declaration.body as? IrBlockBody)?.statements?.let { statement ->
            statement.clear()
            statement.add(localFunction)
            statement.add(returnStatement)
        }
    }

    /**
     * Replaces the function body with:
     *   1. [localFunction] declaration (the `$<name>` local function holding the original body)
     *   2. @Around advice calls, each receiving the provided [proceedingJoinPoint] expression.
     */
    fun generateAroundAdviceCalls(
        declaration: IrFunction,
        target: FqName,
        localFunction: IrSimpleFunction,
        proceedingJoinPoint: IrExpression,
        checkInherits: Boolean = false,
    ) {
        val aroundCallback =
            buildAroundCallBlock(
                declaration,
                target,
                proceedingJoinPoint,
                checkInherits,
            )
        (declaration.body as? IrBlockBody)?.statements?.let { statement ->
            statement.clear()
            statement.add(localFunction)
            statement.add(aroundCallback)
        }
    }

    private fun buildCallBlock(
        declaration: IrFunction,
        target: FqName,
        joinPointExpr: IrExpression,
        checkInherits: Boolean,
        kind: AspectContext.Kind,
    ) = aspectKContext.withIrBuilder(declaration.symbol) {
        irBlock {
            aspectKContext.aspectLookUp[target].forEach { targetContext ->
                if (targetContext.kind != kind) return@forEach
                if (checkInherits && !targetContext.inherits) return@forEach
                +irCall(targetContext.advice.symbol).apply {
                    dispatchReceiver = irGetObject(targetContext.aspect)
                    arguments[1] = joinPointExpr.deepCopyWithSymbols()
                }
            }
        }
    }

    private fun buildAroundCallBlock(
        declaration: IrFunction,
        target: FqName,
        joinPointExpr: IrExpression,
        checkInherits: Boolean,
    ) = aspectKContext.withIrBuilder(declaration.symbol) {
        irBlock {
            aspectKContext.aspectLookUp[target].forEach { targetContext ->
                if (checkInherits && !targetContext.inherits) return@forEach
                +irReturn(
                    irAs(
                        irCall(targetContext.advice.symbol).apply {
                            dispatchReceiver = irGetObject(targetContext.aspect)
                            arguments[1] = joinPointExpr.deepCopyWithSymbols(declaration)
                        },
                        declaration.returnType,
                    ),
                )
            }
        }
    }
}
