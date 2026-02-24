package com.mole.sample.aspects

import com.mole.aspectk.runtime.Aspect
import com.mole.aspectk.runtime.Before
import com.mole.aspectk.runtime.JoinPoint
import com.mole.sample.annotations.Trace

/**
 * [Trace] 어노테이션이 붙은 함수 호출을 계층적으로 추적합니다.
 *
 * 호출 깊이(depth)에 따라 들여쓰기하여 함수 호출 트리를 시각화합니다.
 *
 * 출력 예:
 * ```
 * [TRACE] → processOrder (depth=1)
 * [TRACE]   → validatePayment (depth=2)
 * [TRACE]     → chargeCard (depth=3)
 * ```
 *
 * 사용 예:
 * ```kotlin
 * @Trace(spanName = "validate-payment")
 * fun validatePayment(orderId: String) { ... }
 * ```
 */
@Aspect
object TracingAspect {
    /** 현재 추적 중인 스팬 스택. 들여쓰기 깊이 계산에 사용됩니다. */
    val callStack = mutableListOf<String>()

    /** 트레이스 출력 핸들러. 테스트에서 오버라이드할 수 있습니다. */
    var logger: (String) -> Unit = { message -> println(message) }

    @Before(Trace::class)
    fun trace(joinPoint: JoinPoint) {
        val annotationInfo =
            joinPoint.signature.annotations.firstOrNull {
                it.typeName.contains("Trace")
            }
        val spanName =
            annotationInfo
                ?.let { info ->
                    val idx = info.parameterNames.indexOf("spanName")
                    (info.args.getOrNull(idx) as? String)?.takeIf { it.isNotEmpty() }
                } ?: joinPoint.signature.methodName

        val depth = callStack.size
        val indent = "  ".repeat(depth)
        callStack.add(spanName)

        val message = "[TRACE] $indent→ $spanName (depth=${depth + 1})"
        logger(message)
    }

    /** 스택을 초기화합니다. 각 테스트 케이스 시작 전에 호출하세요. */
    fun clearStack() = callStack.clear()
}
