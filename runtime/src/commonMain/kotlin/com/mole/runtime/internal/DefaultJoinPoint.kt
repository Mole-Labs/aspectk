package com.mole.runtime.internal

import com.mole.runtime.JoinPoint
import com.mole.runtime.MethodSignature

public data class DefaultJoinPoint(
    override val args: List<Any?>,
    override val signature: MethodSignature,
    override val target: Any?
) : JoinPoint