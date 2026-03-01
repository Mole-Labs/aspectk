package sample.multiplatform.aspects

import io.github.molelabs.aspectk.runtime.Aspect
import io.github.molelabs.aspectk.runtime.Before
import io.github.molelabs.aspectk.runtime.JoinPoint
import sample.multiplatform.annotations.RequirePermission
import sample.multiplatform.exceptions.PermissionDeniedException

/**
 * [RequirePermission] 어노테이션이 붙은 함수 실행 전 권한을 검사합니다.
 *
 * 권한이 없으면 [PermissionDeniedException]을 던져 함수 실행을 차단합니다.
 *
 * 사용 예:
 * ```kotlin
 * PermissionAspect.grantedPermissions += "ADMIN"
 *
 * @RequirePermission("ADMIN")
 * fun deleteUser(userId: String) { ... }
 * ```
 */
@Aspect
object PermissionAspect {
    /**
     * 현재 부여된 권한 목록.
     * 실제 앱에서는 세션 또는 인증 서비스에서 동적으로 주입합니다.
     */
    val grantedPermissions = mutableSetOf<String>()

    @Before(RequirePermission::class)
    fun checkPermission(joinPoint: JoinPoint) {
        val annotationInfo =
            joinPoint.signature.annotations.firstOrNull {
                it.typeName.contains("RequirePermission")
            } ?: return

        val idx = annotationInfo.parameterNames.indexOf("permission")
        val permission = annotationInfo.args.getOrNull(idx) as? String ?: return

        if (permission !in grantedPermissions) {
            throw PermissionDeniedException(
                "Permission '$permission' is required to call '${joinPoint.signature.methodName}' " +
                    "but was not granted. Granted: $grantedPermissions",
            )
        }
    }

    /** 모든 권한을 초기화합니다. 테스트 케이스 간 격리에 사용하세요. */
    fun revokeAll() = grantedPermissions.clear()
}
