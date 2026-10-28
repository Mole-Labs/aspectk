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
package io.github.molelabs.aspectk.core

import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.KtDiagnosticsContainer
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies
import org.jetbrains.kotlin.diagnostics.error0
import org.jetbrains.kotlin.diagnostics.error1
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.diagnostics.rendering.CommonRenderers
import org.jetbrains.kotlin.diagnostics.warning0
import org.jetbrains.kotlin.diagnostics.warning1
import org.jetbrains.kotlin.psi.KtElement

// TODO: the target-side diagnostics below are reported from IR (AspectTransformer), where the
//  advice-to-target mapping exists, so they only show up in Gradle builds, not in the IDE.
//  Moving them to FIR checkers needs the hint logic (same-module aspects, carried-forward and
//  external hints, resolve) migrated to FIR as well.
internal object AspectKDiagnostics : KtDiagnosticsContainer() {
    val ASPECT_NOT_OBJECT by error0<KtElement>(SourceElementPositioningStrategies.DECLARATION_NAME)

    val ADVICE_INVALID_PARAMETER by error1<KtElement, String>(SourceElementPositioningStrategies.DECLARATION_SIGNATURE)

    val AROUND_INVALID_RETURN_TYPE by error0<KtElement>(SourceElementPositioningStrategies.DECLARATION_RETURN_TYPE)

    val ADVICE_OUTSIDE_ASPECT by warning0<KtElement>(SourceElementPositioningStrategies.DECLARATION_NAME)

    // Target-side, reported from IR with the advice's name as the argument
    val AROUND_SUSPEND_MISMATCH by error1<KtElement, String>()

    val AROUND_INLINE_LAMBDA by error1<KtElement, String>()

    val TAILREC_ADVICE by warning1<KtElement, String>()

    override fun getRendererFactory(): BaseDiagnosticRendererFactory = AspectKRenderers

    private object AspectKRenderers : BaseDiagnosticRendererFactory() {
        @Suppress("ktlint:standard:property-naming")
        override val MAP by KtDiagnosticFactoryToRendererMap("AspectK") { map ->
            map.put(
                ASPECT_NOT_OBJECT,
                "[AspectK] An @Aspect must be an object declaration: advice is called on its single instance. Declare it with object instead of class.",
            )
            map.put(
                ADVICE_INVALID_PARAMETER,
                "[AspectK] Advice must take exactly one parameter, of type {0}, and no receiver.",
                CommonRenderers.STRING,
            )
            map.put(
                AROUND_INVALID_RETURN_TYPE,
                "[AspectK] @Around advice must return Any? or Any: its result becomes the return value of every target function.",
            )
            map.put(
                ADVICE_OUTSIDE_ASPECT,
                "[AspectK] This advice is never applied: advice is only collected from an @Aspect object.",
            )
            map.put(
                AROUND_SUSPEND_MISMATCH,
                "[AspectK] @Around advice {0} must be suspend and take a SuspendProceedingJoinPoint exactly when the target function is suspend.",
                CommonRenderers.STRING,
            )
            map.put(
                AROUND_INLINE_LAMBDA,
                "[AspectK] @Around advice {0} cannot wrap an inline function with inline or crossinline lambda parameters: the body moves into a lambda that cannot call them. Mark them noinline.",
                CommonRenderers.STRING,
            )
            map.put(
                TAILREC_ADVICE,
                "[AspectK] Advice {0} wraps a tailrec function: the recursive call is no longer a tail call, so it is not optimized and deep recursion can overflow the stack.",
                CommonRenderers.STRING,
            )
        }
    }
}
