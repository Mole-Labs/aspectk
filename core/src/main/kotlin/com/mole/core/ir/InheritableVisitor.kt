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

import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid

internal class InheritableVisitor(
    private val aspectkContext: AspectKIrCompilerContext,
) : IrVisitorVoid() {
    private val targetAnnotations = aspectkContext.aspectLookUp.targets

    override fun visitFile(declaration: IrFile) {
        declaration.acceptChildrenVoid(this)
    }

    override fun visitClass(declaration: IrClass) {
        declaration.acceptChildrenVoid(this)
    }

    override fun visitFunction(declaration: IrFunction) {
        val target =
            targetAnnotation(declaration)
                ?: return super.visitFunction(declaration)
        val parent = declaration.parent as? IrClass ?: return super.visitFunction(declaration)
        if (parent.isInheritable()) {
            aspectkContext.aspectLookUp.addInheritable(
                fqName = target,
                target = parent,
            )
        }
    }

    private fun targetAnnotation(declaration: IrFunction) = targetAnnotations.find(declaration::hasAnnotation)
}
