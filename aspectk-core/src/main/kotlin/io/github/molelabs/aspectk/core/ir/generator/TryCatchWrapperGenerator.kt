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

import io.github.molelabs.aspectk.core.ir.AspectKIrCompilerContext
import io.github.molelabs.aspectk.core.ir.withIrBuilder
import org.jetbrains.kotlin.backend.common.lower.irThrow
import org.jetbrains.kotlin.ir.builders.declarations.buildVariable
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrCatch
import org.jetbrains.kotlin.ir.expressions.IrTry
import org.jetbrains.kotlin.ir.expressions.impl.IrCatchImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrTryImpl
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.name.Name

@OptIn(UnsafeDuringIrConstructionAPI::class)
internal class TryCatchWrapperGenerator(
    private val aspectKCompilerContext: AspectKIrCompilerContext,
) {
    fun generateTryCatchWrapper(
        declaration: IrFunction,
        localFunc: IrSimpleFunction,
    ): IrTry {
        val valueParams = declaration.parameters.filter { it.kind == IrParameterKind.Regular }
        val tryResult =
            aspectKCompilerContext.withIrBuilder(declaration.symbol) {
                irReturn(
                    irCall(localFunc.symbol).apply {
                        // $doSomething(arg1, arg2, ...)
                        valueParams.forEachIndexed { index, param ->
                            arguments[index] = irGet(param)
                        }
                    },
                )
            }

        val catchParams =
            buildVariable(
                parent = declaration,
                startOffset = -1,
                endOffset = -1,
                origin = IrDeclarationOrigin.CATCH_PARAMETER,
                name = Name.identifier("e"),
                type = aspectKCompilerContext.pluginContext.irBuiltIns.throwableType,
            )

        val catches =
            listOf<IrCatch>(
                IrCatchImpl(
                    startOffset = -1,
                    endOffset = -1,
                    catchParameter = catchParams,
                ).apply {
                    result =
                        aspectKCompilerContext.withIrBuilder(declaration.symbol) {
                            irReturn(
                                irThrow(irGet(catchParams)),
                            )
                        }
                },
            )

        return IrTryImpl(
            startOffset = -1,
            endOffset = -1,
            type = aspectKCompilerContext.pluginContext.irBuiltIns.nothingType,
            tryResult = tryResult,
            catches = catches,
            finallyExpression = null,
        )
    }
}
