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
package io.github.molelabs.aspectk.core.ir

import io.github.molelabs.aspectk.core.thisParameterInfo
import io.github.molelabs.aspectk.runtime.AnnotationInfo
import io.github.molelabs.aspectk.runtime.MethodParameter
import io.github.molelabs.aspectk.runtime.MethodSignature
import org.jetbrains.annotations.NotNull
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
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

@Suppress("UNCHECKED_CAST")
fun singleFieldWithNoAnnotationArgs(
    loader: URLClassLoader,
    methodName: String,
    annotationName: String,
) = MethodSignature(
    methodName = methodName,
    annotations =
    listOf(
        AnnotationInfo(
            type = loader.loadClass(annotationName).kotlin as KClass<out Annotation>,
            typeName = annotationName,
            args = listOf(),
            parameterNames = listOf(),
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
fun singleFieldWithMultipleAnnotations(
    loader: URLClassLoader,
    methodName: String,
    vararg annotationNames: String,
) = MethodSignature(
    methodName = methodName,
    annotations =
    annotationNames.map { name ->
        AnnotationInfo(
            type = loader.loadClass(name).kotlin as KClass<out Annotation>,
            typeName = name,
            args = listOf(),
            parameterNames = listOf(),
        )
    },
    parameter =
    listOf(
        loader.thisParameterInfo(),
    ),
    returnType = Unit::class,
    returnTypeName = "kotlin.Unit",
)

@Suppress("UNCHECKED_CAST")
fun singleFieldWithNoThisParameter(
    loader: URLClassLoader,
    methodName: String,
    vararg annotationNames: String,
) = MethodSignature(
    methodName = methodName,
    annotations =
    annotationNames.map { name ->
        AnnotationInfo(
            type = loader.loadClass(name).kotlin as KClass<out Annotation>,
            typeName = name,
            args = listOf(),
            parameterNames = listOf(),
        )
    },
    parameter =
    listOf(),
    returnType = Unit::class,
    returnTypeName = "kotlin.Unit",
)

@OptIn(ExperimentalCompilerApi::class)
@Suppress("UNCHECKED_CAST")
fun doubleFieldWithMethodArgs(loader: ClassLoader) = MethodSignature(
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
    parameter = listOf(loader.thisParameterInfo()),
    returnType = Unit::class,
    returnTypeName = "kotlin.Unit",
)

@OptIn(ExperimentalCompilerApi::class)
@Suppress("UNCHECKED_CAST")
fun varargFunMethodSignature(loader: ClassLoader) = MethodSignature(
    methodName = "varargFun",
    annotations =
    listOf(
        AnnotationInfo(
            type = loader.loadClass("TargetExample").kotlin as KClass<out Annotation>,
            typeName = "TargetExample",
            args = listOf(),
            parameterNames = listOf(),
        ),
    ),
    parameter =
    listOf(
        loader.thisParameterInfo(),
        MethodParameter(
            name = "names",
            type = Array<String>::class,
            typeName = "kotlin.Array",
            annotations = listOf(),
            isNullable = false,
        ),
    ),
    returnType = Unit::class,
    returnTypeName = "kotlin.Unit",
)

@OptIn(ExperimentalCompilerApi::class)
@Suppress("UNCHECKED_CAST")
fun defaultParamFunMethodSignature(loader: ClassLoader) = MethodSignature(
    methodName = "defaultParamFun",
    annotations =
    listOf(
        AnnotationInfo(
            type = loader.loadClass("TargetExample").kotlin as KClass<out Annotation>,
            typeName = "TargetExample",
            args = listOf(),
            parameterNames = listOf(),
        ),
    ),
    parameter =
    listOf(
        loader.thisParameterInfo(),
        MethodParameter(
            name = "name",
            type = String::class,
            typeName = "kotlin.String",
            annotations = listOf(),
            isNullable = false,
        ),
    ),
    returnType = Unit::class,
    returnTypeName = "kotlin.Unit",
)

@OptIn(ExperimentalCompilerApi::class)
@Suppress("UNCHECKED_CAST")
fun returnsSuspendFunMethodSignature(loader: ClassLoader) = MethodSignature(
    methodName = "returnsSuspendFun",
    annotations =
    listOf(
        AnnotationInfo(
            type = loader.loadClass("TargetExample").kotlin as KClass<out Annotation>,
            typeName = "TargetExample",
            args = listOf(),
            parameterNames = listOf(),
        ),
    ),
    parameter = listOf(loader.thisParameterInfo()),
    returnType = Function1::class,
    returnTypeName = "kotlin.coroutines.SuspendFunction0",
)

@OptIn(ExperimentalCompilerApi::class)
@Suppress("UNCHECKED_CAST")
fun operatorPlusMethodSignature(loader: ClassLoader) = MethodSignature(
    methodName = "plus",
    annotations =
    listOf(
        AnnotationInfo(
            type = loader.loadClass("TargetExample").kotlin as KClass<out Annotation>,
            typeName = "TargetExample",
            args = listOf(),
            parameterNames = listOf(),
        ),
    ),
    parameter =
    listOf(
        MethodParameter(
            name = "<this>",
            type = loader.loadClass("Point").kotlin,
            typeName = "Point",
            annotations = listOf(),
            isNullable = false,
        ),
        MethodParameter(
            name = "other",
            type = loader.loadClass("Point").kotlin,
            typeName = "Point",
            annotations = listOf(),
            isNullable = false,
        ),
    ),
    returnType = loader.loadClass("Point").kotlin,
    returnTypeName = "Point",
)

@OptIn(ExperimentalCompilerApi::class)
@Suppress("UNCHECKED_CAST")
fun derivedClassWorkMethodSignature(loader: ClassLoader) = MethodSignature(
    methodName = "work",
    annotations = listOf(),
    parameter = listOf(loader.thisParameterInfo("Derived")),
    returnType = Unit::class,
    returnTypeName = "kotlin.Unit",
)

@OptIn(ExperimentalCompilerApi::class)
@Suppress("UNCHECKED_CAST")
fun baseClassWorkMethodSignature(
    loader: ClassLoader,
    targetName: String = "TargetExample",
    className: String = "Base",
) = derivedClassWorkMethodSignature(loader).copy(
    parameter = listOf(loader.thisParameterInfo(className)),
    annotations =
    listOf(
        AnnotationInfo(
            type = loader.loadClass(targetName).kotlin as KClass<out Annotation>,
            typeName = targetName,
            args = listOf(),
            parameterNames = listOf(),
        ),
    ),
)

@OptIn(ExperimentalCompilerApi::class)
@Suppress("UNCHECKED_CAST")
fun companionObjectWorkMethodSignature(loader: ClassLoader) = MethodSignature(
    methodName = "work",
    annotations =
    listOf(
        AnnotationInfo(
            type = loader.loadClass("TargetExample").kotlin as KClass<out Annotation>,
            typeName = "TargetExample",
            args = listOf(),
            parameterNames = listOf(),
        ),
    ),
    parameter =
    listOf(
        MethodParameter(
            name = "<this>",
            type = loader.loadClass("MyClassWithCompanion\$Companion").kotlin,
            typeName = "MyClassWithCompanion.Companion",
            annotations = listOf(),
            isNullable = false,
        ),
    ),
    returnType = Unit::class,
    returnTypeName = "kotlin.Unit",
)

@OptIn(ExperimentalCompilerApi::class)
@Suppress("UNCHECKED_CAST")
fun doubleFieldWithMethodArgsCopy(loader: ClassLoader) = doubleFieldWithMethodArgs(loader).copy(
    methodName = "test2",
    annotations =
    listOf(
        AnnotationInfo(
            type = loader.loadClass("TargetExample").kotlin as KClass<out Annotation>,
            typeName = "TargetExample",
            args = listOf("example2"),
            parameterNames = listOf("name"),
        ),
    ),
)

@OptIn(ExperimentalCompilerApi::class)
@Suppress("UNCHECKED_CAST")
fun singleGenericField(
    loader: URLClassLoader,
    type: KClass<*> = Any::class,
    typeName: String = "kotlin.Any",
    nullable: Boolean = false,
    className: String = "Test",
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
        MethodParameter(
            name = "arg1",
            type = type,
            typeName = typeName,
            annotations = listOf(),
            isNullable = nullable,
        ),
    ),
    returnType = Unit::class,
    returnTypeName = "kotlin.Unit",
)

@OptIn(ExperimentalCompilerApi::class)
@Suppress("UNCHECKED_CAST")
fun doubleGenericField(
    loader: URLClassLoader,
    type: KClass<*> = Any::class,
    typeName: String = "kotlin.Any",
    nullable: Boolean = false,
) = singleField(loader).copy(
    parameter =
    listOf(
        loader.thisParameterInfo(),
        MethodParameter(
            name = "arg1",
            type = type,
            typeName = typeName,
            annotations = listOf(),
            isNullable = nullable,
        ),
        MethodParameter(
            name = "arg2",
            type = List::class,
            typeName = "kotlin.collections.List",
            annotations = listOf(),
            isNullable = false,
        ),
    ),
)

@OptIn(ExperimentalCompilerApi::class)
@Suppress("UNCHECKED_CAST")
fun stringConsumerConsumeMethodSignature(
    loader: ClassLoader,
    className: String = "StringConsumer",
) = MethodSignature(
    methodName = "consume",
    annotations = listOf(),
    parameter =
    listOf(
        loader.thisParameterInfo(className),
        MethodParameter(
            name = "item",
            type = String::class,
            typeName = "kotlin.String",
            annotations = listOf(),
            isNullable = false,
        ),
    ),
    returnType = Unit::class,
    returnTypeName = "kotlin.Unit",
)

@OptIn(ExperimentalCompilerApi::class)
@Suppress("UNCHECKED_CAST")
fun stringProducerProduceMethodSignature(
    loader: ClassLoader,
    className: String = "StringProducer",
) = MethodSignature(
    methodName = "produce",
    annotations = listOf(),
    parameter = listOf(loader.thisParameterInfo(className)),
    returnType = String::class,
    returnTypeName = "kotlin.String",
)

@OptIn(ExperimentalCompilerApi::class)
@Suppress("UNCHECKED_CAST")
fun complexGenericMethodSignature(
    loader: ClassLoader,
    className: String = "TestClass",
) = MethodSignature(
    methodName = "process",
    annotations =
    listOf(
        AnnotationInfo(
            type = loader.loadClass("TargetExample").kotlin as KClass<out Annotation>,
            typeName = "TargetExample",
            args = listOf("process"),
            parameterNames = listOf("name"),
        ),
    ),
    parameter =
    listOf(
        loader.thisParameterInfo(className),
        MethodParameter(
            name = "input",
            type = List::class,
            typeName = "java.util.List",
            annotations = listOf(),
            isNullable = false,
        ),
        MethodParameter(
            name = "output",
            type = Map::class,
            typeName = "java.util.Map",
            annotations = listOf(),
            isNullable = false,
        ),
    ),
    returnType = Unit::class,
    returnTypeName = "kotlin.Unit",
)
