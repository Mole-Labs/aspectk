package com.mole.core

import org.jetbrains.kotlin.util.suffixIfNot

internal const val REPORT_ASPECTK_MESSAGE =
    "This is possibly a bug in the AspectK compiler, please report it with details and/or a reproducer to https://github.com/Mole-Labs/aspectk."

internal fun reportCompilerBug(message: String): Nothing {
    error("${message.suffixIfNot(".")} $REPORT_ASPECTK_MESSAGE ")
}
