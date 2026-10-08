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

import io.github.molelabs.aspectk.core.ir.AspectContext.Kind
import io.github.molelabs.aspectk.core.ir.generator.AdviceCallGenerator
import io.github.molelabs.aspectk.core.ir.generator.JoinPointGenerator
import io.github.molelabs.aspectk.core.ir.generator.MethodSignatureGenerator
import io.github.molelabs.aspectk.core.ir.generator.ProceedingJoinPointGenerator
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

@OptIn(UnsafeDuringIrConstructionAPI::class)
internal class AspectTransformer(
    private val joinPointGenerator: JoinPointGenerator,
    private val methodSignatureGenerator: MethodSignatureGenerator,
    private val adviceCallGenerator: AdviceCallGenerator,
    private val proceedingJoinPointGenerator: ProceedingJoinPointGenerator,
    private val aspectKContext: AspectKIrCompilerContext,
) : IrElementTransformerVoidWithContext() {
    private val targets = aspectKContext.aspectLookUp.targets

    override fun visitSimpleFunction(declaration: IrSimpleFunction): IrStatement {
        // Fake Override 메서드는 패스
        if (declaration !is IrFunctionImpl) return super.visitSimpleFunction(declaration)
        val contexts = adviceFor(declaration)
        if (contexts.isNotEmpty()) {
            val parent = findParent(declaration) ?: return super.visitSimpleFunction(declaration)
            weave(declaration, contexts, generateSignature(parent, declaration))
        }
        return super.visitSimpleFunction(declaration)
    }

    // 함수에 직접 붙은 타겟의 어드바이스, 그 뒤에 상속 관계로 적용되는 어드바이스 (inherits = true만)
    private fun adviceFor(declaration: IrFunction): List<AspectContext> {
        val lookUp = aspectKContext.aspectLookUp
        val direct =
            if (declaration.hasBody()) targetAnnotations(declaration).flatMap { lookUp[it] } else emptyList()
        val overridden = lookUp.getOverridden(declaration.attributeOwnerId)
        val inherited = targets.filter { it in overridden }.flatMap { lookUp[it].filter(AspectContext::inherits) }
        return direct + inherited
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

    private fun weave(
        declaration: IrFunction,
        contexts: List<AspectContext>,
        signatureProperty: IrProperty,
    ) {
        val joinPoint = joinPointGenerator.generate(declaration, signatureProperty)
        val byKind = contexts.groupBy { it.kind }

        // @After wraps the body first, so it stays inside every @Around: it only runs when the
        // body did, once per proceed().
        byKind[Kind.AFTER]?.forEach { context ->
            adviceCallGenerator.generateAfterAdviceCalls(declaration, context, joinPoint)
        }

        // Each @Around wraps whatever the body holds so far, so they are woven last to first
        byKind[Kind.AROUND]?.asReversed()?.forEach { context ->
            val proceedingJoinPoint =
                proceedingJoinPointGenerator.generateProceedingJoinPoint(declaration, signatureProperty)
            adviceCallGenerator.generateAroundAdviceCalls(declaration, context, proceedingJoinPoint)
        }

        // @Before is prepended after the body structure is finalized
        byKind[Kind.BEFORE]?.let { adviceCallGenerator.generateAdviceCalls(declaration, it, joinPoint) }
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

    private fun IrDeclarationContainer.toNormalizedName(basename: String): String {
        val file = this as? IrFile ?: return basename
        val module = file.module.name.asStringStripSpecialMarkers().replace(NON_IDENTIFIER, "_")
        return "$basename$$module$${file.name.replace(".", "")}"
    }

    private companion object {
        val NON_IDENTIFIER = Regex("[^A-Za-z0-9_]")
    }
}
