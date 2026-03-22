package sample.multiplatform.aspects

import io.github.molelabs.aspectk.runtime.Around
import io.github.molelabs.aspectk.runtime.Aspect
import io.github.molelabs.aspectk.runtime.ProceedingJoinPoint
import sample.multiplatform.annotations.Timed
import sample.multiplatform.platform.currentTimeMillis

/**
 * [Timed] 어노테이션이 붙은 함수의 실행 시간을 측정합니다.
 *
 * `@Around` 어드바이스를 사용하므로 원본 함수의 실행을 직접 제어합니다.
 * [ProceedingJoinPoint.proceed]를 호출하기 전과 후의 시각 차이를 측정하여 로그로 출력하고,
 * 원본 함수의 반환값을 그대로 호출자에게 전달합니다.
 *
 * 출력 형식: `[TIMED] methodName completed in Xms`
 *
 * 사용 예:
 * ```kotlin
 * @Timed
 * fun requestPayment(orderId: String, amount: Double): String { ... }
 * // → [TIMED] requestPayment completed in 12ms
 * ```
 */
@Aspect
object TimingAspect {
    /**
     * 현재 시각을 밀리초로 반환하는 제공자.
     * 테스트에서 시간을 제어하기 위해 교체할 수 있습니다.
     */
    var clock: () -> Long = { currentTimeMillis() }

    /** 수집된 측정 결과 목록. 테스트에서 사용합니다. */
    val timings = mutableListOf<String>()

    /** 타이밍 로그 출력 핸들러. 테스트에서 오버라이드할 수 있습니다. */
    var logger: (String) -> Unit = { println(it) }

    @Around(Timed::class)
    fun measure(joinPoint: ProceedingJoinPoint): Any? {
        val methodName = joinPoint.signature.methodName
        val start = clock()
        val result = joinPoint.proceed()
        val elapsed = clock() - start
        val message = "[TIMED] $methodName completed in ${elapsed}ms"
        timings.add(message)
        logger(message)
        return result
    }

    /** 수집된 측정 결과를 초기화합니다. 각 테스트 케이스 시작 전에 호출하세요. */
    fun clearTimings() = timings.clear()
}