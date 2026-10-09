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
 * Proceeds with the original arguments and returns the result cast to [T].
 *
 * ### Example
 * ```kotlin
 * @Around(Scaled::class)
 * fun doAround(pjp: ProceedingJoinPoint): Any? = pjp.proceedAs<Int>() * 2
 * ```
 *
 * @throws ClassCastException if the result cannot be cast to [T].
 */
public inline fun <reified T> ProceedingJoinPoint.proceedAs(): T = proceed() as T

/**
 * Proceeds with substituted arguments, as [ProceedingJoinPoint.proceed] does, and returns the
 * result cast to [T].
 *
 * @throws ClassCastException if the result cannot be cast to [T].
 */
public inline fun <reified T> ProceedingJoinPoint.proceedAs(vararg args: Any?): T = proceed(*args) as T

/**
 * Proceeds with the arguments named in [replacements] replaced and every other one kept.
 *
 * ### Example
 * ```kotlin
 * @Around(Normalized::class)
 * fun doAround(pjp: ProceedingJoinPoint): Any? =
 *     pjp.proceedWith("name" to pjp.getArg<String>("name").trim())
 * ```
 *
 * @throws NoSuchElementException if a name is not a parameter of the intercepted function.
 */
public fun ProceedingJoinPoint.proceedWith(vararg replacements: Pair<String, Any?>): Any? = proceed(*argsWith(replacements))

/**
 * Proceeds with the original arguments and returns the result cast to [T].
 *
 * @throws ClassCastException if the result cannot be cast to [T].
 */
public suspend inline fun <reified T> SuspendProceedingJoinPoint.proceedAs(): T = proceed() as T

/**
 * Proceeds with substituted arguments, as [SuspendProceedingJoinPoint.proceed] does, and returns
 * the result cast to [T].
 *
 * @throws ClassCastException if the result cannot be cast to [T].
 */
public suspend inline fun <reified T> SuspendProceedingJoinPoint.proceedAs(vararg args: Any?): T = proceed(*args) as T

/**
 * Proceeds with the arguments named in [replacements] replaced and every other one kept.
 *
 * @throws NoSuchElementException if a name is not a parameter of the intercepted function.
 */
public suspend fun SuspendProceedingJoinPoint.proceedWith(vararg replacements: Pair<String, Any?>): Any? = proceed(*argsWith(replacements))

// The full argument list with [replacements] applied. Passing every slot to proceed(vararg)
// leaves nothing for it to align, so the receiver slots are written back unchanged.
private fun JoinPoint.argsWith(replacements: Array<out Pair<String, Any?>>): Array<Any?> {
    val replaced = args.toTypedArray()
    replacements.forEach { (name, value) ->
        val index = signature.parameter.indexOfFirst { it.name == name }
        if (index < 0) throw NoSuchElementException("No parameter named '$name' in ${signature.methodName}")
        replaced[index] = value
    }
    return replaced
}
