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

import com.mole.core.reportCompilerBug
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrClassReference
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.expressions.IrVarargElement
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.name.FqName

/*
@Aspect
object FirebaseAspect {
	@Before(target = MoleEvent::class)
	fun log(joinPoint:JoinPoint) {

	}
}

이런 Aspect 분석 후 AspectLookUp으로 변환

 */
@OptIn(UnsafeDuringIrConstructionAPI::class)
internal class AspectVisitor(
    private val aspectkContext: AspectKIrCompilerContext,
) : IrVisitorVoid() {
    override fun visitFile(declaration: IrFile) {
        declaration.acceptChildrenVoid(this)
    }

    override fun visitClass(declaration: IrClass) {
        declaration.acceptChildrenVoid(this)
        if (canSkip(declaration)) return super.visitClass(declaration)

        declaration.functions.forEach { func ->
            func.annotations.forEach { annotation ->
                val fqName = annotation.type.classFqName ?: return@forEach
                if (fqName !in AspectKIrCompilerContext.ADVICE_ANNOTATIONS_FQ_NAME) return@forEach
                val kind = AspectContext.find(fqName) ?: reportCompilerBug("kind not found: $fqName")
                val inherits = (annotation.arguments[1] as? IrConst)?.value as? Boolean

                when (val targetArg = annotation.arguments[0]) {
                    is IrVararg -> {
                        targetArg.elements.forEach { element ->
                            processElement(element, func, declaration, kind, inherits)
                        }
                    }

                    else -> {
                        reportCompilerBug("invalid targetArg: $targetArg")
                    }
                }
            }
        }
    }

    private fun processElement(
        element: IrVarargElement,
        func: IrSimpleFunction,
        aspectClass: IrClass,
        kind: AspectContext.Kind,
        inherits: Boolean?,
    ) {
        if (element is IrClassReference) {
            val targetFqName =
                element.classType.classFqName
                    ?: reportCompilerBug("advice argument type should not be null")
            val context =
                AspectContext(
                    advice = func,
                    aspect = aspectClass.symbol,
                    kind = kind,
                    methodSignature = null,
                )

            aspectkContext.aspectLookUp.add(
                fqName = targetFqName,
                aspectContext = inherits?.let { context.copy(inherits = it) } ?: context,
            )
        }
    }

    private fun canSkip(declaration: IrClass): Boolean = !declaration.hasAnnotation(FqName(AspectKIrCompilerContext.ASPECT_ANNOTATION_FQ_NAME))
}
