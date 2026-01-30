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
package com.mole.core.ir

import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.constructors

@OptIn(UnsafeDuringIrConstructionAPI::class)
internal class JoinPointGenerator(
    private val aspectKContext: AspectKIrCompilerContext,
) {
    private val joinPointConstructor = aspectKContext.joinPointSymbol.constructors.first()

    fun generate(
        declaration: IrFunction,
        methodSignatureField: IrField,
    ): IrExpression =
        aspectKContext.withIrBuilder(declaration.symbol) {
            irCall(joinPointConstructor).apply {
                val receiver =
                    declaration.dispatchReceiverParameter?.let {
                        irGet(it)
                    } ?: irNull(aspectKContext.pluginContext.irBuiltIns.anyNType)

                arguments[0] = receiver
                arguments[1] = irGetField(null, methodSignatureField, methodSignatureField.type)
                arguments[2] =
                    aspectKContext.createIrListOf(
                        scope = declaration.symbol,
                        elements =
                            declaration.parameters.map {
                                irGet(it)
                            },
                    )
            }
        }
}
