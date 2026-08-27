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
 * The `suspend` counterpart of [ProceedingJoinPoint].
 *
 * When an [Around]-annotated advice targets a `suspend` function, the advice must itself be
 * declared `suspend` and accept a `SuspendProceedingJoinPoint` instead of a [ProceedingJoinPoint],
 * because [proceed] resumes the original suspending body and therefore has to suspend too.
 *
 * ### Usage
 * ```kotlin
 * @Around(target = [Timed::class])
 * suspend fun measureTime(jp: SuspendProceedingJoinPoint): Any? {
 *     val start = currentTimeMillis()
 *     val result = jp.proceed()
 *     println("Elapsed: ${currentTimeMillis() - start} ms")
 *     return result
 * }
 * ```
 *
 * A non-suspending target still uses [ProceedingJoinPoint]; the AspectK compiler plugin picks
 * the right join point type based on whether the intercepted function is `suspend`.
 *
 * @see Around
 * @see ProceedingJoinPoint
 * @see io.github.molelabs.aspectk.runtime.internal.DefaultSuspendProceedingJoinPoint
 */
public interface SuspendProceedingJoinPoint : JoinPoint {
    /**
     * Proceeds to the intercepted function body with the original arguments.
     *
     * @return the return value of the original function, or `null` for `Unit`-returning functions.
     */
    public suspend fun proceed(): Any?

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
    public suspend fun proceed(vararg args: Any?): Any?

    /**
     * SAM interface used by the AspectK compiler plugin to bridge the wrapper lambda
     * generated at each intercepted call site with [proceed].
     *
     * User code should not implement or reference this interface directly.
     * Implement [SuspendProceedingJoinPoint] and call [proceed] instead.
     */
    public fun interface SuspendOnProceedListener {
        /**
         * Invokes the original function body with the supplied [args].
         *
         * @param args the argument list to forward to the intercepted function, in declaration order.
         * @return the return value of the original function, or `null` for `Unit`-returning functions.
         */
        public suspend fun onProceed(args: List<Any?>): Any?
    }
}
