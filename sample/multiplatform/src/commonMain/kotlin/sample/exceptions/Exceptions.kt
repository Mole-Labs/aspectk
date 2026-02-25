package sample.exceptions

/** [sample.annotations.RequirePermission] 검사 실패 시 던져지는 예외. */
class PermissionDeniedException(message: String) : Exception(message)

/** [sample.annotations.PreventDoubleClick] 쿨다운 내 중복 호출 시 던져지는 예외. */
class DoubleClickException(message: String) : Exception(message)
