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

import io.github.molelabs.aspectk.core.compat.IrCompat
import io.github.molelabs.aspectk.core.ir.AspectKIrCompilerContext
import io.github.molelabs.aspectk.core.ir.withIrBuilder
import io.github.molelabs.aspectk.core.reportCompilerBug
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.impl.IrGetValueImpl
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.transformStatement
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.util.statements
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.Name

internal class LocalFunctionGenerator(
    private val aspectKCompilerContext: AspectKIrCompilerContext,
    private val irCompat: IrCompat,
) {
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
        val localFuncName = $$"$$${declaration.name.asString()}"

        val localFunc =
            declaration.body
                ?.statements
                ?.filterIsInstance<IrFunction>()
                ?.filter {
                    it.name.asString() == localFuncName
                }

        return if (localFunc.isNullOrEmpty()) {
            buildLocalFunction(declaration, originalStatements, valueParams, localFuncName)
        } else {
            localFunc.first() as? IrSimpleFunction ?: reportCompilerBug("Unexpected local function")
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
        localFuncName: String,
    ): IrSimpleFunction {
        val localFunc =
            aspectKCompilerContext.pluginContext.irFactory
                .buildFun {
                    name = Name.identifier(localFuncName)
                    visibility = DescriptorVisibilities.LOCAL
                    returnType = declaration.returnType
                    origin = irCompat.localFunctionOrigin()
                }.apply {
                    parent = declaration
                }

        // Mirror the outer value parameters (same name, same type, no defaults)
        val localParams =
            valueParams.map {
                it.deepCopyWithSymbols(localFunc)
            }

        localFunc.parameters = localParams

        // deepCopyWithSymbols only remaps symbols *declared within* the copied tree.
        // Outer parameter symbols (declared in declaration.parameters) are not remapped,
        // so IrGetValue nodes that reference them still point to the outer function's params.
        // We must explicitly substitute them to the local function's own params so that
        // proceed(vararg args) overrides work correctly.
        val paramSubstitutions: Map<IrValueParameter, IrValueParameter> =
            valueParams.zip(localParams).associate { (outer, local) -> outer to local }

        val copiedStatements =
            originalStatements.map {
                it.deepCopyWithSymbols(localFunc).transformStatement(
                    BodyTransformer(localFunc, paramSubstitutions),
                )
            }

        localFunc.body =
            aspectKCompilerContext.withIrBuilder(localFunc.symbol) {
                irBlockBody {
                    copiedStatements.forEach { +it }
                }
            }

        return localFunc
    }

    /**
     * Transforms the deep-copied body of a local function by:
     * 1. Fixing return targets to point to [localFunc] instead of the outer function.
     * 2. Substituting `IrGetValue` references to outer parameters with references to the
     *    local function's own parameters, so that `proceed(vararg args)` argument
     *    substitution takes effect correctly.
     */
    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private class BodyTransformer(
        private val localFunc: IrSimpleFunction,
        private val paramSubstitutions: Map<IrValueParameter, IrValueParameter>,
    ) : IrElementTransformerVoid() {
        override fun visitReturn(expression: IrReturn): IrExpression {
            expression.returnTargetSymbol = localFunc.symbol
            return super.visitReturn(expression)
        }

        override fun visitGetValue(expression: IrGetValue): IrExpression {
            val replacement =
                paramSubstitutions[expression.symbol.owner]
                    ?: return super.visitGetValue(expression)
            return IrGetValueImpl(
                startOffset = expression.startOffset,
                endOffset = expression.endOffset,
                type = replacement.type,
                symbol = replacement.symbol,
                origin = expression.origin,
            )
        }
    }
}
