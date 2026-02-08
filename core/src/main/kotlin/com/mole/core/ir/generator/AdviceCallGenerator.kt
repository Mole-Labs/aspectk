package com.mole.core.ir.generator

import com.mole.core.ir.AspectKIrCompilerContext
import com.mole.core.ir.add
import com.mole.core.ir.withIrBuilder
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
    ): IrStatement =
        aspectKContext
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
