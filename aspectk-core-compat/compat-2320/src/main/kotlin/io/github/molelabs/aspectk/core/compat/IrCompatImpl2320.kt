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
package io.github.molelabs.aspectk.core.compat

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId

class IrCompatImpl2320 : IrCompat {
    override val kotlinVersion: KotlinVersion = KotlinVersion(2, 3, 20)

    override fun instanceReceiverOrigin(): IrDeclarationOrigin = IrDeclarationOrigin.INSTANCE_RECEIVER

    override fun propertyBackingFieldOrigin(): IrDeclarationOrigin = IrDeclarationOrigin.PROPERTY_BACKING_FIELD

    override fun localFunctionOrigin(): IrDeclarationOrigin = IrDeclarationOrigin.LOCAL_FUNCTION

    override fun localFunctionForLambdaOrigin(): IrDeclarationOrigin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA

    override fun catchParameterOrigin(): IrDeclarationOrigin = IrDeclarationOrigin.CATCH_PARAMETER

    override fun valueParameterOrigin(): IrDeclarationOrigin = IrDeclarationOrigin.DEFINED

    override fun referenceFunctions(pluginContext: IrPluginContext, callableId: CallableId): Collection<IrSimpleFunctionSymbol> =
        pluginContext.finderForBuiltins().findFunctions(callableId)

    override fun referenceClass(pluginContext: IrPluginContext, classId: ClassId): IrClassSymbol? =
        pluginContext.finderForBuiltins().findClass(classId)
}