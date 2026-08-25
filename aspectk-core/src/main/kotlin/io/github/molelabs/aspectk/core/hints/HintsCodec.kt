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
package io.github.molelabs.aspectk.core.hints

import java.io.File

// Hand-rolled JSON codec restricted to the fixed HintRecord shape — no serialization
// library dependency (docs/design-decision/cross-module-weaving.md §4).
internal object HintsCodec {
    fun write(
        records: List<HintRecord>,
        file: File,
    ) {
        file.parentFile?.mkdirs()
        file.writeText(encode(records))
    }

    fun read(file: File): List<HintRecord> {
        if (!file.exists()) return emptyList()
        return decode(file.readText())
    }

    internal fun encode(records: List<HintRecord>): String = buildString {
        append('[')
        records.forEachIndexed { index, record ->
            if (index > 0) append(',')
            append('{')
            append("\"package\":").append(quote(record.packageName)).append(',')
            append("\"class\":").append(quote(record.className)).append(',')
            append("\"function\":").append(quote(record.functionName)).append(',')
            append("\"kind\":").append(quote(record.kind)).append(',')
            append("\"targets\":[").append(record.targets.joinToString(",") { quote(it) }).append("],")
            append("\"inherits\":").append(record.inherits)
            append('}')
        }
        append(']')
    }

    internal fun decode(json: String): List<HintRecord> {
        val records = mutableListOf<HintRecord>()
        var i = skipWhitespace(json, 0)
        require(i < json.length && json[i] == '[') { "Expected '[' at $i" }
        i = skipWhitespace(json, i + 1)
        while (i < json.length && json[i] != ']') {
            val (record, next) = parseObject(json, i)
            records.add(record)
            i = skipWhitespace(json, next)
            if (i < json.length && json[i] == ',') {
                i = skipWhitespace(json, i + 1)
            }
        }
        return records
    }

    private fun parseObject(
        json: String,
        start: Int,
    ): Pair<HintRecord, Int> {
        require(json[start] == '{') { "Expected '{' at $start" }
        var i = skipWhitespace(json, start + 1)
        var packageName = ""
        var className = ""
        var functionName = ""
        var kind = ""
        var targets = emptyList<String>()
        var inherits = false
        while (json[i] != '}') {
            val (key, afterKey) = parseString(json, i)
            i = skipWhitespace(json, afterKey)
            require(json[i] == ':') { "Expected ':' at $i" }
            i = skipWhitespace(json, i + 1)
            when (key) {
                "package" -> parseString(json, i).also {
                    packageName = it.first
                    i = it.second
                }

                "class" -> parseString(json, i).also {
                    className = it.first
                    i = it.second
                }

                "function" -> parseString(json, i).also {
                    functionName = it.first
                    i = it.second
                }

                "kind" -> parseString(json, i).also {
                    kind = it.first
                    i = it.second
                }

                "targets" -> parseStringArray(json, i).also {
                    targets = it.first
                    i = it.second
                }

                "inherits" -> parseBoolean(json, i).also {
                    inherits = it.first
                    i = it.second
                }

                else -> error("Unknown hint field '$key'")
            }
            i = skipWhitespace(json, i)
            if (json[i] == ',') i = skipWhitespace(json, i + 1)
        }
        return HintRecord(packageName, className, functionName, kind, targets, inherits) to (i + 1)
    }

    private fun parseStringArray(
        json: String,
        start: Int,
    ): Pair<List<String>, Int> {
        require(json[start] == '[') { "Expected '[' at $start" }
        var i = skipWhitespace(json, start + 1)
        val items = mutableListOf<String>()
        while (json[i] != ']') {
            val (value, next) = parseString(json, i)
            items.add(value)
            i = skipWhitespace(json, next)
            if (json[i] == ',') i = skipWhitespace(json, i + 1)
        }
        return items to (i + 1)
    }

    private fun parseBoolean(
        json: String,
        start: Int,
    ): Pair<Boolean, Int> = when {
        json.startsWith("true", start) -> true to (start + 4)
        json.startsWith("false", start) -> false to (start + 5)
        else -> error("Expected boolean at $start")
    }

    private fun parseString(
        json: String,
        start: Int,
    ): Pair<String, Int> {
        require(json[start] == '"') { "Expected '\"' at $start" }
        val sb = StringBuilder()
        var i = start + 1
        while (json[i] != '"') {
            if (json[i] == '\\') {
                i++
                when (json[i]) {
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    else -> sb.append(json[i])
                }
            } else {
                sb.append(json[i])
            }
            i++
        }
        return sb.toString() to (i + 1)
    }

    private fun quote(value: String): String = buildString {
        append('"')
        value.forEach { c ->
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\t' -> append("\\t")
                else -> append(c)
            }
        }
        append('"')
    }

    private fun skipWhitespace(
        json: String,
        start: Int,
    ): Int {
        var i = start
        while (i < json.length && json[i].isWhitespace()) i++
        return i
    }
}
