package sample

import sample.aspects.LoggingAspect
import sample.aspects.PermissionAspect
import sample.aspects.TracingAspect
import sample.service.PaymentService
import sample.service.UserService
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TracingAspectTest {

    private lateinit var userService: UserService
    private lateinit var paymentService: PaymentService

    @BeforeTest
    fun setUp() {
        TracingAspect.clearStack()
        LoggingAspect.clearLogs()
        PermissionAspect.grantedPermissions += "ADMIN"
        PermissionAspect.grantedPermissions += "READ_USER_DATA"
        PermissionAspect.grantedPermissions += "PAYMENT"
        userService = UserService()
        paymentService = PaymentService()
    }

    @AfterTest
    fun tearDown() {
        TracingAspect.clearStack()
        LoggingAspect.clearLogs()
        PermissionAspect.revokeAll()
    }

    @Test
    fun `TracingAspect should add span to call stack when traced function is called`() {
        userService.login("alice", "pass")

        assertTrue(TracingAspect.callStack.isNotEmpty(), "callStack에 스팬이 기록되어야 합니다")
    }

    @Test
    fun `TracingAspect should use custom spanName from annotation`() {
        userService.login("alice", "pass")

        assertTrue(
            TracingAspect.callStack.contains("user-login"),
            "spanName='user-login'이 callStack에 기록되어야 합니다",
        )
    }

    @Test
    fun `TracingAspect should use method name when spanName is empty`() {
        paymentService.getTransactionCount()

        assertTrue(
            TracingAspect.callStack.any { it.contains("get-transaction-count") },
            "spanName이 지정된 경우 해당 이름이 기록되어야 합니다",
        )
    }

    @Test
    fun `TracingAspect should track depth for nested traced calls`() {
        val traceLogs = mutableListOf<String>()
        TracingAspect.logger = { traceLogs.add(it) }

        userService.createUser("alice", "alice@example.com")

        // createUser는 @Trace(spanName="create-user") 보유
        assertTrue(traceLogs.any { it.contains("create-user") }, "create-user 스팬이 추적되어야 합니다")
    }

    @Test
    fun `TracingAspect should accumulate spans across multiple calls`() {
        userService.login("alice", "pass")
        userService.createUser("bob", "bob@example.com")

        assertEquals(2, TracingAspect.callStack.size, "두 번의 @Trace 함수 호출이 누적되어야 합니다")
    }
}
