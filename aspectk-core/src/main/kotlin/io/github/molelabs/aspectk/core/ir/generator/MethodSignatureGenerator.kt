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

import io.github.molelabs.aspectk.core.ir.AspectKIrCompilerContext
import io.github.molelabs.aspectk.core.ir.createIrListOf
import io.github.molelabs.aspectk.core.ir.createKClassExpression
import io.github.molelabs.aspectk.core.ir.getUpperBoundClassName
import io.github.molelabs.aspectk.core.ir.isGeneric
import io.github.molelabs.aspectk.core.ir.withIrBuilder
import io.github.molelabs.aspectk.core.reportCompilerBug
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.declarations.IrValueParameterBuilder
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.builders.declarations.buildField
import org.jetbrains.kotlin.ir.builders.declarations.buildProperty
import org.jetbrains.kotlin.ir.builders.declarations.buildValueParameter
import org.jetbrains.kotlin.ir.builders.irBoolean
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.createExpressionBody
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.addSimpleDelegatingConstructor
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.isNullable
import org.jetbrains.kotlin.name.Name

@OptIn(UnsafeDuringIrConstructionAPI::class)
internal class MethodSignatureGenerator(
    private val aspectKContext: AspectKIrCompilerContext,
) {
    private var fieldCounter: Int = 0

    private val methodSignatureConstructor =
        aspectKContext.methodSignatureSymbol.constructors.first()

    private lateinit var parentClass: IrDeclarationParent

    fun generateInnerObject(
        name: String,
        parentClass: IrDeclarationParent,
    ): IrClass {
        this.parentClass = parentClass
        return aspectKContext.pluginContext.irFactory
            .buildClass {
                this.name = Name.identifier(name)
                visibility = DescriptorVisibilities.PUBLIC
                kind = ClassKind.OBJECT
            }.apply {
                parent = parentClass
                superTypes = listOf(aspectKContext.pluginContext.irBuiltIns.anyType)
                thisReceiver =
                    factory.buildValueParameter(
                        parent = this,
                        builder =
                        IrValueParameterBuilder().apply {
                            this.name = Name.identifier("<this>")
                            type = symbol.typeWith()
                            origin = IrDeclarationOrigin.INSTANCE_RECEIVER
                        },
                    )
                addSimpleDelegatingConstructor(
                    superConstructor =
                    aspectKContext.pluginContext.irBuiltIns.anyClass.owner.constructors
                        .first(),
                    irBuiltIns = aspectKContext.pluginContext.irBuiltIns,
                    isPrimary = true,
                )
            }
    }

    fun toProperty(
        innerObject: IrClass,
        methodSignature: IrExpression,
    ): IrProperty {
        val field =
            aspectKContext.pluginContext.irFactory
                .buildField {
                    name = Name.identifier($$"ajc$tjp_$${fieldCounter++}") // 유니크한 이름
                    type = aspectKContext.methodSignatureSymbol.defaultType
                    isStatic = true
                    isFinal = true
                    visibility = DescriptorVisibilities.PRIVATE
                }.apply {
                    parent = innerObject
                    initializer =
                        aspectKContext.pluginContext.irFactory.createExpressionBody(
                            methodSignature,
                        )
                }

        return aspectKContext.pluginContext.irFactory
            .buildProperty {
                name = field.name
                visibility = DescriptorVisibilities.PRIVATE
                origin = IrDeclarationOrigin.DEFINED
            }.apply {
                parent = innerObject
                backingField = field
                innerObject.declarations.add(this)
            }
    }

    fun generate(
        declaration: IrFunction,
        parentClass: IrDeclarationParent,
    ): IrExpression {
        this.parentClass = parentClass
        return aspectKContext.withIrBuilder(declaration.symbol) {
            createMethodSignatureInitializer(declaration)
        }
    }

    private fun IrBuilderWithScope.createMethodSignatureInitializer(declaration: IrFunction): IrExpression = irCall(methodSignatureConstructor).apply {
        arguments[0] = irString(declaration.name.asString())
        arguments[1] =
            aspectKContext.createIrListOf(
                scope = aspectKContext.methodSignatureSymbol,
                elementType = aspectKContext.annotationInfoSymbol.defaultType,
                elements =
                declaration.annotations.map { annotation ->
                    createAnnotationInfoInitializer(annotation)
                },
            )
        arguments[2] =
            aspectKContext.createIrListOf(
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
            aspectKContext.createKClassExpression(
                startOffset = declaration.startOffset,
                endOffset = declaration.endOffset,
                classType = declaration.returnType,
            )
        arguments[4] =
            irString(
                declaration.returnType.classFqName?.asString()
                    ?: reportCompilerBug("function return type should not be null"),
            )
    }

    private fun createMethodParameterInitializer(
        declaration: IrFunction,
        param: IrValueParameter,
    ): IrExpression = aspectKContext.withIrBuilder(aspectKContext.methodParameterSymbol) {
        irCall(aspectKContext.methodParameterSymbol.constructors.first()).apply {
            arguments[0] = irString(param.name.asString())
            arguments[1] =
                aspectKContext.createKClassExpression(
                    startOffset = declaration.startOffset,
                    endOffset = declaration.endOffset,
                    classType = param.type,
                )
            arguments[2] =
                irString(
                    if (param.type.isGeneric()) {
                        param.type.getUpperBoundClassName()
                    } else {
                        param.type.classFqName?.asString()
                    }
                        ?: reportCompilerBug("value parameter type should not be null"),
                )

            arguments[3] =
                aspectKContext.createIrListOf(
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

    private fun createAnnotationInfoInitializer(annotation: IrConstructorCall): IrExpression = aspectKContext.withIrBuilder(aspectKContext.annotationInfoSymbol) {
        irCall(aspectKContext.annotationInfoSymbol.constructors.first()).apply {
            arguments[0] =
                aspectKContext.createKClassExpression(
                    startOffset = annotation.startOffset,
                    endOffset = annotation.endOffset,
                    classType = annotation.type,
                )
            arguments[1] =
                irString(
                    annotation.type.classFqName?.asString()
                        ?: reportCompilerBug("annotation name type should not be null"),
                )

            val args = annotation.arguments.filterNotNull()
            val parameterNames =
                annotation.arguments.mapIndexedNotNull { idx, arg ->
                    if (arg != null) {
                        irString(
                            annotation.symbol.owner.parameters[idx]
                                .name
                                .asString(),
                        )
                    } else {
                        null
                    }
                }
            arguments[2] =
                aspectKContext.createIrListOf(
                    scope = aspectKContext.annotationInfoSymbol,
                    elements = args,
                )

            arguments[3] =
                aspectKContext.createIrListOf(
                    scope = aspectKContext.annotationInfoSymbol,
                    elements = parameterNames,
                    elementType = aspectKContext.pluginContext.irBuiltIns.stringType,
                )
        }
    }
}
