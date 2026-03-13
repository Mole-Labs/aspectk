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
import org.jetbrains.kotlin.ir.IrStatement
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
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.IrTypeOperator
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrTypeOperatorCallImpl
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.transformStatement
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.Name

@OptIn(UnsafeDuringIrConstructionAPI::class)
internal class ProceedingJoinPointGenerator(
    private val aspectKContext: AspectKIrCompilerContext,
) {
    private val proceedingJoinPointConstructor =
        aspectKContext.proceedingJoinPointSymbol.constructors.first()

    /**
     * Builds `fun $<name>(p0: T0, p1: T1, ...)` whose body is the original [declaration] body
     * with outer parameters substituted by the local function's own parameters.
     *
     * Call [generateProceedingJoinPoint] afterwards to obtain the [DefaultProceedingJoinPoint]
     * constructor expression that references this local function.
     */
    fun generateLocalFunction(declaration: IrFunction): IrSimpleFunction {
        val originalStatements =
            (declaration.body as? IrBlockBody)?.statements?.toList().orEmpty()
        val valueParams =
            declaration.parameters.filter { it.kind == IrParameterKind.Regular }
        return buildLocalFunction(declaration, originalStatements, valueParams)
    }

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
        val valueParams: List<IrValueDeclaration> =
            declaration.parameters.filter { it.kind == IrParameterKind.Regular }
        val wrapperLambda = buildWrapperLambda(declaration, localFunc, valueParams)

        val argsExpression =
            aspectKContext.createIrListOf(
                scope = declaration.symbol,
                elements =
                    declaration.parameters.map { param ->
                        aspectKContext.withIrBuilder(declaration.symbol) { irGet(param) }
                    },
            )

        return aspectKContext.withIrBuilder(declaration.symbol) {
            irCall(proceedingJoinPointConstructor).apply {
                arguments[0] = declaration.dispatchReceiverParameter?.let { irGet(it) }
                    ?: irNull(aspectKContext.pluginContext.irBuiltIns.anyNType)
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
                        type = aspectKContext.onProceedListenerType,
                        operator = IrTypeOperator.SAM_CONVERSION,
                        typeOperand = aspectKContext.onProceedListenerType,
                        argument = wrapperLambda,
                    )
            }
        }
    }

    /**
     * Builds `fun $<name>(p0: T0, p1: T1, ...) { <original body with params substituted> }`.
     * No default values — all parameters must be supplied at the call site.
     */
    private fun buildLocalFunction(
        declaration: IrFunction,
        originalStatements: List<IrStatement>,
        valueParams: List<IrValueParameter>,
    ): IrSimpleFunction {
        val localFuncName = $$"$$${declaration.name.asString()}"
        val localFunc =
            aspectKContext.pluginContext.irFactory
                .buildFun {
                    name = Name.identifier(localFuncName)
                    visibility = DescriptorVisibilities.LOCAL
                    returnType = declaration.returnType
                    origin = IrDeclarationOrigin.LOCAL_FUNCTION
                }.apply {
                    parent = declaration
                }

        // Mirror the outer value parameters (same name, same type, no defaults)
        val localParams =
            valueParams.map {
                it.deepCopyWithSymbols(localFunc)
            }

        localFunc.parameters = localParams

        // Substitute outerParam.symbol → localParam.symbol in the deep-copied body
        val copiedStatements =
            originalStatements.map {
                it.deepCopyWithSymbols(localFunc).transformStatement(
                    ReturnTransformer(localFunc),
                )
            }

        localFunc.body =
            aspectKContext.withIrBuilder(localFunc.symbol) {
                irBlockBody {
                    copiedStatements.forEach { +it }
                }
            }

        return localFunc
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
            aspectKContext.pluginContext.irFactory
                .buildFun {
                    name = Name.special("<anonymous>")
                    visibility = DescriptorVisibilities.LOCAL
                    returnType = aspectKContext.pluginContext.irBuiltIns.anyNType
                    origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
                }.apply {
                    parent = declaration
                }

        val argsParam =
            aspectKContext.pluginContext.irFactory.buildValueParameter(
                parent = lambdaFun,
                builder =
                    IrValueParameterBuilder().apply {
                        name = Name.identifier("__args")
                        type = aspectKContext.listAnyNType
                        kind = IrParameterKind.Regular
                        origin = IrDeclarationOrigin.DEFINED
                    },
            )
        lambdaFun.parameters = listOf(argsParam)

        lambdaFun.body =
            aspectKContext.withIrBuilder(lambdaFun.symbol) {
                irBlockBody {
                    +irReturn(
                        irCall(localFunc.symbol).apply {
                            // $doSomething(args[0] as T0, args[1] as T1, ...)
                            valueParams.forEachIndexed { index, param ->
                                val castedArg =
                                    irAs(
                                        irCall(aspectKContext.listGetFun).apply {
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
            type = aspectKContext.function1Type,
            function = lambdaFun,
            origin = IrStatementOrigin.LAMBDA,
        )
    }

    // transform returnTargetSymbol from origin to generated local fun
    // if you don't use this, local function will be always 'non local return'
    private class ReturnTransformer(
        private val localFunc: IrSimpleFunction,
    ) : IrElementTransformerVoid() {
        override fun visitReturn(expression: IrReturn): IrExpression {
            expression.returnTargetSymbol = localFunc.symbol
            return super.visitReturn(expression)
        }
    }
}
