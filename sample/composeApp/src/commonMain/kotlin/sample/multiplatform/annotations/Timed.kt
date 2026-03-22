package sample.multiplatform.annotations

/**
 * 함수의 실행 시간을 밀리초 단위로 측정하여 로그에 출력합니다.
 *
 * [sample.multiplatform.aspects.TimingAspect]가 `@Around` 어드바이스로
 * [ProceedingJoinPoint.proceed][io.github.molelabs.aspectk.runtime.ProceedingJoinPoint.proceed]
 * 호출 전후의 시간 차이를 측정합니다.
 *
 * 사용 예:
 * ```kotlin
 * @Timed
 * fun requestPayment(orderId: String, amount: Double): String { ... }
 * // → [TIMED] requestPayment completed in 12ms
 * ```
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class Timed