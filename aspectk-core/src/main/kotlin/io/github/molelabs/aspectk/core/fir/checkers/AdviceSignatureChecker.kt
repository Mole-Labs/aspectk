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
package io.github.molelabs.aspectk.core.fir.checkers

import io.github.molelabs.aspectk.core.AspectKClassIds
import io.github.molelabs.aspectk.core.AspectKDiagnostics
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFunctionChecker
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.name.StandardClassIds

internal object AdviceSignatureChecker : FirFunctionChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirFunction) {
        if (declaration.symbol !is FirNamedFunctionSymbol) return
        val session = context.session
        val isAround = declaration.hasAnnotation(AspectKClassIds.AROUND, session)
        if (!isAround && declaration.hasAdviceAnnotations(session)) return

        if (!checkDeclaredInAspect(declaration)) return
        checkParameter(declaration, isAround)
        if (isAround) checkAroundReturnType(declaration)
    }

    // Returns whether the advice sits in an @Aspect class, and warns if it doesn't.
    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkDeclaredInAspect(declaration: FirFunction): Boolean {
        val container = context.containingDeclarations.lastOrNull { it != declaration.symbol }
        if (container is FirRegularClassSymbol && container.hasAnnotation(
                AspectKClassIds.ASPECT,
                context.session,
            )
        ) {
            return true
        }
        reporter.reportOn(declaration.source, AspectKDiagnostics.ADVICE_OUTSIDE_ASPECT, context)
        return false
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkParameter(declaration: FirFunction, isAround: Boolean) {
        val parameterType =
            declaration.valueParameters.singleOrNull()?.returnTypeRef?.coneType?.classId
        val isSuspend = declaration.status.isSuspend
        val (accepted, expected) =
            when {
                isAround && isSuspend -> SUSPEND_AROUND_PARAMETERS to "SuspendProceedingJoinPoint"
                isAround -> AROUND_PARAMETERS to "ProceedingJoinPoint (declare the advice suspend to take a SuspendProceedingJoinPoint)"
                else -> JOIN_POINT_PARAMETERS to "JoinPoint"
            }

        // The extension function must not be used either
        if (declaration.receiverParameter == null && parameterType in accepted) return
        reporter.reportOn(
            declaration.source,
            AspectKDiagnostics.ADVICE_INVALID_PARAMETER,
            expected,
            context,
        )
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkAroundReturnType(declaration: FirFunction) {
        if (declaration.returnTypeRef.coneType.classId == StandardClassIds.Any) return
        reporter.reportOn(
            declaration.source,
            AspectKDiagnostics.AROUND_INVALID_RETURN_TYPE,
            context,
        )
    }

    private fun FirFunction.hasAdviceAnnotations(session: FirSession): Boolean = AspectKClassIds.ADVICE_ANNOTATIONS.none {
        hasAnnotation(it, session)
    }

    private val JOIN_POINT_PARAMETERS = setOf(AspectKClassIds.JOIN_POINT, StandardClassIds.Any)
    private val AROUND_PARAMETERS = JOIN_POINT_PARAMETERS + AspectKClassIds.PROCEEDING_JOIN_POINT
    private val SUSPEND_AROUND_PARAMETERS = JOIN_POINT_PARAMETERS + AspectKClassIds.SUSPEND_PROCEEDING_JOIN_POINT
}
