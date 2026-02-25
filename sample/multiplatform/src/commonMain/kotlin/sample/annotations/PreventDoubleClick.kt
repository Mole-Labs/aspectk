package sample.annotations

/**
 * 짧은 시간 안에 같은 함수가 중복 호출되는 것을 방지합니다.
 * 쿨다운 시간 내에 재호출되면 [sample.exceptions.DoubleClickException]을 던집니다.
 *
 * @param cooldownMs 중복 호출을 막을 최소 시간 간격 (밀리초). 기본값은 1000ms (1초).
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class PreventDoubleClick(
    val cooldownMs: Long = 1000L,
)
