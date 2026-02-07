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

import com.mole.core.ir.AspectContext.Kind.BEFORE
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.name.FqName
import java.util.*
import java.util.concurrent.ConcurrentHashMap

// Target Annotation과 AspectContext는 다대다관계
// 즉 하나의 Target에 여러 Advice를 가질 수 있고
// 하나의 Advice에 여러 Target을 가질 수 있음
internal class AspectLookUp {
    private val aspectContexts: ConcurrentHashMap<FqName, MutableList<AspectContext>> = ConcurrentHashMap()
    private val inheritableAspects: ConcurrentHashMap<FqName, MutableList<IrClass>> = ConcurrentHashMap()

    val targets: Set<FqName> get() = aspectContexts.keys.toSet()

    operator fun get(fqName: FqName): List<AspectContext> = aspectContexts[fqName]?.toList() ?: emptyList()

    fun getInheritable(fqName: FqName): List<IrClass> = inheritableAspects[fqName]?.toList() ?: emptyList()

    fun addInheritable(
        fqName: FqName,
        target: IrClass,
    ) {
        inheritableAspects
            .computeIfAbsent(fqName) {
                Collections.synchronizedList(mutableListOf<IrClass>())
            }.add(target)
    }

    fun add(
        fqName: FqName,
        aspectContext: AspectContext,
    ) {
        aspectContexts
            .computeIfAbsent(fqName) {
                Collections.synchronizedList(mutableListOf<AspectContext>())
            }.add(aspectContext)
    }
}

internal data class AspectContext(
    val advice: IrFunction,
    val aspect: IrClassSymbol,
    val kind: Kind,
    val methodSignature: IrExpression? = null, // TODO mapping context to methodSignature when compiling. currently, it is always null
) {
    enum class Kind {
        BEFORE, // 추후 After, Around 추가 예정
    }

    companion object {
        fun find(fqName: FqName) = when (fqName.asString()) {
            AspectKIrCompilerContext.BEFORE_ANNOTATION_FQ_NAME -> BEFORE
            else -> null
        }
    }
}
