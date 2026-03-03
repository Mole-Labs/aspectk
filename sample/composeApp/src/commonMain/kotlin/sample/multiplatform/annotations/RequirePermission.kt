package sample.multiplatform.annotations

/**
 * 함수 실행 전 지정한 권한이 있는지 검사합니다.
 * 권한이 없으면 [sample.multiplatform.exceptions.PermissionDeniedException]을 던져 함수 실행을 막습니다.
 *
 * @param permission 필요한 권한 이름 (예: "ADMIN", "READ_USER_DATA").
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class RequirePermission(
    val permission: String,
)
