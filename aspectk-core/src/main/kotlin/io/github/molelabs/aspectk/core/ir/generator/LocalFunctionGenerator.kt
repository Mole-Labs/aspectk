package io.github.molelabs.aspectk.core.ir.generator

import io.github.molelabs.aspectk.core.ir.AspectKIrCompilerContext
import io.github.molelabs.aspectk.core.ir.withIrBuilder
import io.github.molelabs.aspectk.core.reportCompilerBug
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.transformStatement
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.util.statements
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.Name

internal class LocalFunctionGenerator(
    private val aspectKCompilerContext: AspectKIrCompilerContext,
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
            aspectKCompilerContext.withIrBuilder(localFunc.symbol) {
                irBlockBody {
                    copiedStatements.forEach { +it }
                }
            }

        return localFunc
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
