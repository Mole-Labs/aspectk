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
package com.mole.core

import com.mole.runtime.AnnotationInfo
import com.mole.runtime.MethodParameter
import com.mole.runtime.MethodSignature
import org.jetbrains.annotations.NotNull
import java.net.URLClassLoader
import kotlin.reflect.KClass

@Suppress("UNCHECKED_CAST")
fun singleField(loader: URLClassLoader) = MethodSignature(
    methodName = "test1",
    annotations =
    listOf(
        AnnotationInfo(
            type = loader.loadClass("TargetExample").kotlin as KClass<out Annotation>,
            typeName = "TargetExample",
            args = listOf("example1"),
            parameterNames = listOf("name"),
        ),
    ),
    parameter =
    listOf(
        loader.thisParameterInfo(),
    ),
    returnType = Unit::class,
    returnTypeName = "kotlin.Unit",
)

@Suppress("UNCHECKED_CAST")
fun singleFieldWithMethodArgs(loader: URLClassLoader) = MethodSignature(
    methodName = "test1",
    annotations =
    listOf(
        AnnotationInfo(
            type = loader.loadClass("TargetExample").kotlin as KClass<out Annotation>,
            typeName = "TargetExample",
            args = listOf("example1"),
            parameterNames = listOf("name"),
        ),
    ),
    parameter =
    listOf(
        loader.thisParameterInfo(),
        MethodParameter(
            name = "arg1",
            type = Int::class,
            typeName = "kotlin.Int",
            annotations = listOf(),
            isNullable = true,
        ),
        MethodParameter(
            name = "arg2",
            type = String::class,
            typeName = "kotlin.String",
            annotations =
            listOf(
                AnnotationInfo(
                    type = NotNull::class,
                    typeName = "org.jetbrains.annotations.NotNull",
                    args = listOf("test"),
                    parameterNames = listOf("value"),
                ),
            ),
            isNullable = false,
        ),
    ),
    returnType = Unit::class,
    returnTypeName = "kotlin.Unit",
)

@Suppress("UNCHECKED_CAST")
fun doubleFieldWithMethodArgs(loader: URLClassLoader) = MethodSignature(
    methodName = "test1",
    annotations =
    listOf(
        AnnotationInfo(
            type = loader.loadClass("TargetExample").kotlin as KClass<out Annotation>,
            typeName = "TargetExample",
            args = listOf("example1"),
            parameterNames = listOf("name"),
        ),
    ),
    parameter =
    listOf(
        loader.thisParameterInfo(),
    ),
    returnType = Unit::class,
    returnTypeName = "kotlin.Unit",
)

@Suppress("UNCHECKED_CAST")
fun singleFieldWithDoubleClass(
    loader: URLClassLoader,
    className: String,
) = MethodSignature(
    methodName = "test1",
    annotations =
    listOf(
        AnnotationInfo(
            type = loader.loadClass("TargetExample").kotlin as KClass<out Annotation>,
            typeName = "TargetExample",
            args = listOf("example1"),
            parameterNames = listOf("name"),
        ),
    ),
    parameter =
    listOf(
        loader.thisParameterInfo(className),
    ),
    returnType = Unit::class,
    returnTypeName = "kotlin.Unit",
)
