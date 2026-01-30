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

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.name.FqName

/*
일단 acceptChild로 IrCall, IrSymbol 객체 저장
자료구조가 Map<FqName, List<Context>>

data class Context(
    val irCall:IrCall,
    val symbol:IrSymbol,
    val methodSignature:
    val kind:AspectKind
)
 */

@OptIn(UnsafeDuringIrConstructionAPI::class)
internal class AspectTransformer(
    private val joinPointGenerator: JoinPointGenerator,
    private val methodSignatureFieldGenerator: MethodSignatureFieldGenerator,
    private val aspectKContext: AspectKIrCompilerContext,
    private val targetAnnotations: List<FqName>,
) : IrElementTransformerVoidWithContext() {
    override fun visitFunctionNew(declaration: IrFunction): IrStatement {
        val target =
            targetAnnotation(declaration)
                ?: return super.visitFunctionNew(declaration)
        val parentClass =
            currentClass?.irElement as? IrClass ?: return super.visitFunctionNew(declaration)
        val signature = methodSignatureFieldGenerator.generate(declaration, parentClass)
        val signatureField = methodSignatureFieldGenerator.toField(signature)
        parentClass.declarations.add(signatureField)

        val joinPoint =
            joinPointGenerator.generate(declaration, signatureField)
        val adviceCalls =
            aspectKContext.withIrBuilder(declaration.symbol) {
                irBlock {
                    aspectKContext.aspectLookUp[target].forEach {
                        +irCall(it.advice.symbol).apply {
                            dispatchReceiver = irGetObject(it.aspect)
                            arguments[1] = joinPoint
                        }
                    }
                }
            }
        (declaration.body as? IrBlockBody)?.statements?.add(0, adviceCalls)

        return super.visitFunctionNew(declaration)
    }

    private fun targetAnnotation(declaration: IrFunction) = targetAnnotations.find(declaration::hasAnnotation)
}
