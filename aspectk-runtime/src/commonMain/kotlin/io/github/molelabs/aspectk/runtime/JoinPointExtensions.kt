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
 * Returns the argument value for the parameter named [name], cast to [T].
 *
 * ### Example
 * ```kotlin
 * @Around(target = [Transactional::class])
 * fun doAround(pjp: ProceedingJoinPoint): Any? {
 *     val userId = pjp.getArg<String>("userId")
 *     return pjp.proceed()
 * }
 * ```
 *
 * @throws NoSuchElementException if no parameter with [name] exists on the intercepted function.
 * @throws ClassCastException if the argument value cannot be cast to [T].
 */
public inline fun <reified T> JoinPoint.getArg(name: String): T {
    val index = signature.parameter.indexOfFirst { it.name == name }
    if (index < 0) throw NoSuchElementException("No parameter named '$name' in ${signature.methodName}")
    @Suppress("UNCHECKED_CAST")
    return args[index] as T
}

/**
 * Returns the argument value for the parameter named [name], cast to [T],
 * or `null` if no such parameter exists or the value cannot be cast to [T].
 *
 * ### Example
 * ```kotlin
 * @Before(target = [Logged::class])
 * fun doBefore(jp: JoinPoint) {
 *     val label = jp.getArgOrNull<String>("label") ?: "unknown"
 * }
 * ```
 */
public inline fun <reified T> JoinPoint.getArgOrNull(name: String): T? {
    val index = signature.parameter.indexOfFirst { it.name == name }
    if (index < 0) return null
    return args[index] as? T
}

/**
 * Returns [JoinPoint.target] cast to [T].
 *
 * Useful when advice code needs to interact with the receiver beyond the generic [Any?] type.
 *
 * ### Example
 * ```kotlin
 * @Before(target = [Audited::class])
 * fun doBefore(jp: JoinPoint) {
 *     val service = jp.getTarget<UserService>()
 *     service.recordAccess()
 * }
 * ```
 *
 * @throws ClassCastException if the target cannot be cast to [T].
 * @throws NullPointerException if the target is `null` (top-level or companion-object function).
 */
public inline fun <reified T> JoinPoint.getTarget(): T {
    @Suppress("UNCHECKED_CAST")
    return target as T
}

/**
 * Returns [JoinPoint.target] cast to [T], or `null` if the target is `null` or cannot
 * be cast to [T].
 */
public inline fun <reified T> JoinPoint.getTargetOrNull(): T? = target as? T

/**
 * Returns the [AnnotationInfo] for annotation [T] present on the intercepted function,
 * or `null` if no such annotation is present.
 *
 * ### Example
 * ```kotlin
 * @Before(target = [RateLimit::class])
 * fun doBefore(jp: JoinPoint) {
 *     val rateLimit = jp.findAnnotation<RateLimit>() ?: return
 *     val limit = rateLimit.getArg<Int>("maxCalls")
 * }
 * ```
 */
public inline fun <reified T : Annotation> JoinPoint.findAnnotation(): AnnotationInfo? = signature.findAnnotation<T>()
