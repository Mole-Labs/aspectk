package com.mole.core.ir

import com.mole.core.ir.AspectContext.Kind.BEFORE
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.name.FqName

// Target Annotation과 AspectContext는 다대다관계
// 즉 하나의 Target에 여러 Advice를 가질 수 있고
// 하나의 Advice에 여러 Target을 가질 수 있음
internal class AspectLookUp {
    private val _targets = mutableListOf<FqName>()
    val targets: List<FqName> get() = _targets.toList()
    private val targetToIdx = mutableMapOf<FqName, Int>()

    private val contexts = mutableListOf<AspectContext>()
    private val contextToIdx = mutableMapOf<AspectContext, Int>()

    private val targetToContextsMap = mutableMapOf<Int, MutableList<Int>>()
    private val contextToTargetsMap = mutableMapOf<Int, MutableList<Int>>()

    operator fun get(fqName: FqName): List<AspectContext> {
        val tIdx = targetToIdx[fqName] ?: return emptyList()
        return targetToContextsMap[tIdx]?.map { contexts[it] } ?: emptyList()
    }

    operator fun get(context: AspectContext): List<FqName> {
        val cIdx = contextToIdx[context] ?: return emptyList()
        return contextToTargetsMap[cIdx]?.map { _targets[it] } ?: emptyList()
    }

    fun add(
        fqName: FqName,
        aspectContext: AspectContext,
    ) {
        val tIdx =
            targetToIdx.getOrPut(fqName) {
                _targets.add(fqName)
                _targets.size - 1
            }

        val cIdx =
            contextToIdx.getOrPut(aspectContext) {
                contexts.add(aspectContext)
                contexts.size - 1
            }

        targetToContextsMap.getOrPut(tIdx) { mutableListOf() }.add(cIdx)
        contextToTargetsMap.getOrPut(cIdx) { mutableListOf() }.add(tIdx)
    }
}

internal data class AspectContext(
    val advice: IrFunction,
    val aspect: IrSymbol,
    val kind: Kind,
    val methodSignature: IrExpression? = null,
) {
    enum class Kind {
        BEFORE, // 추후 After, Around 추가 예정
    }

    companion object {
        fun find(fqName: FqName) =
            when (fqName.asString()) {
                AspectKIrCompilerContext.BEFORE_ANNOTATION_FQ_NAME -> BEFORE
                else -> null
            }
    }
}
