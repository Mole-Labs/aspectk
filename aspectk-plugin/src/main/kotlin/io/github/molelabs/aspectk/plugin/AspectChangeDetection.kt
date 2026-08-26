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
package io.github.molelabs.aspectk.plugin

import java.io.File

// Every annotation whose presence, absence, or arguments change what AspectTransformer must
// inject into a target function -- mirrors AspectKIrCompilerContext.ADVICE_ANNOTATIONS_FQ_NAME
// (Before/After/Around) plus the @Aspect class annotation itself.
internal val ASPECT_RELEVANT_MARKERS = listOf("@Aspect", "@Before", "@After", "@Around")

// ponytail: a plain regex pass, not a real lexer -- doesn't know about string literals, and
// nested block comments only get stripped up to their first "*/". Both are safe in the direction
// that matters: they can only leave MORE text behind (never hide a real marker), so the worst
// case is treating a file as relevant when it isn't, never the reverse. Upgrade to real lexing
// only if that ever costs enough to matter.
private val LINE_COMMENT = Regex("//.*")
private val BLOCK_COMMENT = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)

private fun stripComments(text: String): String = text.replace(BLOCK_COMMENT, "").replace(LINE_COMMENT, "")

// Whether this file's comment-stripped text mentions any of ASPECT_RELEVANT_MARKERS -- used by
// DetectAspectChangeTask to decide, per actually-changed file, whether an incremental round needs
// a full (non-incremental) recompile. See docs/design-decision/cross-module-weaving.md.
internal fun fileHasAspectMarker(file: File): Boolean {
    val text = stripComments(file.readText())
    return ASPECT_RELEVANT_MARKERS.any { marker -> marker in text }
}
