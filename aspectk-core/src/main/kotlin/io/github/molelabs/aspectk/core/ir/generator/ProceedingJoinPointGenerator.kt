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
import io.github.molelabs.aspectk.core.ir.createIrListOf
import io.github.molelabs.aspectk.core.ir.function1Type
import io.github.molelabs.aspectk.core.ir.listAnyNType
import io.github.molelabs.aspectk.core.ir.listGetFun
import io.github.molelabs.aspectk.core.ir.withIrBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.builders.declarations.IrValueParameterBuilder
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.declarations.buildValueParameter
import org.jetbrains.kotlin.ir.builders.irAs
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueDeclaration
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.IrTypeOperator
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrTypeOperatorCallImpl
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.name.Name

@OptIn(UnsafeDuringIrConstructionAPI::class)
internal class ProceedingJoinPointGenerator(
    private val aspectKCompilerContext: AspectKIrCompilerContext,
) {
    private val proceedingJoinPointConstructor =
        aspectKCompilerContext.proceedingJoinPointSymbol.constructors.first()

    /**
     * Builds a [DefaultProceedingJoinPoint] constructor call whose `onProceedListener` is
     * `{ args -> localFunc(args[0] as T0, args[1] as T1, ...) }` wrapped as a SAM conversion.
     *
     * [localFunc] must be the value returned by [generateLocalFunction] for the same [declaration].
     */
    fun generateProceedingJoinPoint(
        declaration: IrFunction,
        localFunc: IrSimpleFunction,
        signatureProperty: IrProperty,
    ): IrExpression {
        val valueParams = declaration.parameters.filter { it.kind == IrParameterKind.Regular }
        val wrapperLambda = buildWrapperLambda(declaration, localFunc, valueParams)

        val argsExpression =
            aspectKCompilerContext.createIrListOf(
                scope = declaration.symbol,
                elements =
                    declaration.parameters.map { param ->
                        aspectKCompilerContext.withIrBuilder(declaration.symbol) { irGet(param) }
                    },
            )

        return aspectKCompilerContext.withIrBuilder(declaration.symbol) {
            irCall(proceedingJoinPointConstructor).apply {
                arguments[0] = declaration.dispatchReceiverParameter?.let { irGet(it) }
                    ?: irNull(context.irBuiltIns.anyNType)
                arguments[1] =
                    irCall(signatureProperty.getter!!).apply {
                        insertDispatchReceiver(
                            irGetObject((signatureProperty.parent as IrClass).symbol),
                        )
                    }
                arguments[2] = argsExpression
                arguments[3] =
                    IrTypeOperatorCallImpl(
                        startOffset = -1,
                        endOffset = -1,
                        type = aspectKCompilerContext.onProceedListenerType,
                        operator = IrTypeOperator.SAM_CONVERSION,
                        typeOperand = aspectKCompilerContext.onProceedListenerType,
                        argument = wrapperLambda,
                    )
            }
        }
    }

    /**
     * Builds `{ args: List<Any?> -> $<name>(args[0] as T0, args[1] as T1, ...) }`.
     * Casting happens only at the call site, keeping the local function body clean.
     */
    private fun buildWrapperLambda(
        declaration: IrFunction,
        localFunc: IrSimpleFunction,
        valueParams: List<IrValueDeclaration>,
    ): IrFunctionExpression {
        val lambdaFun =
            aspectKCompilerContext.pluginContext.irFactory
                .buildFun {
                    name = Name.special("<anonymous>")
                    visibility = DescriptorVisibilities.LOCAL
                    returnType = aspectKCompilerContext.pluginContext.irBuiltIns.anyNType
                    origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
                }.apply {
                    parent = declaration
                }

        val argsParam =
            aspectKCompilerContext.pluginContext.irFactory.buildValueParameter(
                parent = lambdaFun,
                builder =
                    IrValueParameterBuilder().apply {
                        name = Name.identifier("__args")
                        type = aspectKCompilerContext.listAnyNType
                        kind = IrParameterKind.Regular
                        origin = IrDeclarationOrigin.DEFINED
                    },
            )
        lambdaFun.parameters = listOf(argsParam)

        lambdaFun.body =
            aspectKCompilerContext.withIrBuilder(lambdaFun.symbol) {
                irBlockBody {
                    +irReturn(
                        irCall(localFunc.symbol).apply {
                            // $doSomething(args[0] as T0, args[1] as T1, ...)
                            valueParams.forEachIndexed { index, param ->
                                val castedArg =
                                    irAs(
                                        irCall(aspectKCompilerContext.listGetFun).apply {
                                            dispatchReceiver = irGet(argsParam)
                                            arguments[1] = irInt(index + 1)
                                        },
                                        param.type,
                                    )
                                arguments[index] = castedArg
                            }
                        },
                    )
                }
            }

        return IrFunctionExpressionImpl(
            startOffset = -1,
            endOffset = -1,
            type = aspectKCompilerContext.function1Type,
            function = lambdaFun,
            origin = IrStatementOrigin.LAMBDA,
        )
    }
}
