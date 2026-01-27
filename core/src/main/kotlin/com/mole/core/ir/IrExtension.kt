package com.mole.core.ir

import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.IrGeneratorContext
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irVararg
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrClassReferenceImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

@OptIn(UnsafeDuringIrConstructionAPI::class)
internal fun AspectKIrCompilerContext.createIrListOf(
    scope: IrSymbol,
    elements: List<IrExpression>,
    elementType: IrType = pluginContext.irBuiltIns.anyNType,
): IrExpression {
    val listOfFunc =
        pluginContext
            .referenceFunctions(
                CallableId(FqName("kotlin.collections"), Name.identifier("listOf")),
            ).first {
                it.owner.parameters
                    .firstOrNull { it.kind == IrParameterKind.Regular }
                    ?.varargElementType != null
            }

    // 2. 리스트에 들어갈 요소들을 irVararg로 묶기
    return DeclarationIrBuilder(pluginContext, scope).run {
        irCall(listOfFunc).apply {
            typeArguments[0] = elementType
            arguments[0] = irVararg(elementType, elements)
        }
    }
}

@OptIn(UnsafeDuringIrConstructionAPI::class)
internal fun AspectKIrCompilerContext.createKClassExpression(
    startOffset: Int,
    endOffset: Int,
    classType: IrType,
): IrExpression {
    val classSymbol =
        classType.classOrNull
            ?: throw IllegalArgumentException("Type ${classType.render()} has no class symbol")

    return IrClassReferenceImpl(
        startOffset = startOffset,
        endOffset = endOffset,
        type = pluginContext.irBuiltIns.kClassClass.typeWith(classType),
        symbol = classSymbol,
        classType = classType,
    )
}

internal fun AspectKIrCompilerContext.getSymbol(fqName: String): IrClassSymbol =
    pluginContext.referenceClass(ClassId.topLevel(FqName(fqName)))
        ?: error("Cannot find symbol for $fqName")

internal fun <T> AspectKIrCompilerContext.withIrBuilder(
    symbol: IrSymbol,
    generatorContext: IrGeneratorContext = pluginContext,
    startOffset: Int = -1,
    endOffset: Int = -1,
    block: IrBuilderWithScope.() -> T,
): T =
    DeclarationIrBuilder(generatorContext, symbol, startOffset, endOffset).run {
        block()
    }
