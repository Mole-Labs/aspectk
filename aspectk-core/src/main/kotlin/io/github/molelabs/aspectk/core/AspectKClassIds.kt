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
package io.github.molelabs.aspectk.core

import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

// The aspectk-runtime classes the plugin refers to, shared by the FIR checkers and the IR weaving.
internal object AspectKClassIds {
    private val RUNTIME = FqName("io.github.molelabs.aspectk.runtime")
    private val RUNTIME_INTERNAL = FqName("io.github.molelabs.aspectk.runtime.internal")

    val ASPECT = runtime("Aspect")
    val BEFORE = runtime("Before")
    val AFTER = runtime("After")
    val AROUND = runtime("Around")
    val ADVICE_ANNOTATIONS = listOf(BEFORE, AFTER, AROUND)

    val JOIN_POINT = runtime("JoinPoint")
    val PROCEEDING_JOIN_POINT = runtime("ProceedingJoinPoint")
    val SUSPEND_PROCEEDING_JOIN_POINT = runtime("SuspendProceedingJoinPoint")
    val ON_PROCEED_LISTENER = PROCEEDING_JOIN_POINT.createNestedClassId(Name.identifier("OnProceedListener"))
    val SUSPEND_ON_PROCEED_LISTENER =
        SUSPEND_PROCEEDING_JOIN_POINT.createNestedClassId(Name.identifier("SuspendOnProceedListener"))

    val METHOD_SIGNATURE = runtime("MethodSignature")
    val METHOD_PARAMETER = runtime("MethodParameter")
    val ANNOTATION_INFO = runtime("AnnotationInfo")

    val DEFAULT_JOIN_POINT = ClassId(RUNTIME_INTERNAL, Name.identifier("DefaultJoinPoint"))
    val DEFAULT_PROCEEDING_JOIN_POINT = ClassId(RUNTIME_INTERNAL, Name.identifier("DefaultProceedingJoinPoint"))
    val DEFAULT_SUSPEND_PROCEEDING_JOIN_POINT =
        ClassId(RUNTIME_INTERNAL, Name.identifier("DefaultSuspendProceedingJoinPoint"))

    private fun runtime(name: String) = ClassId(RUNTIME, Name.identifier(name))
}
