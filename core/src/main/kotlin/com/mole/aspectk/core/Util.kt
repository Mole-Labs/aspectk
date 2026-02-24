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
package com.mole.aspectk.core

import org.jetbrains.kotlin.util.suffixIfNot

internal const val REPORT_ASPECTK_MESSAGE =
    "This is possibly a bug in the AspectK compiler, please report it with details and/or a reproducer to https://github.com/Mole-Labs/aspectk."

internal fun reportCompilerBug(message: String): Nothing {
    error("${message.suffixIfNot(".")} $REPORT_ASPECTK_MESSAGE ")
}
