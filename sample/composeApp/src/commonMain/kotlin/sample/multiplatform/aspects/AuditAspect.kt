package sample.multiplatform.aspects

import io.github.molelabs.aspectk.runtime.Aspect
import io.github.molelabs.aspectk.runtime.Before
import io.github.molelabs.aspectk.runtime.JoinPoint
import sample.multiplatform.annotations.AuditAction

/**
 * [AuditAction] 어노테이션이 붙은 함수(및 그 오버라이딩 메서드)의 호출을 감사(Audit) 로그로 기록합니다.
 *
 * `inherits = true`를 사용하므로 부모 클래스에만 [AuditAction]이 붙어 있어도
 * 자식 클래스의 오버라이딩 메서드에 대한 Advice가 자동 적용됩니다.
 *
 * 출력 형식: `[AUDIT] action=<action> | method=<methodName>`
 */
@Aspect
object AuditAspect {
    /** 감사 로그 출력 핸들러. 테스트에서 오버라이드하여 출력 내용을 캡처할 수 있습니다. */
    var auditLogger: (String) -> Unit = { println(it) }

    /** 수집된 감사 로그 목록. 테스트에서 사용합니다. */
    val auditLogs = mutableListOf<String>()

    @Before(AuditAction::class, inherits = true)
    fun audit(joinPoint: JoinPoint) {
        val annotationInfo =
            joinPoint.signature.annotations
                .firstOrNull { it.typeName.contains("AuditAction") }
        val action =
            annotationInfo?.argByName("action") as? String
                ?: joinPoint.signature.methodName
        val message = "[AUDIT] action=$action | method=${joinPoint.signature.methodName}"
        auditLogs.add(message)
        auditLogger(message)
    }

    fun clearLogs() = auditLogs.clear()
}

private fun io.github.molelabs.aspectk.runtime.AnnotationInfo.argByName(name: String): Any? {
    val index = parameterNames.indexOf(name)
    return if (index >= 0) args.getOrNull(index) else null
}
