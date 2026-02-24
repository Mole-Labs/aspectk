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
package com.mole.aspectk.runtime

import kotlin.reflect.KClass

/**
 * Holds metadata about a single annotation instance present on a method or parameter.
 *
 * Instances are created at compile time by the AspectK compiler plugin and embedded
 * inside [MethodSignature] or [MethodParameter]. They allow advice methods to inspect
 * annotation details at runtime without incurring additional reflection overhead.
 *
 * Only arguments that are explicitly provided in source are included in [args]; omitted
 * optional arguments (those relying on their default values) are not present.
 *
 * @property type The [KClass] of the annotation.
 * @property typeName The fully-qualified class name of the annotation as a [String].
 * @property args The list of argument values supplied to the annotation, in declaration order.
 * @property parameterNames The names of the annotation parameters corresponding to each
 *   entry in [args], in the same order.
 */
public data class AnnotationInfo(
    public val type: KClass<out Annotation>,
    public val typeName: String,
    public val args: List<Any?>,
    public val parameterNames: List<String>,
)
