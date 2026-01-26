package com.mole.core.ir

import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.name.FqName

@OptIn(UnsafeDuringIrConstructionAPI::class)
internal class AspectVisitor(
    private val aspectkContext: AspectKIrCompilerContext,
) : IrVisitorVoid() {
    override fun visitClass(declaration: IrClass) {
        if (canSkip(declaration)) return super.visitClass(declaration)

        declaration.functions
            .filter { func ->
                AspectKIrCompilerContext.ADVICE_ANNOTATIONS_FQ_NAME.any(func::hasAnnotation)
            }.forEach {
                val annotation = it.annotations.first()
                annotation.arguments.forEach {
                }
                aspectkContext.aspectLookUp
            }
    }

    private fun canSkip(declaration: IrClass): Boolean =
        !declaration.hasAnnotation(FqName(AspectKIrCompilerContext.ASPECT_ANNOTATION_FQ_NAME))
}
