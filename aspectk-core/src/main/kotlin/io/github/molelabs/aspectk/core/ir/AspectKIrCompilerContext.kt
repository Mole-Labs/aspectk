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

import io.github.molelabs.aspectk.core.AspectKClassIds
import io.github.molelabs.aspectk.core.compat.IrCompat
import io.github.molelabs.aspectk.core.hints.HintRecord
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.name.ClassId
import java.util.Collections

internal data class AspectKIrCompilerContext(
    val pluginContext: IrPluginContext,
    val irCompat: IrCompat,
    val aspectLookUp: AspectLookUp = AspectLookUp(),
    val localHints: MutableList<HintRecord> = Collections.synchronizedList(mutableListOf()),
    val visitedClassIds: MutableSet<ClassId> = Collections.synchronizedSet(mutableSetOf()),
) {
    val joinPointSymbol: IrClassSymbol = getSymbol(AspectKClassIds.DEFAULT_JOIN_POINT)
    val proceedingJoinPointSymbol: IrClassSymbol = getSymbol(AspectKClassIds.DEFAULT_PROCEEDING_JOIN_POINT)
    val onProceedListenerSymbol: IrClassSymbol = getSymbol(AspectKClassIds.ON_PROCEED_LISTENER)
    val onProceedListenerType: IrType = onProceedListenerSymbol.typeWith()
    val suspendProceedingJoinPointSymbol: IrClassSymbol = getSymbol(AspectKClassIds.DEFAULT_SUSPEND_PROCEEDING_JOIN_POINT)
    val suspendOnProceedListenerSymbol: IrClassSymbol = getSymbol(AspectKClassIds.SUSPEND_ON_PROCEED_LISTENER)
    val suspendOnProceedListenerType: IrType = suspendOnProceedListenerSymbol.typeWith()
    val methodSignatureSymbol: IrClassSymbol = getSymbol(AspectKClassIds.METHOD_SIGNATURE)
    val methodParameterSymbol: IrClassSymbol = getSymbol(AspectKClassIds.METHOD_PARAMETER)
    val annotationInfoSymbol: IrClassSymbol = getSymbol(AspectKClassIds.ANNOTATION_INFO)
}
