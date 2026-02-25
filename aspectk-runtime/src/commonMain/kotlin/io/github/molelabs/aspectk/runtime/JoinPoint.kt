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
 * Provides contextual information about an intercepted function call.
 *
 * A `JoinPoint` instance is constructed by the AspectK compiler plugin at each intercepted
 * call site and passed to every matching advice function. It exposes the receiver object,
 * static method-signature metadata, and the runtime arguments of the call.
 *
 * The concrete implementation injected at runtime is
 * [io.github.molelabs.aspectk.runtime.internal.DefaultJoinPoint]. Advice code should program to this
 * interface rather than the concrete type.
 *
 * ### Usage in advice
 * ```kotlin
 * @Before(target = [Authenticated::class])
 * fun checkAuth(joinPoint: JoinPoint) {
 *     val receiver = joinPoint.target
 *     val name    = joinPoint.signature.methodName
 *     val firstArg = joinPoint.args.firstOrNull()
 * }
 * ```
 *
 * @see io.github.molelabs.aspectk.runtime.internal.DefaultJoinPoint
 * @see MethodSignature
 */
public interface JoinPoint {
    /**
     * The receiver object on which the intercepted function is called,
     * or `null` for top-level or static functions.
     */
    public val target: Any?

    /**
     * Compile-time metadata describing the intercepted function,
     * including its name, annotations, and parameter list.
     */
    public val signature: MethodSignature

    /** The runtime arguments passed to the intercepted function, in declaration order. */
    public val args: List<Any?>
}
