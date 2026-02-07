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
package com.mole.core.ir

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.impl.IrFunctionImpl
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.allOverridden
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.name.FqName

@OptIn(UnsafeDuringIrConstructionAPI::class)
internal class AspectTransformer(
    private val joinPointGenerator: JoinPointGenerator,
    private val methodSignatureFieldGenerator: MethodSignatureFieldGenerator,
    private val aspectKContext: AspectKIrCompilerContext,
) : IrElementTransformerVoidWithContext() {
    private val targetAnnotations = aspectKContext.aspectLookUp.targets

    override fun visitSimpleFunction(declaration: IrSimpleFunction): IrStatement {
        if (declaration !is IrFunctionImpl) return super.visitSimpleFunction(declaration)
        val parent = declaration.parent as? IrClass ?: return super.visitFunctionNew(declaration)
        val target = targetAnnotation(declaration)

        // 상속 가능한 클래스가 아니며, 타겟에 해당되는 경우
        if (!parent.isInheritable() && target != null) {
            generateInner(declaration, target)
            return super.visitFunctionNew(declaration)
        }

        // 부모타입 어노테이션 체크
        val allOverridden = declaration.allOverridden().map { it.parent }

        targetAnnotations.forEach { targetAnnotation ->
            val inheritable = aspectKContext.aspectLookUp.getInheritable(targetAnnotation)
            val isOverridden = allOverridden.any { inheritable.contains(it) }

            if (isOverridden) {
                generateInner(declaration, targetAnnotation)
            }
        }

        return super.visitSimpleFunction(declaration)
    }

    private fun generateInner(
        declaration: IrFunction,
        target: FqName,
    ) {
        val parent = findParent(declaration) ?: return
        val signature = methodSignatureFieldGenerator.generate(declaration, parent)
        val signatureField = methodSignatureFieldGenerator.toField(signature)
        parent.declarations.add(signatureField)

        val joinPoint =
            joinPointGenerator.generate(declaration, signatureField)
        val adviceCalls =
            aspectKContext.withIrBuilder(declaration.symbol) {
                irBlock {
                    aspectKContext.aspectLookUp[target].forEach {
                        +irCall(it.advice.symbol).apply {
                            dispatchReceiver = irGetObject(it.aspect)
                            arguments[1] = joinPoint.deepCopyWithSymbols()
                        }
                    }
                }
            }
        (declaration.body as? IrBlockBody)?.statements?.add(0, adviceCalls)
    }

    private fun findParent(declaration: IrFunction): IrDeclarationContainer? {
        var current = declaration.parent
        while (current !is IrDeclarationContainer) {
            current = (current as? IrDeclaration)?.parent ?: return null
        }
        return current
    }

    private fun targetAnnotation(declaration: IrFunction) = targetAnnotations.find(declaration::hasAnnotation)
}
