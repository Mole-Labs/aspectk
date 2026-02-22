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
package com.mole.runtime

import kotlin.reflect.KClass

/**
 * Marks a function inside an [Aspect]-annotated class as *before* advice.
 *
 * The annotated function is invoked at every call site of any function carrying one of the
 * [target] annotations, **before** the target function body executes. The injection is
 * performed at compile time by the AspectK compiler plugin.
 *
 * The advice function must declare exactly one parameter of type [JoinPoint].
 *
 * Multiple [target] annotations may be listed; the advice will be applied to all of them.
 * Multiple `@Before` declarations may also share the same target, enabling many-to-many
 * relationships between targets and advice.
 *
 * ### Example
 * ```kotlin
 * @Before(target = [Authenticated::class, Logged::class])
 * fun checkAndLog(joinPoint: JoinPoint) { ... }
 * ```
 *
 * @property target One or more annotation classes that identify the functions to intercept.
 * @property inherits When `true`, the advice is also applied to functions that **override**
 *   a function annotated with one of the [target] annotations, even if the overriding
 *   function itself is not directly annotated. Defaults to `false`.
 *
 * @see Aspect
 * @see JoinPoint
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CONSTRUCTOR)
@Retention(AnnotationRetention.BINARY)
@MustBeDocumented
public annotation class Before(
    vararg val target: KClass<out Annotation>,
    val inherits: Boolean = false,
)
