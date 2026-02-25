package sample.annotations

/**
 * 함수 호출 계층(스택 깊이)을 추적하여 트레이싱 로그를 출력합니다.
 *
 * @param spanName 트레이싱 스팬 이름. 비어있으면 함수명을 사용합니다.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class Trace(
    val spanName: String = "",
)
