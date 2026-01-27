package com.mole.core.ir

import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irNull
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
        methodSignature: IrExpression,
    ): IrExpression =
        DeclarationIrBuilder(aspectKContext.pluginContext, declaration.symbol).run {
            irCall(joinPointConstructor).apply {
                arguments[0] =
                    declaration.dispatchReceiverParameter?.let {
                        irGet(it)
                    } ?: irNull(aspectKContext.pluginContext.irBuiltIns.anyNType)
                arguments[1] = methodSignature
                arguments[2] =
                    aspectKContext.pluginContext.createIrListOf(
                        scope = declaration.symbol,
                        elements =
                            declaration.parameters.map {
                                irGet(it)
                            },
                    )
            }
        }
}
