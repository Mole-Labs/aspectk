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

import kotlin.reflect.KClass

/**
 * Marks a function inside an [Aspect]-annotated class as *around* advice.
 *
 * The annotated function **replaces** the target function's execution. The advice receives a
 * [ProceedingJoinPoint] and can call [ProceedingJoinPoint.proceed] to invoke the original
 * function body, optionally supplying different arguments. If [ProceedingJoinPoint.proceed]
 * is never called, the original body is skipped entirely.
 *
 * The advice function must declare exactly one parameter of type [ProceedingJoinPoint].
 *
 * ### Example
 * ```kotlin
 * @Around(target = [Timed::class])
 * fun measureTime(joinPoint: ProceedingJoinPoint) {
 *     val start = System.currentTimeMillis()
 *     joinPoint.proceed()
 *     println("Elapsed: ${System.currentTimeMillis() - start} ms")
 * }
 * ```
 *
 * @property target One or more annotation classes that identify the functions to intercept.
 * @property inherits When `true`, the advice is also applied to functions that **override**
 *   a function annotated with one of the [target] annotations, even if the overriding
 *   function itself is not directly annotated. Defaults to `false`.
 *
 * @see Aspect
 * @see ProceedingJoinPoint
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CONSTRUCTOR)
@Retention(AnnotationRetention.BINARY)
@MustBeDocumented
public annotation class Around(
    vararg val target: KClass<out Annotation>,
    val inherits: Boolean = false,
)
