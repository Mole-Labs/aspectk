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

import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import java.util.ServiceLoader

interface IrCompat {
    val kotlinVersion: KotlinVersion

    fun instanceReceiverOrigin(): IrDeclarationOrigin

    fun propertyBackingFieldOrigin(): IrDeclarationOrigin

    fun localFunctionOrigin(): IrDeclarationOrigin

    fun localFunctionForLambdaOrigin(): IrDeclarationOrigin

    fun catchParameterOrigin(): IrDeclarationOrigin

    fun valueParameterOrigin(): IrDeclarationOrigin

    companion object {
        fun create(version: KotlinVersion): IrCompat =
            ServiceLoader
                .load(IrCompat::class.java, IrCompat::class.java.classLoader)
                .filter { it.kotlinVersion <= version }
                .maxByOrNull { it.kotlinVersion }
                ?: error("No IrCompat found for Kotlin $version")
    }
}
