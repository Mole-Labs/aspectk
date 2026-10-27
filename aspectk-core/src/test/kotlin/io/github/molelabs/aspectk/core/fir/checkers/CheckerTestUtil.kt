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
package io.github.molelabs.aspectk.core.fir.checkers

import com.tschuchort.compiletesting.JvmCompilationResult
import io.github.molelabs.aspectk.core.compile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

private val HEADER =
    """
    import io.github.molelabs.aspectk.runtime.*

    annotation class Logged
    """.trimIndent()

/** Compiles [body] with the runtime imported and a `Logged` target annotation declared. */
@OptIn(ExperimentalCompilerApi::class)
internal fun check(body: String): JvmCompilationResult = compile("$HEADER\n$body")

@OptIn(ExperimentalCompilerApi::class)
internal fun JvmCompilationResult.count(message: String): Int = Regex(Regex.escape(message)).findAll(messages).count()
