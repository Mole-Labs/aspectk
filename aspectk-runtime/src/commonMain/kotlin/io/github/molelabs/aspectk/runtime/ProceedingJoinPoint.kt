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
package io.github.molelabs.aspectk.runtime

/**
 * Extends [JoinPoint] with the ability to proceed to the intercepted function's body.
 *
 * A `ProceedingJoinPoint` is passed to [Around]-annotated advice functions. The advice
 * controls whether and how the original function body executes by calling [proceed].
 *
 * ### Usage
 * ```kotlin
 * @Around(target = [Timed::class])
 * fun measureTime(jp: ProceedingJoinPoint) {
 *     val start = System.currentTimeMillis()
 *     jp.proceed()
 *     println("Elapsed: ${System.currentTimeMillis() - start} ms")
 * }
 * ```
 *
 * @see Around
 * @see io.github.molelabs.aspectk.runtime.internal.DefaultProceedingJoinPoint
 */
public interface ProceedingJoinPoint : JoinPoint {
    /**
     * Proceeds to the intercepted function body with the original arguments.
     *
     * @return the return value of the original function, or `null` for `Unit`-returning functions.
     */
    public fun proceed(): Any?

    /**
     * Proceeds to the intercepted function body with substituted arguments.
     *
     * The provided [args] replace the original function's parameters in declaration order.
     * For methods, the dispatch receiver is captured from the call site and cannot be
     * substituted via this overload.
     *
     * @param args replacement values for the target function's parameters.
     * @return the return value of the original function, or `null` for `Unit`-returning functions.
     */
    public fun proceed(vararg args: Any?): Any?

    /**
     * SAM interface used by the AspectK compiler plugin to bridge the wrapper lambda
     * generated at each intercepted call site with [proceed].
     *
     * User code should not implement or reference this interface directly.
     * Implement [ProceedingJoinPoint] and call [proceed] instead.
     */
    public fun interface OnProceedListener {
        /**
         * Invokes the original function body with the supplied [args].
         *
         * @param args the argument list to forward to the intercepted function, in declaration order.
         * @return the return value of the original function, or `null` for `Unit`-returning functions.
         */
        public fun onProceed(args: List<Any?>): Any?
    }
}
