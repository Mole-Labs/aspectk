package sample.annotations

/**
 * 어노테이션이 붙은 함수 호출 시 함수명, 파라미터, 반환 타입 정보를 자동으로 로깅합니다.
 *
 * @param tag 로그 출력에 사용할 태그. 기본값은 "ASPECTK".
 * @param level 로그 레벨 문자열 (예: "DEBUG", "INFO", "WARN"). 기본값은 "DEBUG".
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class LogExecution(
    val tag: String = "ASPECTK",
    val level: String = "DEBUG",
)
