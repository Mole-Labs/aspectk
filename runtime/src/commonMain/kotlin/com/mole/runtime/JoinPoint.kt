package com.mole.runtime

public interface JoinPoint {
    public val target:Any?
    public val signature:MethodSignature
    public val args: List<Any?>
}