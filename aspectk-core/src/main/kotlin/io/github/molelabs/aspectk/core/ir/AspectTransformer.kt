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

import io.github.molelabs.aspectk.core.ir.generator.AdviceCallGenerator
import io.github.molelabs.aspectk.core.ir.generator.JoinPointGenerator
import io.github.molelabs.aspectk.core.ir.generator.MethodSignatureGenerator
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationContainer
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.impl.IrFunctionImpl
import org.jetbrains.kotlin.ir.declarations.name
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.name.FqName

@OptIn(UnsafeDuringIrConstructionAPI::class)
internal class AspectTransformer(
    private val joinPointGenerator: JoinPointGenerator,
    private val methodSignatureGenerator: MethodSignatureGenerator,
    private val adviceCallGenerator: AdviceCallGenerator,
    private val aspectKContext: AspectKIrCompilerContext,
) : IrElementTransformerVoidWithContext() {
    private val targets = aspectKContext.aspectLookUp.targets

    override fun visitSimpleFunction(declaration: IrSimpleFunction): IrStatement {
        // Fake Override 메서드는 패스
        if (declaration !is IrFunctionImpl) return super.visitSimpleFunction(declaration)
        val targetAnnotations = targetAnnotations(declaration)

        // 함수 본문이 있으며, 타겟에 해당되는 경우
        if (targetAnnotations.isNotEmpty() && declaration.hasBody()) {
            val parent = findParent(declaration) ?: return super.visitSimpleFunction(declaration)
            val signatureProperty = generateSignature(parent, declaration)
            targetAnnotations.forEach { target ->
                generateInner(declaration, target, false, signatureProperty)
            }
        }

        // 일반 메서드라도 상속 관계일 경우 처리
        generateIfOverridden(declaration)
        return super.visitSimpleFunction(declaration)
    }

    private fun generateIfOverridden(declaration: IrFunction) {
        targets.forEach { targetAnnotation ->
            val isOverridden =
                aspectKContext.aspectLookUp
                    .getOverridden(declaration.attributeOwnerId)
                    .contains(targetAnnotation)
            val inherits = aspectKContext.aspectLookUp[targetAnnotation].any { it.inherits }
            if (isOverridden && inherits) {
                val parent = findParent(declaration) ?: return
                val signatureProperty = generateSignature(parent, declaration)
                generateInner(declaration, targetAnnotation, true, signatureProperty)
            }
        }
    }

    private fun generateSignature(
        parent: IrDeclarationContainer,
        declaration: IrFunction,
    ): IrProperty {
        val innerObjectName = parent.toNormalizedName($$"$MethodSignatures")

        val innerObject =
            parent.getOrPutAspectObject(innerObjectName) {
                methodSignatureGenerator.generateInnerObject(innerObjectName, it)
            }

        val signature = methodSignatureGenerator.generate(declaration, parent)
        return methodSignatureGenerator.toProperty(innerObject, signature)
    }

    private fun generateInner(
        declaration: IrFunction,
        target: FqName,
        checkInherits: Boolean,
        signatureProperty: IrProperty,
    ) {
        val joinPoint = joinPointGenerator.generate(declaration, signatureProperty)
        adviceCallGenerator.generateAdviceCalls(declaration, target, joinPoint, checkInherits)
    }

    private fun findParent(declaration: IrFunction): IrDeclarationContainer? {
        var current = declaration.parent
        while (current !is IrDeclarationContainer) {
            current = (current as? IrDeclaration)?.parent ?: return null
        }
        return current
    }

    private fun targetAnnotations(declaration: IrFunction) = targets.filter(declaration::hasAnnotation)

    private fun IrDeclarationContainer.getOrPutAspectObject(
        name: String,
        factory: (IrDeclarationContainer) -> IrClass,
    ): IrClass = declarations
        .filterIsInstance<IrClass>()
        .firstOrNull { it.name.asString() == name }
        ?: factory(this).also { declarations.add(it) }

    private fun IrDeclarationContainer.toNormalizedName(basename: String) = "$basename${(this as? IrFile)?.name.orEmpty().let{
        if (it.isNotEmpty()) "$$it" else ""
    }.replace(".", "")}"
}
