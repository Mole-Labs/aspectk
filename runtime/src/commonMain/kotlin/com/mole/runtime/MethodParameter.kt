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
 * Describes a single parameter of an intercepted method.
 *
 * Instances are created at compile time by the AspectK compiler plugin and embedded in the
 * [MethodSignature] of each intercepted function. They give advice methods access to full
 * type and annotation metadata for every parameter of the target function.
 *
 * **Generic type erasure**: For parameters whose declared type is a generic type parameter
 * (e.g., `T`), [type] and [typeName] are resolved to the type's upper bound at compile
 * time (e.g., `kotlin.Any` for an unconstrained `T`, or the declared bound class for a
 * bounded `T : SomeClass`).
 *
 * @property name The parameter name as declared in source code.
 * @property type The erased [KClass] of the parameter type.
 * @property typeName The fully-qualified class name of the parameter type as a [String].
 *   Follows the same erasure rules as [type].
 * @property annotations Metadata for all annotations present on this parameter.
 * @property isNullable `true` if the parameter type is nullable (e.g., `String?`).
 */
public data class MethodParameter(
    val name: String,
    val type: KClass<*>,
    val typeName: String,
    val annotations: List<AnnotationInfo>,
    val isNullable: Boolean,
)
