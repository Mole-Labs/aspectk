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
package com.mole.aspectk.core.ir.generator

import com.mole.aspectk.core.ir.AspectKIrCompilerContext
import com.mole.aspectk.core.ir.add
import com.mole.aspectk.core.ir.withIrBuilder
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.name.FqName

@OptIn(UnsafeDuringIrConstructionAPI::class)
internal class AdviceCallGenerator(
    private val aspectKContext: AspectKIrCompilerContext,
) {
    fun generateAdviceCalls(
        declaration: IrFunction,
        target: FqName,
        joinPoint: IrExpression,
        checkInherits: Boolean = false,
    ): IrStatement = aspectKContext
        .withIrBuilder(declaration.symbol) {
            irBlock {
                aspectKContext.aspectLookUp[target].forEach {
                    if (checkInherits && !it.inherits) return@forEach
                    +irCall(it.advice.symbol).apply {
                        dispatchReceiver = irGetObject(it.aspect)
                        arguments[1] = joinPoint.deepCopyWithSymbols()
                    }
                }
            }
        }.also { declaration.body?.add(it) }
}
