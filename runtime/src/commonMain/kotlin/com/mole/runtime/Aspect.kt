package com.mole.runtime

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY) // 또는 RUNTIME
@MustBeDocumented
public annotation class Aspect
