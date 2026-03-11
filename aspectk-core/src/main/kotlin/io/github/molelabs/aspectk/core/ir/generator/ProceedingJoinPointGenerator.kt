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
import io.github.molelabs.aspectk.core.ir.withIrBuilder
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.declarations.IrValueParameterBuilder
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.declarations.buildValueParameter
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
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.IrTypeOperator
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrReturnImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrTypeOperatorCallImpl
import org.jetbrains.kotlin.ir.symbols.IrValueSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.transformStatement
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

@OptIn(UnsafeDuringIrConstructionAPI::class)
internal class ProceedingJoinPointGenerator(
    private val aspectKContext: AspectKIrCompilerContext,
) {
    private val proceedingJoinPointConstructor =
        aspectKContext.proceedingJoinPointSymbol.constructors.first()

    private val listClass =
        aspectKContext.pluginContext.referenceClass(
            ClassId.topLevel(FqName("kotlin.collections.List")),
        )!!
    private val listAnyNType = listClass.typeWith(aspectKContext.pluginContext.irBuiltIns.anyNType)

    // (List<Any?>) -> Any? = kotlin.Function1<List<Any?>, Any?>
    private val function1Type =
        aspectKContext.pluginContext
            .referenceClass(ClassId(FqName("kotlin"), Name.identifier("Function1")))!!
            .typeWith(listAnyNType, aspectKContext.pluginContext.irBuiltIns.anyNType)

    private val listGetFun =
        aspectKContext.pluginContext.referenceFunctions(
            CallableId(
                ClassId.topLevel(FqName("kotlin.collections.List")),
                Name.identifier("get"),
            ),
        ).first()

    /**
     * Generates:
     * 1. A local function `$<name>(p0: T0, p1: T1, ...)` whose body is the original function body
     *    with outer parameters substituted by the local function's own parameters.
     * 2. A [DefaultProceedingJoinPoint] constructor call whose `proceedFn` is
     *    `{ args -> $<name>(args[0] as T0, args[1] as T1, ...) }`.
     *
     * The caller is responsible for inserting the local function into the target function's body
     * and replacing that body with the [Around] advice call.
     */
    fun generate(
        declaration: IrFunction,
        signatureProperty: IrProperty,
    ): Pair<IrSimpleFunction, IrExpression> {
        val originalStatements =
            (declaration.body as? IrBlockBody)?.statements?.toList().orEmpty()

        val valueParams: List<IrValueDeclaration> =
            declaration.parameters.filter { it.kind == IrParameterKind.Regular }

        val localFunc = buildLocalFunction(declaration, originalStatements, valueParams)
        val wrapperLambda = buildWrapperLambda(declaration, localFunc, valueParams)

        val argsExpression = aspectKContext.createIrListOf(
            scope = declaration.symbol,
            elements = declaration.parameters.map { param ->
                aspectKContext.withIrBuilder(declaration.symbol) { irGet(param) }
            },
        )

        val pjpExpression = aspectKContext.withIrBuilder(declaration.symbol) {
            irCall(proceedingJoinPointConstructor).apply {
                arguments[0] = declaration.dispatchReceiverParameter?.let { irGet(it) }
                    ?: irNull(aspectKContext.pluginContext.irBuiltIns.anyNType)
                arguments[1] = irCall(signatureProperty.getter!!).apply {
                    insertDispatchReceiver(
                        irGetObject((signatureProperty.parent as IrClass).symbol),
                    )
                }
                arguments[2] = argsExpression
                arguments[3] = wrapperLambda
            }
        }

        return localFunc to pjpExpression
    }

    /**
     * Builds `fun $<name>(p0: T0, p1: T1, ...) { <original body with params substituted> }`.
     * No default values — all parameters must be supplied at the call site.
     */
    private fun buildLocalFunction(
        declaration: IrFunction,
        originalStatements: List<IrStatement>,
        valueParams: List<IrValueDeclaration>,
    ): IrSimpleFunction {
        val irFactory = aspectKContext.pluginContext.irFactory

        val localFunc: IrSimpleFunction = irFactory.buildFun {
            name = Name.identifier("\$${declaration.name.asString()}")
            visibility = DescriptorVisibilities.LOCAL
            returnType = declaration.returnType
            origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
        }
        localFunc.parent = declaration

        // Mirror the outer value parameters (same name, same type, no defaults)
        val localParams = valueParams.map { outerParam ->
            irFactory.buildValueParameter(
                parent = localFunc,
                builder = IrValueParameterBuilder().apply {
                    name = outerParam.name
                    type = outerParam.type
                    kind = IrParameterKind.Regular
                    origin = IrDeclarationOrigin.DEFINED
                },
            )
        }
        localFunc.parameters = localParams

        // Substitute outerParam.symbol → localParam.symbol in the deep-copied body
        val substitutionMap: Map<IrValueSymbol, IrValueDeclaration> =
            valueParams.mapIndexed { i, p -> p.symbol to localParams[i] }.toMap()
        val substituter = ParamAndReturnSubstituter(substitutionMap, localFunc)
        val copiedStatements = originalStatements.map { stmt ->
            stmt.deepCopyWithSymbols(declaration).transformStatement(substituter)
        }

        localFunc.body = DeclarationIrBuilder(aspectKContext.pluginContext, localFunc.symbol)
            .irBlockBody { copiedStatements.forEach { +it } }

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
        val irFactory = aspectKContext.pluginContext.irFactory
        val irBuiltIns = aspectKContext.pluginContext.irBuiltIns

        val lambdaFun: IrSimpleFunction = irFactory.buildFun {
            name = Name.special("<anonymous>")
            visibility = DescriptorVisibilities.LOCAL
            returnType = irBuiltIns.anyNType
            origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
        }
        lambdaFun.parent = declaration

        val argsParam = irFactory.buildValueParameter(
            parent = lambdaFun,
            builder = IrValueParameterBuilder().apply {
                name = Name.identifier("__args")
                type = listAnyNType
                kind = IrParameterKind.Regular
                origin = IrDeclarationOrigin.DEFINED
            },
        )
        lambdaFun.parameters = listOf(argsParam)

        lambdaFun.body = DeclarationIrBuilder(aspectKContext.pluginContext, lambdaFun.symbol)
            .irBlockBody {
                +irReturn(
                    irCall(localFunc.symbol).apply {
                        // $doSomething(args[0] as T0, args[1] as T1, ...)
                        valueParams.forEachIndexed { index, param ->
                            arguments[index] = IrTypeOperatorCallImpl(
                                startOffset = -1,
                                endOffset = -1,
                                type = param.type,
                                operator = IrTypeOperator.CAST,
                                typeOperand = param.type,
                                argument = irCall(listGetFun).apply {
                                    dispatchReceiver = irGet(argsParam)
                                    arguments[0] = irInt(index)
                                },
                            )
                        }
                    },
                )
            }

        return IrFunctionExpressionImpl(
            startOffset = -1,
            endOffset = -1,
            type = function1Type,
            function = lambdaFun,
            origin = IrStatementOrigin.LAMBDA,
        )
    }
}

/**
 * Replaces [IrGetValue] references to the outer function's value parameters with the
 * corresponding local-function parameters, and redirects [IrReturn] to the local function.
 */
private class ParamAndReturnSubstituter(
    private val substitutions: Map<IrValueSymbol, IrValueDeclaration>,
    private val returnTargetFunc: IrFunction,
) : IrElementTransformerVoid() {
    override fun visitGetValue(expression: IrGetValue): IrExpression {
        val substitute = substitutions[expression.symbol]
            ?: return super.visitGetValue(expression)
        return IrGetValueImpl(
            startOffset = expression.startOffset,
            endOffset = expression.endOffset,
            type = expression.type,
            symbol = substitute.symbol,
        )
    }

    override fun visitReturn(expression: IrReturn): IrExpression {
        val transformedValue = expression.value.transform(this, null) as IrExpression
        return IrReturnImpl(
            startOffset = expression.startOffset,
            endOffset = expression.endOffset,
            type = expression.type.makeNullable(),
            returnTargetSymbol = returnTargetFunc.symbol,
            value = transformedValue,
        )
    }
}
