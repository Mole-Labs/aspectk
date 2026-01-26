package com.mole.runtime

import kotlin.reflect.KClass

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CONSTRUCTOR)
@Retention(AnnotationRetention.BINARY) // 또는 RUNTIME
@MustBeDocumented
public annotation class Before(
    vararg val target: KClass<out Annotation>,
)
