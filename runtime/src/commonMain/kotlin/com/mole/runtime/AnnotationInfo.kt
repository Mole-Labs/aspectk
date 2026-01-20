package com.mole.runtime

import kotlin.reflect.KClass

public data class AnnotationInfo(
    public val type: KClass<out Annotation>,
    public val typeName: String,
    public val arguments: Map<String, Any?>,
)
