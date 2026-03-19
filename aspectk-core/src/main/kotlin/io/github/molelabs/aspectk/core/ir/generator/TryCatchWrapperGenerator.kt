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
