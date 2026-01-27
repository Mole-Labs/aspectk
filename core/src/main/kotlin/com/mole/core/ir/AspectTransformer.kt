package com.mole.core.ir

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
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

// TODO 컴파일러 코드에서 stdlib 의존성 제거 -> 현재 IR트리가 너무 깊습니다.
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
            joinPointGenerator.generate(declaration, signature.deepCopyWithSymbols())
        val adviceCalls =
            DeclarationIrBuilder(aspectKContext.pluginContext, declaration.symbol).irBlock {
                aspectKContext.aspectLookUp[target].forEach {
                    +irCall(it.advice.symbol).apply {
                        dispatchReceiver = irGetObject(it.aspect)
                        arguments[1] = joinPoint
                    }
                }
            }
        (declaration.body as? IrBlockBody)?.statements?.add(0, adviceCalls)

        return super.visitFunctionNew(declaration)
    }

    private fun targetAnnotation(declaration: IrFunction) = targetAnnotations.find(declaration::hasAnnotation)
}
