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
package io.github.molelabs.aspectk.core.ir

import io.github.molelabs.aspectk.core.reportCompilerBug
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.IrGeneratorContext
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irVararg
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.impl.IrClassReferenceImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.classifierOrNull
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
        irCompat
            .referenceFunctions(
                pluginContext,
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
    val (targetType, targetSymbol) =
        if (classType.isGeneric()) {
            classType.getUpperBound()
        } else {
            val symbol =
                classType.classOrNull
                    ?: throw IllegalArgumentException("Type ${classType.render()} has no class symbol")
            classType to symbol
        }

    return IrClassReferenceImpl(
        startOffset = startOffset,
        endOffset = endOffset,
        type = pluginContext.irBuiltIns.kClassClass.typeWith(targetType),
        symbol = targetSymbol,
        classType = targetType,
    )
}

internal fun AspectKIrCompilerContext.getSymbol(fqName: String): IrClassSymbol = irCompat.referenceClass(pluginContext, ClassId.topLevel(FqName(fqName)))
    ?: reportCompilerBug("Cannot find symbol for $fqName")

internal fun <T> AspectKIrCompilerContext.withIrBuilder(
    symbol: IrSymbol,
    generatorContext: IrGeneratorContext = pluginContext,
    startOffset: Int = -1,
    endOffset: Int = -1,
    block: IrBuilderWithScope.() -> T,
): T = DeclarationIrBuilder(generatorContext, symbol, startOffset, endOffset).run {
    block()
}

internal val AspectKIrCompilerContext.listAnyNType: IrType
    get() =
        irCompat
            .referenceClass(pluginContext, ClassId.topLevel(FqName("kotlin.collections.List")))!!
            .typeWith(pluginContext.irBuiltIns.anyNType)

internal val AspectKIrCompilerContext.function1Type: IrType
    get() =
        irCompat
            .referenceClass(pluginContext, ClassId(FqName("kotlin"), Name.identifier("Function1")))!!
            .typeWith(listAnyNType, pluginContext.irBuiltIns.anyNType)

@OptIn(UnsafeDuringIrConstructionAPI::class)
internal val AspectKIrCompilerContext.listGetFun: IrSimpleFunctionSymbol
    get() =
        irCompat
            .referenceFunctions(
                pluginContext,
                CallableId(
                    ClassId.topLevel(FqName("kotlin.collections.List")),
                    Name.identifier("get"),
                ),
            ).first()

internal fun IrBody.add(element: IrStatement) {
    (this as? IrBlockBody)?.statements?.add(0, element)
}

// Inserts element before the last IrReturn; if none, appends at end.
internal fun IrBody.addLast(element: IrStatement) {
    val statements = (this as? IrBlockBody)?.statements ?: return
    val lastReturnIndex = statements.indexOfLast { it is IrReturn }
    if (lastReturnIndex >= 0) {
        statements.add(lastReturnIndex, element)
    } else {
        statements.add(element)
    }
}

internal fun IrFunction.hasBody(): Boolean = body != null && body is IrBlockBody

internal fun IrType.isGeneric(): Boolean = (this as? IrSimpleType)?.classifier is IrTypeParameterSymbol

@OptIn(UnsafeDuringIrConstructionAPI::class)
internal fun IrType.getUpperBound(): Pair<IrType, IrClassSymbol> {
    var currentType = this
    while (true) {
        val symbol = currentType.classifierOrNull as? IrTypeParameterSymbol ?: break
        currentType = symbol.owner.superTypes.firstOrNull() ?: break
    }
    return currentType to (
        currentType.classOrNull
            ?: reportCompilerBug("$currentType class should not be null")
        )
}

internal fun IrType.getUpperBoundClassName(): String? = getUpperBound().first.classFqName?.asString()
