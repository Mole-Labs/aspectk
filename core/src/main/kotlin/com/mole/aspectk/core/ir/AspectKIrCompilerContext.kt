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
package com.mole.aspectk.core.ir

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.name.FqName

internal data class AspectKIrCompilerContext(
    val pluginContext: IrPluginContext,
    val aspectLookUp: AspectLookUp = AspectLookUp(),
) {
    val joinPointSymbol: IrClassSymbol = getSymbol(JOIN_POINT_FQ_NAME)
    val methodSignatureSymbol: IrClassSymbol = getSymbol(METHOD_SIGNATURE_FQ_NAME)
    val methodParameterSymbol: IrClassSymbol = getSymbol(METHOD_PARAMETER_FQ_NAME)
    val annotationInfoSymbol: IrClassSymbol = getSymbol(ANNOTATION_INFO_FQ_NAME)

    companion object {
        val ADVICE_ANNOTATIONS_FQ_NAME =
            listOf(
                FqName(BEFORE_ANNOTATION_FQ_NAME),
            )
        const val BEFORE_ANNOTATION_FQ_NAME = "com.mole.aspectk.runtime.Before"
        const val ASPECT_ANNOTATION_FQ_NAME = "com.mole.aspectk.runtime.Aspect"
        const val METHOD_SIGNATURE_FQ_NAME = "com.mole.aspectk.runtime.MethodSignature"
        const val METHOD_PARAMETER_FQ_NAME = "com.mole.aspectk.runtime.MethodParameter"

        const val ANNOTATION_INFO_FQ_NAME = "com.mole.aspectk.runtime.AnnotationInfo"

        const val JOIN_POINT_FQ_NAME = "com.mole.aspectk.runtime.internal.DefaultJoinPoint"
    }
}
