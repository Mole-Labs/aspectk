package com.mole.core.ir

import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.name.FqName

internal class AspectLookUp {
    private val map: MutableMap<FqName, AspectContext> = mutableMapOf()

    operator fun get(fqName: FqName) = map[fqName]

    operator fun set(
        fqName: FqName,
        aspectContext: AspectContext,
    ) {
        map[fqName] = aspectContext
    }
}

internal data class AspectContext(
    val advice: IrCall,
    val aspect: IrSymbol,
    val methodSignature: IrExpression,
    val kind: Kind,
) {
    enum class Kind {
        BEFORE, // 추후 After, Around 추가 예정
    }
}
