package sample.aspects

import io.github.molelabs.aspectk.runtime.Aspect
import io.github.molelabs.aspectk.runtime.Before
import io.github.molelabs.aspectk.runtime.JoinPoint
import sample.annotations.PreventDoubleClick
import sample.exceptions.DoubleClickException
import sample.platform.currentTimeMillis

/**
 * [PreventDoubleClick] 어노테이션이 붙은 함수의 중복(빠른 연속) 호출을 방지합니다.
 *
 * 각 함수별 마지막 호출 시각을 기록하고, 쿨다운 시간 이내에 재호출되면
 * [DoubleClickException]을 던져 함수 실행을 차단합니다.
 *
 * 사용 예:
 * ```kotlin
 * @PreventDoubleClick(cooldownMs = 500L)
 * fun onSubmitButtonClick() { ... }
 * // 500ms 이내 재호출 시 → DoubleClickException
 * ```
 */
@Aspect
object DebounceAspect {
    /**
     * 현재 시각을 밀리초로 반환하는 제공자.
     * 테스트에서 시간을 제어하기 위해 교체할 수 있습니다.
     */
    var timeProvider: () -> Long = { currentTimeMillis() }

    /** 함수명 → 마지막 호출 시각(ms) 매핑. */
    private val lastCallTime = mutableMapOf<String, Long>()

    @Before(PreventDoubleClick::class)
    fun checkDebounce(joinPoint: JoinPoint) {
        println(joinPoint)
//        val methodName = joinPoint.signature.methodName
//        val now = timeProvider()
//
//        val annotationInfo =
//            joinPoint.signature.annotations.firstOrNull {
//                it.typeName.contains("PreventDoubleClick")
//            }
//        val cooldownMs =
//            annotationInfo
//                ?.let { info ->
//                    val idx = info.parameterNames.indexOf("cooldownMs")
//                    info.args.getOrNull(idx) as? Long
//                } ?: 1000L
//
//        val lastTime = lastCallTime[methodName] ?: -cooldownMs
//        val elapsed = now - lastTime
//
//        if (elapsed < cooldownMs) {
//            val remaining = cooldownMs - elapsed
//            throw DoubleClickException(
//                "Function '$methodName' was called too rapidly. " +
//                    "Wait ${remaining}ms before calling again (cooldown: ${cooldownMs}ms).",
//            )
//        }
//
//        lastCallTime[methodName] = now
    }

    /** 모든 함수의 마지막 호출 시각을 초기화합니다. 테스트 케이스 간 격리에 사용하세요. */
    fun reset() = lastCallTime.clear()
}
