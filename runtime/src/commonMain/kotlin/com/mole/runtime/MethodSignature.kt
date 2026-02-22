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
 * Compile-time metadata describing the signature of an intercepted method.
 *
 * A single `MethodSignature` instance is generated per intercepted function and stored as
 * a static field in a synthetic inner object that the AspectK compiler plugin adds to the
 * enclosing class or file. This ensures that signature metadata is allocated once per
 * function definition, not once per invocation.
 *
 * **Generic type erasure**: When a function's return type is a generic type parameter,
 * [returnType] and [returnTypeName] are resolved to the upper bound at compile time.
 *
 * @property methodName The simple name of the intercepted function.
 * @property annotations Metadata for all annotations present on the intercepted function.
 * @property parameter The ordered list of [MethodParameter] descriptors, one per parameter
 *   in declaration order.
 * @property returnType The erased [KClass] of the function's return type.
 * @property returnTypeName The fully-qualified class name of the return type as a [String].
 */
public data class MethodSignature(
    val methodName: String,
    val annotations: List<AnnotationInfo>,
    val parameter: List<MethodParameter>,
    val returnType: KClass<*>,
    val returnTypeName: String,
)
