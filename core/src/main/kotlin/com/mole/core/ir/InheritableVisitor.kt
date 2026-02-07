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
