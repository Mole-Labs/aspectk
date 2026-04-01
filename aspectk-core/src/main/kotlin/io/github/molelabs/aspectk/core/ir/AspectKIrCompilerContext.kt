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

import io.github.molelabs.aspectk.core.compat.IrCompat
import io.github.molelabs.aspectk.core.reportCompilerBug
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

internal data class AspectKIrCompilerContext(
    val pluginContext: IrPluginContext,
    val irCompat: IrCompat,
    val aspectLookUp: AspectLookUp = AspectLookUp(),
) {
    val joinPointSymbol: IrClassSymbol = getSymbol(JOIN_POINT_FQ_NAME)
    val proceedingJoinPointSymbol: IrClassSymbol = getSymbol(PROCEEDING_JOIN_POINT_FQ_NAME)
    val onProceedListenerSymbol: IrClassSymbol =
        irCompat.referenceClass(
            pluginContext,
            ClassId(
                FqName("io.github.molelabs.aspectk.runtime"),
                FqName("ProceedingJoinPoint.OnProceedListener"),
                false,
            ),
        ) ?: reportCompilerBug("Cannot find symbol for $ON_PROCEED_LISTENER_FQ_NAME")
    val onProceedListenerType: IrType = onProceedListenerSymbol.typeWith()
    val methodSignatureSymbol: IrClassSymbol = getSymbol(METHOD_SIGNATURE_FQ_NAME)
    val methodParameterSymbol: IrClassSymbol = getSymbol(METHOD_PARAMETER_FQ_NAME)
    val annotationInfoSymbol: IrClassSymbol = getSymbol(ANNOTATION_INFO_FQ_NAME)

    companion object {
        val ADVICE_ANNOTATIONS_FQ_NAME =
            listOf(
                FqName(BEFORE_ANNOTATION_FQ_NAME),
                FqName(AFTER_ANNOTATION_FQ_NAME),
                FqName(AROUND_ANNOTATION_FQ_NAME),
            )
        const val BEFORE_ANNOTATION_FQ_NAME = "io.github.molelabs.aspectk.runtime.Before"
        const val AFTER_ANNOTATION_FQ_NAME = "io.github.molelabs.aspectk.runtime.After"
        const val AROUND_ANNOTATION_FQ_NAME = "io.github.molelabs.aspectk.runtime.Around"
        const val ASPECT_ANNOTATION_FQ_NAME = "io.github.molelabs.aspectk.runtime.Aspect"
        const val METHOD_SIGNATURE_FQ_NAME = "io.github.molelabs.aspectk.runtime.MethodSignature"
        const val METHOD_PARAMETER_FQ_NAME = "io.github.molelabs.aspectk.runtime.MethodParameter"

        const val ANNOTATION_INFO_FQ_NAME = "io.github.molelabs.aspectk.runtime.AnnotationInfo"

        const val JOIN_POINT_FQ_NAME = "io.github.molelabs.aspectk.runtime.internal.DefaultJoinPoint"
        const val PROCEEDING_JOIN_POINT_FQ_NAME = "io.github.molelabs.aspectk.runtime.internal.DefaultProceedingJoinPoint"
        const val ON_PROCEED_LISTENER_FQ_NAME = "io.github.molelabs.aspectk.runtime.ProceedingJoinPoint.OnProceedListener"
    }
}