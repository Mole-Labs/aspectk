package com.mole.core

import com.mole.runtime.AnnotationInfo
import com.mole.runtime.MethodParameter
import com.mole.runtime.MethodSignature
import org.jetbrains.annotations.NotNull
import java.net.URLClassLoader
import kotlin.reflect.KClass

@Suppress("UNCHECKED_CAST")
fun singleField(loader: URLClassLoader) =
    MethodSignature(
        methodName = "test1",
        annotations =
            listOf(
                AnnotationInfo(
                    type = loader.loadClass("TargetExample").kotlin as KClass<out Annotation>,
                    typeName = "TargetExample",
                    arguments = mapOf("name" to "example1"),
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
fun singleFieldWithMethodArgs(loader: URLClassLoader) =
    MethodSignature(
        methodName = "test1",
        annotations =
            listOf(
                AnnotationInfo(
                    type = loader.loadClass("TargetExample").kotlin as KClass<out Annotation>,
                    typeName = "TargetExample",
                    arguments = mapOf("name" to "example1"),
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
                                arguments = mapOf("value" to "test"),
                            ),
                        ),
                    isNullable = false,
                ),
            ),
        returnType = Unit::class,
        returnTypeName = "kotlin.Unit",
    )

@Suppress("UNCHECKED_CAST")
fun doubleFieldWithMethodArgs(loader: URLClassLoader) =
    MethodSignature(
        methodName = "test1",
        annotations =
            listOf(
                AnnotationInfo(
                    type = loader.loadClass("TargetExample").kotlin as KClass<out Annotation>,
                    typeName = "TargetExample",
                    arguments = mapOf("name" to "example1"),
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
                arguments = mapOf("name" to "example1"),
            ),
        ),
    parameter =
        listOf(
            loader.thisParameterInfo(className),
        ),
    returnType = Unit::class,
    returnTypeName = "kotlin.Unit",
)
