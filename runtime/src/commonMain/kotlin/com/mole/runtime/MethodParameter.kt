package com.mole.runtime

import kotlin.reflect.KClass

public data class MethodParameter(
    val name: String,
    val type: KClass<*>,
    val typeName: String,
    val annotations: List<AnnotationInfo>,
    val isNullable: Boolean,
)
