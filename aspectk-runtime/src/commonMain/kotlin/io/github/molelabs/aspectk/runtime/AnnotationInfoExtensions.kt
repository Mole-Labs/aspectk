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
 * Returns the annotation argument value for the parameter named [paramName], cast to [T].
 *
 * ### Example
 * ```kotlin
 * @Before(target = [RateLimit::class])
 * fun doBefore(jp: JoinPoint) {
 *     val info = jp.findAnnotation<RateLimit>()!!
 *     val maxCalls = info.getArg<Int>("maxCalls")
 * }
 * ```
 *
 * @throws NoSuchElementException if no parameter with [paramName] exists in the annotation.
 * @throws ClassCastException if the argument value cannot be cast to [T].
 */
public inline fun <reified T> AnnotationInfo.getArg(paramName: String): T {
    val index = parameterNames.indexOf(paramName)
    if (index < 0) throw NoSuchElementException("No parameter named '$paramName' in annotation $typeName")
    @Suppress("UNCHECKED_CAST")
    return args[index] as T
}

/**
 * Returns the annotation argument value for the parameter named [paramName], cast to [T],
 * or `null` if no such parameter exists or the value cannot be cast to [T].
 */
public inline fun <reified T> AnnotationInfo.getArgOrNull(paramName: String): T? {
    val index = parameterNames.indexOf(paramName)
    if (index < 0) return null
    return args[index] as? T
}
