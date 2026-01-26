package com.mole.core.ir

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.declarations.buildField
import org.jetbrains.kotlin.ir.builders.irBoolean
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.createExpressionBody
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.isNullable
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/*
일단 acceptChild로 IrCall, IrSymbol 객체 저장
자료구조가 Map<FqName, List<Context>>

data class Context(
    val irCall:IrCall,
    val symbol:IrSymbol,
    val methodSignature:
    val kind:AspectKind
)
 */

// TODO 컴파일러 코드에서 stdlib 의존성 제거 -> 현재 IR트리가 너무 깊습니다.
@OptIn(UnsafeDuringIrConstructionAPI::class)
internal class MethodSignatureInjectTransformer(
    private val aspectKContext: AspectKIrCompilerContext,
    private val targetAnnotation: FqName,
) : IrElementTransformerVoidWithContext() {
    private var fieldCounter: Int = 0

    private val methodSignatureConstructor = aspectKContext.methodSignatureSymbol.constructors.first()

    private lateinit var parentClass: IrClass

    override fun visitFunctionNew(declaration: IrFunction): IrStatement {
        if (canSkip(declaration)) return super.visitFunctionNew(declaration)
        parentClass =
            currentClass?.irElement as? IrClass ?: return super.visitFunctionNew(declaration)
        val companion =
            parentClass.declarations
                .filterIsInstance<IrClass>()
                .firstOrNull { it.isCompanion }
                ?: aspectKContext.pluginContext.createCompanionObject(parentClass)

        val signatureField =
            aspectKContext.pluginContext.irFactory
                .buildField {
                    startOffset = companion.startOffset
                    endOffset = companion.endOffset
                    name = Name.identifier($$"ajc$tjp_$${fieldCounter++}") // 유니크한 이름
                    type = aspectKContext.methodSignatureSymbol.defaultType
                    isStatic = true
                    isFinal = true
                    visibility = DescriptorVisibilities.PRIVATE
                }.apply {
                    val builder = DeclarationIrBuilder(aspectKContext.pluginContext, companion.symbol)
                    parent = companion
                    initializer =
                        aspectKContext.pluginContext.irFactory.createExpressionBody(
                            builder.createMethodSignatureInitializer(declaration),
                        )
                }
        companion.declarations.add(signatureField)

        return super.visitFunctionNew(declaration)
    }

    private fun IrBuilderWithScope.createMethodSignatureInitializer(declaration: IrFunction): IrExpression =
        irCall(methodSignatureConstructor).apply {
            arguments[0] = irString(declaration.name.asString())
            arguments[1] =
                aspectKContext.pluginContext.createIrListOf(
                    scope = aspectKContext.methodSignatureSymbol,
                    elementType = aspectKContext.annotationInfoSymbol.defaultType,
                    elements =
                        declaration.annotations.map { annotation ->
                            createAnnotationInfoInitializer(annotation)
                        },
                )
            arguments[2] =
                aspectKContext.pluginContext.createIrListOf(
                    scope = symbol,
                    elementType = aspectKContext.methodParameterSymbol.defaultType,
                    elements =
                        declaration.parameters.map { param ->
                            createMethodParameterInitializer(
                                declaration = declaration,
                                param = param,
                            )
                        },
                )
            arguments[3] =
                aspectKContext.pluginContext.createKClassExpression(
                    startOffset = declaration.startOffset,
                    endOffset = declaration.endOffset,
                    classType = declaration.returnType,
                )
            arguments[4] = irString(declaration.returnType.classFqName?.asString() ?: error("not found"))
        }

    private fun createMethodParameterInitializer(
        declaration: IrFunction,
        param: IrValueParameter,
    ): IrExpression =
        DeclarationIrBuilder(aspectKContext.pluginContext, aspectKContext.methodParameterSymbol).run {
            irCall(aspectKContext.methodParameterSymbol.constructors.first()).apply {
                arguments[0] = irString(param.name.asString())
                arguments[1] =
                    aspectKContext.pluginContext.createKClassExpression(
                        startOffset = declaration.startOffset,
                        endOffset = declaration.endOffset,
                        classType = param.type,
                    )
                arguments[2] = irString(param.type.classFqName?.asString() ?: error("Not found"))

                arguments[3] =
                    aspectKContext.pluginContext.createIrListOf(
                        scope = aspectKContext.methodParameterSymbol,
                        elementType = aspectKContext.annotationInfoSymbol.defaultType,
                        elements =
                            param.annotations.map { annotation ->
                                createAnnotationInfoInitializer(annotation)
                            },
                    )

                arguments[4] = irBoolean(param.type.isNullable())
            }
        }

    private fun createAnnotationInfoInitializer(annotation: IrConstructorCall): IrExpression {
        return DeclarationIrBuilder(aspectKContext.pluginContext, aspectKContext.annotationInfoSymbol).run {
            irCall(aspectKContext.annotationInfoSymbol.constructors.first()).apply {
                arguments[0] =
                    aspectKContext.pluginContext.createKClassExpression(
                        startOffset = annotation.startOffset,
                        endOffset = annotation.endOffset,
                        classType = annotation.type,
                    )
                arguments[1] = irString(annotation.type.classFqName?.asString() ?: error("Not found"))

                val parameters = annotation.symbol.owner.parameters
                val argMap: Map<IrExpression, IrExpression> =
                    parameters
                        .mapIndexedNotNull { index, param ->
                            val value = annotation.arguments[index] ?: return@mapIndexedNotNull null
                            irString(param.name.asString()) to value
                        }.toMap()

                arguments[2] =
                    aspectKContext.pluginContext.createIrMapOf(
                        scope = aspectKContext.annotationInfoSymbol,
                        elements = argMap,
                    )
            }
        }
    }

    private fun canSkip(declaration: IrFunction): Boolean = !declaration.hasAnnotation(targetAnnotation)
}
