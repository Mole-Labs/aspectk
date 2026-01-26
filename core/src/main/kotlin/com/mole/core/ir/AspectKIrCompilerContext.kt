package com.mole.core.ir

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.name.FqName

internal data class AspectKIrCompilerContext(
    val pluginContext: IrPluginContext,
    val aspectLookUp: AspectLookUp = AspectLookUp(),
) {
    val methodSignatureSymbol: IrClassSymbol = pluginContext.getSymbol(METHOD_SIGNATURE_FQ_NAME)
    val methodParameterSymbol: IrClassSymbol = pluginContext.getSymbol(METHOD_PARAMETER_FQ_NAME)
    val annotationInfoSymbol: IrClassSymbol = pluginContext.getSymbol(ANNOTATION_INFO_FQ_NAME)

    companion object {
        val ADVICE_ANNOTATIONS_FQ_NAME =
            listOf(
                FqName(BEFORE_ANNOTATION_FQ_NAME),
            )
        const val BEFORE_ANNOTATION_FQ_NAME = "com.mole.runtime.Before"
        const val ASPECT_ANNOTATION_FQ_NAME = "com.mole.runtime.Aspect"
        const val METHOD_SIGNATURE_FQ_NAME = "com.mole.runtime.MethodSignature"
        const val METHOD_PARAMETER_FQ_NAME = "com.mole.runtime.MethodParameter"

        const val ANNOTATION_INFO_FQ_NAME = "com.mole.runtime.AnnotationInfo"
    }
}
