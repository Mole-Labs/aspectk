package com.mole.runtime

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CONSTRUCTOR)
@Retention(AnnotationRetention.BINARY) // 또는 RUNTIME
@MustBeDocumented
public annotation class Aspect
