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

/**
 * Marks a class or object as an AspectK aspect.
 *
 * The AspectK compiler plugin scans all classes annotated with `@Aspect` to discover
 * advice declarations. Inside an aspect class, use [Before] to define advice methods
 * that are injected at compile time before the body of any matching target function.
 *
 * Aspects are typically declared as Kotlin `object`s to avoid instantiation overhead,
 * but regular classes are also supported.
 *
 * ### Example
 * ```kotlin
 * @Aspect
 * object LoggingAspect {
 *     @Before(target = [Authenticated::class])
 *     fun log(joinPoint: JoinPoint) {
 *         println("Calling: ${joinPoint.signature.methodName}")
 *     }
 * }
 * ```
 *
 * @see Before
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY) // or RUNTIME
@MustBeDocumented
public annotation class Aspect
