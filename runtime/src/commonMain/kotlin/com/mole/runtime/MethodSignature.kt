package com.mole.runtime

import kotlin.reflect.KClass

public data class MethodSignature(
    val methodName: String,
    val parameter: List<MethodParameter>,
    val returnType: KClass<*>,
    val returnTypeName: String,
)
