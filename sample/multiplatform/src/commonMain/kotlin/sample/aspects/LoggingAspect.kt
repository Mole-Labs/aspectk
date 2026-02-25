package sample.aspects

import io.github.molelabs.aspectk.runtime.Aspect
import io.github.molelabs.aspectk.runtime.Before
import io.github.molelabs.aspectk.runtime.JoinPoint
import sample.annotations.LogExecution

/**
 * [LogExecution] 어노테이션이 붙은 함수 호출을 가로채서 콘솔에 로그를 출력합니다.
 *
 * 출력 형식: `[LEVEL][TAG] → methodName(param1=value1, param2=value2) : ReturnType`
 *
 * 사용 예:
 * ```kotlin
 * @LogExecution(tag = "UserService", level = "INFO")
 * fun login(username: String, password: String): Boolean { ... }
 * // → [INFO][UserService] → login(username=alice, password=***) : Boolean
 * ```
 */
@Aspect
object LoggingAspect {
    /** 로그 출력 핸들러. 테스트에서 오버라이드하여 출력 내용을 캡처할 수 있습니다. */
    var logger: (String) -> Unit = { message -> println(message) }

    /** 수집된 로그 메시지 목록. 테스트에서 사용합니다. */
    val logs = mutableListOf<String>()

    @Before(LogExecution::class)
    fun log(joinPoint: JoinPoint) {
        val signature = joinPoint.signature

        // @LogExecution 어노테이션의 tag, level 파라미터 추출
        val annotationInfo =
            signature.annotations.firstOrNull {
                it.typeName.contains("LogExecution")
            }
        val tag = annotationInfo?.argByName("tag") as? String ?: "ASPECTK"
        val level = annotationInfo?.argByName("level") as? String ?: "DEBUG"

        // 파라미터 목록 구성 (args[0]은 receiver, args[1..]이 파라미터)
        val paramStr =
            signature.parameter
                .mapIndexed { index, param ->
                    val value = joinPoint.args.getOrNull(index + 1)
                    val displayValue = if (param.name.contains("password", ignoreCase = true)) "***" else value
                    "${param.name}=$displayValue"
                }.joinToString(", ")

        val message = "[$level][$tag] → ${signature.methodName}($paramStr) : ${signature.returnTypeName.substringAfterLast('.')}"
        logs.add(message)
        logger(message)
    }

    fun clearLogs() = logs.clear()
}

/** [io.github.molelabs.aspectk.runtime.AnnotationInfo]에서 파라미터 이름으로 값을 조회하는 헬퍼. */
private fun io.github.molelabs.aspectk.runtime.AnnotationInfo.argByName(name: String): Any? {
    val index = parameterNames.indexOf(name)
    return if (index >= 0) args.getOrNull(index) else null
}
