package com.mole.runtime.internal

import com.mole.runtime.JoinPoint
import com.mole.runtime.MethodSignature

public data class DefaultJoinPoint(
    override val target: Any?,
    override val signature: MethodSignature,
    override val args: List<Any?>,
) : JoinPoint
