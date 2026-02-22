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
package com.mole.tests

import com.mole.runtime.Aspect
import com.mole.runtime.Before
import com.mole.runtime.JoinPoint
import kotlin.reflect.KClass

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_SETTER)
annotation class ExpectTarget(
    val name: String,
)

@Aspect
object ExpectAspect {
    var executed: Boolean = false
    var size: Int = -1
    var arg1: String = ""
    var type: KClass<*>? = null

    @Before(ExpectTarget::class)
    fun doBefore(joinPoint: JoinPoint) {
        executed = true
        size = joinPoint.args.size
        type = if (joinPoint.target != null) joinPoint.target!!::class else null
        arg1 = joinPoint.args[0] as String
    }
}

@ExpectTarget("example1")
expect fun expectRun(arg1: String)
