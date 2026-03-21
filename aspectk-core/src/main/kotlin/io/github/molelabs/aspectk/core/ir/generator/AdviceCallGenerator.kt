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
package io.github.molelabs.aspectk.core.ir.generator

import io.github.molelabs.aspectk.core.ir.AspectContext
import io.github.molelabs.aspectk.core.ir.AspectKIrCompilerContext
import io.github.molelabs.aspectk.core.ir.add
import io.github.molelabs.aspectk.core.ir.withIrBuilder
import org.jetbrains.kotlin.ir.builders.irAs
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrTry
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.name.FqName

@OptIn(UnsafeDuringIrConstructionAPI::class)
internal class AdviceCallGenerator(
    private val aspectKContext: AspectKIrCompilerContext,
) {
    /** Prepends @Before advice calls to the function body. */
    fun generateAdviceCalls(
        declaration: IrFunction,
        target: FqName,
        joinPoint: IrExpression,
        checkInherits: Boolean = false,
    ) = buildCallBlock(declaration, target, joinPoint, checkInherits, AspectContext.Kind.BEFORE)
        .also { declaration.body?.add(it) }

    /** Appends @After advice calls to the function body (before any trailing return). */
    fun generateAfterAdviceCalls(
        declaration: IrFunction,
        target: FqName,
        joinPoint: IrExpression,
        tryCatchWrapper: IrTry,
        localFunction: IrSimpleFunction,
        checkInherits: Boolean = false,
    ) {
        val finalExpression =
            buildCallBlock(declaration, target, joinPoint, checkInherits, AspectContext.Kind.AFTER)
        tryCatchWrapper.finallyExpression = finalExpression
        val returnStatement =
            aspectKContext.withIrBuilder(declaration.symbol) {
                irReturn(tryCatchWrapper)
            }

        (declaration.body as? IrBlockBody)?.statements?.let { statement ->
            statement.clear()
            statement.add(localFunction)
            statement.add(returnStatement)
        }
    }

    /**
     * Replaces the function body with:
     *   1. [localFunction] declaration (the `$<name>` local function holding the original body)
     *   2. @Around advice calls, each receiving the provided [proceedingJoinPoint] expression.
     */
    fun generateAroundAdviceCalls(
        declaration: IrFunction,
        target: FqName,
        localFunction: IrSimpleFunction,
        proceedingJoinPoint: IrExpression,
        checkInherits: Boolean = false,
    ) {
        val aroundCallback =
            buildAroundCallBlock(
                declaration,
                target,
                proceedingJoinPoint,
                checkInherits,
            )
        (declaration.body as? IrBlockBody)?.statements?.let { statement ->
            statement.clear()
            statement.add(localFunction)
            statement.add(aroundCallback)
        }
    }

    private fun buildCallBlock(
        declaration: IrFunction,
        target: FqName,
        joinPointExpr: IrExpression,
        checkInherits: Boolean,
        kind: AspectContext.Kind,
    ) = aspectKContext.withIrBuilder(declaration.symbol) {
        irBlock {
            aspectKContext.aspectLookUp[target].forEach { targetContext ->
                if (targetContext.kind != kind) return@forEach
                if (checkInherits && !targetContext.inherits) return@forEach
                +irCall(targetContext.advice.symbol).apply {
                    dispatchReceiver = irGetObject(targetContext.aspect)
                    arguments[1] = joinPointExpr.deepCopyWithSymbols()
                }
            }
        }
    }

    /**
     * Returns an [IrExpression] that calls the first applicable @Around advice for [target],
     * suitable for use as the try-body in a try-finally wrapper.
     *
     * Only one @Around advice is invoked per target annotation.
     * Supporting multiple chained @Around advices requires an ordering engine (TODO).
     */
    fun buildAroundCallExpression(
        declaration: IrFunction,
        target: FqName,
        proceedingJoinPoint: IrExpression,
        checkInherits: Boolean = false,
    ): IrExpression = buildAroundCallBlock(declaration, target, proceedingJoinPoint, checkInherits)

    private fun buildAroundCallBlock(
        declaration: IrFunction,
        target: FqName,
        joinPointExpr: IrExpression,
        checkInherits: Boolean,
    ) = aspectKContext.withIrBuilder(declaration.symbol) {
        /*
        TODO support multiple @Around advices,
        ProceedingJoinPointGenerator.generateProceedingJoinPoint may be called
        in each iteration of aspectKContext.aspectLookUp[target],

        generateProceedingJoinPoint method parameter should be added 'innerProceed'
        which contains advice body of around, after annotation.

        if innerProceed parameter exists, the result of generateProceedingJoinPoint
        may be added to input innerProceed such as.

        @Target
        fun doSomething(arg1:String):String {
            fun $doSomething(arg1:String = arg1):String {
                   println("hello aspectk")
                   return ""
            }
            return SomeAspect.doAround1(
                ProceedingJoinPoint(...) { args:List<Any?> ->
                    SomeAspect.doAround2(
                        ProceedingJoinPoint(...) { args:List<Any?> ->
                            try {
                                $doSomething(args[0] as String)
                            } catch (e:Exception) {
                                throw e
                            } finally {
                                SomeAspect.doAfter1(JoinPoint(...))
                            }
                       }
                   )
                }
            )
        }

        this is only raw draft. so it may not be correct.

        Design rationale — @After placement (innermost):
        @After is placed in the finally block that wraps only $doSomething (the original function
        body), not the entire @Around chain. This is intentional:

        1. @After's contract is "execute after the target function", not "execute after all aspects".
           If an @Around advice throws before calling pjp.proceed(), the original function never
           ran, so @After should not fire in that case.

        2. @Around advice is responsible for handling its own exceptions internally.
           Wrapping the outer @Around call with the finally block would mean @After fires even
           when @Around itself fails — which conflates two unrelated concerns.

        3. This design gives predictable execution order control after the original code runs:
           @After fires first (innermost finally), then @Around's post-proceed logic runs
           outward. The order is deterministic and mirrors the lexical nesting of the generated IR.
         */
        val targetContext =
            aspectKContext.aspectLookUp[target]
                .filter { it.kind == AspectContext.Kind.AROUND }
                .firstOrNull { !checkInherits || it.inherits }

        irBlock {
            if (targetContext != null) {
                +irReturn(
                    irAs(
                        irCall(targetContext.advice.symbol).apply {
                            dispatchReceiver = irGetObject(targetContext.aspect)
                            arguments[1] = joinPointExpr.deepCopyWithSymbols(declaration)
                        },
                        declaration.returnType,
                    ),
                )
            }
        }
    }
}
