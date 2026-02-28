package sample.multiplatform

import sample.multiplatform.aspects.LoggingAspect
import sample.multiplatform.aspects.PermissionAspect
import sample.multiplatform.service.UserService
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LoggingAspectTest {

    private lateinit var userService: UserService

    @BeforeTest
    fun setUp() {
        LoggingAspect.clearLogs()
        PermissionAspect.grantedPermissions += "ADMIN"
        PermissionAspect.grantedPermissions += "READ_USER_DATA"
        userService = UserService()
    }

    @AfterTest
    fun tearDown() {
        LoggingAspect.clearLogs()
        PermissionAspect.revokeAll()
    }

    @Test
    fun `LoggingAspect should record log when annotated function is called`() {
        userService.login("alice", "pass123")

        assertTrue(LoggingAspect.logs.isNotEmpty(), "로그가 기록되어야 합니다")
    }

    @Test
    fun `LoggingAspect should include method name in log`() {
        userService.login("alice", "pass123")

        val log = LoggingAspect.logs.first { it.contains("login") }
        assertTrue(log.contains("login"), "로그에 함수명이 포함되어야 합니다")
    }

    @Test
    fun `LoggingAspect should use custom tag and level from annotation`() {
        userService.login("alice", "pass123")

        val log = LoggingAspect.logs.first { it.contains("login") }
        assertTrue(log.contains("[INFO]"), "level=INFO가 로그에 반영되어야 합니다")
        assertTrue(log.contains("[UserService]"), "tag=UserService가 로그에 반영되어야 합니다")
    }

    @Test
    fun `LoggingAspect should mask password parameter`() {
        userService.login("alice", "secretpassword")

        val log = LoggingAspect.logs.first { it.contains("login") }
        assertTrue(log.contains("***"), "비밀번호는 마스킹되어야 합니다")
        assertTrue(!log.contains("secretpassword"), "실제 비밀번호가 로그에 노출되면 안 됩니다")
    }

    @Test
    fun `LoggingAspect should record multiple function calls`() {
        userService.login("alice", "pass")
        userService.createUser("bob", "bob@example.com")
        userService.getAllUsers()

        assertEquals(3, LoggingAspect.logs.size, "세 번의 함수 호출이 모두 기록되어야 합니다")
    }

    @Test
    fun `LoggingAspect should include return type in log`() {
        userService.getAllUsers()

        val log = LoggingAspect.logs.first { it.contains("getAllUsers") }
        assertTrue(log.contains("List"), "반환 타입이 로그에 포함되어야 합니다")
    }
}
