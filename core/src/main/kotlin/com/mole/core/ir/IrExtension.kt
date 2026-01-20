package com.mole.core.ir

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.builders.declarations.addConstructor
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irVararg
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrClassReferenceImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.symbols.impl.IrValueParameterSymbolImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.createType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.irConstructorCall
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.name.*

@OptIn(UnsafeDuringIrConstructionAPI::class)
internal fun IrPluginContext.createCompanionObject(parentClass: IrClass): IrClass =
    irFactory
        .buildClass {
            startOffset = parentClass.startOffset
            endOffset = parentClass.endOffset
            origin = IrDeclarationOrigin.DEFAULT_PROPERTY_ACCESSOR
            name = SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT
            kind = ClassKind.OBJECT
            isCompanion = true
            visibility = DescriptorVisibilities.PUBLIC
            modality = Modality.FINAL
        }.apply {
            val owner = this@apply
            parent = parentClass
            superTypes = listOf(irBuiltIns.anyType)
            thisReceiver =
                irFactory
                    .createValueParameter(
                        startOffset = startOffset,
                        endOffset = endOffset,
                        kind = IrParameterKind.DispatchReceiver,
                        origin = IrDeclarationOrigin.INSTANCE_RECEIVER,
                        name = Name.special("<this>"),
                        type = symbol.createType(false, emptyList()),
                        isAssignable = false,
                        symbol = IrValueParameterSymbolImpl(),
                        varargElementType = null,
                        isCrossinline = false,
                        isNoinline = false,
                        isHidden = false,
                    ).apply {
                        parent = owner
                    }

            addConstructor {
                origin = IrDeclarationOrigin.DEFAULT_PROPERTY_ACCESSOR
                isPrimary = true
                visibility = DescriptorVisibilities.PRIVATE
            }.apply {
                body = irFactory.createBlockBody(startOffset, endOffset)
            }

            // 3. 부모 클래스의 선언 목록에 이 객체를 등록
            parentClass.declarations.add(this)
        }

@OptIn(UnsafeDuringIrConstructionAPI::class)
internal fun IrPluginContext.createIrListOf(
    scope: IrSymbol,
    elements: List<IrExpression>,
    elementType: IrType = irBuiltIns.anyNType,
): IrExpression {
    val listOfFunc =
        referenceFunctions(
            CallableId(FqName("kotlin.collections"), Name.identifier("listOf")),
        ).first {
            it.owner.parameters
                .firstOrNull { it.kind == IrParameterKind.Regular }
                ?.varargElementType != null
        }

    // 2. 리스트에 들어갈 요소들을 irVararg로 묶기
    return DeclarationIrBuilder(this, scope).run {
        irCall(listOfFunc).apply {
            typeArguments[0] = elementType
            arguments[0] = irVararg(elementType, elements)
        }
    }
}

@OptIn(UnsafeDuringIrConstructionAPI::class)
internal fun IrPluginContext.createIrMapOf(
    scope: IrSymbol,
    elements: Map<IrExpression, IrExpression>,
    keyType: IrType = irBuiltIns.anyNType,
    valueType: IrType = irBuiltIns.anyNType,
): IrExpression {
    val mapOfFunc =
        referenceFunctions(
            CallableId(FqName("kotlin.collections"), Name.identifier("mapOf")),
        ).first {
            it.owner.parameters
                .firstOrNull { p -> p.kind == IrParameterKind.Regular }
                ?.varargElementType != null
        }

    return DeclarationIrBuilder(this, scope).run {
        // 1. 모든 엔트리를 Pair 객체 생성 코드로 변환
        val pairExpressions =
            elements.map { (k, v) ->
                createPair(scope, k, v, k.type, v.type)
            }

        val pairConstructor = getSymbol("kotlin.Pair").constructors.first()

        // 2. Pair들의 배열(vararg) 생성
        val pairType = pairConstructor.owner.returnType
        val vararg =
            irVararg(
                elementType = pairType,
                values = pairExpressions,
            )

        // 3. mapOf 호출
        irCall(mapOfFunc).apply {
            typeArguments[0] = keyType
            typeArguments[1] = valueType
            arguments[0] = vararg
        }
    }
}

internal fun IrPluginContext.createPair(
    scope: IrSymbol,
    key: IrExpression,
    value: IrExpression,
    keyType: IrType = irBuiltIns.anyNType,
    valueType: IrType = irBuiltIns.anyNType,
): IrExpression {
    val pairConstructor =
        referenceConstructors(
            ClassId(FqName("kotlin"), Name.identifier("Pair")),
        ).first()

    return DeclarationIrBuilder(this, scope).run {
        val call =
            irCallConstructor(
                pairConstructor,
                listOf(
                    keyType,
                    valueType,
                ),
            )
        irConstructorCall(call, pairConstructor).apply {
            arguments[0] = key
            arguments[1] = value
        }
    }
}

@OptIn(UnsafeDuringIrConstructionAPI::class)
internal fun IrPluginContext.createKClassExpression(
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
        type = irBuiltIns.kClassClass.typeWith(classType),
        symbol = classSymbol,
        classType = classType,
    )
}

internal fun IrPluginContext.getSymbol(fqName: String): IrClassSymbol =
    referenceClass(ClassId.topLevel(FqName(fqName)))
        ?: error("Cannot find symbol for $fqName")
